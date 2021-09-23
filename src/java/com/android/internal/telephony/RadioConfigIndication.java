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

import android.os.AsyncResult;

import com.android.internal.telephony.uicc.IccSlotStatus;
import com.android.telephony.Rlog;

import java.util.ArrayList;

/**
 * This class is the implementation of IRadioConfigIndication interface.
 */
public class RadioConfigIndication {
    private static final String TAG = "RadioConfigIndication";

    private final RadioConfig mRadioConfig;

    private RadioConfigIndicationV1 mRadioConfigIndicationV1;
    private RadioConfigIndicationV2 mRadioConfigIndicationV2;

    public RadioConfigIndication(RadioConfig radioConfig, HalVersion halVersion) {
        mRadioConfig = radioConfig;

        if (halVersion.greaterOrEqual(RIL.RADIO_HAL_VERSION_2_0)) {
            mRadioConfigIndicationV2 = new RadioConfigIndicationV2();
        } else {
            mRadioConfigIndicationV1 = new RadioConfigIndicationV1();
        }
    }

    public android.hardware.radio.config.V1_2.IRadioConfigIndication getV1() {
        return mRadioConfigIndicationV1;
    }

    public android.hardware.radio.config.IRadioConfigIndication getV2() {
        return mRadioConfigIndicationV2;
    }

    private final class RadioConfigIndicationV2 extends
            android.hardware.radio.config.IRadioConfigIndication.Stub {
        private static final String TAG = "RadioConfigIndication";

        /**
         * Unsolicited indication for slot status changed
         */
        @Override
        public void simSlotsStatusChanged(
                int type, android.hardware.radio.config.SimSlotStatus[] slotStatus) {
            ArrayList<IccSlotStatus> ret = RadioConfig.convertHalSlotStatus(slotStatus);
            logd("[UNSL]< UNSOL_SIM_SLOT_STATUS_CHANGED " + ret.toString());
            if (mRadioConfig.mSimSlotStatusRegistrant != null) {
                mRadioConfig.mSimSlotStatusRegistrant.notifyRegistrant(
                        new AsyncResult(null, ret, null));
            }
        }
    }

    private final class RadioConfigIndicationV1 extends
            android.hardware.radio.config.V1_2.IRadioConfigIndication.Stub {
        private static final String TAG = "RadioConfigIndication";

        /**
         * Unsolicited indication for slot status changed
         */
        public void simSlotsStatusChanged(int indicationType,
                ArrayList<android.hardware.radio.config.V1_0.SimSlotStatus> slotStatus) {
            ArrayList<IccSlotStatus> ret = RadioConfig.convertHalSlotStatus(slotStatus);
            logd("[UNSL]< UNSOL_SIM_SLOT_STATUS_CHANGED " + ret.toString());
            if (mRadioConfig.mSimSlotStatusRegistrant != null) {
                mRadioConfig.mSimSlotStatusRegistrant.notifyRegistrant(
                        new AsyncResult(null, ret, null));
            }
        }

        /**
         * Unsolicited indication for slot status changed
         */
        public void simSlotsStatusChanged_1_2(int indicationType,
                ArrayList<android.hardware.radio.config.V1_2.SimSlotStatus> slotStatus) {
            ArrayList<IccSlotStatus> ret = RadioConfig.convertHalSlotStatus_1_2(slotStatus);
            logd("[UNSL]< UNSOL_SIM_SLOT_STATUS_CHANGED " + ret.toString());
            if (mRadioConfig.mSimSlotStatusRegistrant != null) {
                mRadioConfig.mSimSlotStatusRegistrant.notifyRegistrant(
                        new AsyncResult(null, ret, null));
            }
        }
    }

    private static void logd(String log) {
        Rlog.d(TAG, log);
    }
}
