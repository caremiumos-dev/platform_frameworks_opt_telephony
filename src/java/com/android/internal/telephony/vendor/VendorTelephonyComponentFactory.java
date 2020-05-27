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

package com.android.internal.telephony.vendor;

import android.content.Context;
import android.os.Looper;
import android.telephony.Rlog;

import com.android.internal.telephony.dataconnection.DcTracker;
import com.android.internal.telephony.vendor.dataconnection.VendorDcTracker;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneNotifier;
import com.android.internal.telephony.PhoneSwitcher;
import com.android.internal.telephony.SubscriptionController;

import com.android.internal.telephony.TelephonyComponentFactory;
import com.android.internal.telephony.*;

public class VendorTelephonyComponentFactory extends TelephonyComponentFactory {
    private static String LOG_TAG = "VendorTelephonyComponentFactory";

    private static VendorTelephonyComponentFactory sInstance;

    public static VendorTelephonyComponentFactory getInstance() {
        return sInstance;
    }

    public VendorTelephonyComponentFactory() {
        sInstance = this;
    }

    @Override
    public ServiceStateTracker makeServiceStateTracker(GsmCdmaPhone phone, CommandsInterface ci) {
        Rlog.d(LOG_TAG, "makeVendorServiceStateTracker");
        return new VendorServiceStateTracker(phone, ci);
    }

    @Override
    public DcTracker makeDcTracker(Phone phone, int transportType) {
        Rlog.d(LOG_TAG, "makeVendorDcTracker");
        return new VendorDcTracker(phone, transportType);
    }

    @Override
    public Phone makePhone(Context context, CommandsInterface ci, PhoneNotifier notifier,
            int phoneId, int precisePhoneType,
            TelephonyComponentFactory telephonyComponentFactory) {
        Rlog.d(LOG_TAG, "makePhone");
        return new VendorGsmCdmaPhone(context, ci, notifier, phoneId, precisePhoneType,
                telephonyComponentFactory);
    }

    @Override
    public SubscriptionController initSubscriptionController(Context c) {
        Rlog.d(LOG_TAG, "initVendorSubscriptionController");
        return VendorSubscriptionController.init(c);
    }

    @Override
    public SubscriptionInfoUpdater makeSubscriptionInfoUpdater(Looper looper, Context context,
            CommandsInterface[] ci) {
        Rlog.d(LOG_TAG, "makeVendorSubscriptionInfoUpdater");
        return VendorSubscriptionInfoUpdater.init(looper, context, ci);
    }

    @Override
    public PhoneSwitcher makePhoneSwitcher(int maxDataAttachModemCount, Context context,
            Looper looper) {
        Rlog.d(LOG_TAG, "makeVendorPhoneSwitcher");
        return VendorPhoneSwitcher.make(maxDataAttachModemCount, context, looper);
    }

    @Override
    public MultiSimSettingController initMultiSimSettingController(Context c,
            SubscriptionController sc) {
        Rlog.i(LOG_TAG, "initMultiSimSettingController");
        return VendorMultiSimSettingController.init(c, sc);
    }

}
