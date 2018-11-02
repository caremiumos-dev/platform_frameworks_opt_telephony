/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.internal.telephony;

import android.content.Context;
import android.content.Intent;
import android.hardware.radio.V1_0.RadioError;
import android.telephony.TelephonyManager;

import com.android.internal.annotations.VisibleForTesting;

import java.util.HashMap;

/**
 * RIL Health Detector
 *
 * {@hide}
 */
public class RadioErrorDetector {
    private static final String LOG_TAG = "RadioErrorDetector";

    //***** Variables
    private Context mContext;
    private int mContinuousWakelockTimoutCount = 0;
    private int mRadioErrorStatus = RADIO_ERROR_NONE;
    private boolean mInitialized = false;
    private int mSlotId;

    private HashMap<Integer, Integer> mSysErrRecord = new HashMap<Integer, Integer>();

    //***** Constants
    static final int THRESHOLD_WAKELOCK_TIMEOUT = 3;
    static final int THRESHOLD_SYSTEM_ERR = 10;

    /** Radio error constants */
    static final int RADIO_ERROR_NONE = 0x00;
    static final int RADIO_ERROR_UNEXPECTED_RESET = 0x01;
    static final int RADIO_ERROR_UNKNOWN_ERROR = 0x02;

    //***** Constructors
    public RadioErrorDetector(Context context, int slotId) {
        mContext = context;
        mSlotId = slotId;
    }

    /**
     * Function to process radio state change
     **/
    @VisibleForTesting
    public void processRadioStateChanged(int state) {
        if (state == TelephonyManager.RADIO_POWER_UNAVAILABLE) {
            if (mInitialized) {
                // radio crash detected
                mRadioErrorStatus = RADIO_ERROR_UNEXPECTED_RESET;
            }
        } else {
            mInitialized = true;
            if (mRadioErrorStatus == RADIO_ERROR_UNEXPECTED_RESET) {
                mRadioErrorStatus = RADIO_ERROR_NONE;
            }
        }
    }

    /**
     * Function to process RIL response is received
     * @param requestType The command type information we retrieved
     * @param error The error we received
     **/
    public void processResponse(int requestType, int error) {
        if (!mInitialized) return;

        //If we can get response, it means RIL is still alive
        //But we may get SYSTEM_ERR if the connection with modem is broken
        //So notify RIL is still alive if error is not SYSTEM_ERR
        if (error == RadioError.SYSTEM_ERR) {
            int errorCount = mSysErrRecord.getOrDefault(requestType, 0);
            errorCount++;
            mSysErrRecord.put(requestType, errorCount);
            processError(true);
        } else {
            mContinuousWakelockTimoutCount = 0;
            //Reset system error count if we get non-system error response.
            mSysErrRecord.remove(requestType);
            processNormalResponse();
        }
    }

    /**
     * Function to process wakelock timeout is detected
     **/
    public void processWakelockTimeout() {
        mContinuousWakelockTimoutCount++;
        processError(false);
    }

    private void processNormalResponse() {
        int errorCount = getSystemErrorCount();
        if (errorCount < THRESHOLD_SYSTEM_ERR) {
            mRadioErrorStatus = RADIO_ERROR_NONE;
        }
    }

    private void processError(boolean isSystemError) {
        if (!mInitialized) {
            // Radio is not yet ready, ignore it
            return;
        }
        if (mRadioErrorStatus == RADIO_ERROR_UNEXPECTED_RESET) {
            // Error is caused by radio reset, ignore it
            return;
        }
        if (isSystemError) {
            int errorCount = getSystemErrorCount();
            if (errorCount < THRESHOLD_SYSTEM_ERR) {
                //only notify RIL stuck if we continously receive SYSTEM_ERR and the number
                // larger than RIL_HEALTH_THRESHOLD_SYSTEM_ERR
                return;
            }
        } else {
            if (mContinuousWakelockTimoutCount < THRESHOLD_WAKELOCK_TIMEOUT) {
                //only notify RIL stuck if we continously receive WAKELOCK TIMEOUT and the number
                // larger than RIL_HEALTH_THRESHOLD_WAKELOCK_TIMEOUT
                return;
            }
        }
        boolean changed = false;
        if (mRadioErrorStatus != RADIO_ERROR_UNKNOWN_ERROR) {
            changed = true;
            mRadioErrorStatus = RADIO_ERROR_UNKNOWN_ERROR;
        }
        if (changed) {
            Intent intent = new Intent(TelephonyIntents.ACTION_RADIO_ERROR);
            intent.putExtra(TelephonyIntents.EXTRA_SLOT_ID, mSlotId);
            intent.putExtra(TelephonyIntents.EXTRA_RADIO_ERROR, mRadioErrorStatus);
            mContext.sendBroadcast(intent);
        }
    }

    private int getSystemErrorCount() {
        int countForError = 0;
        for (int count : mSysErrRecord.values()) {
            countForError += count;
        }
        return countForError;
    }

    @VisibleForTesting
    public int getRadioErrorStatus() {
        return mRadioErrorStatus;
    }
}
