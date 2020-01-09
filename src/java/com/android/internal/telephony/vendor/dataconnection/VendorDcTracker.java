/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.qualcomm.qti.internal.telephony.dataconnection;

import android.app.AlertDialog;
import android.view.WindowManager;

import android.telephony.AccessNetworkConstants;
import android.telephony.Annotation.DataFailureCause;
import android.telephony.CarrierConfigManager;
import android.telephony.DataFailCause;
import android.telephony.data.ApnSetting;
import android.os.SystemProperties;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import com.android.internal.telephony.dataconnection.ApnContext;
import com.android.internal.telephony.dataconnection.DcTracker;
import com.android.internal.telephony.DctConstants;
import com.android.internal.telephony.GsmCdmaPhone;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.util.ArrayUtils;
import com.android.internal.util.AsyncChannel;

import android.database.Cursor;
import android.content.Context;
import android.os.PersistableBundle;
import android.os.SystemProperties;
import android.provider.Telephony;

import java.util.HashSet;
import java.util.Iterator;

public final class QtiDcTracker extends DcTracker {
    private String LOG_TAG = "QtiDCT";
    private HashSet<String> mIccidSet = new HashSet<String>();
    private int mTransportType = AccessNetworkConstants.TRANSPORT_TYPE_WWAN;

    // Maximum data reject count
    protected static final int MAX_PDP_REJECT_COUNT = 3;

    // Data reset event tracker to know reset events.
    protected QtiDataResetEventTracker mQtiDataResetEventTracker = null;

    // data reject dialog, made static because only one dialog object can be
    // used between multiple dataconnection objects.
    protected static AlertDialog mDataRejectDialog = null;

    //Store data reject cause for comparison
    private String mPdpRejectCause = "NONE";

    // Constructor
    public QtiDcTracker(Phone phone, int transportType) {
        super(phone, transportType);
        mTransportType = transportType;
        LOG_TAG = LOG_TAG + "-" +
                ((transportType == AccessNetworkConstants.TRANSPORT_TYPE_WWAN) ? "C" : "I");
        if (DBG) log(LOG_TAG + ".constructor");
        fillIccIdSet();
    }

    @Override
    protected void onRecordsLoadedOrSubIdChanged() {
        if (DBG) log("onRecordsLoaded: createAllApnList");
        // Just support auto attach for WWAN only
        if (mTransportType == AccessNetworkConstants.TRANSPORT_TYPE_WWAN) {
            mAutoAttachOnCreationConfig = mPhone.getContext().getResources()
                    .getBoolean(com.android.internal.R.bool.config_auto_attach_data_on_creation);
        }

        createAllApnList();
        setDataProfilesAsNeeded();
        if (mIccRecords.get() != null && mIccRecords.get().getEssentialRecordsLoaded()) {
            setInitialAttachApn();
        }
        mPhone.notifyAllActiveDataConnections();
        setupDataOnAllConnectableApns(Phone.REASON_SIM_LOADED);
    }

    @Override
    protected boolean allowInitialAttachForOperator() {
        IccRecords r = mIccRecords.get();
        String iccId = (r != null) ? r.getIccId() : "";
        if (iccId != null) {
            Iterator<String> itr = mIccidSet.iterator();
            while (itr.hasNext()) {
                if (iccId.contains(itr.next())) {
                    return false;
                }
            }
        }
        return true;
    }

    private void fillIccIdSet() {
        mIccidSet.add("8991840");
        mIccidSet.add("8991854");
        mIccidSet.add("8991855");
        mIccidSet.add("8991856");
        mIccidSet.add("8991857");
        mIccidSet.add("8991858");
        mIccidSet.add("8991859");
        mIccidSet.add("899186");
        mIccidSet.add("8991870");
        mIccidSet.add("8991871");
        mIccidSet.add("8991872");
        mIccidSet.add("8991873");
        mIccidSet.add("8991874");
    }

    @Override
    protected void onVoiceCallEnded() {
        if (DBG) log("onVoiceCallEnded");
        mInVoiceCall = false;
        if (isConnected()) {
            if (!mPhone.getServiceStateTracker().isConcurrentVoiceAndDataAllowed()) {
                startNetStatPoll();
                startDataStallAlarm(DATA_STALL_NOT_SUSPECTED);
                mPhone.notifyAllActiveDataConnections();
            } else {
                // clean slate after call end.
                resetPollStats();
            }
        }
        //Allow data call retry only on DDS sub
        if (mPhone.getSubId() == SubscriptionManager.getDefaultDataSubscriptionId()) {
            // reset reconnect timer
            setupDataOnAllConnectableApns(Phone.REASON_VOICE_CALL_ENDED);
        }
    }

