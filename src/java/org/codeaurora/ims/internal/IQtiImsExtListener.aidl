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

package org.codeaurora.ims.internal;

/**
 * QTI IMS extension listener (vendor). Declaration order fixes the transaction codes and
 * argument arities must match the device (all oneway, 1..15).
 */
oneway interface IQtiImsExtListener {
    void onSetCallForwardUncondTimer(int status, int reason);
    void onGetCallForwardUncondTimer(int startHour, int startMinute, int endHour, int endMinute,
            int action, int condition, int serviceClass, String number, int error);
    void onUTReqFailed(int errorCode, int error, String errString);
    void onGetPacketCount(int status, int error, long packetCount);
    void onGetPacketErrorCount(int status, int error, long packetErrorCount);
    void receiveCallDeflectResponse(int result, int error);
    void receiveCallTransferResponse(int result, int error);
    void receiveCancelModifyCallResponse(int result, int error);
    void notifyVopsStatus(int vopsStatus, boolean isVopsOn);
    void notifySsacStatus(int ssacStatus, boolean isSsacOn);
    void notifyParticipantStatusInfo(int operation, int participantId, int status, String confUri,
            boolean isUserDisconnected);
    void onVoltePreferenceUpdated(int result, int error);
    void onVoltePreferenceQueried(int result, int phoneId, int error);
    void onSetHandoverConfig(int status, int error);
    void onGetHandoverConfig(int status, int hoConfig, int error);
}
