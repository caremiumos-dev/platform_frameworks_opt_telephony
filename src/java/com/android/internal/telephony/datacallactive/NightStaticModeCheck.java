package com.android.internal.telephony.datacallactive;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import java.util.Calendar;

/**
 * XTC night-static-mode checker that arms a "wake at 06:00" alarm.
 *
 * @hide
 */
public class NightStaticModeCheck implements SensorStateProvider {
    private static final String TAG = "NightStaticModeCheck";
    private static final String ALARM_HEART_ACTION =
            "com.android.internal.telephony.datacallactive.ALARM_HEART_ACTION";
    private static final long STATIC_CHECK_TIME = 300000;
    private static final int TIME_END = 6;

    private final Context mContext;
    private final NightStaticModeCheckCompletely mNightStaticModeCheckCompletely;
    private AlarmManager alarmManager;
    private HeartbeatBroadCastReceiver mHeartbeatBroadCastReceiver;
    private Runnable mNightStaticCheckRun;
    private PendingIntent pendingIntentTimeChangeAlarm;
    private boolean isHBCRRegister = false;
    private boolean isReachExiteTime = false;
    private long startTime = 0;

    class HeartbeatBroadCastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            dealScreenOnOrMontionStateChanged();
            isReachExiteTime = true;
        }

        public void register() {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(ALARM_HEART_ACTION);
            mContext.registerReceiver(this, intentFilter);
            setIsHBCRRegister(true);
        }

        public void unregister() {
            mContext.unregisterReceiver(this);
            setIsHBCRRegister(false);
        }
    }

    public NightStaticModeCheck(Context context, NightStaticModeCheckCompletely completely) {
        mContext = context;
        mNightStaticModeCheckCompletely = completely;
        initData();
    }

    private void logD(String msg) {
        Logger.logD(TAG, msg);
    }

    private void logE(String msg) {
        Logger.logE(TAG, msg);
    }

    public void cancelTimeChangeAlarm() {
        logE("cancelTimeChangeAlarm");
        if (alarmManager == null || pendingIntentTimeChangeAlarm == null) {
            return;
        }
        alarmManager.cancel(pendingIntentTimeChangeAlarm);
    }

    public void closeCheckNightStatic() {
        if (isHBCRRegister()) {
            mHeartbeatBroadCastReceiver.unregister();
        }
        startTime = 0L;
    }

    public void dealScreenOnOrMontionStateChanged() {
        if (!isReachExiteTime) {
            startCheck();
        } else {
            startTime = 0L;
            sendExitModeMessage();
        }
    }

    public void enterNightStaticMode() {
        logE("enterNightStaticMode");
        sendEnterModeMessage();
    }

    public void initData() {
        logE(TAG);
        isReachExiteTime = false;
        if (mHeartbeatBroadCastReceiver == null) {
            mHeartbeatBroadCastReceiver = new HeartbeatBroadCastReceiver();
        }
        if (!isHBCRRegister()) {
            mHeartbeatBroadCastReceiver.register();
        }
        if (mNightStaticCheckRun == null) {
            mNightStaticCheckRun = new Runnable() {
                @Override
                public void run() {
                    enterNightStaticMode();
                }
            };
        }
        if (alarmManager == null) {
            alarmManager = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
        }
    }

    public boolean isHBCRRegister() {
        return isHBCRRegister;
    }

    public void sendEnterModeMessage() {
        logD("sendEnterModeMessage");
        if (mNightStaticModeCheckCompletely != null) {
            mNightStaticModeCheckCompletely.enterNightStaticMode();
        }
        startTimeChangeAlarm();
    }

    public void sendExitModeMessage() {
        logD("sendExitModeMessage");
        if (mNightStaticModeCheckCompletely != null) {
            mNightStaticModeCheckCompletely.exitNightStaticMode();
        }
        cancelTimeChangeAlarm();
    }

    public void setIsHBCRRegister(boolean register) {
        isHBCRRegister = register;
    }

    public void startCheck() {
        logE("startCheck");
        enterNightStaticMode();
    }

    public void startTimeChangeAlarm() {
        logE("startTimeChangeAlarm");
        pendingIntentTimeChangeAlarm = PendingIntent.getBroadcast(mContext, 0,
                new Intent(ALARM_HEART_ACTION), PendingIntent.FLAG_UPDATE_CURRENT);
        Calendar calendar = Calendar.getInstance();
        long now = (System.currentTimeMillis() / 1000) * 1000;
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);
        int second = calendar.get(Calendar.SECOND);
        long duration = (((((TIME_END - hour) * 60) * 60) - (minute * 60)) - second) * 1000;
        long alarmTime = now + duration;
        logE("hour = " + hour + ",min = " + minute + ",sec = " + second
                + ", duration = " + duration + ",alramTime = " + alarmTime);
        long wakeTime = STATIC_CHECK_TIME + alarmTime;
        if (alarmManager != null) {
            alarmManager.cancel(pendingIntentTimeChangeAlarm);
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, wakeTime, pendingIntentTimeChangeAlarm);
        }
    }
}
