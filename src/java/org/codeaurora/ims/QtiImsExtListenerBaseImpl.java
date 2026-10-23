/*
 * Copyright (c) 2017, The Linux Foundation. All rights reserved.
 *
 * Not a Contribution.
 * Copyright (C) 2016 The Android Open Source Project
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

package org.codeaurora.ims;

import org.codeaurora.ims.internal.IQtiImsExtListener;

public class QtiImsExtListenerBaseImpl extends IQtiImsExtListener.Stub {
    @Override
    public void notifyParticipantStatusInfo(int operation, int participantId, int status,
            String confUri, boolean isUserDisconnected) {
    }

    @Override
    public void notifySsacStatus(int ssacStatus, boolean isSsacOn) {
    }

    @Override
    public void notifyVopsStatus(int vopsStatus, boolean isVopsOn) {
    }

    @Override
    public void onGetCallForwardUncondTimer(int startHour, int startMinute, int endHour,
            int endMinute, int action, int condition, int serviceClass, String number, int error) {
    }

    @Override
    public void onGetHandoverConfig(int status, int hoConfig, int error) {
    }

    @Override
    public void onGetPacketCount(int status, int error, long packetCount) {
    }

    @Override
    public void onGetPacketErrorCount(int status, int error, long packetErrorCount) {
    }

    @Override
    public void onSetCallForwardUncondTimer(int status, int reason) {
    }

    @Override
    public void onSetHandoverConfig(int status, int error) {
    }

    @Override
    public void onUTReqFailed(int errorCode, int error, String errString) {
    }

    @Override
    public void onVoltePreferenceQueried(int result, int phoneId, int error) {
    }

    @Override
    public void onVoltePreferenceUpdated(int result, int error) {
    }

    @Override
    public void receiveCallDeflectResponse(int result, int error) {
    }

    @Override
    public void receiveCallTransferResponse(int result, int error) {
    }

    @Override
    public void receiveCancelModifyCallResponse(int result, int error) {
    }
}
