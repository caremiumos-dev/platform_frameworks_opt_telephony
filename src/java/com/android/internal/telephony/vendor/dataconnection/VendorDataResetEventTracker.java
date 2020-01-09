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
package com.android.internal.telephony.vendor.dataconnection;

import com.android.internal.telephony.DctConstants;
import com.android.internal.telephony.Phone;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.os.SystemProperties;

import android.telephony.CellLocation;
import android.telephony.PhoneStateListener;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import java.util.ArrayList;

public class VendorDataResetEventTracker {

    public static interface ResetEventListener {
        public void onResetEvent();
    }

    private static final boolean DBG = true;

    private VendorDataConnection mDc;
    private TelephonyManager mTelephonyManager = null;
    private GsmCellLocation mPreviousLocation = null;
    private PhoneStateListener mPhoneStateListener = null;
    private Context mContext = null;
    private Phone mPhone = null;
    private ResetEventListener mListener = null;
    private int mPreviousRAT = 0;
    private int mTransportType;

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case DctConstants.EVENT_DATA_RAT_CHANGED:
                AsyncResult ar = (AsyncResult) msg.obj;
                Pair<Integer, Integer> result = (Pair<Integer, Integer>) ar.result;
                if (result != null) {
                    if (mPreviousRAT > 0 && result.second > 0
                            && mPreviousRAT != result.second) {
                        if (DBG) log("RAT CHANGED, " + mPreviousRAT
                                 + "->" + result.second);
                        notifyResetEvent();
                    }
                    mPreviousRAT = result.second;
                }
                break;
            }
        }
    };

    public VendorDataResetEventTracker(int transportType, Phone phone, VendorDataConnection dc,
            ResetEventListener listener) {
        mDc = dc;
        if (DBG) log("VendorDataResetEventTracker constructor: " + this);
        mPhone = phone;
        mContext = mPhone.getContext();
        this.mListener = listener;
        mTransportType = transportType;
        mTelephonyManager = (TelephonyManager) mContext
                .getSystemService(Context.TELEPHONY_SERVICE);
    }

    /**
     * Register listener for RAU update and RAT change.
     */
    public void startResetEventTracker() {
        if (DBG) log("startResetEventTracker");
        stopResetEventTracker();
        mPhone.getServiceStateTracker().registerForDataRegStateOrRatChanged(
                mTransportType, mHandler, DctConstants.EVENT_DATA_RAT_CHANGED, null);

        CellLocation currentCellLocation = mPhone.getCellLocation();
        if (currentCellLocation instanceof GsmCellLocation) {
            mPreviousLocation = (GsmCellLocation) mPhone.getCellLocation();
            if (DBG) log("DataConnection mPreviousLocation : " + mPreviousLocation);
        }
        int ddsSubId = SubscriptionManager.getDefaultDataSubscriptionId();

        if (mPhoneStateListener == null) {
            mPhoneStateListener = new PhoneStateListener() {
                public void onCellLocationChanged(CellLocation location) {
                    if (DBG) log("DataConnection onCellLocationChanged : "
                                + location);

                    if (location instanceof GsmCellLocation) {
                        GsmCellLocation currentLocation = (GsmCellLocation) location;

                        if (mPreviousLocation != null
                                && currentLocation != null) {
                            if (mPreviousLocation.getCid() != currentLocation
                                    .getCid()
                                    || mPreviousLocation.getLac() != currentLocation
                                            .getLac()) {
                                if (DBG) log("DataConnection location updated");
                                notifyResetEvent();
                            }
                        }
                        mPreviousLocation = currentLocation;
                    }
                }
            };
        }

        mTelephonyManager.
                createForSubscriptionId(ddsSubId).
                listen(mPhoneStateListener, PhoneStateListener.LISTEN_CELL_LOCATION);
    }

    /**
     * Unregister for RAU update and RAT change.
     */
    public void stopResetEventTracker() {
        if (DBG) log("stopResetTimer");
        try {
            mPreviousRAT = 0;
            mPreviousLocation = null;
            if (mPhoneStateListener != null) {
                mTelephonyManager.listen(mPhoneStateListener,
                        PhoneStateListener.LISTEN_NONE);
            }
            mPhone.getServiceStateTracker()
                    .unregisterForDataRegStateOrRatChanged(mTransportType, mHandler);
        } catch (Exception e) {
            if (DBG) log("error:" + e.getMessage());
            e.printStackTrace();
        }
    }

    public void dispose() {
        if (DBG) log("dispose");
        stopResetEventTracker();
        mTelephonyManager = null;
    }

    /**
     * notify the listener for reset event
     */
    private void notifyResetEvent() {
        stopResetEventTracker();
        if (mListener != null) {
            mListener.onResetEvent();
        }
    }

    private void log(String log) {
        Rlog.d(mDc.getName() + "[DRET]", log);
    }
}

