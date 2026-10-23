package com.android.internal.telephony.reportdata;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.os.SystemProperties;

import java.util.LinkedHashMap;

/**
 * XTC modem activity report. Periodically samples modem sleep/idle/tx/rx time
 * and forwards it through {@code XtcBehavior}.
 *
 * @hide
 */
public class ReportDataForModemInfo extends ReportData {
    private static final String FUNSION_NAME = "SST_QCRIL_MODEM_INFO";
    private static final String TAG = "ReportDataForModemInfo";
    private static final int TIMES = 0;
    private static final long TIME_DURADION = 0;
    private static final int TYPE_ALL = 4;

    private static int[] mModemInfo = new int[4];

    private boolean isReport;
    private Context mContext;
    private LinkedHashMap mReportMsg;
    private long timeReport;
    private int times;

    public ReportDataForModemInfo(Context context) {
        super(context);
        times = 0;
        timeReport = -1L;
        isReport = true;
        mContext = context;
    }

    private int getLogType() {
        String value = SystemProperties.getInt("log.tag.network.server.type",
                com.android.internal.telephony.ServiceStateTracker.CS_NOTIFICATION) + "";
        int type = value.substring(0, 2).equals("14")
                ? Integer.parseInt(value.substring(2)) : 0;
        log(TAG, "modem type = " + type);
        return type;
    }

    private long getRealtime() {
        return SystemClock.elapsedRealtime();
    }

    private long getReportTime() {
        return timeReport;
    }

    private long getRoportTimeDuration() {
        return getReportTime() == -1 ? TIME_DURADION : getRealtime() - getReportTime();
    }

    private int getTimes() {
        return times;
    }

    private boolean isTimeDurationOff() {
        return getRoportTimeDuration() >= TIME_DURADION;
    }

    private boolean isTimesOff() {
        return getTimes() >= TIMES;
    }

    private void sendModemLog(int[] values, int type) {
        int mask = 0;
        for (int i = 0; i < TYPE_ALL; i++) {
            mask = (int) ((values[i] > 0 ? Math.pow(2.0d, i) : 0.0d) + ((double) mask));
        }
        log(TAG, "type = " + type + ",rel = true");
        if (mask == type) {
            mContext.sendBroadcast(getCommandIntent());
        }
    }

    private void setReportTime() {
        if (isReport) {
            timeReport = getRealtime();
            isReport = false;
        }
    }

    private void setTimes(int times) {
        this.times = times;
    }

    private boolean shouldSendLog() {
        return SystemProperties.getBoolean("log.tag.network.server", false);
    }

    @Override
    public void clearData() {
        log(TAG, "clearData ... ");
        mModemInfo = null;
        mModemInfo = new int[TYPE_ALL];
    }

    @Override
    public void dataEngine(int... values) {
        for (int i = 0; i < TYPE_ALL; i++) {
            mModemInfo[i] = values[i];
        }
    }

    public Intent getCommandIntent() {
        Intent intent = new Intent("com.android.qcom.network.startmodemlog");
        intent.putExtra("time", 20);
        log(TAG, "getCommandIntent mSendIntent = " + intent);
        return intent;
    }

    @Override
    public String getFunsionName() {
        return FUNSION_NAME;
    }

    @Override
    public LinkedHashMap getReprotValue() {
        mReportMsg = new LinkedHashMap();
        for (int i = 0; i < TYPE_ALL; i++) {
            mReportMsg.put("mModemInfo_" + i, "" + mModemInfo[i]);
        }
        if (shouldSendLog() && getLogType() > 0) {
            sendModemLog(mModemInfo, getLogType());
        }
        return mReportMsg;
    }

    @Override
    public boolean isReportData() {
        if (isTimesOff()) {
            return true;
        }
        return isTimeDurationOff();
    }
}
