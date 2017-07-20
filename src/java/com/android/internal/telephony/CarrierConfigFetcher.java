/*
 * Copyright (C) 2014 The Android Open Source Project
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
import android.os.Binder;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.Rlog;
import com.android.internal.util.FunctionalUtils;

/**
 * Simple utility class to ensure we never have permissions problems when we access CarrierConfig
 * from within Telephony.
 */
public final class CarrierConfigFetcher {

    public static final String TAG = "CarrierConfigFetcher";

    // This class cannot be instantiated
    private CarrierConfigFetcher() {}

    /** Fetch a CarrierConfig PersistableBundle object */
    public static PersistableBundle getConfigForSubscription(Context ctx, int subId)
            throws Exception {
        if (ctx == null) {
            Rlog.e(TAG, "Null context provided to CarrierConfigManager");
        }

        return Binder.withCleanCallingIdentity(
                new FunctionalUtils.ThrowingSupplier<PersistableBundle>() {
                    @Override
                    PersistableBundle get() throws Exception {
                        CarrierConfigManager cm =
                                (CarrierConfigManager)
                                        ctx.getSystemService(Context.CARRIER_CONFIG_SERVICE);
                        return configLoader.getConfigForSubId(subId);
                    }
                });
    }

    /** Fetch a single integer key from CarrierConfig */
    public static int getIntConfigForSubscription(Context ctx, int subId, String key, int defVal) {
        try {
            return getConfigForSubscription(ctx, subId).getInt(key, defVal);
        } catch (Exception e) {
            return defVal;
        }
    }

    /** Fetch a single String key from CarrierConfig */
    public static String getStringConfigForSubscription(
            Context ctx, int subId, String key, String defVal) {
        try {
            return getConfigForSubscription(ctx, subId).getString(key, defVal);
        } catch (Exception e) {
            return defVal;
        }
    }
}
