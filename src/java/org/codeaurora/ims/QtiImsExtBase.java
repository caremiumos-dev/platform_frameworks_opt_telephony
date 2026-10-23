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

import org.codeaurora.ims.internal.IQtiImsExt;
import org.codeaurora.ims.internal.IQtiImsExtListener;

public abstract class QtiImsExtBase {
    private QtiImsExtBinder mQtiImsExtBinder;

    public final class QtiImsExtBinder extends IQtiImsExt.Stub {
        public QtiImsExtBinder() {
        }

        @Override
        public void getCallForwardUncondTimer(int phoneId, int reason, int serviceClass,
                IQtiImsExtListener listener) {
            QtiImsExtBase.this.onGetCallForwardUncondTimer(phoneId, reason, serviceClass, listener);
        }

        @Override
        public void getHandoverConfig(int phoneId, IQtiImsExtListener listener) {
            QtiImsExtBase.this.onGetHandoverConfig(phoneId, listener);
        }

        @Override
        public void getPacketCount(int phoneId, IQtiImsExtListener listener) {
            QtiImsExtBase.this.onGetPacketCount(phoneId, listener);
        }

        @Override
        public void getPacketErrorCount(int phoneId, IQtiImsExtListener listener) {
            QtiImsExtBase.this.onGetPacketErrorCount(phoneId, listener);
        }

        @Override
        public int getRcsAppConfig(int phoneId) {
            return QtiImsExtBase.this.onGetRcsAppConfig(phoneId);
        }

        @Override
        public int getVvmAppConfig(int phoneId) {
            return QtiImsExtBase.this.onGetVvmAppConfig(phoneId);
        }

        @Override
        public void querySsacStatus(int phoneId, IQtiImsExtListener listener) {
            QtiImsExtBase.this.onQuerySsacStatus(phoneId, listener);
        }

        @Override
        public void queryVoltePreference(int phoneId, IQtiImsExtListener listener) {
            QtiImsExtBase.this.onQueryVoltePreference(phoneId, listener);
        }

        @Override
        public void queryVopsStatus(int phoneId, IQtiImsExtListener listener) {
            QtiImsExtBase.this.onQueryVopsStatus(phoneId, listener);
        }

        @Override
        public void registerForParticipantStatusInfo(int phoneId, IQtiImsExtListener listener) {
            QtiImsExtBase.this.onRegisterForParticipantStatusInfo(phoneId, listener);
        }

        @Override
        public void resumePendingCall(int phoneId, int videoState) {
            QtiImsExtBase.this.onResumePendingCall(phoneId, videoState);
        }

        @Override
        public void sendCallDeflectRequest(int phoneId, String deflectNumber,
                IQtiImsExtListener listener) {
            QtiImsExtBase.this.onSendCallDeflectRequest(phoneId, deflectNumber, listener);
        }

        @Override
        public void sendCallTransferRequest(int phoneId, int type, String number,
                IQtiImsExtListener listener) {
            QtiImsExtBase.this.onSendCallTransferRequest(phoneId, type, number, listener);
        }

        @Override
        public void sendCancelModifyCall(int phoneId, IQtiImsExtListener listener) {
            QtiImsExtBase.this.onSendCancelModifyCall(phoneId, listener);
        }

        @Override
        public void setCallForwardUncondTimer(int phoneId, int startHour, int startMinute,
                int endHour, int endMinute, int action, int condition, int serviceClass,
                String number, IQtiImsExtListener listener) {
            QtiImsExtBase.this.onSetCallForwardUncondTimer(phoneId, startHour, startMinute, endHour,
                    endMinute, action, condition, serviceClass, number, listener);
        }

        @Override
        public void setHandoverConfig(int phoneId, int hoConfig, IQtiImsExtListener listener) {
            QtiImsExtBase.this.onSetHandoverConfig(phoneId, hoConfig, listener);
        }

        @Override
        public int setRcsAppConfig(int phoneId, int defaultSmsApp) {
            return QtiImsExtBase.this.onSetRcsAppConfig(phoneId, defaultSmsApp);
        }

        @Override
        public int setVvmAppConfig(int phoneId, int defaultVvmApp) {
            return QtiImsExtBase.this.onSetVvmAppConfig(phoneId, defaultVvmApp);
        }

        @Override
        public void updateVoltePreference(int phoneId, int preference,
                IQtiImsExtListener listener) {
            QtiImsExtBase.this.onUpdateVoltePreference(phoneId, preference, listener);
        }
    }

    public QtiImsExtBinder getBinder() {
        if (mQtiImsExtBinder == null) {
            mQtiImsExtBinder = new QtiImsExtBinder();
        }
        return mQtiImsExtBinder;
    }

    protected void onGetCallForwardUncondTimer(int phoneId, int reason, int serviceClass,
            IQtiImsExtListener listener) {
    }

    protected void onGetHandoverConfig(int phoneId, IQtiImsExtListener listener) {
    }

    protected void onGetPacketCount(int phoneId, IQtiImsExtListener listener) {
    }

    protected void onGetPacketErrorCount(int phoneId, IQtiImsExtListener listener) {
    }

    protected int onGetRcsAppConfig(int phoneId) {
        return 0;
    }

    protected int onGetVvmAppConfig(int phoneId) {
        return 0;
    }

    protected void onQuerySsacStatus(int phoneId, IQtiImsExtListener listener) {
    }

    protected void onQueryVoltePreference(int phoneId, IQtiImsExtListener listener) {
    }

    protected void onQueryVopsStatus(int phoneId, IQtiImsExtListener listener) {
    }

    protected void onRegisterForParticipantStatusInfo(int phoneId, IQtiImsExtListener listener) {
    }

    protected void onRegisterForViceRefreshInfo(int phoneId, IQtiImsExtListener listener) {
    }

    protected void onResumePendingCall(int phoneId, int videoState) {
    }

    protected void onSendCallDeflectRequest(int phoneId, String deflectNumber,
            IQtiImsExtListener listener) {
    }

    protected void onSendCallTransferRequest(int phoneId, int type, String number,
            IQtiImsExtListener listener) {
    }

    protected void onSendCancelModifyCall(int phoneId, IQtiImsExtListener listener) {
    }

    protected void onSetCallForwardUncondTimer(int phoneId, int startHour, int startMinute,
            int endHour, int endMinute, int action, int condition, int serviceClass, String number,
            IQtiImsExtListener listener) {
    }

    protected void onSetHandoverConfig(int phoneId, int hoConfig, IQtiImsExtListener listener) {
    }

    protected int onSetRcsAppConfig(int phoneId, int defaultSmsApp) {
        return 0;
    }

    protected int onSetVvmAppConfig(int phoneId, int defaultVvmApp) {
        return 0;
    }

    protected void onUpdateVoltePreference(int phoneId, int preference,
            IQtiImsExtListener listener) {
    }
}
