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

package com.android.internal.telephony.datacallactive;

import android.util.Log;

/**
 * XTC data-call-active logging helper. See {@link DataCallActiveMonitor}.
 *
 * @hide
 */
public class Logger {
    private static String TAG = "DataCallActiveMonitor";
    private static boolean DBG = true;

    public static void logD(String tag, String msg) {
        Log.d(TAG + "_" + tag, msg);
    }

    public static void logE(String tag, String msg) {
        Log.e(TAG + "_" + tag, msg);
    }
}