    @Override
    protected void setupDataOnConnectableApn(ApnContext apnContext, String reason,
            RetryFailures retryFailures) {
        if (mPhone.getContext().getResources().getBoolean(
                com.android.internal.R.bool.config_enable_retry_for_pdp_reject_29_33_55) &&
                apnContext.getDataRejectCount() > 0) {
            log("setupDataOnConnectableApn: data retry in progress, skip processing");
        } else {
            super.setupDataOnConnectableApn(apnContext, reason, retryFailures);
        }
    }

    @Override
    protected void onDataSetupComplete(ApnContext apnContext, boolean success, int cause,
            @RequestNetworkType int requestType) {
        boolean isPdpRejectConfigEnabled = mPhone.getContext().getResources().getBoolean(
                com.android.internal.R.bool.config_enable_retry_for_pdp_reject_29_33_55);
        if (isPdpRejectConfigEnabled && success) {
            handlePdpRejectCauseSuccess(apnContext);
        }

        super.onDataSetupComplete(apnContext, success, cause, requestType);
    }

    @Override
    protected void onDataSetupCompleteError(ApnContext apnContext,
            @RequestNetworkType int requestType) {
        long delay = 0;
        boolean isPdpRejectConfigEnabled = mPhone.getContext().getResources().getBoolean(
                com.android.internal.R.bool.config_enable_retry_for_pdp_reject_29_33_55);

        if (isPdpRejectConfigEnabled) {
            String reason = apnContext.getDataRejectReason();

            if (isPdpRejectCause(reason)) {
                if (mQtiDataResetEventTracker == null) {
                    mQtiDataResetEventTracker = new QtiDataResetEventTracker(mTransportType,
                            mPhone, mResetEventListener);
                }
                if (apnContext.getDataRejectCount() == 0) {
                    mQtiDataResetEventTracker.startResetEventTracker();
                }
                handlePdpRejectCauseFailure(apnContext, reason);

                /* If MAX Reject count reached, display pop-up to user */
                if (MAX_PDP_REJECT_COUNT <= apnContext.getDataRejectCount()) {
                    if (DBG) log("handleDataReject: reached max retry count");
                    displayPopup(apnContext.getDataRejectReason());
                    delay = -1;
                } else {
                    delay = mPhone.getContext().getResources().getInteger(
                            com.android.internal.R.integer.data_retry_delay_ms);
                    if (DBG) log("onDataSetupCompleteError: delay from config: " + delay);
                }
            }
        } else {
            delay = apnContext.getDelayForNextApn(mFailFast);
        }

        // Check if we need to retry or not.
        // TODO: We should support handover retry in the future.
        if (delay >= 0) {
            if (DBG) log("onDataSetupCompleteError: Try next APN. delay = " + delay);
            apnContext.setState(DctConstants.State.RETRYING);
            // Wait a bit before trying the next APN, so that
            // we're not tying up the RIL command channel

            startReconnect(delay, apnContext);
        } else {
            // If we are not going to retry any APN, set this APN context to failed state.
            // This would be the final state of a data connection.
            apnContext.setState(DctConstants.State.FAILED);
            mPhone.notifyDataConnection(apnContext.getApnType());
            apnContext.setDataConnection(null);
            if (DBG) log("onDataSetupCompleteError: Stop retrying APNs. delay=" + delay
                    + ", requestType=" + requestTypeToString(requestType));
        }
    }

    /*
     * Reset data reject params on data call success
     */
    private void handlePdpRejectCauseSuccess(ApnContext apnContext) {
        if (apnContext.getDataRejectCount() > 0) {
            if (DBG) log("handlePdpRejectCauseSuccess: reset reject count");
            apnContext.resetDataRejectCount();
            apnContext.resetDataRejectReason();
        }
    }

    /*
     * Process data failure if RAT is WCDMA
     */
    private void handlePdpRejectCauseFailure(ApnContext apnContext, String reason) {
        // only check for reject cause USER_AUTHENTICATION or
        // SERVICE_OPTION_NOT_SUBSCRIBED or MULTI_CONN_TO_SAME_PDN_NOT_ALLOWED if the rat
        // isWCDMA and feature flag is enabled
        if (isWCDMA(getDataRat())) {
            if (DBG) log("isPdpRejectCauseFailureHandled: RAT is WCDMA, handleDataReject");
            handleDataReject(apnContext, reason);
        } else {
            if (DBG) log("isPdpRejectCauseFailureHandled: DataConnection not on wcdma");
            apnContext.resetDataRejectCount();
            apnContext.resetDataRejectReason();
        }
    }

