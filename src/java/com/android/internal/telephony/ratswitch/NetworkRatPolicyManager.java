package com.android.internal.telephony.ratswitch;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.PreciseCallState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.ims.ImsManager;
import com.android.internal.telephony.CommandsInterface;
import com.xtc.utils.KeyguardEventManager;

import java.util.ArrayList;
import java.util.List;

/**
 * XTC network RAT policy manager. Watches repeated RAT switches and calls, and
 * limits the preferred network type when switching too often.
 *
 * @hide
 */
public class NetworkRatPolicyManager {
    private static final String TAG = "NetworkRatPolicyManager";

    private static final String ACTION_MUTI_NETWORK_MODEM =
            "android.intent.action.xtc.mutinetworkmodem";
    private static final String ACTION_NETWORK_RAT =
            "com.qualcomm.qcrilmsgtunnel.ACTION_NETWORK_RAT";
    private static final String ACTION_QCRIL_SET_DATA_ALLLOW =
            "qualcomm.intent.action.QCRIL_SET_DATA_ALLLOW";
    private static final String ACTION_REBACK_NEWTOK_MODE =
            "com.android.phone.vvm.omtp.enternight.REBACK_NW";
    private static final String ACTION_SET_NETWORK_SWITCH =
            "com.android.phone.vvm.omtp.enternight.SETNW_SWITCH";
    private static final String DATA_CALL_RAT = "android.intent.action.xtc.datacall";
    private static final String DATA_STATE_MODEM = "persist.dataradio.enable";
    private static final String ENTER_DRTATION = "enter_duration";
    private static final String ENTER_TIME = "enter_time";
    private static final String IS_LTE = "is_lte";
    private static final String MODEM_CMD = "cs_only";
    private static final String PREFERRED_NETWORK_MODE = "preferred_network_mode";
    private static final String REBACK_DURATION = "reback_duration";
    private static final String REBACK_TIME = "reback_time";
    private static final String SEND_MODEM_LOG = "gsm.network.server";
    protected static final String XTC_MODEM_TURNOFFIMS_PROPERTY = "gsm.modem.turnoffims";

    private static final int CMD_2 = 2;
    private static final int CMD_3 = 3;
    private static final int CMD_4 = 4;
    private static final int CMD_5 = 5;
    private static final int CMD_6 = 6;
    private static final int CMD_7 = 7;
    private static final int CMD_8 = 8;
    private static final int CMD_9 = 9;
    private static final int CMD_11 = 11;
    private static final int CMD_26 = 26;
    private static final int CMD_27 = 27;
    private static final int CMD_31 = 31;
    private static final int CMD_32 = 32;
    private static final int CMD_36 = 36;
    private static final int CMD_37 = 37;
    private static final int CMD_38 = 38;
    private static final int CMD_39 = 39;
    private static final int CMD_40 = 40;
    private static final int CMD_41 = 41;
    private static final int CMD_42 = 42;
    private static final int CMD_43 = 43;
    private static final int CMD_44 = 44;
    private static final int CMD_45 = 45;
    private static final int CMD_46 = 46;
    private static final int CMD_47 = 47;
    private static final int CMD_48 = 48;
    private static final int CMD_49 = 49;
    private static final int CMD_50 = 50;

    private static final int DATA_DEFAULT = 0;
    private static final int DATA_DISABLE_FOR_OTHER = 1;
    private static final int DATA_DISABLE_BEFORE_MODEM = 2;
    private static final int DATA_DISABLE_FOR_MODEM = 3;
    private static final int ENTER_REBACK_MODE = 2016;
    private static final int GET_PREFERNETWORK_DONE = 1988;
    private static final int SET_PREFERNETWORK_DONE = 1990;
    private static final int SET_PREFERNETWORK_DONE_ARG1 = 1964;
    private static final int SET_PREFERNETWORK_DONE_ARG_ATT = 1971;
    private static final long DEFAULT_ATT_TIME = 600000;
    private static final long RAT_SWICTH_DURATION = 600000;
    private static final int RAT_SWICTH_TIMES = 5;
    public static final int RIL_RADIO_TECHNOLOGY_LTE = 14;

