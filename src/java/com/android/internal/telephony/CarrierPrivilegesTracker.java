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
import android.telephony.SubscriptionManager;
import android.util.ArraySet;
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

    private final Phone mPhone;
    private final Context mContext;
    private final CarrierConfigManager mCarrierConfigManager;
    private final PackageManager mPackageManager;

    @VisibleForTesting public final Set<CarrierPrivilegesListener> mListeners;
    @VisibleForTesting public final Set<String> mCarrierConfigCerts;
    @VisibleForTesting public final Set<Integer> mPrivilegedUids;
    @VisibleForTesting public final List<PackageInfo> mPackageInfos;

    private final BroadcastReceiver mIntentReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (action == null) return;

                    switch (action) {
                        case CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED: {
                            Bundle extras = intent.getExtras();
                            int subId;
                            if (extras.containsKey(EXTRA_SUBSCRIPTION_INDEX)) {
                                subId = extras.getInt(EXTRA_SUBSCRIPTION_INDEX);
                            } else {
                                subId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
                            }
                            sendMessage(
                                    obtainMessage(ACTION_CARRIER_CONFIG_CERTS_UPDATED, subId));
                            break;
                        }
                    }
                }
            };

    /** @hide */
    public CarrierPrivilegesTracker(
            @NonNull Looper looper,
            @NonNull Phone phone,
            @NonNull CarrierConfigManager carrierConfigManager,
            @NonNull PackageManager packageManager) {
        super(looper);
        mPhone = phone;
        mContext = phone.getContext();
        mCarrierConfigManager = carrierConfigManager;
        mPackageManager = packageManager;

        IntentFilter filter = new IntentFilter();
        filter.addAction(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        mContext.registerReceiver(mIntentReceiver, filter);

        mListeners = new HashSet<>();
        mCarrierConfigCerts = new HashSet<>();
        mPrivilegedUids = new ArraySet<>();
        mPackageInfos = new ArrayList<>();
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case ACTION_REGISTER_LISTENER: {
                CarrierPrivilegesListener listener = (CarrierPrivilegesListener) msg.obj;
                mListeners.add(listener);

                listener.onCarrierPrivilegesChanged(
                        Collections.unmodifiableSet(mPrivilegedUids));
                break;
            }
            case ACTION_UNREGISTER_LISTENER: {
                CarrierPrivilegesListener listener = (CarrierPrivilegesListener) msg.obj;
                mListeners.remove(listener);
                break;
            }
            case ACTION_CARRIER_CONFIG_CERTS_UPDATED: {
                handleCarrierConfigCertsUpdated((int) msg.obj);
                break;
            }
        }
    }

    /** @hide */
    @VisibleForTesting
    public void handleCarrierConfigCertsUpdated(int subId) {
        Set<String> updatedCarrierConfigCerts = new HashSet<>();

        if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            PersistableBundle carrierConfigs = mCarrierConfigManager.getConfigForSubId(subId);
            String[] carrierConfigCerts =
                    carrierConfigs.getStringArray(KEY_CARRIER_CERTIFICATE_STRING_ARRAY);

            if (carrierConfigCerts != null) {
                for (String cert : carrierConfigCerts) {
                    // TODO(b/150221469): explore how to efficiently protect against cert casing
                    updatedCarrierConfigCerts.add(cert.toUpperCase());
                    updatedCarrierConfigCerts.add(cert.toLowerCase());
                }
            }
        }

        if (!mCarrierConfigCerts.equals(updatedCarrierConfigCerts)) {
            mCarrierConfigCerts.clear();
            mCarrierConfigCerts.addAll(updatedCarrierConfigCerts);
            updateCarrierPrivilegedUids();
        }
    }

    private void updateCarrierPrivilegedUids() {
        Set<Integer> administratorUids = new HashSet<>();
        for (PackageInfo packageInfo : mPackageInfos) {
            Signature[] signatures = packageInfo.signingInfo.getApkContentsSigners();
            for (Signature signature : signatures) {
                String cert = signature.toCharsString();
                if (mCarrierConfigCerts.contains(cert)) {
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

        if (!mPrivilegedUids.equals(administratorUids)) {
            mPrivilegedUids.clear();
            mPrivilegedUids.addAll(administratorUids);

            Set<Integer> unmodifiableAdministratorUids =
                    Collections.unmodifiableSet(mPrivilegedUids);
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
    public interface CarrierPrivilegesListener {
        /**
         * This method will be invoked whenever the CarrierPrivilegesTracker detects a change in
         * Carrier Privileged UIDs for the Phone this listener is registered under.
         *
         * <p>Long running operations should not be done in implementations of this method.
         *
         * @hide
         */
        void onCarrierPrivilegesChanged(@NonNull Set<Integer> privilegedUids);
    }
}
