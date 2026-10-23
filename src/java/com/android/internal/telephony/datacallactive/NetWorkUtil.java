package com.android.internal.telephony.datacallactive;

import android.content.Context;
import android.content.Intent;
import android.os.SystemProperties;
import android.provider.Settings;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import com.android.internal.telephony.CommandsInterface;

/**
 * XTC network-mode helpers for {@link NetSightMode}.
 *
 * @hide
 */
public class NetWorkUtil {
    private static final String TAG = "NetWorkUtil-SYSTEM";
    private static final String PREFERRED_NETWORK_MODE = "preferred_network_mode";
    private static final int NETWORK_MODE_GSM_ONLY = 1;
    private static final int NETWORK_MODE_CDMA_NO_EVDO = 5;
    private static final int NETWORK_MODE_TD_SCDMA_CDMA_EVDO_GSM_WCDMA = 21;
    private static final int NETWORK_MODE_TD_SCDMA_LTE_CDMA_EVDO_GSM_WCDMA = 22;

    private CommandsInterface mCi;
    private final Context mContext;
    private TelephonyManager mTelephonyManager;

    public NetWorkUtil(Context context, TelephonyManager telephonyManager) {
        mContext = context;
        if (telephonyManager != null) {
            mTelephonyManager = telephonyManager;
        }
    }

    public NetWorkUtil(Context context, TelephonyManager telephonyManager,
            CommandsInterface ci) {
        mContext = context;
        mCi = ci;
        if (telephonyManager != null) {
            mTelephonyManager = telephonyManager;
        }
    }

    private int getDefaultSubscriptionId() {
        SubscriptionManager.from(mContext);
        int defaultSubscriptionId = SubscriptionManager.getDefaultSubscriptionId();
        logE("defaultSubscriptionId = " + defaultSubscriptionId);
        return defaultSubscriptionId;
    }

    private void logD(String msg) {
        Logger.logD(TAG, msg);
    }

    private void logE(String msg) {
        Logger.logE(TAG, msg);
    }

    private void setLimitativeNetworkType(int networkType, int a, int b) {
        logD("setLimitativeNetworkType = " + networkType);
        mCi.setPreferredNetworkType(networkType, null);
    }

    public int getCurrentNetworkMode() {
        return getCurrentNetworkMode(getDefaultSubscriptionId());
    }

    public int getCurrentNetworkMode(int subId) {
        int mode = Settings.Global.getInt(mContext.getContentResolver(),
                PREFERRED_NETWORK_MODE + subId,
                SystemProperties.getInt("ro.telephony.default_network", 0));
        logE("currentPreferredNetworkMode：" + mode);
        return mode;
    }

    public boolean getMobileDataEnabled() {
        logE("getMobileDataEnabled：");
        return mTelephonyManager.getDataEnabled();
    }

    public boolean isAirplaneModeOn() {
        return Settings.Global.getInt(mContext.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0)
                != 0;
    }

    public void setAirplaneModeOn(boolean enable) {
        logE("setAirplaneModeOn：" + enable);
        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON,
                enable ? 1 : 0);
        Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        intent.putExtra("state", enable);
        mContext.sendBroadcast(intent);
    }

    public void setMobileDataEnabled(boolean enable) {
        logE("setMobileDataEnabled：" + enable);
        mTelephonyManager.setDataEnabled(enable);
    }

    public boolean setNetworkMode(Integer mode) {
        int networkMode = NETWORK_MODE_TD_SCDMA_LTE_CDMA_EVDO_GSM_WCDMA;
        if (mode != null && mode.intValue() != 1 && mode.intValue() == 2) {
            networkMode = NETWORK_MODE_TD_SCDMA_CDMA_EVDO_GSM_WCDMA;
        }
        logE("setNetworkMode，mode：" + networkMode);
        setLimitativeNetworkType(networkMode, 0, 0);
        return true;
    }

    public boolean setPreferredNetworkType(int networkType) {
        logE("networkMode=" + networkType);
        int subId = getDefaultSubscriptionId();
        if (getCurrentNetworkMode(subId) != networkType) {
            return mTelephonyManager.setPreferredNetworkType(subId, networkType);
        }
        logE("不需要更改网络模式");
        return false;
    }
}
