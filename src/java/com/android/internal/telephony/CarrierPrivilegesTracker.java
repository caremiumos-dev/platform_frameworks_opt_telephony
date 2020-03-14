/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.telephony;

import static android.telephony.CarrierConfigManager.EXTRA_SUBSCRIPTION_INDEX;
import static android.telephony.CarrierConfigManager.KEY_CARRIER_CERTIFICATE_STRING_ARRAY;
import static android.telephony.TelephonyManager.SIM_STATE_ABSENT;
import static android.telephony.TelephonyManager.SIM_STATE_LOADED;
import static android.telephony.TelephonyManager.SIM_STATE_NOT_READY;

import android.annotation.NonNull;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PersistableBundle;
import android.os.Process;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.UserHandle;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.ArrayMap;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CarrierPrivilegesTracker will track the Carrier Privileges for a specific {@link Phone}.
 * Registered Telephony entities will receive notifications when UIDs gain or lose these privileges.
 *
 * @hide
 */
public class CarrierPrivilegesTracker extends Handler {
    private static final String TAG = CarrierPrivilegesTracker.class.getSimpleName();

    /**
     * Action for tracking when Carrier Configs are updated.
     * obj: {@link Bundle} of extras from the received Broadcast Intent.
     */
    private static final int ACTION_CARRIER_CONFIG_CERTS_UPDATED = 1;

    /**
     * Action for tracking when the Phone's SIM state changes.
     * arg1: slotId that this Action applies to
     * arg2: simState reported by this Broadcast
     */
    private static final int ACTION_SIM_STATE_UPDATED = 2;

    /**
     * Action for tracking when a package is installed on the device.
     * arg1: uid for the installed package. If this is not included in the Intent, {@link
     * Process#INVALID_UID} will be used.
     * obj: String package name that was installed on the device.
     */
    private static final int ACTION_PACKAGE_ADDED = 3;

    /**
     * Action for tracking when a package is uninstalled on the device.
     * obj: String package name that was uninstalled on the device.
     */
    private static final int ACTION_PACKAGE_REMOVED = 4;

    /**
     * Action used to initialize the state of the Tracker.
     */
    private static final int ACTION_INITIALIZE_TRACKER = 5;

    private final Context mContext;
    private final Phone mPhone;
    private final CarrierConfigManager mCarrierConfigManager;
    private final PackageManager mPackageManager;
    private final TelephonyManager mTelephonyManager;
    private final RegistrantList mRegistrantList;
    private final Set<String> mCarrierConfigCerts;
    private final Set<String> mUiccCerts;
    private final List<PackageInfo> mPackageInfos;
    private final Map<PackageInfo, Integer> mCachedUids;
    private int[] mPrivilegedUids;

    private final BroadcastReceiver mIntentReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (action == null) return;

