package com.android.internal.telephony.datacallactive;

import android.content.Context;
import android.telephony.TelephonyManager;

import com.android.internal.telephony.CommandsInterface;

/**
 * XTC base class for the "no LTE" network-sight modes.
 *
 * @hide
 */
public abstract class NetSightMode {
    private static final int MODE_ALL = 1;
    private static final int MODE_2G_ONLY = 2;
    private static final String TAG = "NetSightMode";

    private CommandsInterface mCi;
    private Context mContext;
    private NetWorkUtil mNetWorkUtil;
    private TelephonyManager mTelephonyManager;

    private void logE(String msg) {
        Logger.logE(TAG, msg);
    }

    public void doWork(Context context) {
        mContext = context;
    }

    public void doWork(Context context, CommandsInterface ci) {
        mContext = context;
        mCi = ci;
    }

    public void exitWork() {
    }

    public TelephonyManager getmTelephonyManager() {
        if (mTelephonyManager == null) {
            mTelephonyManager = (TelephonyManager) mContext.getSystemService(
                    Context.TELEPHONY_SERVICE);
        }
        return mTelephonyManager;
    }

    public void rebackLteNetmode() {
        logE("rebackLteNetmode");
        if (mNetWorkUtil == null) {
            if (mCi == null) {
                mNetWorkUtil = new NetWorkUtil(mContext, getmTelephonyManager());
            } else {
                mNetWorkUtil = new NetWorkUtil(mContext, getmTelephonyManager(), mCi);
            }
        }
        mNetWorkUtil.setNetworkMode(MODE_ALL);
    }

    public void setDataEnabledNS(boolean enabled) {
        getmTelephonyManager().setDataEnabled(enabled);
    }

    public void setNoLteNetmode() {
        logE("setNoLteNetmode");
        if (mNetWorkUtil == null) {
            if (mCi == null) {
                mNetWorkUtil = new NetWorkUtil(mContext, getmTelephonyManager());
            } else {
                mNetWorkUtil = new NetWorkUtil(mContext, getmTelephonyManager(), mCi);
            }
        }
        mNetWorkUtil.setNetworkMode(MODE_2G_ONLY);
    }
}
