package com.android.internal.telephony.reportdata;

import android.content.Context;
import android.os.SystemClock;

import java.util.LinkedHashMap;

/**
 * XTC modem-sleep-exception report.
 *
 * @hide
 */
public class ReportDataForModemSleepException extends ReportData {
    private static final String FUNSION_NAME = "SST_QCRIL_MODEM_SLEEP_EXCEPTION";
    private static final String TAG = "ReportDataForModemSleepException";
    private static final int TIMES = 0;
    private static final long TIME_DURADION = 0;

    private boolean isReport;
    private LinkedHashMap mReportMsg;
    private long timeReport;
    private int times;

    public ReportDataForModemSleepException(Context context) {
        super(context);
        times = 0;
        timeReport = -1L;
        isReport = true;
    }

    private long getRealtime() {
        return SystemClock.elapsedRealtime();
    }

    private long getReportTime() {
        return timeReport;
    }

    private long getRoportTimeDuration() {
        if (getReportTime() == -1) {
            return 0L;
        }
        return getRealtime() - getReportTime();
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

    private void setReportTime() {
        if (isReport) {
            timeReport = getRealtime();
            isReport = false;
        }
    }

    private void setTimes(int times) {
        this.times = times;
    }

    @Override
    public void clearData() {
        log(TAG, "clearData ... ");
    }

    @Override
    public void dataEngine(int... values) {
    }

    @Override
    public String getFunsionName() {
        return FUNSION_NAME;
    }

    @Override
    public LinkedHashMap getReprotValue() {
        mReportMsg = new LinkedHashMap();
        mReportMsg.put("ModemSleepException", "1");
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
