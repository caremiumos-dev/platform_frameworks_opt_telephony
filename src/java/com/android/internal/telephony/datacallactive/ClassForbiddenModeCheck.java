package com.android.internal.telephony.datacallactive;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

/**
 * XTC listener for the class-forbidden mode broadcast.
 *
 * @hide
 */
public class ClassForbiddenModeCheck {
    private static final String TAG = "ClassForbiddenModeCheck";
    private static final String ACTION_CLASS_MODE = "com.xtc.setting.action.CLASS.ACTION";
    private static final String EXTRA_STATE = "com.xtc.setting.extra.STATE";

    private final Context mContext;
    private final ClassForbiddenModeCheckComplelety mClassForbiddenModeCheckComplelety;
    private ForbiddenBroadCastReceiver mHForbiddenBroadCastReceiver;
    private boolean isHBCRRegister = false;

    public interface ClassForbiddenModeCheckComplelety {
        void exitClassForbiddenMode();
    }

    class ForbiddenBroadCastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            logD("onReceive com.xtc.setting.action.CLASS.ACTION");
            if (intent.getBooleanExtra(EXTRA_STATE, false)) {
                sendEndForbiddenModeMessage();
            }
        }

        public void register() {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(ACTION_CLASS_MODE);
            mContext.registerReceiver(this, intentFilter);
            setIsHBCRRegister(true);
        }

        public void unregister() {
            mContext.unregisterReceiver(this);
            setIsHBCRRegister(false);
        }
    }

    public ClassForbiddenModeCheck(Context context,
            ClassForbiddenModeCheckComplelety complelety) {
        mContext = context;
        mClassForbiddenModeCheckComplelety = complelety;
        if (mHForbiddenBroadCastReceiver == null) {
            mHForbiddenBroadCastReceiver = new ForbiddenBroadCastReceiver();
        }
        if (isHBCRRegister()) {
            return;
        }
        mHForbiddenBroadCastReceiver.register();
    }

    private void logD(String msg) {
        Logger.logD(TAG, msg);
    }

    private void logE(String msg) {
        Logger.logE(TAG, msg);
    }

    public void closeCheckClassForbidden() {
        if (isHBCRRegister) {
            mHForbiddenBroadCastReceiver.unregister();
        }
    }

    public boolean isHBCRRegister() {
        return isHBCRRegister;
    }

    public void sendEndForbiddenModeMessage() {
        if (mClassForbiddenModeCheckComplelety != null) {
            mClassForbiddenModeCheckComplelety.exitClassForbiddenMode();
        }
    }

    public void setIsHBCRRegister(boolean register) {
        isHBCRRegister = register;
    }
}
