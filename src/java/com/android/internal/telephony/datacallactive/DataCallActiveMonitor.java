package com.android.internal.telephony.datacallactive;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.telephony.PhoneStateListener;
import android.telephony.PreciseCallState;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.internal.telephony.CommandsInterface;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * XTC monitor that downgrades the network to 2G when a data call stays active
 * while the screen is off (night-static / class-forbidden heuristics).
 *
 * @hide
 */
public class DataCallActiveMonitor implements SensorStateProvider,
        NightStaticModeCheckCompletely,
        ClassForbiddenModeCheck.ClassForbiddenModeCheckComplelety {
    private static String TAG = "DataCallActiveMonitor";
    private static final String ACTION_DATACALL_DORMANT_MONITOR =
            "qualcomm.intent.action.ACTION_DATACALL_DORMANT_MONITOR";
    private static final String ACTION_LTE_RRC_DURATION =
            "qualcomm.intent.action.ACTION_LTE_RRC_DURATION_FLAG";
    private static final String CLASS_PROP = "persist.sys.classmodestatus";
    private static final String NIGHT_START_TIME = "00:00:00";
    private static final String NIGHT_END_TIME = "6:00:00";
    private static final String NIGHT_END_TIME_DGB = "22:00:00";
    private static final String lte_rrc_duration = "duration";
    private static final int DATACALL_DORMANTSTATE = 1;
    private static final int DATA_CALL_ACTIVE = 2;
    private static final int MODE_NIGHT_STATIC = 1;
    private static final int MODE_NOMAL = 2;
    private static final int MODE_CLASS_FORBIDDEN = 3;
    private static final int RRC_TIME = 1;
    private static final long ENTER_TIME = 55000;
    private static final long ENTER_TIME_NIGHT = 28000;
    private static final long TIME_DORMANT_DURADION = 600000;

    private CommandsInterface mCi;
    private ClassForbiddenModeCheck mClassForbiddenModeCheck;
    private Context mContext;
    private NetSightMode mNetSightMode;
    private NightStaticModeCheck mNightStaticModeCheck;
    private PhoneStateListener mPhoneStateListener;
    private ScreenBroadCastReceiver mScreenBroadCastReceiver;
    private TelephonyManager mTelephonyManager;

    private boolean DGB = true;
    private int netMode = 0;
    private int activeType = -1;
    private long activeTime = 0;
    private boolean isJudging = false;
    private boolean isInNightStaticMode = false;
    private boolean isScreenOn = false;
    private boolean dataCallEnable = true;
    private boolean debug = false;
    private boolean inWork = false;
    public int DataCallDormantNum = 0;

    class ScreenBroadCastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            long j = 0;
            String action = intent.getAction();
            if (action.endsWith(Intent.ACTION_SCREEN_ON)) {
                if (isInNightStaticMode) {
                    exitNightStaticMode();
                }
                if (inWork) {
                    exitWork();
                }
                isScreenOn = true;
                return;
            }
            if (action.endsWith(Intent.ACTION_SCREEN_OFF)) {
                isScreenOn = false;
                setActiveTime(SystemClock.elapsedRealtime());
                logD("screen off ,if data is active and not in call, restart judge. "
                        + "set active time is " + getActiveTime());
                return;
            }
            if (action.endsWith(ACTION_LTE_RRC_DURATION)) {
                long duration = intent.getLongExtra(lte_rrc_duration, 0L);
                if (duration == RRC_TIME && dataCallEnable && !isScreenOn) {
                    long elapsed = SystemClock.elapsedRealtime() - getActiveTime();
                    if (getActiveTime() == 0) {
                        logD("getActiveTime() == 0");
                    } else {
                        j = elapsed;
                    }
                    logD("time_duration = " + j + ",System.elapsedRealtime() = "
                            + SystemClock.elapsedRealtime() + ",getActiveTime = " + getActiveTime());
                    if (isClassForbidden() || isInNightStaticMode) {
                        if (j >= ENTER_TIME_NIGHT) {
                            judgeMode();
                        }
                    } else if (j >= ENTER_TIME) {
                        judgeMode();
                    }
                }
                logD("ACTION_LTE_RRC_DURATION_FLAG time : " + duration + ",getActiveType = "
                        + getActiveType() + ",isScreenOn = " + isScreenOn + ",dataCallEnable = "
                        + dataCallEnable + ",isClassForbidden = " + isClassForbidden()
                        + ",isInNightStaticMode = " + isInNightStaticMode);
            }
        }

        public void register() {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(Intent.ACTION_SCREEN_ON);
            intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
            intentFilter.addAction(ACTION_LTE_RRC_DURATION);
            mContext.registerReceiver(this, intentFilter);
        }

        public void unregister() {
            mContext.unregisterReceiver(this);
        }
    }

    public DataCallActiveMonitor(int activeType) {
        mContext = null;
        startDataCallMonitor(activeType);
    }

    public DataCallActiveMonitor(Context context) {
        mContext = context;
    }

    public DataCallActiveMonitor(Context context, CommandsInterface ci, Handler handler) {
        mContext = context;
        mCi = ci;
        initLisn(handler);
    }

    private void cancelDataCallMonitorAlarm() {
        PendingIntent broadcast = PendingIntent.getBroadcast(mContext, 0,
                new Intent(ACTION_DATACALL_DORMANT_MONITOR), PendingIntent.FLAG_CANCEL_CURRENT);
        AlarmManager alarmManager = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null || broadcast == null) {
            return;
        }
        alarmManager.cancel(broadcast);
    }

    private void initLisn(Handler handler) {
        mPhoneStateListener = new PhoneStateListener(handler.getLooper()) {
            @Override
            public void onPreciseCallStateChanged(PreciseCallState preciseCallState) {
                logD("onPreciseCallStateChanged callStateString = " + preciseCallState.toString());
                if (preciseCallState.getRingingCallState() == 0
                        && preciseCallState.getForegroundCallState() == 0
                        && preciseCallState.getBackgroundCallState() == 0) {
                    logD("has call , set datacall able is false");
                    dataCallEnable = true;
                } else if (preciseCallState.getForegroundCallState() >= 7
                        || preciseCallState.getRingingCallState() >= 7) {
                    logD("has call , disconnected ... set datacall able is true");
                    dataCallEnable = true;
                } else {
                    dataCallEnable = false;
                    setActiveType(0);
                    startTimeForJudge();
                }
            }
        };
        mTelephonyManager = (TelephonyManager) mContext.getSystemService(
                Context.TELEPHONY_SERVICE);
        if (mTelephonyManager != null) {
            mTelephonyManager.listen(mPhoneStateListener,
                    android.hardware.radio.V1_0.RadioAccessFamily.HSPA);
        }
    }

    private boolean isErrorTime() {
        return Calendar.getInstance().get(Calendar.YEAR) < 2018;
    }

    private void logD(String msg) {
        if (DGB) {
            Logger.logD(TAG, msg);
        }
    }

    private void logE(String msg) {
        if (DGB) {
            Logger.logE(TAG, msg);
        }
    }

    public void clearDataCallDormantNum() {
        DataCallDormantNum = 0;
    }

    public void doWork() {
        if (isScreenOn || !dataCallEnable) {
            logE("screen on or in call do not work isScreenOn = " + isScreenOn
                    + ",dataCallEnable = " + dataCallEnable);
        } else if (mNetSightMode != null) {
            mNetSightMode.doWork(mContext, mCi);
            inWork = true;
        }
    }

    @Override
    public void enterNightStaticMode() {
        isInNightStaticMode = true;
    }

    @Override
    public void exitClassForbiddenMode() {
        if (mClassForbiddenModeCheck != null) {
            mClassForbiddenModeCheck.closeCheckClassForbidden();
            mClassForbiddenModeCheck = null;
        }
    }

    @Override
    public void exitNightStaticMode() {
        isInNightStaticMode = false;
        if (mNightStaticModeCheck != null) {
            mNightStaticModeCheck.closeCheckNightStatic();
            mNightStaticModeCheck = null;
        }
    }

    public void exitWork() {
        if (mNetSightMode != null) {
            mNetSightMode.exitWork();
            inWork = false;
        }
    }

    public long getActiveTime() {
        return activeTime;
    }

    public int getActiveType() {
        return activeType;
    }

    public int getDataCallDormantNum() {
        return DataCallDormantNum;
    }

    public int getNetMode() {
        return netMode;
    }

    public void inNightStaticMode() {
        logE("enterNightStaticMode");
        setNetMode(MODE_NIGHT_STATIC);
        doWork();
    }

    public void initData() {
        if (mScreenBroadCastReceiver == null) {
            mScreenBroadCastReceiver = new ScreenBroadCastReceiver();
            mScreenBroadCastReceiver.register();
        }
    }

    public boolean isClassForbidden() {
        return SystemProperties.getBoolean(CLASS_PROP, false);
    }

    public boolean isJudging() {
        return isJudging;
    }

    public boolean isNightStatic() {
        return isInNightStaticMode;
    }

    protected boolean isShouldNightStatic() {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("HH:mm:ss");
        Calendar now = Calendar.getInstance();
        Calendar start = Calendar.getInstance();
        Calendar end = Calendar.getInstance();
        try {
            now.setTime(simpleDateFormat.parse(simpleDateFormat.format(new Date())));
            start.setTime(simpleDateFormat.parse(NIGHT_START_TIME));
            end.setTime(simpleDateFormat.parse(debug ? NIGHT_END_TIME_DGB : NIGHT_END_TIME));
        } catch (Exception e) {
            logE(e.toString());
        }
        boolean isOnNight = now.after(start) ? now.before(end) : false;
        logE("isOnNight:" + isOnNight);
        return isOnNight;
    }

    public void judgeMode() {
        if (isErrorTime()) {
            return;
        }
        boolean shouldNightStatic = isShouldNightStatic();
        boolean classForbidden = isClassForbidden();
        logD("judgeMode: is should night static judge = " + shouldNightStatic
                + ",is classforbidden time = " + classForbidden);
        if (!classForbidden) {
            setNetMode(MODE_NOMAL);
            if (shouldNightStatic) {
                startCheckNightStaticMode();
            }
        } else if (mClassForbiddenModeCheck == null) {
            mClassForbiddenModeCheck = new ClassForbiddenModeCheck(mContext, this);
        }
        doWork();
    }

    public void setActiveTime(long activeTime) {
        this.activeTime = activeTime;
    }

    public void setActiveType(int activeType) {
        this.activeType = activeType;
        setActiveTime(SystemClock.elapsedRealtime());
        logD("setActiveType = " + activeType + ",settime = " + getActiveTime());
    }

    public void setJudging(boolean judging) {
        isJudging = judging;
    }

    public void setNetMode(int netMode) {
        this.netMode = netMode;
        if (mNetSightMode != null && isInNightStaticMode) {
            mNetSightMode.exitWork();
            mNetSightMode = null;
        }
        logE("setNetMode netMode = " + netMode);
        switch (getNetMode()) {
            case MODE_NIGHT_STATIC:
                mNetSightMode = new NightStatic();
                break;
            case MODE_NOMAL:
                mNetSightMode = new NomalSight();
                break;
            case MODE_CLASS_FORBIDDEN:
                mNetSightMode = new ClassForbidden();
                break;
            default:
                break;
        }
    }

    public void startCheckNightStaticMode() {
        logE("startCheckNightStaticMode");
        if (isInNightStaticMode) {
            return;
        }
        if (mNightStaticModeCheck == null) {
            mNightStaticModeCheck = new NightStaticModeCheck(mContext, this);
        }
        mNightStaticModeCheck.startCheck();
    }

    public void startDataCallMonitor(int activeType) {
        logE("DataCallActiveMonitor start activeType = " + activeType + ",isScreenOn = "
                + isScreenOn + ",dataCallEnable = " + dataCallEnable);
        if (!dataCallEnable) {
            activeType = 0;
        }
        initData();
        setActiveType(activeType);
    }

    public void startDataCallMonitorAlarm(int active) {
        if (DATACALL_DORMANTSTATE == active) {
            DataCallDormantNum++;
        } else {
            if (DataCallDormantNum >= 2) {
                cancelDataCallMonitorAlarm();
            }
            DataCallDormantNum = 0;
        }
        if (DATA_CALL_ACTIVE == DataCallDormantNum) {
            PendingIntent broadcast = PendingIntent.getBroadcast(mContext, 0,
                    new Intent(ACTION_DATACALL_DORMANT_MONITOR),
                    PendingIntent.FLAG_CANCEL_CURRENT);
            AlarmManager alarmManager = (AlarmManager) mContext.getSystemService(
                    Context.ALARM_SERVICE);
            if (alarmManager == null || broadcast == null) {
                return;
            }
            alarmManager.cancel(broadcast);
            alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + TIME_DORMANT_DURADION, broadcast);
        }
    }

    public void startTimeForJudge() {
        if (getActiveType() == DATA_CALL_ACTIVE) {
            if (isJudging()) {
                return;
            }
            setJudging(true);
            logD("startTimeForJudge ...");
            return;
        }
        if (isJudging()) {
            logD("stopTimeJudge  ...");
            stopTimeJudge();
        }
    }

    public void stopTimeJudge() {
        setJudging(false);
    }
}
