package com.android.internal.telephony.dataconnection;

import java.util.HashMap;

import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.DctConstants;
import com.android.internal.telephony.Phone;

import android.os.Messenger;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.NetworkAgent;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.TrafficDescriptor;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.telephony.AccessNetworkConstants;
import android.telephony.Rlog;
import android.telephony.TelephonyManager;
import android.util.LocalLog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;

import android.telephony.data.RouteSelectionDescriptor;
import android.hardware.radio.V1_5.UrspEvaluateResult;

public class UrspTracker extends Handler{
    private static final String TAG = "UrspTracker";

    private final Phone mPhone;
    private final HashMap<NetworkRequest, LocalLog> mRequests =
            new HashMap<NetworkRequest, LocalLog>();
    private ConnectivityManager mCm = null;
    public CommandsInterface mCi;

    public UrspTracker(Phone phone) {
        mPhone = phone;
        mCm = (ConnectivityManager)mPhone.getContext().getSystemService(
                Context.CONNECTIVITY_SERVICE);
    }

    @Override
    public void handleMessage(Message msg) {
        log("handleMessage msg = " + msg.toString());
        AsyncResult ar = (AsyncResult) msg.obj;
        switch (msg.what) {
            case DctConstants.EVENT_EVALUATE_URSP_DONE:
                onEvaluateUrspDone(ar);
                break;

            default:
                break;
        }
    }

    public void evaluateUrsp(NetworkRequest request, LocalLog log) {
        mRequests.put(request, log);
        log.log("UrspTrack evaluateUrsp");
        TrafficDescriptor td = request.trafficDescriptor;
        log("evaluateUrsp request = " + request);
        mPhone.mCi.evaluateUrspRules(td,
                obtainMessage(DctConstants.EVENT_EVALUATE_URSP_DONE, request));
    }

    private void onEvaluateUrspDone(AsyncResult ar) {
        int state;
        UrspEvaluateResult result = null;
        NetworkRequest request = (NetworkRequest)ar.userObj;
        LocalLog log = mRequests.remove(request);

        if (ar.exception == null) {
            result = (UrspEvaluateResult)ar.result;
            state = result.state;
        } else {
            state = ConnectivityManager.URSP_RESULT_FAILED;
        }
        log("onEvaluateUrspDone, state = " + state);
        switch (state) {
            case ConnectivityManager.URSP_RESULT_FAILED:
                onEvaluatedFaled(request, log);
                break;
            case ConnectivityManager.URSP_RESULT_EXISTPDU:
                onEvaluatedExistPdu(request, result, log);
                break;
            case ConnectivityManager.URSP_RESULT_NON3GPP:
                onEvaluatedNon3gpp(request, log);
                break;
            case ConnectivityManager.URSP_RESULT_NEWPDU:
                onEvaluatedNewPdu(request, result, log);
                break;
            default:
                break;
        }
    }

    private void onEvaluatedFaled(NetworkRequest request, LocalLog log) {
        mCm.handleEvaluatedNetworkRequest(ConnectivityManager.URSP_RESULT_FAILED, 0, request);
    }

    private void onEvaluatedExistPdu(NetworkRequest request,
            UrspEvaluateResult result, LocalLog log) {
        int cid = result.cid;
        int netId = mPhone.getDcTracker(AccessNetworkConstants.TRANSPORT_TYPE_WWAN).getNetworkAgentId(cid);
        mCm.handleEvaluatedNetworkRequest(ConnectivityManager.URSP_RESULT_EXISTPDU, netId, request);
    }

    private void onEvaluatedNon3gpp(NetworkRequest request, LocalLog log) {
        mCm.handleEvaluatedNetworkRequest(ConnectivityManager.URSP_RESULT_NON3GPP, 0, request);
    }

    private void onEvaluatedNewPdu(NetworkRequest request,
            UrspEvaluateResult result, LocalLog log) {
        RouteSelectionDescriptor rsd =
                converToRouteSelectionDescriptor(result);
        mPhone.getDcTracker(AccessNetworkConstants.TRANSPORT_TYPE_WWAN).requestNetworkWithRsd(request, rsd, log);
    }

    private RouteSelectionDescriptor converToRouteSelectionDescriptor(
            UrspEvaluateResult result) {
        RouteSelectionDescriptor rsd = new RouteSelectionDescriptor();
        if (result != null) {
            rsd.setUrspCount(result.rsd.urspCount);
            rsd.setRsdCount(result.rsd.rsdCount);
            rsd.setId(result.rsd.id);
            rsd.setDnn(result.rsd.dnn);
            rsd.setSnssai(result.rsd.snssai);
            rsd.setSscMode(result.rsd.sscMode);
        }
        return rsd;
    }

    private class UrspNetworkAgent extends NetworkAgent {

        public UrspNetworkAgent(Looper looper, Context context, String logTag,
                NetworkInfo ni, NetworkCapabilities nc, LinkProperties lp,
                int score) {
            super(looper, context, logTag, ni, nc, lp, score);
        }

        protected void unwanted() {
        }
    }
    private void log(String str) {
        Rlog.d(TAG, "[" + mPhone.getPhoneId() + "]" + str);
    }
}