    private List<Long> attenuationList;
    private List<Long> attenuationTime;
    private long enter_duration;
    private Handler handler;
    private AttenuationEngine mAttenuationEngine;
    private CommandsInterface mCi;
    private Context mContext;
    private KeyguardEventManager mKeyguard;
    private PowerManager.WakeLock mModemRestartRadioPowerWakeLock;
    private NetworkRatPolicyManagerReceiver mNetworkRatPolicyManagerReceiver;
    private PhoneStateListener mPhoneStateListener;
    private TelephonyManager mTelephonyManager;
    private Message message;

    private boolean isGsm = true;
    private int currentNetworkType = 0;
    private boolean isIncoming = false;
    private boolean isDisconnected = false;
    private boolean isDial = false;
    private boolean isReg = false;
    private int oldRat = -1;
    private int currentRat = -1;
    private int ratSwitchTimes = 0;
    private long oldTime = 0;
    private long currentTime = 0;
    private long tmpTime = 0;
    private boolean isHBCRRegister = false;
    private long exitStartTime = 0;
    private long attenuationAverageTime = 0;
    private long attenuationStartTime = 0;
    private long attenuationEndTime = 0;
    private int attenuationTimes = -1;
    private boolean is_lte = true;
    private boolean isInRatReback = false;
    private boolean isScreenOn = false;
    private boolean isInNoLteMode = false;
    private long pre_cmd = -1;

    private final KeyguardEventManager.Listener mScreenOnListener =
            new KeyguardEventManager.Listener() {
        @Override
        public void onFinishedGoingToSleep() {
            isScreenOn = false;
        }

        @Override
        public void onFinishedWakingUp() {
            if (isInRatReback) {
                message = handler.obtainMessage(ENTER_REBACK_MODE);
                handler.sendMessage(message);
            } else {
                if (mNetworkRatPolicyManagerReceiver == null) {
                    return;
                }
                mNetworkRatPolicyManagerReceiver.rebackWhenScreenOn();
            }
        }
    };

    class NetworkRatPolicyManagerReceiver extends BroadcastReceiver {
        private void releaseModemRestartRadioPowerWakeLock() {
            if (mModemRestartRadioPowerWakeLock != null) {
                if (mModemRestartRadioPowerWakeLock.isHeld()) {
                    mModemRestartRadioPowerWakeLock.release();
                    Log.d(TAG, "mModemBehaviorWakeLockrestartRadioPower release = "
                            + mModemRestartRadioPowerWakeLock);
                }
                mModemRestartRadioPowerWakeLock = null;
            }
        }

        private void restartRadioPowerForModemInd() {
            PowerManager powerManager = (PowerManager) mContext.getSystemService(
                    Context.POWER_SERVICE);
            if (mModemRestartRadioPowerWakeLock == null) {
                mModemRestartRadioPowerWakeLock = powerManager.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK, "modemRestartRadioPower");
                mModemRestartRadioPowerWakeLock.acquire(8000L);
            }
            new Thread(new Runnable() {
                @Override
                public void run() {
                    int count = 0;
                    boolean radioOn = getmTelephonyManager().isRadioOn();
                    Log.d(TAG, "first radioPowerState = " + radioOn);
                    if (radioOn) {
                        getmTelephonyManager().setRadioPower(false);
                        SystemClock.sleep(1000L);
                        if (getmTelephonyManager().isRadioOn()) {
                            Log.d(TAG, "Command 50 set radio power off fail.");
                        }
                    }
                    do {
                        getmTelephonyManager().setRadioPower(true);
                        SystemClock.sleep(1000L);
                        boolean radioOn2 = getmTelephonyManager().isRadioOn();
                        count++;
                        Log.d(TAG, "stop radio power off again radioPowerState = " + radioOn2
                                + ",apcnt = " + count);
                        if (radioOn2) {
                            break;
                        }
                    } while (count < 6);
                    if (count < 6) {
                        releaseModemRestartRadioPowerWakeLock();
                        Log.d(TAG, "stop radio power off  success.and count = " + count);
                    } else {
                        releaseModemRestartRadioPowerWakeLock();
                        Log.d(TAG, "stop radio power off fail.");
                    }
                }
            }).start();
        }

