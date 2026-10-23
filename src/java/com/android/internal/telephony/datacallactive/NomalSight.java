package com.android.internal.telephony.datacallactive;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;

import com.android.internal.telephony.CommandsInterface;

/**
 * XTC normal network-sight mode.
 *
 * @hide
 */
public class NomalSight extends NetSightMode {
    private static final String DATA_CALL_RAT = "android.intent.action.xtc.datacall";
    private static final String TAG = "NomalSight";

    private Handler mHandler;

    private void initData() {
        if (mHandler == null) {
            mHandler = new Handler();
        }
    }

    private void logE(String msg) {
        Logger.logE(TAG, msg);
    }

    @Override
    public void doWork(Context context) {
        logE("NomalSight doWork");
        super.doWork(context);
        initData();
        setNoLteNetmode();
        context.sendBroadcast(new Intent(DATA_CALL_RAT));
        logE("NomalSight NoLteNetmode");
    }

    @Override
    public void doWork(Context context, CommandsInterface ci) {
        super.doWork(context, ci);
        doWork(context);
    }

    @Override
    public void exitWork() {
        rebackLteNetmode();
    }
}
