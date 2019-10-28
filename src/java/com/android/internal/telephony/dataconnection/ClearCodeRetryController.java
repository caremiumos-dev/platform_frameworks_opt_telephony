package com.android.internal.telephony.dataconnection;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.Resources;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.telephony.DataFailCause;
import android.telephony.PhoneStateListener;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.WindowManager;

import com.android.internal.telephony.DctConstants;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.dataconnection.DataConnection;

public class ClearCodeRetryController extends Handler{
    private static final String TAG = "ClearCode";

    private static final int EVENT_RADIO_ON = 0;
    private static final int EVENT_RAU_SUCCESS = 1;
    private static final int EVENT_TRY_SETUP_DATA = 2;
    private static final int EVENT_RADIO_OFF_OR_NOT_AVAILABLE = 3;
    private static final int EVENT_SERVICE_STATE_CHANGED = 4;

    // when the data can't register to LTE in 40s, we will retry again.
    private static final int SWITCH_TIMEOUT = 40 * 1000;

    private static final int IDLE = 0;
    private static final int SWITCHING_TO_3G = 1;
    private static final int SWITCHING_TO_4G = 2;
    private static final int MAX_PDN_REJ_TIMES_FOR_VOLTE = 1;
    private static final int MAX_PDN_REJ_TIMES = 3;

    public static final int RETRY_DELAY_LONG  = 45000;
    public static final int RETRY_DELAY_SHORT = 10000;
    public static final int RETRY_FROM_FAILURE_DELAY = 2 * 3600 * 1000; // 2 hours

    private static final String VOLTE_CLEAR_CODE = "volte";
    private static final String NON_VOLTE_CLEAR_CODE = "non-volte";

    private Phone mPhone;
    private DcTracker mDct;
    private HandlerThread mHt;
    private boolean mLteDisabled = false;
    private int mState = IDLE;
    private String mClearCodeConfig = null;

    public ClearCodeRetryController(Phone phone, DcTracker dct) {
        mPhone = phone;
        mDct =  dct;
        mPhone.mCi.registerForOn(this, EVENT_RADIO_ON, null);
        mPhone.mCi.registerForRauSuccess(this, EVENT_RAU_SUCCESS, null);
        mHt = new HandlerThread(TAG);
        mHt.start();
    }

