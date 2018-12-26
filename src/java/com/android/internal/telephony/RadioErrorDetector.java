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
import android.provider.Settings;

import com.android.internal.annotations.VisibleForTesting;

import java.util.HashMap;

/**
 * Radio Error Detector
 *
 * {@hide}
 */
public class RadioErrorDetector {
    private static final String LOG_TAG = "RadioErrorDetector";

    //***** Variables
    private Context mContext;
    private int mContinuousWakelockTimoutCount = 0;
    private int mRadioErrorStatus = RADIO_ERROR_NONE;
    private int mSlotId;
    private int mDetectionStatus = RADIO_ERROR_DETECTION_UNKNOWN;
    private int mWakelockTimeoutThreshold = 0;
    private int mSystemErrorThreshold = 0;

    private HashMap<Integer, Integer> mSysErrRecord = new HashMap<Integer, Integer>();

    //***** Constants
    private static final int RADIO_ERROR_DETECTION_UNKNOWN = 0;
    private static final int RADIO_ERROR_DETECTION_DISABLED = 1;
    private static final int RADIO_ERROR_DETECTION_ENABLED = 2;

    /** Radio error constants */
    static final int RADIO_ERROR_NONE = 0x00;
    static final int RADIO_ERROR_UNEXPECTED_ERROR = 0x01;

    // Default configuration values for data stall detection.
    private static final int DEFAULT_WAKELOCK_TIMEOUT_THRESHOLD = 10;
    private static final int DEFAULT_SYSTEM_ERROR_THRESHOLD = 100;

    //***** Constructors
    public RadioErrorDetector(Context context, int slotId) {
        mContext = context;
        mSlotId = slotId;
        init();
    }

    private void init() {
        if (mDetectionStatus == RADIO_ERROR_DETECTION_UNKNOWN) {
            mDetectionStatus = Settings.Global.getInt(mContext.getContentResolver(),
                    Settings.Global.ENABLE_RADIO_ERROR_DETECTION, 0) != 0
                    ? RADIO_ERROR_DETECTION_ENABLED
                    : RADIO_ERROR_DETECTION_DISABLED;
            mWakelockTimeoutThreshold = Settings.Global.getInt(
                    mContext.getContentResolver(),
                    Settings.Global.RADIO_ERROR_WAKELOCK_TIMEOUT_THRESHOLD,
                    DEFAULT_WAKELOCK_TIMEOUT_THRESHOLD);
            mSystemErrorThreshold = Settings.Global.getInt(
                    mContext.getContentResolver(),
                    Settings.Global.RADIO_ERROR_SYSTEM_ERROR_THRESHOLD,
                    DEFAULT_SYSTEM_ERROR_THRESHOLD);
        }
    }

    /**
     * Function to process RIL response is received
     * @param requestType The command type information we retrieved
     * @param error The error we received
     **/
    public void processResponse(int requestType, int error) {
        if (mDetectionStatus != RADIO_ERROR_DETECTION_ENABLED) return;

        //If we can get response, it means RIL is still alive
        //But we may get SYSTEM_ERR if the connection with modem is broken
        //So notify RIL is still alive if error is not SYSTEM_ERR
        if (error == RadioError.SYSTEM_ERR) {
            int errorCount = mSysErrRecord.getOrDefault(requestType, 0);
            errorCount++;
            mSysErrRecord.put(requestType, errorCount);
            processError(true);
        } else {
            //Reset system error count if we get non-system error response.
            mSysErrRecord.remove(requestType);
            processNormalResponse();
        }
    }

    /**
     * Function to process wakelock timeout is detected
     **/
    public void processWakelockTimeout() {
        if (mDetectionStatus != RADIO_ERROR_DETECTION_ENABLED) return;
        mContinuousWakelockTimoutCount++;
        processError(false);
    }

    private void processNormalResponse() {
        mContinuousWakelockTimoutCount = 0;
        if (!isContinuousSystemError()) {
            mRadioErrorStatus = RADIO_ERROR_NONE;
        }
    }

    private void processError(boolean isSystemError) {
        if (isSystemError) {
            if (!isContinuousSystemError()) {
                //only notify RIL stuck if we continously receive SYSTEM_ERR and the number
                // larger than threshold
                return;
            }
        } else {
            if (mContinuousWakelockTimoutCount < mWakelockTimeoutThreshold) {
                //only notify RIL stuck if we continously receive WAKELOCK TIMEOUT and the number
                // larger than threshold
                return;
            }
        }
        boolean changed = false;
        if (mRadioErrorStatus != RADIO_ERROR_UNEXPECTED_ERROR) {
            changed = true;
            mRadioErrorStatus = RADIO_ERROR_UNEXPECTED_ERROR;
        }
        if (changed && mRadioErrorStatus != RADIO_ERROR_NONE) {
            Intent intent = new Intent(TelephonyIntents.ACTION_RADIO_ERROR);
            intent.putExtra(TelephonyIntents.EXTRA_SLOT_ID, mSlotId);
            intent.putExtra(TelephonyIntents.EXTRA_RADIO_ERROR, mRadioErrorStatus);
            mContext.sendBroadcast(intent);
        }
    }

    private boolean isContinuousSystemError() {
        int countForError = 0;
        boolean error = false;
        for (int count : mSysErrRecord.values()) {
            countForError += count;
            if (countForError >= mSystemErrorThreshold) {
                error = true;
                break;
            }
        }
        return error;
    }

    @VisibleForTesting
    public int getRadioErrorStatus() {
        return mRadioErrorStatus;
    }

    @VisibleForTesting
    public int getWakelockTimeoutThreshold() {
        return mWakelockTimeoutThreshold;
    }

    @VisibleForTesting
    public int getSystemErrorThreshold() {
        return mSystemErrorThreshold;
    }
}
