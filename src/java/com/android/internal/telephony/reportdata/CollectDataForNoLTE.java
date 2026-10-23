package com.android.internal.telephony.reportdata;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.SystemProperties;

import java.util.LinkedHashMap;

/**
 * XTC collector that reports when the device stays off-LTE.
 *
 * @hide
 */
public class CollectDataForNoLTE extends ReportData {
    private static final String TAG = "CollectDataForNoLTE";
    private static final int LOG_TIME = 10;
    private static final int OUT_OF_SERVICE = 0;
    private static final int RAT_LTE = 14;
    private static final long TIME_DURADION = 600000;

    private AlarmManager alarmManager;
    private Context context;
    private boolean isReport;
    private long log_time;
    private PendingIntent mPendingIntent;
    private Intent mSendIntent;
    private int pre_data_rat;
    private int pre_voice_rat;
    private long timeReport;
    private int times;

    public CollectDataForNoLTE(Context context) {
        super(context);
        times = 0;
        pre_voice_rat = -1;
        pre_data_rat = -1;
        timeReport = -1L;
        isReport = false;
        log_time = TIME_DURADION;
        this.context = context;
        log_time = getLogType();
    }

    private void canselTimeChangeAlarm() {
        if (alarmManager != null) {
            alarmManager.cancel(mPendingIntent);
        }
        log(TAG, "canselTimeChangeAlarm...");
    }

    private long getLogType() {
        long time = TIME_DURADION;
        String value = SystemProperties.getInt("log.tag.network.server.type",
                com.android.internal.telephony.ServiceStateTracker.CS_NOTIFICATION) + "";
        if (value.substring(0, 2).equals("20")) {
            time = Long.parseLong(value.substring(2)) * 60000;
        }
        log(TAG, "log time = " + time + "ms");
        return time;
    }

    private PendingIntent getPendingIntent() {
        return PendingIntent.getBroadcast(context, 0,
                new Intent("com.qualcomm.qcrilmsgtunnel.ACTION_NO_LTE"),
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void initAlarmTime() {
        if (alarmManager == null) {
            alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        }
    }

    private void startTimeChangeAlarm() {
        mPendingIntent = getPendingIntent();
        initAlarmTime();
        if (alarmManager != null) {
            alarmManager.cancel(mPendingIntent);
            alarmManager.setExact(AlarmManager.RTC, System.currentTimeMillis() + log_time,
                    mPendingIntent);
        }
        log(TAG, "startTimeChangeAlarm...");
    }

    @Override
    public void clearData() {
        log(TAG, "clearData ... ");
        isReport = false;
    }

    @Override
    public void dataEngine(int... values) {
        int voiceRat = values[0];
        int dataRat = values[1];
        if (dataRat == pre_data_rat) {
            log(TAG, "dataEngine RAT is same...");
            return;
        }
        log(TAG, "dataEngine voice_rat = " + voiceRat + ",data_rat = " + dataRat);
        if (voiceRat == RAT_LTE || voiceRat == OUT_OF_SERVICE || dataRat != OUT_OF_SERVICE) {
            canselTimeChangeAlarm();
        } else {
            startTimeChangeAlarm();
        }
    }

    public Intent getCommandIntent() {
        mSendIntent = new Intent("com.android.qcom.network.startmodemlog");
        mSendIntent.putExtra("time", LOG_TIME);
        log(TAG, "getCommandIntent mSendIntent = " + mSendIntent);
        return mSendIntent;
    }

    @Override
    public String getFunsionName() {
        return null;
    }

    @Override
    public LinkedHashMap getReprotValue() {
        return null;
    }

    @Override
    public boolean isReportData() {
        return isReport;
    }
}
