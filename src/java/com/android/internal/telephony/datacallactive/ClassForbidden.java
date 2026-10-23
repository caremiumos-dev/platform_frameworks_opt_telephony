package com.android.internal.telephony.datacallactive;

import android.content.Context;

import com.android.internal.telephony.CommandsInterface;

/**
 * XTC network-sight mode used while the class-forbidden mode is active.
 *
 * @hide
 */
public class ClassForbidden extends NetSightMode {
    private static final String TAG = "ClassForbidden";

    private void logE(String msg) {
        Logger.logE(TAG, msg);
    }

    @Override
    public void doWork(Context context) {
        logE("ClassForbidden doWork");
        super.doWork(context);
        setNoLteNetmode();
    }

    @Override
    public void doWork(Context context, CommandsInterface ci) {
        logE("ClassForbidden doWork");
        super.doWork(context, ci);
        doWork(context);
    }

    @Override
    public void exitWork() {
        rebackLteNetmode();
    }
}
