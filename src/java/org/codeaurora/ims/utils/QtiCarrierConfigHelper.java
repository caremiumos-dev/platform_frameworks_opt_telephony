/*
 * Copyright (c) 2017, The Linux Foundation. All rights reserved.
 *
 * Not a Contribution.
 * Copyright (C) 2016 The Android Open Source Project
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

package org.codeaurora.ims.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class QtiCarrierConfigHelper {
    private CarrierConfigManager mCarrierConfigManager;
    private Map<Integer, PersistableBundle> mConfigsMap;
    private Context mContext;
    private AtomicBoolean mInitialized;
    private final SubscriptionManager.OnSubscriptionsChangedListener mOnSubscriptionsChangeListener;
    private final BroadcastReceiver mReceiver;
    private SubscriptionManager mSubscriptionManager;
    private static final String TAG = QtiCarrierConfigHelper.class.getSimpleName();
    private static int PHONE_COUNT = TelephonyManager.getDefault().getPhoneCount();

    private static class SingletonHolder {
        public static final QtiCarrierConfigHelper sInstance = new QtiCarrierConfigHelper(null);

        private SingletonHolder() {
        }
    }

    private QtiCarrierConfigHelper() {
        mConfigsMap = new ConcurrentHashMap<>();
        mInitialized = new AtomicBoolean(false);
        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null
                        || !intent.getAction().equals(
                                CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED)) {
                    return;
                }
                int subId = intent.getIntExtra("subscription", -1);
                if (mSubscriptionManager == null) {
                    return;
                }
                SubscriptionInfo info = mSubscriptionManager.getActiveSubscriptionInfo(subId);
                if (info != null) {
                    Log.d(TAG, "Reload carrier configs on sub Id: " + subId);
                    loadConfigsForSubInfo(info);
                    return;
                }
                int phoneId = intent.getIntExtra("phone", -1);
                if (mCarrierConfigManager == null
                        || mCarrierConfigManager.getConfigForSubId(subId) != null) {
                    return;
                }
                mConfigsMap.remove(Integer.valueOf(phoneId));
                Log.d(TAG, "Clear carrier configs on phone Id: " + phoneId);
            }
        };
        mOnSubscriptionsChangeListener = new SubscriptionManager.OnSubscriptionsChangedListener() {
            @Override
            public void onSubscriptionsChanged() {
                if (mSubscriptionManager == null) {
                    return;
                }
                List<SubscriptionInfo> infoList = mSubscriptionManager.getActiveSubscriptionInfoList();
                if (infoList == null) {
                    return;
                }
                for (SubscriptionInfo info : infoList) {
                    Log.d(TAG, "Reload carrier configs on sub Id due sub changed: "
                            + info.getSubscriptionId());
                    loadConfigsForSubInfo(info);
                }
            }
        };
    }

    QtiCarrierConfigHelper(QtiCarrierConfigHelper other) {
        this();
    }

    public static QtiCarrierConfigHelper getInstance() {
        return SingletonHolder.sInstance;
    }

    private void loadConfigsForSubInfo(SubscriptionInfo info) {
        if (info == null || mCarrierConfigManager == null) {
            return;
        }
        PersistableBundle config = mCarrierConfigManager.getConfigForSubId(
                info.getSubscriptionId());
        if (config != null) {
            Log.d(TAG, "Load carrier configs on sub Id: " + info.getSubscriptionId()
                    + " slot Id: " + info.getSimSlotIndex());
            mConfigsMap.put(Integer.valueOf(info.getSimSlotIndex()), config);
        } else {
            Log.d(TAG, "No configs on sub Id: " + info.getSubscriptionId());
            mConfigsMap.put(Integer.valueOf(info.getSimSlotIndex()), null);
        }
    }

    private void sanityCheckConfigsLoaded(Context context, int phoneId) {
        if (context == null || !mInitialized.compareAndSet(false, true)) {
            return;
        }
        setup(context);
    }

    public boolean getBoolean(int phoneId, String key) {
        if (!isValidPhoneId(phoneId)) {
            Log.d(TAG, "Invalid phone ID: " + phoneId);
            return false;
        }
        PersistableBundle config = mConfigsMap.get(Integer.valueOf(phoneId));
        if (config != null) {
            return config.getBoolean(key, false);
        }
        if (mInitialized.get()) {
            Log.d(TAG, "WARNING, no carrier configs on phone Id: " + phoneId);
            return false;
        }
        Log.d(TAG, "WARNING, Don't set up yet.");
        return false;
    }

    public boolean getBoolean(Context context, int phoneId, String key) {
        if (!isValidPhoneId(phoneId)) {
            Log.d(TAG, "Invalid phone ID: " + phoneId);
            return false;
        }
        sanityCheckConfigsLoaded(context, phoneId);
        PersistableBundle config = mConfigsMap.get(Integer.valueOf(phoneId));
        if (config != null) {
            return config.getBoolean(key, false);
        }
        Log.d(TAG, "WARNING, no carrier configs on phone Id: " + phoneId);
        return false;
    }

    public boolean isValidPhoneId(int phoneId) {
        return phoneId >= 0 && phoneId < PHONE_COUNT;
    }

    public void setup(Context context) {
        if (context == null) {
            return;
        }
        mContext = context.getApplicationContext();
        if (mContext != null) {
            mInitialized.set(true);
            mSubscriptionManager = SubscriptionManager.from(mContext);
            mCarrierConfigManager = (CarrierConfigManager) mContext.getSystemService(
                    Context.CARRIER_CONFIG_SERVICE);
            List<SubscriptionInfo> infoList = mSubscriptionManager.getActiveSubscriptionInfoList();
            if (infoList != null) {
                Iterator<SubscriptionInfo> it = infoList.iterator();
                while (it.hasNext()) {
                    loadConfigsForSubInfo(it.next());
                }
            }
            mContext.registerReceiver(mReceiver, new IntentFilter(
                    CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED));
            mSubscriptionManager.addOnSubscriptionsChangedListener(mOnSubscriptionsChangeListener);
        }
    }

    public void teardown() {
        mConfigsMap.clear();
        mInitialized.set(false);
        if (mContext != null) {
            mContext.unregisterReceiver(mReceiver);
            if (mSubscriptionManager != null) {
                mSubscriptionManager.removeOnSubscriptionsChangedListener(
                        mOnSubscriptionsChangeListener);
            }
        }
    }
}