     /**
     * if Data call is rejected with following cause codes:
     * 1. USER_AUTHENTICATION
     * 2. SERVICE_OPTION_NOT_SUBSCRIBED
     * 3. MULTI_CONN_TO_SAME_PDN_NOT_ALLOWED
     */
    private void handleDataReject(ApnContext apnContext, String failCause) {
        if (DBG) log("handleDataReject: failCause=" + failCause +
                    ", mPdpRejectCause=" + mPdpRejectCause);
        /*
         * If previously rejected code is not same as current data reject cause,
         * then reset the count and reset the reject cause.
         */
        if (!apnContext.getDataRejectReason().equalsIgnoreCase(mPdpRejectCause)) {
            apnContext.resetDataRejectCount();
            apnContext.resetDataRejectReason();
        }

        /*
         * If failure reason is USER_AUTHENTICATION or
         * SERVICE_OPTION_NOT_SUBSCRIBED or MULTI_CONN_TO_SAME_PDN_NOT_ALLOWED,
         * increment counter and store cause
         */
        if (isPdpRejectCause(failCause)) {
            apnContext.incDataRejectCount();
            apnContext.setDataRejectReason(failCause);
            mPdpRejectCause = failCause;
            if (DBG) log ("handleDataReject: DataRejectCount = " + apnContext.getDataRejectCount());
        }
    }

    // Data reset event listener. Dc will get get onResetEvent
    // whenever any data reset event occurs.
    protected QtiDataResetEventTracker.ResetEventListener mResetEventListener =
           new QtiDataResetEventTracker.ResetEventListener() {

        @Override
        public void onResetEvent(boolean retry) {
            if (DBG) log("onResetEvent: retry=" + retry);

            //Dismiss dialog
            if (mDataRejectDialog != null && mDataRejectDialog.isShowing()) {
                if (DBG) log("onResetEvent: Dismiss dialog");
                mDataRejectDialog.dismiss();
            }
            mQtiDataResetEventTracker.stopResetEventTracker();

            for (ApnContext apnContext : mApnContexts.values()) {
                int rejectCount = apnContext.getDataRejectCount();
                if (rejectCount > 0) {
                    if (DBG) log("onResetEvent: reset reject count=" + rejectCount);
                    apnContext.resetDataRejectCount();
                    apnContext.resetDataRejectReason();
                    cancelReconnect(apnContext);
                    if (retry) {
                        if (DBG) log("onResetEvent: retry data call on apnContext=" + apnContext);
                        sendMessage(obtainMessage(DctConstants.EVENT_TRY_SETUP_DATA, apnContext));
                    }
                }
            }
        }
    };

    /**
     * This function will display the pdp reject message
     */
    private void displayPopup(String pdpRejectCause) {
        if (DBG) log("displayPopup : " + pdpRejectCause);
        String title = mPhone.getContext().getResources().
                getString(com.android.internal.R.string.data_conn_status_title);
        String message = null;
        if (pdpRejectCause.equalsIgnoreCase("USER_AUTHENTICATION")) {
            message = mPhone.getContext().getResources().
                    getString(com.android.internal.R.string.user_authentication_failed);
        } else if (pdpRejectCause.equalsIgnoreCase("SERVICE_OPTION_NOT_SUBSCRIBED")) {
            message = mPhone.getContext().getResources().getString(
                    com.android.internal.R.string.service_not_subscribed);
        } else if (pdpRejectCause.equalsIgnoreCase("MULTI_CONN_TO_SAME_PDN_NOT_ALLOWED")) {
            message = mPhone.getContext().getResources().getString(
                    com.android.internal.R.string.multi_conn_to_same_pdn_not_allowed);
        }
        if (mDataRejectDialog == null || !mDataRejectDialog.isShowing()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(
                    mPhone.getContext());
            builder.setPositiveButton(android.R.string.ok, null);
            mDataRejectDialog = builder.create();
        }
        mDataRejectDialog.setMessage(message);
        mDataRejectDialog.setCanceledOnTouchOutside(false);
        mDataRejectDialog.setTitle(title);
        mDataRejectDialog.getWindow().setType(
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        mDataRejectDialog.show();
    }

    /*
     * returns true if data reject cause matches errors listed
     */
    private boolean isPdpRejectCause(String reason) {
        return reason.equalsIgnoreCase("USER_AUTHENTICATION") ||
               reason.equalsIgnoreCase("SERVICE_OPTION_NOT_SUBSCRIBED") ||
               reason.equalsIgnoreCase("MULTI_CONN_TO_SAME_PDN_NOT_ALLOWED");
    }

    /**
     * returns true if radioTechnology is WCDMA rat, else false
     */
    private boolean isWCDMA(int radioTechnology) {
        return radioTechnology == ServiceState.RIL_RADIO_TECHNOLOGY_UMTS
            || radioTechnology == ServiceState.RIL_RADIO_TECHNOLOGY_HSDPA
            || radioTechnology == ServiceState.RIL_RADIO_TECHNOLOGY_HSUPA
            || radioTechnology == ServiceState.RIL_RADIO_TECHNOLOGY_HSPA
            || radioTechnology == ServiceState.RIL_RADIO_TECHNOLOGY_HSPAP;

    }

    @Override
    protected void log(String s) {
        Rlog.d(LOG_TAG, "[" + mPhone.getPhoneId() + "]" + s);
    }
}

