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

import com.android.internal.annotations.VisibleForTesting;

import java.util.HashMap;

/**
 * RIL Health Detector
 *
 * {@hide}
 */
public class RILHealthDetector {
    private static final String LOG_TAG = "RILHealthDetector";

    //***** Variables
    private Context mContext;
    private int mContinuousWakelockTimoutCount = 0;
    private int mRilHealthStatus = RILConstants.RIL_HEALTH_NORMAL;

    private HashMap<Integer, Integer> mSysErrRecord = new HashMap<Integer, Integer>();

    //***** Constants
    static final int RIL_HEALTH_THRESHOLD_WAKELOCK_TIMEOUT = 3;
    static final int RIL_HEALTH_THRESHOLD_SYSTEM_ERR_COMMAND_COUNT = 3;
    static final int RIL_HEALTH_THRESHOLD_SYSTEM_ERR = 15;

    //***** Constructors
    public RILHealthDetector(Context context) {
        mContext = context;
    }

    /**
     * Function to process RIL Indication is received
     **/
    public void processIndication() {
        //If we can get unsolicit command from low layer, it means RIL is not stuck or error
        //So notify RIL is still alive. So notify RIL is normal.
        notifyRilNormal();
    }

    /**
     * Function to process RIL response is received
     * @param requestType The command type information we retrieved
     * @param error The error we received
     **/
    public void processResponse(int requestType, int error) {
        //If we can get response, it means RIL is still alive
        //But we may get SYSTEM_ERR if the connection with modem is broken
        //So notify RIL is still alive if error is not SYSTEM_ERR
        if (error == RadioError.SYSTEM_ERR) {
            int errorCount = mSysErrRecord.getOrDefault(requestType, 0);
            errorCount++;
            mSysErrRecord.put(requestType, errorCount);
            notifyRilError(true);
        } else {
            notifyRilNormal();
        }
    }

    /**
     * Function to process wakelock timeout is detected
     **/
    public void processWakelockTimeout() {
        notifyRilError(false);
    }

    private void notifyRilNormal() {
        mContinuousWakelockTimoutCount = 0;
        mSysErrRecord.clear();
        if (mRilHealthStatus != RILConstants.RIL_HEALTH_NORMAL) {
            mRilHealthStatus = RILConstants.RIL_HEALTH_NORMAL;
            Intent intent = new Intent(TelephonyIntents.ACTION_RIL_HEALTH_STATUS);
            //intent.putExtra(TelephonyIntents.EXTRA_SUB_ID, getSubId());
            intent.putExtra(TelephonyIntents.EXTRA_RIL_HEALTH, mRilHealthStatus);
            mContext.sendBroadcast(intent);

        }
    }

    private void notifyRilError(boolean isSystemError) {
        if (isSystemError) {
            int commandCountForError = mSysErrRecord.size();
            int countForError = 0;
            for (int count : mSysErrRecord.values()) {
                countForError += count;
            }
            if (commandCountForError < RIL_HEALTH_THRESHOLD_SYSTEM_ERR_COMMAND_COUNT) {
                //only notify RIL stuck if we continously receive SYSTEM_ERR and the number
                // of different commands larger than RIL_HEALTH_THRESHOLD_SYSTEM_ERR_COMMAND_COUNT
                return;
            }
            if (countForError < RIL_HEALTH_THRESHOLD_SYSTEM_ERR) {
                //only notify RIL stuck if we continously receive SYSTEM_ERR and the number
                // larger than RIL_HEALTH_THRESHOLD_SYSTEM_ERR
                return;
            }
        } else {
            if (mContinuousWakelockTimoutCount < RIL_HEALTH_THRESHOLD_WAKELOCK_TIMEOUT) {
                //only notify RIL stuck if we continously receive WAKELOCK TIMEOUT and the number
                // larger than RIL_HEALTH_THRESHOLD_WAKELOCK_TIMEOUT
                return;
            }
        }
        boolean changed = false;
        if (isSystemError) {
            if ((mRilHealthStatus & RILConstants.RIL_HEALTH_CONSTANT_SYSTEM_ERR) == 0) {
                changed = true;
                mRilHealthStatus |= RILConstants.RIL_HEALTH_CONSTANT_SYSTEM_ERR;
            }
        } else {
            if ((mRilHealthStatus & RILConstants.RIL_HEALTH_STUCK) == 0) {
                changed = true;
                mRilHealthStatus |= RILConstants.RIL_HEALTH_STUCK;
            }
        }
        if (changed) {
            Intent intent = new Intent(TelephonyIntents.ACTION_RIL_HEALTH_STATUS);
            //intent.putExtra(TelephonyIntents.EXTRA_SUB_ID, getSubId());
            intent.putExtra(TelephonyIntents.EXTRA_RIL_HEALTH, mRilHealthStatus);
            mContext.sendBroadcast(intent);
        }
    }

    @VisibleForTesting
    public int getRilHealthStatus() {
        return mRilHealthStatus;
    }
}
