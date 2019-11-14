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

package com.android.internal.telephony.util;

import android.annotation.UnsupportedAppUsage;
import android.content.Context;
import android.content.Intent;
import android.telephony.Rlog;

/**
 * This class provides several phonebook util functions.
 *
 */
public class TelePhonebookUtil {

    private static final String LOG_TAG = "TelePhonebookUtil";
    private static final boolean DBG = true;

    private static final String ACTION_FDN_STATE_CHANGED =
            "android.telephony.action.ACTION_FDN_STATE_CHANGED";
    // ACTION_FDN_STATE_CHANGED extra key
    private static final String EXTRA_FDN_STATE = "fdnState";
    private static final String PHONE_KEY = "phone";

    /**
     * Broadcast Action: It indicates ICC fdn (fixed dialing number) state has changed
     * from enabled to disabled or disabled to enabled.
     *
     * @param iccFdnEnabled whether ICC fdn is enabled or disabled before changed.
     * @param desiredFdnEnabled whether ICC fdn is enabled or disabled after changed.
     * @param phoneId Phone id.
     *
     */
    @UnsupportedAppUsage
    public static void broadcastFdnChangedDone(Context context, boolean iccFdnEnabled,
                                                boolean desiredFdnEnabled, int phoneId) {
        if (iccFdnEnabled != desiredFdnEnabled && context != null) {
            Intent intent = new Intent(ACTION_FDN_STATE_CHANGED);
            intent.putExtra(PHONE_KEY, phoneId);
            intent.putExtra(EXTRA_FDN_STATE, desiredFdnEnabled);
            context.sendBroadcast(intent);
            if (DBG) Rlog.d(LOG_TAG, "Send ACTION_FDN_STATE_CHANGED broadcast.");
        }
    }
}
