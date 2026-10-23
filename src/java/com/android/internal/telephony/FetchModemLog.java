/*
 * Copyright (C) 2018 The Android Open Source Project
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
import android.os.SystemProperties;
import android.util.Log;

/**
 * XTC 差异：modem 日志抓取。通话结束时通过广播通知厂商应用抓取/停止 modem 日志。
 * 仅记录行为（厂商对通话过程的额外日志采集），不改变通话逻辑。
 */
public class FetchModemLog {
    public static final String ACTION_END_MODEM = "com.xtc.call.modem.ACTION_END_MODEM";
    public static final String ACTION_START_MODEM = "com.xtc.call.modem.ACTION_START_MODEM";
    public static final int INVALID_PRO_END_TIME = -1;
    private static final String LOG_TAG = "FetchModemLog";
    private final String EXTRA_IS_NORMAL_CALL = "com.xtc.call.modem.extra.IS_NORMAL_CALL";
    private final String PRO_NORMAL_CALL_END_TIME = "persist.sys.call.failure.time";

    public void endModem2App(Context context, boolean isNormalCall) {
        Intent intent = new Intent();
        intent.setAction(ACTION_END_MODEM);
        intent.putExtra("com.xtc.call.modem.extra.IS_NORMAL_CALL", isNormalCall);
        context.sendBroadcast(intent);
        Log.d(LOG_TAG, "end modem log call drop normal = " + isNormalCall);
    }

    public int getNormalCallProValue() {
        Log.d(LOG_TAG, "getNormalCallProValue = "
                + SystemProperties.getInt(PRO_NORMAL_CALL_END_TIME, INVALID_PRO_END_TIME));
        return SystemProperties.getInt(PRO_NORMAL_CALL_END_TIME, INVALID_PRO_END_TIME);
    }

    public void startModem2App(Context context) {
    }
}