    public void dispose() {
        log("ClearCodeRetryController.dispose()");
        mPhone.mCi.unregisterForOn(this);
        mPhone.mCi.unregisterForRauSuccess(this);

        mHt.quit();
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case EVENT_TRY_SETUP_DATA:
                log("EVENT_TRY_SETUP_DATA");
                mDct.clearPreFailCause();
                mState = IDLE;
                mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_TRY_SETUP_DATA, msg.obj));
                break;
            case EVENT_RAU_SUCCESS:
                log("EVENT_RAU_SUCCESS");
                onDataConnectionRau();
                break;
            case EVENT_RADIO_ON:
                log("EVENT_RADIO_ON");
                mDct.clearPreFailCause();
                mState = IDLE;
                // in case the lte is disabled before, enable it now
                if (mLteDisabled || (isNonVolteClearCode() && mDct.getFailCount() >= MAX_PDN_REJ_TIMES)
                        || (isVolteClearCode() && mDct.getFailCount() >= MAX_PDN_REJ_TIMES_FOR_VOLTE)) {
                    enableLte(true);
                    mLteDisabled = false;
                }
                if (isVolteClearCode()){
                    mDct.stopFailRetryAlarm();
                    mDct.cancelAllReconnectAlarms();
                }
                break;
            case EVENT_SERVICE_STATE_CHANGED:
                log("EVENT_SERVICE_STATE_CHANGED");
                AsyncResult ar = (AsyncResult) msg.obj;
                ServiceState state = (ServiceState) ar.result;
                int voiceNetType = state.getVoiceNetworkType();
                int voiceRegState = state.getVoiceRegState();
                log("onServiceStateChanged voiceNetType=" + voiceNetType
                        + " voiceRegState=" + voiceRegState);
                handleVoiceServiceChange(voiceNetType, voiceRegState);
                break;
            case EVENT_RADIO_OFF_OR_NOT_AVAILABLE:
                log("EVENT_RADIO_OFF_OR_NOT_AVAILABLE");
                // clear flags when radio is off
                mDct.clearPreFailCause();
                mState = IDLE;
                break;
        }
    }

    public void notifyRadioOffOrNotAvailable() {
        sendMessage(obtainMessage(EVENT_RADIO_OFF_OR_NOT_AVAILABLE));
    }

    public void switchTo3G() {
        log("Start switching to 3G...");
        dataConnectionAttach(false);
        enableLte(false);
        mLteDisabled = true;
        mState = SWITCHING_TO_3G;
        mPhone.registerForServiceStateChanged(this, EVENT_SERVICE_STATE_CHANGED, null);
    }

    // Wait for the RAT we want to register to
    public void handleDataServiceChange(int rat, int regState) {
        log("Data RAT=" + rat + ", regState=" + regState + ", mState = " + mState);
        if (mState == SWITCHING_TO_3G) {
            if (rat != ServiceState.RIL_RADIO_TECHNOLOGY_LTE &&
                    rat != ServiceState.RIL_RADIO_TECHNOLOGY_UNKNOWN) {
                // retry in 3G, so clear mPreFailcause
                mDct.clearPreFailCause();
                mState = IDLE;
            }
        } else if (mState == SWITCHING_TO_4G) {
            if (rat == ServiceState.RIL_RADIO_TECHNOLOGY_LTE) {
                // restart from beginning, so clear mPreFailcause
                mDct.clearPreFailCause();
                mState = IDLE;
                this.removeMessages(EVENT_TRY_SETUP_DATA);
            }
        }
    }

    // Finish the "detach -> disable lte -> attach" sequence
    public void handleVoiceServiceChange(int rat, int regState) {
        if (rat != TelephonyManager.NETWORK_TYPE_LTE
                && regState == ServiceState.STATE_IN_SERVICE) {
            log("Voice registerd on " + rat + ", switching to 3G completed");
            dataConnectionAttach(true);
            mPhone.unregisterForServiceStateChanged(this);
        }
    }

    public void restartForChanged(String reason) {
        restartCycle(reason);
        // We will restart from begin, so stop all retry alarms
        mDct.stopFailRetryAlarm();
        mDct.cancelAllReconnectAlarms();
    }

    private void onDataConnectionRau() {
        restartForChanged("RoutingAreaUpdate");
    }

    // After 2 hours or RAU, restart from beginning
    public void restartCycle(Object obj) {
        log("restartCycle: " + obj + ", mLteDisabled = " + mLteDisabled
                + ", mDct.getFailCount = " + mDct.getFailCount());
        mState = IDLE;
        mDct.clearPreFailCause();
        if (mLteDisabled || (isNonVolteClearCode() && mDct.getFailCount() >= MAX_PDN_REJ_TIMES)
                || (isVolteClearCode() && mDct.getFailCount() >= MAX_PDN_REJ_TIMES_FOR_VOLTE)) {
            enableLte(true);
            mState = SWITCHING_TO_4G;
            mLteDisabled = false;
            // wait for RAT change to 4G or timeout
            sendMessageDelayed(obtainMessage(EVENT_TRY_SETUP_DATA, obj),
                    SWITCH_TIMEOUT);
        } else {
            sendMessage(obtainMessage(EVENT_TRY_SETUP_DATA, obj));
        }
    }

    private void enableLte(boolean enable) {
        mPhone.mCi.enableLte(enable);
    }

    private void dataConnectionAttach(boolean attach) {
        mPhone.mCi.attachGprs(attach);
    }

    public static boolean supportSpecialClearCode() {
        return (isVolteClearCode() || isNonVolteClearCode());
    }

    public static boolean isVolteClearCode() {
        String clearCodeConfig = Resources.getSystem().
                getString(com.android.internal.R.string.config_dataconnection_data_clear_code);
        if (!TextUtils.isEmpty(clearCodeConfig) && VOLTE_CLEAR_CODE.equals(clearCodeConfig)){
             return true;
        }
        return false;
    }

    public static boolean isNonVolteClearCode() {
        String clearCodeConfig = Resources.getSystem().
                getString(com.android.internal.R.string.config_dataconnection_data_clear_code);
        if (!TextUtils.isEmpty(clearCodeConfig) && NON_VOLTE_CLEAR_CODE.equals(clearCodeConfig)){
             return true;
        }
        return false;
    }

    public boolean isSpecialCode(int fc) {
        if(supportSpecialClearCode()){
            return fc == DataFailCause.USER_AUTHENTICATION
                || fc == DataFailCause.SERVICE_OPTION_NOT_SUBSCRIBED;
        }
        return false;
    }

    public void userNotification(int cause) {
        AlertDialog errorDialog = null;
        if(supportSpecialClearCode()){
            int res = -1;
            if (cause == DataFailCause.USER_AUTHENTICATION) {
                res = com.android.internal.R.string.failcause_user_authentication;
            } else if (cause == DataFailCause.SERVICE_OPTION_NOT_SUBSCRIBED) {
                res = com.android.internal.R.string.failcause_service_option_not_subscribed;
            }

            if (res == -1) {
                return;
            }

            errorDialog = new AlertDialog.Builder(mPhone.getContext(),
                    AlertDialog.THEME_DEVICE_DEFAULT_LIGHT)
                    .setMessage(res)
                    .setPositiveButton(android.R.string.ok, null)
                     .create();
            errorDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
            errorDialog.setCanceledOnTouchOutside(false);
        }

        if (errorDialog != null) {
            errorDialog.show();
        }
    }

    private void log(String s) {
        Rlog.d(TAG, "[" + mPhone.getPhoneId() + "]" + s);
    }
}
