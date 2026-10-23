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

import android.content.Context;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.telephony.SubscriptionManager;
import android.util.Log;

import com.android.ims.ImsException;
import com.android.ims.ImsManager;

import org.codeaurora.ims.internal.IQtiImsExt;
import org.codeaurora.ims.internal.IQtiImsExtListener;

public class QtiImsExtManager {
    private static String LOG_TAG = "QtiImsExtManager";
    public static final String SERVICE_ID = "qti.ims.ext";
    private Context mContext;
    private IQtiImsExt mQtiImsExt;

    public QtiImsExtManager(Context context) {
        mContext = context;
    }

    private void checkFeatureStatus(int phoneId) throws QtiImsException {
        if (mContext == null) {
            throw new QtiImsException("Context is null");
        }
        try {
            // 2 == ImsFeature.STATE_READY（与设备一致，直接比较字面量）。
            if (ImsManager.getInstance(mContext, phoneId).getImsServiceStatus() != 2) {
                Log.e(LOG_TAG, "Feature status for phoneId " + phoneId + " is not ready");
                throw new QtiImsException("Feature state is NOT_READY");
            }
        } catch (ImsException e) {
            Log.e(LOG_TAG, "Got ImsException for phoneId " + phoneId);
            throw new QtiImsException("Feature state is NOT_READY");
        }
    }

    private void checkPhoneId(int phoneId) throws QtiImsException {
        if (SubscriptionManager.isValidPhoneId(phoneId)) {
            return;
        }
        Log.e(LOG_TAG, "phoneId " + phoneId + " is not valid");
        throw new QtiImsException("invalid phoneId");
    }

    private void handleQtiImsExtServiceDeath() {
        mQtiImsExt = null;
        Log.i(LOG_TAG, "qtiImsExtDeathListener QtiImsExt binder died");
    }

    private IQtiImsExt obtainBinder() throws QtiImsException {
        if (mQtiImsExt != null) {
            return mQtiImsExt;
        }
        IBinder service = ServiceManager.getService(SERVICE_ID);
        mQtiImsExt = IQtiImsExt.Stub.asInterface(service);
        if (mQtiImsExt == null) {
            throw new QtiImsException("ImsService is not running");
        }
        try {
            service.linkToDeath(new IBinder.DeathRecipient() {
                @Override
                public void binderDied() {
                    handleQtiImsExtServiceDeath();
                }
            }, 0);
        } catch (RemoteException e) {
            Log.e(LOG_TAG, "Unable to listen for QtiImsExt service death");
        }
        return mQtiImsExt;
    }

    public void getCallForwardUncondTimer(int phoneId, int reason, int serviceClass,
            IQtiImsExtListener listener) throws QtiImsException {
        obtainBinder();
        checkPhoneId(phoneId);
        checkFeatureStatus(phoneId);
        try {
            mQtiImsExt.getCallForwardUncondTimer(phoneId, reason, serviceClass, listener);
        } catch (RemoteException e) {
            throw new QtiImsException("Remote ImsService getCallForwardUncondTimer : " + e);
        }
    }

    public void getHandoverConfig(int phoneId, IQtiImsExtListener listener) throws QtiImsException {
        obtainBinder();
        checkPhoneId(phoneId);
        checkFeatureStatus(phoneId);
        try {
            mQtiImsExt.getHandoverConfig(phoneId, listener);
        } catch (RemoteException e) {
            throw new QtiImsException("Remote ImsService getHandoverConfig : " + e);
        }
    }

    public void getPacketCount(int phoneId, IQtiImsExtListener listener) throws QtiImsException {
        obtainBinder();
        checkPhoneId(phoneId);
        checkFeatureStatus(phoneId);
        try {
            mQtiImsExt.getPacketCount(phoneId, listener);
        } catch (RemoteException e) {
            throw new QtiImsException("Remote ImsService getPacketCount : " + e);
        }
    }

    public void getPacketErrorCount(int phoneId, IQtiImsExtListener listener)
            throws QtiImsException {
        obtainBinder();
        checkPhoneId(phoneId);
        checkFeatureStatus(phoneId);
        try {
            mQtiImsExt.getPacketErrorCount(phoneId, listener);
        } catch (RemoteException e) {
            throw new QtiImsException("Remote ImsService getPacketErrorCount : " + e);
        }
    }

    public int getRcsAppConfig(int phoneId) throws QtiImsException {
        obtainBinder();
        checkPhoneId(phoneId);
        checkFeatureStatus(phoneId);
        try {
            return mQtiImsExt.getRcsAppConfig(phoneId);
        } catch (RemoteException e) {
            throw new QtiImsException("Remote ImsService getRcsAppConfig : " + e);
        }
    }

    public int getVvmAppConfig(int phoneId) throws QtiImsException {
        obtainBinder();
        checkPhoneId(phoneId);
        checkFeatureStatus(phoneId);
        try {
            return mQtiImsExt.getVvmAppConfig(phoneId);
        } catch (RemoteException e) {
            throw new QtiImsException("Remote ImsService getVvmAppConfig : " + e);
        }
    }

    public boolean isConnected(int phoneId, int serviceType, int callType) throws QtiImsException {
        try {
            return ImsManager.getInstance(mContext, phoneId).isConnected(serviceType, callType);
        } catch (ImsException e) {
            throw new QtiImsException("Exception in Ims isConnected : " + e);
        }
    }

    public boolean isOpened(int phoneId) throws QtiImsException {
        try {
            return ImsManager.getInstance(mContext, phoneId).isOpened();
        } catch (ImsException e) {
            throw new QtiImsException("Exception in Ims isOpened : " + e);
        }
    }

