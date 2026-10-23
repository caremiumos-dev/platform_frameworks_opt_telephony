package com.android.internal.telephony.datacallactive;

import android.content.Context;

import com.android.internal.telephony.CommandsInterface;

/**
 * XTC network-sight mode used during the night-static window.
 *
 * @hide
 */
public class NightStatic extends NetSightMode {
    private static final String TAG = "NightStatic";

    private void logE(String msg) {
        Logger.logE(TAG, msg);
    }

    @Override
    public void doWork(Context context) {
        super.doWork(context);
        logE("NightStatic doWork");
        setNoLteNetmode();
    }

    @Override
    public void doWork(Context context, CommandsInterface ci) {
        super.doWork(context, ci);
        logE("NightStatic doWork");
        doWork(context);
    }

    @Override
    public void exitWork() {
        logE("NightStatic exitWork");
        rebackLteNetmode();
    }
}