        public void enableIms(boolean enable) {
            Log.d(TAG, "enableIms = " + enable);
            ImsManager.getInstance(mContext, 0);
            ImsManager.setEnhanced4gLteModeSetting(mContext, enable);
        }

        public void flashLteModeWhenReceiverCommand() {
            isInNoLteMode = true;
            flashRatSwitchTimes();
        }

        public void flashRatSwitchTimes() {
            ratSwitchTimes--;
            if (ratSwitchTimes <= -2) {
                Log.d(TAG, "error state ratSwitchTimes = " + ratSwitchTimes
                        + ",and set ratSwitchTimes == -1");
                ratSwitchTimes = -1;
            }
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "NetworkRatPolicyManagerReceiver action = " + intent.getAction());
            if (intent.getAction().endsWith(DATA_CALL_RAT)) {
                debarDataCall();
                return;
            }
            if (intent.getAction().endsWith(ACTION_QCRIL_SET_DATA_ALLLOW)) {
                int cmd = (int) intent.getLongExtra(MODEM_CMD, 0L);
                Log.d(TAG, "pre_cmd = " + pre_cmd + ",cmd = " + cmd + ",isScreenOn = "
                        + isScreenOn + ",isInNoLteMode = " + isInNoLteMode);
                if (cmd == CMD_6) {
                    message = handler.obtainMessage(ENTER_REBACK_MODE);
                    handler.sendMessage(message);
                    return;
                }
                if (cmd <= 25) {
                    if (isScreenOn || pre_cmd == cmd) {
                        return;
                    }
                    pre_cmd = cmd;
                    return;
                }
                switch (cmd) {
                    case CMD_26:
                    case CMD_48:
                        SystemProperties.set(XTC_MODEM_TURNOFFIMS_PROPERTY, "0");
                        enableIms(true);
                        break;
                    case CMD_27:
                    case CMD_49:
                        SystemProperties.set(XTC_MODEM_TURNOFFIMS_PROPERTY, "1");
                        enableIms(false);
                        break;
                    case CMD_31:
                        setNetworkModeInRat(11);
                        break;
                    case CMD_32:
                        message = handler.obtainMessage(ENTER_REBACK_MODE);
                        handler.sendMessage(message);
                        break;
                    case CMD_36:
                        setNetworkModeInRat(22);
                        break;
                    case CMD_37:
                        setNetworkModeInRat(2);
                        break;
                    case CMD_38:
                        setNetworkModeInRat(1);
                        break;
                    case CMD_39:
                        setNetworkModeInRat(5);
                        break;
                    case CMD_40:
                        message = handler.obtainMessage(ENTER_REBACK_MODE);
                        handler.sendMessage(message);
                        break;
                    case CMD_41:
                        setNetworkModeInRat(11);
                        break;
                    case CMD_42:
                    case CMD_43:
                        setNetworkModeInRat(21);
                        break;
                    case CMD_47:
                        setMobileDataEnabled(true);
                        break;
                    case CMD_50:
                        restartRadioPowerForModemInd();
                        break;
                    default:
                        break;
                }
                isInNoLteMode = false;
            }
        }

        public void rebackNetworkAndDataEnable() {
            setNetworkModeInRat(getCurrentNetworkType());
            setMobileDataEnabled(true);
        }

        public void rebackWhenScreenOn() {
            isScreenOn = true;
            if (isInNoLteMode) {
                isInNoLteMode = false;
                pre_cmd = -1L;
                flashRatSwitchTimes();
                rebackNetworkAndDataEnable();
            }
        }

        public void register() {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(ACTION_QCRIL_SET_DATA_ALLLOW);
            intentFilter.addAction(DATA_CALL_RAT);
            mContext.registerReceiver(this, intentFilter);
            setIsHBCRRegister(true);
        }

        public void restartRadio() {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    getmTelephonyManager().setRadioPower(false);
                    SystemClock.sleep(1000L);
                    getmTelephonyManager().setRadioPower(true);
                }
            }).start();
        }

        public void unregister() {
            mContext.unregisterReceiver(this);
            setIsHBCRRegister(false);
        }
    }

    public NetworkRatPolicyManager(int currentRat, int oldRat, CommandsInterface ci,
            boolean isGsm, Context context) {
        initHandler();
        initHBCR();
        setData(currentRat, oldRat, ci, isGsm, context);
    }

    public NetworkRatPolicyManager(CommandsInterface ci, Context context) {
        mContext = context;
        mCi = ci;
        initHandler();
        initHBCR();
    }

    private void clearAttData() {
        Log.d(TAG, "clearAttData");
        setAttenuationEndTime(0L);
        setAttenuationStartTime(0L);
        setAttenuationTime(null);
        setAttenuationTimes(-1);
        setAttenuationAverageTime(DEFAULT_ATT_TIME);
        setAttenuationList(null);
    }

    private void clearData() {
        oldTime = 0L;
        currentTime = 0L;
        ratSwitchTimes = 0;
    }

    private void clearExitData() {
        setExitStartTime(0L);
        clearData();
        pre_cmd = -1L;
        isInNoLteMode = false;
    }

    private void debarCall() {
        ratSwitchTimes--;
        if (ratSwitchTimes <= 0) {
            clearData();
        }
    }

    private void debarDataCall() {
        ratSwitchTimes -= 2;
        if (ratSwitchTimes <= 0) {
            clearData();
        }
    }

    private void enterRebackModeForce() {
        rebackNetworkType(getCurrentNetworkType(), SET_PREFERNETWORK_DONE_ARG_ATT);
        clearExitData();
    }

    private void enterRebackModeNoStep() {
        long duration = DEFAULT_ATT_TIME;
        int attenuationTimes = getAttenuationTimes();
        if (attenuationTimes != -1 && getAttenuationList() != null) {
            duration = getAttenuationList().get(attenuationTimes).longValue();
        }
        long now = System.currentTimeMillis();
        if (isTimeOut(getExitStartTime(), now, duration)) {
            rebackNetworkType(getCurrentNetworkType(), SET_PREFERNETWORK_DONE_ARG_ATT);
            Intent intent = new Intent(ACTION_MUTI_NETWORK_MODEM);
            intent.putExtra(REBACK_DURATION, now - getExitStartTime());
            intent.putExtra(REBACK_TIME, now);
            sendMutiNetworkModeBrodcast(intent);
            clearExitData();
        }
    }

    private long getAverageTime(List<Long> list) {
        long sum = 0;
        for (int i = 0; i < 4; i++) {
            sum += list.get(i).longValue();
        }
        return (long) (sum / 4);
    }

    private int getDefaultSubscriptionId() {
        SubscriptionManager.from(mContext);
        int defaultSubscriptionId = SubscriptionManager.getDefaultSubscriptionId();
        Log.d(TAG, "functional method:defaultSubscriptionId = " + defaultSubscriptionId);
        return defaultSubscriptionId;
    }

    private void getPreferredNetworkTypeFormHere() {
        message = handler.obtainMessage(GET_PREFERNETWORK_DONE);
        mCi.getPreferredNetworkType(message);
    }

    private void initHBCR() {
        if (mNetworkRatPolicyManagerReceiver == null) {
            mNetworkRatPolicyManagerReceiver = new NetworkRatPolicyManagerReceiver();
        }
        if (!isHBCRRegister()) {
            mNetworkRatPolicyManagerReceiver.register();
        }
        setScreenOnListener();
    }

    private void initHandler() {
        handler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                switch (msg.what) {
                    case GET_PREFERNETWORK_DONE:
                        AsyncResult ar = (AsyncResult) msg.obj;
                        if (ar.exception == null && ar.result != null) {
                            Log.d(TAG, "getPreferredNetworkType:" + ((int[]) ar.result)[0]);
                        } else {
                            Log.e(TAG, "getPreferredNetworkType: CommandException: "
                                    + ar.exception);
                        }
                        break;
                    case SET_PREFERNETWORK_DONE:
                        AsyncResult ar2 = (AsyncResult) msg.obj;
                        boolean ok;
                        if (ar2.exception == null) {
                            ok = true;
                            if (msg.arg1 == SET_PREFERNETWORK_DONE_ARG1) {
                                startExitRPM();
                            } else if (msg.arg1 == SET_PREFERNETWORK_DONE_ARG_ATT) {
                                startAttMode();
                            }
                        } else {
                            Log.e(TAG, "SET_PREFERNETWORK CommandException: " + ar2.exception);
                            ok = false;
                        }
                        Log.d(TAG, "SET_PREFERNETWORK_DONE = " + ok + "msg.arg1 = " + msg.arg1);
                        break;
                    case ENTER_REBACK_MODE:
                        enterRebackModeForce();
                        break;
                    default:
                        break;
                }
            }
        };
    }

    private void initLisn() {
        if (isReg) {
            return;
        }
        mPhoneStateListener = new PhoneStateListener() {
            @Override
            public void onPreciseCallStateChanged(PreciseCallState preciseCallState) {
                Log.d(TAG, "onPreciseCallStateChanged : " + preciseCallState.toString()
                        + ",isDisconnected = " + isDisconnected + ",isIncoming = " + isIncoming
                        + ",isDial = " + isDial);
                if (preciseCallState.getRingingCallState() == 0
                        && preciseCallState.getForegroundCallState() == 0
                        && preciseCallState.getBackgroundCallState() == 0) {
                    if ((isIncoming || isDial) && !isDisconnected) {
                        debarCall();
                        if (ratSwitchTimes == 4) {
                            ratSwitchTimes -= 2;
                        }
                    }
                    isDial = false;
                    isIncoming = false;
                    isDisconnected = false;
                    return;
                }
                if (preciseCallState.getForegroundCallState() >= 7
                        || preciseCallState.getRingingCallState() >= 7) {
                    if (isDisconnected) {
                        return;
                    }
                    isDisconnected = true;
                    isDial = false;
                    isIncoming = false;
                    debarCall();
                    return;
                }
                if (preciseCallState.getRingingCallState() == 5) {
                    if (isIncoming) {
                        return;
                    }
                    isIncoming = true;
                    isDisconnected = false;
                    isDial = false;
                    debarCall();
                    return;
                }
                if (preciseCallState.getForegroundCallState() != 3 || isDial) {
                    return;
                }
                isDial = true;
                isDisconnected = false;
                isIncoming = false;
                debarCall();
            }
        };
        getmTelephonyManager().listen(mPhoneStateListener,
                android.hardware.radio.V1_0.RadioAccessFamily.HSPA);
        isReg = true;
    }

    private void initListData() {
        attenuationList = new ArrayList<>();
        attenuationTime = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            attenuationTime.add(i, 0L);
        }
    }

    private boolean isTimeOut(long startTime, long endTime, long duration) {
        Log.d(TAG, "isTimeOut durationTime = " + duration + ",startTime = " + startTime
                + ",endTime = " + endTime);
        return endTime - startTime > duration;
    }

    private void reInitAttenuationTimeList(long averageTime) {
        if (mAttenuationEngine != null) {
            mAttenuationEngine.shouldModifyTimeList(averageTime);
            setAttenuationList(mAttenuationEngine.getTimeList());
        }
    }

    private void rebackNetworkType(int networkType, int arg) {
        Log.d(TAG, "rebackNetworkType : " + networkType + ",isInRatReback = " + isInRatReback);
        mCi.setPreferredNetworkType(networkType, null);
        setMobileDataEnabled(true);
        if (isInRatReback) {
            isInRatReback = false;
            sendNormalBrodcast(ACTION_REBACK_NEWTOK_MODE);
        }
    }

    private void recordAttenuationEndTime() {
        long tmpDTime = 0;
        if (getAttenuationTimes() != -1) {
            setAttenuationEndTime(getExitStartTime());
            tmpDTime = getAttenuationEndTime() - getAttenuationStartTime();
            if (tmpDTime > 60000) {
                getAttenuationTime().set(getAttenuationTimes(), tmpDTime);
            }
        }
        Log.d(TAG, "recordAttenuationEndTime start time = " + getAttenuationStartTime()
                + ",end time = " + getAttenuationEndTime() + ",tmpDTime = " + tmpDTime);
    }

    private void sendMutiNetworkModeBrodcast(Intent intent) {
        intent.putExtra(ENTER_DRTATION, getEnter_duration());
        intent.putExtra(ENTER_TIME, getExitStartTime());
        intent.putExtra(IS_LTE, isIs_lte());
        mContext.sendBroadcast(intent);
    }

    private void sendNormalBrodcast(String action) {
        mContext.sendBroadcast(new Intent(action));
        Log.d(TAG, "sendNormalBrodcast intent " + action);
    }

    private void setLimitativeNetworkType() {
        int networkMode = 7;
        if (oldRat == RIL_RADIO_TECHNOLOGY_LTE || currentRat == RIL_RADIO_TECHNOLOGY_LTE) {
            setCurrentNetworkType(22);
            setIs_lte(true);
            if (isGsm) {
                networkMode = 3;
            }
        } else {
            setIs_lte(false);
            setCurrentNetworkType(7);
            networkMode = isGsm ? 1 : 5;
        }
        Log.d(TAG, "mutinetwork setLimitativeNetworkType : " + networkMode);
        setExitStartTime(System.currentTimeMillis());
        isInRatReback = true;
        mCi.setPreferredNetworkType(networkMode, null);
        sendNormalBrodcast(ACTION_SET_NETWORK_SWITCH);
    }

    private void setNetworkModeInRat(int mode) {
        Log.d(TAG, "setNetworkModeInRat = " + mode);
        mCi.setPreferredNetworkType(mode, null);
    }

    private void setScreenOnListener() {
        if (mKeyguard == null) {
            mKeyguard = new KeyguardEventManager();
            mKeyguard.registerListener(mScreenOnListener);
        }
    }

    private boolean shouldSendLog() {
        return SystemProperties.getBoolean(SEND_MODEM_LOG, false);
    }

    private boolean shouldSwitch(long time, int times) {
        Log.d(TAG, "shouldSwitch old time  = " + oldTime + ", current time = " + currentTime);
        if (oldTime == 0) {
            oldTime = time;
            return false;
        }
        currentTime = time;
        long delta = currentTime - oldTime;
        if (delta > RAT_SWICTH_DURATION) {
            clearData();
            return false;
        }
        boolean should = times >= RAT_SWICTH_TIMES;
        if (should) {
            clearData();
            setEnter_duration(delta);
        }
        return should;
    }

    private void startAttMode() {
        setAttenuationStartTime(System.currentTimeMillis());
        if (getAttenuationTimes() == -1) {
            setAttenuationTimes(0);
        } else if (getAttenuationTimes() == 3) {
            setAttenuationTimes(0);
            reInitAttenuationTimeList(getAverageTime(getAttenuationTime()));
        } else {
            setAttenuationTimes(getAttenuationTimes() + 1);
        }
        Log.e(TAG, "startAttMode end getAttenuationTimes = " + getAttenuationTimes());
    }

    private void startExitRPM() {
        Log.d(TAG, "startExitRPM ");
        setExitStartTime(System.currentTimeMillis());
        isInRatReback = true;
        recordAttenuationEndTime();
    }

    public long getAttenuationAverageTime() {
        return attenuationAverageTime;
    }

    public long getAttenuationEndTime() {
        return attenuationEndTime;
    }

    public List<Long> getAttenuationList() {
        return attenuationList;
    }

    public long getAttenuationStartTime() {
        return attenuationStartTime;
    }

    public List<Long> getAttenuationTime() {
        return attenuationTime;
    }

    public int getAttenuationTimes() {
        return attenuationTimes;
    }

    public int getCurrentNetworkType() {
        int mode = Settings.Global.getInt(mContext.getContentResolver(),
                PREFERRED_NETWORK_MODE + getDefaultSubscriptionId(), 22);
        Log.d(TAG, "functional method:getCurrentNetworkMode = " + mode);
        return mode;
    }

    public long getEnter_duration() {
        return enter_duration;
    }

    public long getExitStartTime() {
        return exitStartTime;
    }

    public TelephonyManager getmTelephonyManager() {
        if (mTelephonyManager == null) {
            mTelephonyManager = (TelephonyManager) mContext.getSystemService(
                    Context.TELEPHONY_SERVICE);
        }
        return mTelephonyManager;
    }

    public boolean isHBCRRegister() {
        return isHBCRRegister;
    }

    public boolean isIs_lte() {
        return is_lte;
    }

    public void setAttenuationAverageTime(long attenuationAverageTime) {
        this.attenuationAverageTime = attenuationAverageTime;
    }

    public void setAttenuationEndTime(long attenuationEndTime) {
        this.attenuationEndTime = attenuationEndTime;
    }

    public void setAttenuationList(List<Long> attenuationList) {
        if (attenuationList != null) {
            Log.d(TAG, "attenuationList = " + attenuationList.size() + "," + attenuationList);
        }
        this.attenuationList = attenuationList;
    }

    public void setAttenuationStartTime(long attenuationStartTime) {
        this.attenuationStartTime = attenuationStartTime;
    }

    public void setAttenuationTime(List<Long> attenuationTime) {
        this.attenuationTime = attenuationTime;
    }

    public void setAttenuationTimes(int attenuationTimes) {
        this.attenuationTimes = attenuationTimes;
    }

    public void setCurrentNetworkType(int currentNetworkType) {
        this.currentNetworkType = currentNetworkType;
    }

    public void setData(int currentRat, int oldRat, CommandsInterface ci, boolean isGsm,
            Context context) {
        Log.d(TAG, "setData currentRat = " + currentRat);
        if (this.currentRat == currentRat || currentRat == 0) {
            return;
        }
        this.currentRat = currentRat;
        this.oldRat = oldRat;
        mCi = ci;
        this.isGsm = isGsm;
        mContext = context;
        initLisn();
        tmpTime = System.currentTimeMillis();
        ratSwitchTimes++;
        boolean shouldSwitch = shouldSwitch(tmpTime, ratSwitchTimes);
        Log.d(TAG, "ratSwitchTimes = " + ratSwitchTimes + ",shouldSwitch = " + shouldSwitch
                + ",mAttenuationEngine = " + mAttenuationEngine + ",this.currentRat = "
                + this.currentRat + "this.oldRat =" + oldRat);
        if (shouldSwitch) {
            if (shouldSendLog()) {
                Log.e(TAG, "sendBroadcast = com.qualcomm.qcrilmsgtunnel.ACTION_NETWORK_RAT");
                context.sendBroadcast(new Intent(ACTION_NETWORK_RAT));
                clearData();
            } else {
                setLimitativeNetworkType();
            }
        }
        if (mAttenuationEngine == null) {
            clearAttData();
            initListData();
            mAttenuationEngine = AttenuationEngine.getInstance();
            setAttenuationList(mAttenuationEngine.getTimeList());
        }
    }

    public void setEnter_duration(long enter_duration) {
        this.enter_duration = enter_duration;
    }

    public void setExitStartTime(long exitStartTime) {
        this.exitStartTime = exitStartTime;
    }

    public void setIsHBCRRegister(boolean isHBCRRegister) {
        this.isHBCRRegister = isHBCRRegister;
    }

    public void setIs_lte(boolean is_lte) {
        this.is_lte = is_lte;
    }

    public void setMobileDataEnabled(boolean enable) {
        int dataFlag = SystemProperties.getInt(DATA_STATE_MODEM, 0);
        Log.d(TAG, "setMobileDataEnabled：" + enable + ",data_flag = " + dataFlag);
        if (dataFlag == 1) {
            return;
        }
        if (!enable) {
            SystemProperties.set(DATA_STATE_MODEM, "2");
        }
        getmTelephonyManager().setDataEnabled(enable);
    }
}