    public void querySsacStatus(int phoneId, IQtiImsExtListener listener) throws QtiImsException {
        obtainBinder();
        checkPhoneId(phoneId);
        checkFeatureStatus(phoneId);
        try {
            mQtiImsExt.querySsacStatus(phoneId, listener);
        } catch (RemoteException e) {
            throw new QtiImsException("Remote ImsService querySsacStatus : " + e);
        }
    }

    public void queryVoltePreference(int phoneId, IQtiImsExtListener listener)
            throws QtiImsException {
        obtainBinder();
        checkPhoneId(phoneId);
        checkFeatureStatus(phoneId);
        try {
            mQtiImsExt.queryVoltePreference(phoneId, listener);
        } catch (RemoteException e) {
            throw new QtiImsException("Remote ImsService queryVoltePreference : " + e);
        }
    }

    public void queryVopsStatus(int phoneId, IQtiImsExtListener listener) throws QtiImsException {
        obtainBinder();
        checkPhoneId(phoneId);
        checkFeatureStatus(phoneId);
        try {
            mQtiImsExt.queryVopsStatus(phoneId, listener);
        } catch (RemoteException e) {
            throw new QtiImsException("Remote ImsService queryVopsStatus : " + e);
        }
    }

    public void registerForParticipantStatusInfo(int phoneId, IQtiImsExtListener listener)
            throws QtiImsException {
        obtainBinder();
        checkPhoneId(phoneId);
        checkFeatureStatus(phoneId);
        try {
            mQtiImsExt.registerForParticipantStatusInfo(phoneId, listener);
        } catch (RemoteException e) {
            throw new QtiImsException(
                    "Remote ImsService registerForParticipantStatusInfo : " + e);
        }
    }

    public void resumePendingCall(int phoneId, int videoState) throws QtiImsException {
        obtainBinder();
        checkPhoneId(phoneId);
        checkFeatureStatus(phoneId);
        try {
            mQtiImsExt.resumePendingCall(phoneId, videoState);
        } catch (RemoteException e) {
            throw new QtiImsException("Remote ImsService resumePendingCall : " + e);
        }
    }

    public void sendCallDeflectRequest(int phoneId, String deflectNumber,
            IQtiImsExtListener listener) throws QtiImsException {
        obtainBinder();
        checkPhoneId(phoneId);
        checkFeatureStatus(phoneId);
        try {
            mQtiImsExt.sendCallDeflectRequest(phoneId, deflectNumber, listener);
        } catch (RemoteException e) {
            throw new QtiImsException("Remote ImsService sendCallDeflectRequestCount : " + e);
        }
    }

    public void sendCallTransferRequest(int phoneId, int type, String number,
            IQtiImsExtListener listener) throws QtiImsException {
        obtainBinder();
        checkPhoneId(phoneId);
        checkFeatureStatus(phoneId);
        try {
            mQtiImsExt.sendCallTransferRequest(phoneId, type, number, listener);
        } catch (RemoteException e) {
            throw new QtiImsException("Remote ImsService sendCallTransferRequest : " + e);
        }
    }

    public void sendCancelModifyCall(int phoneId, IQtiImsExtListener listener)
            throws QtiImsException {
        obtainBinder();
        checkPhoneId(phoneId);
        checkFeatureStatus(phoneId);
        try {
            mQtiImsExt.sendCancelModifyCall(phoneId, listener);
        } catch (RemoteException e) {
            throw new QtiImsException("Remote ImsService sendCancelModifyCall : " + e);
        }
    }

    public void setCallForwardUncondTimer(int phoneId, int startHour, int startMinute, int endHour,
            int endMinute, int action, int condition, int serviceClass, String number,
            IQtiImsExtListener listener) throws QtiImsException {
        obtainBinder();
        checkPhoneId(phoneId);
        checkFeatureStatus(phoneId);
        try {
            mQtiImsExt.setCallForwardUncondTimer(phoneId, startHour, startMinute, endHour, endMinute,
                    action, condition, serviceClass, number, listener);
        } catch (RemoteException e) {
            throw new QtiImsException("Remote ImsService setCallForwardUncondTimer : " + e);
        }
    }

    public void setHandoverConfig(int phoneId, int hoConfig, IQtiImsExtListener listener)
            throws QtiImsException {
        obtainBinder();
        checkPhoneId(phoneId);
        checkFeatureStatus(phoneId);
        try {
            mQtiImsExt.setHandoverConfig(phoneId, hoConfig, listener);
        } catch (RemoteException e) {
            throw new QtiImsException("Remote ImsService setHandoverConfig : " + e);
        }
    }

    public int setRcsAppConfig(int phoneId, int defaultSmsApp) throws QtiImsException {
        obtainBinder();
        checkPhoneId(phoneId);
        checkFeatureStatus(phoneId);
        try {
            return mQtiImsExt.setRcsAppConfig(phoneId, defaultSmsApp);
        } catch (RemoteException e) {
            throw new QtiImsException("Remote ImsService setRcsAppConfig : " + e);
        }
    }

    public int setVvmAppConfig(int phoneId, int defaultVvmApp) throws QtiImsException {
        obtainBinder();
        checkPhoneId(phoneId);
        checkFeatureStatus(phoneId);
        try {
            return mQtiImsExt.setVvmAppConfig(phoneId, defaultVvmApp);
        } catch (RemoteException e) {
            throw new QtiImsException("Remote ImsService setVvmAppConfig : " + e);
        }
    }

    public void updateVoltePreference(int phoneId, int preference, IQtiImsExtListener listener)
            throws QtiImsException {
        obtainBinder();
        checkPhoneId(phoneId);
        checkFeatureStatus(phoneId);
        try {
            mQtiImsExt.updateVoltePreference(phoneId, preference, listener);
        } catch (RemoteException e) {
            throw new QtiImsException("Remote ImsService updateVoltePreference : " + e);
        }
    }
}