                    switch (action) {
                        case CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED: {
                            Bundle extras = intent.getExtras();
                            int subId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
                            if (extras.containsKey(EXTRA_SUBSCRIPTION_INDEX)) {
                                subId = extras.getInt(EXTRA_SUBSCRIPTION_INDEX);
                            }
                            sendMessage(
                                    obtainMessage(ACTION_CARRIER_CONFIG_CERTS_UPDATED, subId));
                            break;
                        }
                        case TelephonyManager.ACTION_SIM_CARD_STATE_CHANGED: // fall through
                        case TelephonyManager.ACTION_SIM_APPLICATION_STATE_CHANGED: {
                            Bundle extras = intent.getExtras();
                            int simState = extras.getInt(TelephonyManager.EXTRA_SIM_STATE);
                            int slotId = extras.getInt(PhoneConstants.SLOT_KEY);

                            if (simState != SIM_STATE_ABSENT
                                    && simState != SIM_STATE_NOT_READY
                                    && simState != SIM_STATE_LOADED) return;

                            sendMessage(obtainMessage(ACTION_SIM_STATE_UPDATED, slotId, simState));
                            break;
                        }
                        case Intent.ACTION_PACKAGE_ADDED: {
                            Uri uri = intent.getData();
                            String pkgName = (uri != null) ? uri.getSchemeSpecificPart() : null;
                            if (pkgName == null) {
                                Log.e(TAG, "Failed to get package from Intent");
                                return;
                            }

                            Bundle extras = intent.getExtras();
                            int uid = Process.INVALID_UID;
                            if (extras.containsKey(Intent.EXTRA_UID)) {
                                uid = extras.getInt(Intent.EXTRA_UID);
                            }
                            sendMessage(obtainMessage(ACTION_PACKAGE_ADDED, uid, 0, pkgName));
                            break;
                        }
                        case Intent.ACTION_PACKAGE_REMOVED: {
                            Uri uri = intent.getData();
                            String pkgName = (uri != null) ? uri.getSchemeSpecificPart() : null;
                            if (pkgName == null) {
                                Log.e(TAG, "Failed to get package from Intent");
                                return;
                            }
                            sendMessage(obtainMessage(ACTION_PACKAGE_REMOVED, pkgName));
                            break;
                        }
                    }
                }
            };

    /** @hide */
    public CarrierPrivilegesTracker(
            @NonNull Looper looper, @NonNull Phone phone, @NonNull Context context) {
        super(looper);
        mContext = context;
        mCarrierConfigManager = context.getSystemService(CarrierConfigManager.class);
        mPackageManager = context.getSystemService(PackageManager.class);
        mTelephonyManager = context.getSystemService(TelephonyManager.class);
        mPhone = phone;

        IntentFilter filter = new IntentFilter();
        filter.addAction(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        filter.addAction(TelephonyManager.ACTION_SIM_CARD_STATE_CHANGED);
        filter.addAction(TelephonyManager.ACTION_SIM_APPLICATION_STATE_CHANGED);
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        mContext.registerReceiver(mIntentReceiver, filter);

        mRegistrantList = new RegistrantList();
        mCarrierConfigCerts = new HashSet<>();
        mUiccCerts = new HashSet<>();
        mPackageInfos = new ArrayList<>();
        mCachedUids = new ArrayMap<>();
        mPrivilegedUids = new int[0];

        sendMessage(obtainMessage(ACTION_INITIALIZE_TRACKER));
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case ACTION_CARRIER_CONFIG_CERTS_UPDATED: {
                handleCarrierConfigUpdated((int) msg.obj);
                break;
            }
            case ACTION_SIM_STATE_UPDATED: {
                handleSimStateChanged(msg.arg1, msg.arg2);
                break;
            }
            case ACTION_PACKAGE_ADDED: {
                handlePackageAdded(msg.arg1, (String) msg.obj);
                break;
            }
            case ACTION_PACKAGE_REMOVED: {
                handlePackageRemoved((String) msg.obj);
                break;
            }
            case ACTION_INITIALIZE_TRACKER: {
                handleInitializeTracker();
                break;
            }
            default: {
                Log.e(TAG, "Received unknown msg type: " + msg.what);
                break;
            }
        }
    }

    private void handleCarrierConfigUpdated(int subId) {
        Set<String> updatedCarrierConfigCerts = Collections.EMPTY_SET;

        if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            updatedCarrierConfigCerts = getCarrierConfigCerts(subId);
        }

        maybeUpdateCertsAndNotifyRegistrants(mCarrierConfigCerts, updatedCarrierConfigCerts);
    }

    private Set<String> getCarrierConfigCerts(int subId) {
        Set<String> updatedCarrierConfigCerts = new HashSet<>();

        PersistableBundle carrierConfigs = mCarrierConfigManager.getConfigForSubId(subId);
        if (!mCarrierConfigManager.isConfigForIdentifiedCarrier(carrierConfigs)) {
            return updatedCarrierConfigCerts;
        }

        String[] carrierConfigCerts =
                carrierConfigs.getStringArray(KEY_CARRIER_CERTIFICATE_STRING_ARRAY);

        if (carrierConfigCerts != null) {
            for (String cert : carrierConfigCerts) {
                // TODO(b/150221469): explore how to efficiently protect against cert casing
                updatedCarrierConfigCerts.add(cert.toUpperCase());
                updatedCarrierConfigCerts.add(cert.toLowerCase());
            }
        }
        return updatedCarrierConfigCerts;
    }

    private void handleSimStateChanged(int slotId, int simState) {
        if (slotId != mPhone.getPhoneId()) return;

        Set<String> updatedUiccCerts = Collections.EMPTY_SET;

        // Only update the UICC certs if the SIM is fully loaded
        if (simState == SIM_STATE_LOADED) {
            updatedUiccCerts = getSimCerts();
        }

        maybeUpdateCertsAndNotifyRegistrants(mUiccCerts, updatedUiccCerts);
    }

    private Set<String> getSimCerts() {
        Set<String> updatedUiccCerts = new HashSet<>();
        TelephonyManager telMan = mTelephonyManager.createForSubscriptionId(mPhone.getSubId());
        if (telMan.hasIccCard(mPhone.getPhoneId())) {
            List<String> uiccCerts = telMan.getCertsFromCarrierPrivilegeAccessRules();
            if (uiccCerts != null) {
                for (String cert : uiccCerts) {
                    // TODO(b/150221469): explore how to efficiently protect against cert casing
                    updatedUiccCerts.add(cert.toUpperCase());
                    updatedUiccCerts.add(cert.toLowerCase());
                }
            }
        }
        return updatedUiccCerts;
    }

    private void handlePackageAdded(int uid, String pkgName) {
        PackageInfo pkg;
        try {
            pkg = mPackageManager.getPackageInfo(pkgName, PackageManager.GET_SIGNING_CERTIFICATES);
        } catch (NameNotFoundException e) {
            Log.e(TAG, "Error getting installed package", e);
            return;
        }

        if (uid != Process.INVALID_UID) {
            mCachedUids.put(pkg, uid);
        }

        maybeUpdatePrivilegedUidsAndNotifyRegistrants();
    }

    private void handlePackageRemoved(String pkgName) {
        boolean pkgFound = false;
        for (PackageInfo pkg : mPackageInfos) {
            if (pkg.packageName.equals(pkgName)) {
                pkgFound = true;
                mCachedUids.remove(pkg);
                break;
            }
        }

        if (!pkgFound) {
            Log.e(TAG, "Unknown package was uninstalled: " + pkgName);
            return;
        }

        maybeUpdatePrivilegedUidsAndNotifyRegistrants();
    }

    private void handleInitializeTracker() {
        // Cache CarrierConfig Certs
        mCarrierConfigCerts.addAll(getCarrierConfigCerts(mPhone.getSubId()));

        // Cache SIM certs
        mUiccCerts.addAll(getSimCerts());

        // Cache all installed packages
        mPackageInfos.addAll(
                mPackageManager.getInstalledPackages(PackageManager.GET_SIGNING_CERTIFICATES));

        // Okay because no registrants exist yet
        maybeUpdatePrivilegedUidsAndNotifyRegistrants();
    }

    private void maybeUpdateCertsAndNotifyRegistrants(
            Set<String> currentCerts, Set<String> updatedCerts) {
        if (!currentCerts.equals(updatedCerts)) {
            currentCerts.clear();
            currentCerts.addAll(updatedCerts);

            maybeUpdatePrivilegedUidsAndNotifyRegistrants();
        }
    }

    private void maybeUpdatePrivilegedUidsAndNotifyRegistrants() {
        int[] updatedPrivilegedUids = getUpdatedPrivilegedUids();

        // Sort UIDs for the equality check
        Arrays.sort(updatedPrivilegedUids);
        if (!Arrays.equals(mPrivilegedUids, updatedPrivilegedUids)) {
            mPrivilegedUids = updatedPrivilegedUids;
            mRegistrantList.notifyResult(mPrivilegedUids);
        }
    }

    private int[] getUpdatedPrivilegedUids() {
        Set<Integer> privilegedUids = new HashSet<>();

        for (PackageInfo pkg : mPackageInfos) {
            if (isPackagePrivileged(pkg)) {
                int uid;
                try {
                    uid = getUidForPackage(pkg);
                } catch (NameNotFoundException e) {
                    // Didn't find package. Continue looking at other packages
                    Log.e(TAG, "Unable to find uid for package: " + pkg.packageName);
                    continue;
                }
                privilegedUids.add(uid);
            }
        }

        int[] result = new int[privilegedUids.size()];
        int index = 0;
        for (int uid : privilegedUids) {
            result[index] = uid;
            index++;
        }
        return result;
    }

    private boolean isPackagePrivileged(PackageInfo pkg) {
        Signature[] signatures = pkg.signingInfo.getApkContentsSigners();
        for (Signature signature : signatures) {
            String cert = signature.toCharsString();
            if (mCarrierConfigCerts.contains(cert) || mUiccCerts.contains(cert)) {
                return true;
            }
        }
        return false;
    }

    private int getUidForPackage(PackageInfo pkg) throws NameNotFoundException {
        if (mCachedUids.containsKey(pkg)) {
            return mCachedUids.get(pkg);
        }
        int uid = mPackageManager.getPackageUidAsUser(pkg.packageName, UserHandle.USER_SYSTEM);
        mCachedUids.put(pkg, uid);
        return uid;
    }

    /**
     * Registers the given Registrant with this tracker.
     *
     * <p>After being registered, the Registrant will be notified with the current Carrier
     * Privileged UIDs for this Phone.
     *
     * @hide
     */
    public void registerCarrierPrivilegesListener(Registrant registrant) {
        mRegistrantList.add(registrant);
        registrant.notifyResult(mPrivilegedUids);
    }

    /**
     * Unregisters the given listener with this tracker.
     *
     * @hide
     */
    public void unregisterCarrierPrivilegesListener(Handler handler) {
        mRegistrantList.remove(handler);
    }
}
