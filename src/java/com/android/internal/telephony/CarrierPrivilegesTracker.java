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

import static android.telephony.CarrierConfigManager.KEY_CARRIER_CERTIFICATE_STRING_ARRAY;

import android.annotation.NonNull;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.Signature;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PersistableBundle;
import android.os.UserHandle;
import android.telephony.CarrierConfigManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
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
     * Action for registering a Listener with this Tracker.
     * obj: {@link CarrierPrivilegesListener} to be registered
     */
    private static final int ACTION_REGISTER_LISTENER = 1;

    /**
     * Action for unregistering a Listener with this Tracker.
     * obj: {@link CarrierPrivilegesListener} to be unregistered
     */
    private static final int ACTION_UNREGISTER_LISTENER = 2;

    /**
     * Action for tracking when Carrier Configs are updated.
     * obj: {@link Bundle} of extras from the received Broadcast Intent.
     */
    private static final int ACTION_CARRIER_CONFIG_CERTS_UPDATED = 3;

    /**
     * Action for tracking when the Phone's SIM state changes.
     *
     */
    private static final int ACTION_SIM_STATE_CHANGED = 4;

    private final Phone mPhone;
    private final Context mContext;
    private final CarrierConfigManager mCarrierConfigManager;
    private final PackageManager mPackageManager;
    private final TelephonyManager mTelephonyManager;

    @VisibleForTesting public final Set<CarrierPrivilegesListener> mListeners;
    @VisibleForTesting public final Set<String> mCarrierConfigCerts;
    @VisibleForTesting public final Set<String> mUiccCerts;
    @VisibleForTesting public final Set<Integer> mAdministratorUids;
    @VisibleForTesting public final List<PackageInfo> mPackageInfos;

    private final BroadcastReceiver mIntentReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (action == null) return;

                    switch (action) {
                        case CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED: {
                            sendMessage(
                                    obtainMessage(
                                            ACTION_CARRIER_CONFIG_CERTS_UPDATED,
                                            intent.getExtras()));
                            break;
                        }
                        case TelephonyManager.ACTION_SIM_CARD_STATE_CHANGED: {
                            sendMessage(obtainMessage(ACTION_SIM_STATE_CHANGED));
                        }
                    }
                }
            };

    /** @hide */
    public CarrierPrivilegesTracker(
            @NonNull Phone phone,
            @NonNull Looper looper,
            @NonNull CarrierConfigManager carrierConfigManager,
            @NonNull PackageManager packageManager,
            @NonNull TelephonyManager telephonyManager) {
        super(looper);
        mPhone = phone;
        mContext = phone.getContext();
        mCarrierConfigManager = carrierConfigManager;
        mPackageManager = packageManager;
        mTelephonyManager = telephonyManager.createForSubscriptionId(phone.getSubId());

        IntentFilter filter = new IntentFilter();
        filter.addAction(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        filter.addAction(TelephonyManager.ACTION_SIM_CARD_STATE_CHANGED);
        mContext.registerReceiver(mIntentReceiver, filter);

        mListeners = new HashSet<>();
        mCarrierConfigCerts = new HashSet<>();
        mUiccCerts = new HashSet<>();
        mAdministratorUids = new HashSet<>();
        mPackageInfos = new ArrayList<>();
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case ACTION_REGISTER_LISTENER: {
                CarrierPrivilegesListener listener = (CarrierPrivilegesListener) msg.obj;
                mListeners.add(listener);

                listener.onCarrierPrivilegesChanged(
                        Collections.unmodifiableSet(mAdministratorUids));
                break;
            }
            case ACTION_UNREGISTER_LISTENER: {
                CarrierPrivilegesListener listener = (CarrierPrivilegesListener) msg.obj;
                mListeners.remove(listener);
                break;
            }
            case ACTION_CARRIER_CONFIG_CERTS_UPDATED: {
                handleCarrierConfigCertsUpdated((Bundle) msg.obj);
                break;
            }
            case ACTION_SIM_STATE_CHANGED: {
                handleSimStateChanged();
                break;
            }
        }
    }

    /** @hide */
    @VisibleForTesting
    public void handleCarrierConfigCertsUpdated(Bundle extras) {
        if (extras == null) return;

        int slotIndex = extras.getInt(CarrierConfigManager.EXTRA_SLOT_INDEX);
        if (mPhone.getPhoneId() != slotIndex) return; // Config doesn't affect us

        PersistableBundle carrierConfigs =
                mCarrierConfigManager.getConfigForSubId(mPhone.getSubId());

        // no certs available from carrier configs for this subId
        if (!carrierConfigs.containsKey(KEY_CARRIER_CERTIFICATE_STRING_ARRAY)) return;

        String[] carrierConfigCerts =
                carrierConfigs.getStringArray(KEY_CARRIER_CERTIFICATE_STRING_ARRAY);
        Set<String> updatedCarrierConfigCerts = new HashSet<>();
        for (String cert : carrierConfigCerts) {
            // TODO(b/150221469): explore how to efficiently protect against cert casing
            updatedCarrierConfigCerts.add(cert.toUpperCase());
            updatedCarrierConfigCerts.add(cert.toLowerCase());
        }

        if (!mCarrierConfigCerts.equals(updatedCarrierConfigCerts)) {
            mCarrierConfigCerts.clear();
            mCarrierConfigCerts.addAll(updatedCarrierConfigCerts);
            updateCarrierPrivilegeUids();
        }
    }

    /** @hide */
    @VisibleForTesting
    public void handleSimStateChanged() {
        Set<String> updatedUiccCerts = new HashSet<>();
        if (mTelephonyManager.hasIccCard(mPhone.getPhoneId())) {
            List<String> uiccCerts = mTelephonyManager.getCertsFromCarrierPrivilegeAccessRules();
            if (uiccCerts != null) {
                for (String cert : uiccCerts) {
                    // TODO(b/150221469): explore how to efficiently protect against cert casing
                    updatedUiccCerts.add(cert.toUpperCase());
                    updatedUiccCerts.add(cert.toLowerCase());
                }
            }
        }

        if (!mUiccCerts.equals(updatedUiccCerts)) {
            mUiccCerts.clear();
            mUiccCerts.addAll(updatedUiccCerts);
            updateCarrierPrivilegeUids();
        }
    }

    private void updateCarrierPrivilegeUids() {
        Set<Integer> administratorUids = new HashSet<>();
        for (PackageInfo packageInfo : mPackageInfos) {
            Signature[] signatures = packageInfo.signingInfo.getApkContentsSigners();
            for (Signature signature : signatures) {
                String cert = signature.toCharsString();
                if (mCarrierConfigCerts.contains(cert) || mUiccCerts.contains(cert)) {
                    try {
                        int uid =
                                mPackageManager.getPackageUidAsUser(
                                        packageInfo.packageName, UserHandle.USER_SYSTEM);
                        administratorUids.add(uid);
                    } catch (NameNotFoundException e) {
                        // Didn't find package. Continue looking at other packages
                        Log.e(TAG, "Unable to find uid for package: " + packageInfo.packageName);
                        break;
                    }
                }
            }
        }

        if (!mAdministratorUids.equals(administratorUids)) {
            mAdministratorUids.clear();
            mAdministratorUids.addAll(administratorUids);

            Set<Integer> unmodifiableAdministratorUids =
                    Collections.unmodifiableSet(mAdministratorUids);
            for (CarrierPrivilegesListener listener : mListeners) {
                listener.onCarrierPrivilegesChanged(unmodifiableAdministratorUids);
            }
        }
    }

    /**
     * Registers the given listener with this tracker.
     *
     * <p>After being registered, {@link CarrierPrivilegesListener#registerCarrierPrivilegeListener}
     * will be invoked with the current Carrier Privileged UIDs for this Phone.
     *
     * @hide
     */
    public void registerCarrierPrivilegeListener(@NonNull CarrierPrivilegesListener listener) {
        sendMessage(obtainMessage(ACTION_REGISTER_LISTENER, listener));
    }

    /**
     * Unregisters the given listener with this tracker.
     *
     * @hide
     */
    public void unregisterCallback(@NonNull CarrierPrivilegesListener listener) {
        sendMessage(obtainMessage(ACTION_UNREGISTER_LISTENER, listener));
    }

    /**
     * CarrierPrivilegesListener is an abstract class that Telephony entities can define in order to
     * receive updates when Carrier Privileged UIDs change.
     *
     * @hide
     */
    public abstract static class CarrierPrivilegesListener {
        /**
         * This method will be invoked whenever the CarrierPrivilegesTracker detects a change in
         * Carrier Privileged UIDs for the Phone this listener is registered under.
         *
         * @hide
         */
        public abstract void onCarrierPrivilegesChanged(@NonNull Set<Integer> uids);
    }
}
