/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.telephony.CarrierConfigManager;
import android.telephony.Rlog;

import java.util.Arrays;
import java.util.List;

/**
 * This is a basic utility class for common Carrier SMS Functions 
 */
public class CarrierSmsUtils {
    protected static final boolean VDBG = true;
    protected static final String TAG = "CarrierSmsUtils";

    /* FIXME: Real key is defined in aog/326680*/
    /*CarrierConfigManager.KEY_CONFIG_IMS_PACKAGE_OVERRIDE*/
    private static final String CARRIER_IMS_PACKAGE_KEY = "config_ims_package_override";
    private static String getCarrierImsPackageForIntent(
        Context context, Phone phone, Intent intent) {
        PackageManager packageManager = context.getPackageManager();

        CarrierConfigManager cm = (CarrierConfigManager) context.getSystemService(
                            Context.CARRIER_CONFIG_SERVICE);
        if (cm == null) {
            Rlog.e(TAG, "Failed to retrieve CarrierConfigManager");
            return null;
        }

        PersistableBundle config = cm.getConfigForSubId(phone.getSubId());
        if (config == null) {
            if (VDBG) Rlog.v(TAG, "No CarrierConfig for subId:" + phone.getSubId());
            return null;
        }

        String carrierImsPackage = config.getString(CARRIER_IMS_PACKAGE_KEY, null);
        if (carrierImsPackage == null) {
            if (VDBG) Rlog.v(TAG, "No CarrierImsPackage override found");
            return null;
        }

        List<ResolveInfo> receivers = packageManager.queryIntentServices(intent, 0);
        for (ResolveInfo info : receivers) {
            if (info.serviceInfo == null) {
                Rlog.e(TAG, "Can't get service information from " + info);
                continue;
            }

            if (info.serviceInfo.packageName.equals(carrierImsPackage)) {
                return carrierImsPackage;
            }
        }
        return null;
    }

    private CarrierSmsUtils() {}
}
