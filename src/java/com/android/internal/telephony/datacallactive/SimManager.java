package com.android.internal.telephony.datacallactive;

import android.content.Context;
import android.text.TextUtils;
import android.telephony.TelephonyManager;

/**
 * XTC SIM operator helper for the data-call-active monitor.
 *
 * @hide
 */
public class SimManager {
    private static final String TAG = "SimManager";
    public static final int UNKNOWN = -1;

    public static String getIMSI(Context context) {
        return ((TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE))
                .getSubscriberId();
    }

    public static int getOperatorType(Context context) {
        String imsi = getIMSI(context);
        if (TextUtils.isEmpty(imsi)) {
            return UNKNOWN;
        }
        if (imsi.startsWith("46000") || imsi.startsWith("46002") || imsi.startsWith("46007")) {
            return 0;
        }
        if (imsi.startsWith("46001") || imsi.startsWith("46006")) {
            return 1;
        }
        return (imsi.startsWith("46003") || imsi.startsWith("46005") || imsi.startsWith("46011"))
                ? 2 : UNKNOWN;
    }

    private static void logD(String msg) {
        Logger.logD(TAG, msg);
    }

    private static void logE(String msg) {
        Logger.logE(TAG, msg);
    }
}
