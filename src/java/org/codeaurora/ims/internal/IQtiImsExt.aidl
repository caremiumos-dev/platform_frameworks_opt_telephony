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

import org.codeaurora.ims.internal.IQtiImsExtListener;

/**
 * QTI IMS extension interface (vendor). Method order determines transaction codes and
 * must match the device: 1..15 oneway, 16..19 synchronous (return int).
 */
interface IQtiImsExt {
    oneway void setCallForwardUncondTimer(int phoneId, int startHour, int startMinute, int endHour,
            int endMinute, int action, int condition, int serviceClass, String number,
            IQtiImsExtListener listener);
    oneway void getCallForwardUncondTimer(int phoneId, int reason, int serviceClass,
            IQtiImsExtListener listener);
    oneway void getPacketCount(int phoneId, IQtiImsExtListener listener);
    oneway void getPacketErrorCount(int phoneId, IQtiImsExtListener listener);
    oneway void sendCallDeflectRequest(int phoneId, String deflectNumber,
            IQtiImsExtListener listener);
    oneway void sendCallTransferRequest(int phoneId, int type, String number,
            IQtiImsExtListener listener);
    oneway void sendCancelModifyCall(int phoneId, IQtiImsExtListener listener);
    oneway void queryVopsStatus(int phoneId, IQtiImsExtListener listener);
    oneway void querySsacStatus(int phoneId, IQtiImsExtListener listener);
    oneway void resumePendingCall(int phoneId, int videoState);
    oneway void registerForParticipantStatusInfo(int phoneId, IQtiImsExtListener listener);
    oneway void updateVoltePreference(int phoneId, int preference, IQtiImsExtListener listener);
    oneway void queryVoltePreference(int phoneId, IQtiImsExtListener listener);
    oneway void getHandoverConfig(int phoneId, IQtiImsExtListener listener);
    oneway void setHandoverConfig(int phoneId, int hoConfig, IQtiImsExtListener listener);
    int getRcsAppConfig(int phoneId);
    int setRcsAppConfig(int phoneId, int defaultSmsApp);
    int getVvmAppConfig(int phoneId);
    int setVvmAppConfig(int phoneId, int defaultVvmApp);
}
