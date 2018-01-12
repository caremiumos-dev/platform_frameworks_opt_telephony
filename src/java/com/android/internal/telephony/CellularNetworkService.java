/*
 * Copyright 2017 The Android Open Source Project
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

import android.hardware.radio.V1_0.CellInfoType;
import android.hardware.radio.V1_0.DataRegStateResult;
import android.hardware.radio.V1_0.RegState;
import android.hardware.radio.V1_0.VoiceRegStateResult;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.telephony.CellIdentity;
import android.telephony.CellIdentityCdma;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityTdscdma;
import android.telephony.CellIdentityWcdma;
import android.telephony.NetworkRegistrationState;
import android.telephony.NetworkService;
import android.telephony.NetworkServiceCallback;
import android.telephony.Rlog;
import android.telephony.ServiceState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of network services for Cellular. It's a service that handles network requests
 * for Cellular. It passes the requests to inner CellularNetworkServiceProvider which has a
 * handler thread for each slot.
 */
public class CellularNetworkService extends NetworkService {
    private static final boolean DBG = false;
    private static final boolean VDBG = false;

    private static final String TAG = CellularNetworkService.class.getSimpleName();

    private static final int GET_CS_REGISTRATION_STATE_DONE = 1;
    private static final int GET_PS_REGISTRATION_STATE_DONE = 2;
    private static final int NETWORK_REGISTRATION_STATE_CHANGED = 3;

    private class CellularNetworkServiceProvider extends NetworkServiceProvider {

        private final Map<Message, NetworkServiceCallback> mCallbackMap = new HashMap<>();

        private final Looper mLooper;

        private final Handler mHandler;

        private Phone mPhone;

        CellularNetworkServiceProvider(int slotId) {
            super(slotId);

            mPhone = PhoneFactory.getPhone(getSlotId());

            HandlerThread thread = new HandlerThread(CellularNetworkService.class.getSimpleName());
            thread.start();
            mLooper = thread.getLooper();
            mHandler = new Handler(mLooper) {
                @Override
                public void handleMessage(Message message) {
                    NetworkServiceCallback callback = mCallbackMap.get(message);
                    mCallbackMap.remove(message);

                    AsyncResult ar;
                    switch (message.what) {
                        case GET_CS_REGISTRATION_STATE_DONE:
                        case GET_PS_REGISTRATION_STATE_DONE:
                            if (callback == null) return;
                            ar = (AsyncResult) message.obj;
                            int domain = (message.what == GET_CS_REGISTRATION_STATE_DONE)
                                    ? NetworkRegistrationState.DOMAIN_CS
                                    : NetworkRegistrationState.DOMAIN_PS;
                            NetworkRegistrationState netState =
                                    getRegistrationStateFromResult(ar.result, domain);

                            int resultCode;
                            if (ar.exception != null || netState == null) {
                                resultCode = NetworkServiceCallback.RESULT_ERROR_ILLEGAL_STATE;
                            } else {
                                resultCode = NetworkServiceCallback.RESULT_SUCCESS;
                            }

                            try {
                                if (DBG) {
                                    log("Calling callback.onGetNetworkRegistrationStateComplete."
                                            + "resultCode = " + resultCode
                                            + ", netState = " + netState);
                                }
                                callback.onGetNetworkRegistrationStateComplete(
                                         resultCode, netState);
                            } catch (Exception e) {
                                loge("Exception: " + e);
                            }
                            break;
                        case NETWORK_REGISTRATION_STATE_CHANGED:
                            notifyNetworkRegistrationStateChanged();
                            break;
                        default:
                            return;
                    }
                }
            };

            mPhone.mCi.registerForNetworkStateChanged(
                    mHandler, NETWORK_REGISTRATION_STATE_CHANGED, null);
        }

        private int getRegStateFromHalRegState(int halRegState) {
            switch (halRegState) {
                case RegState.NOT_REG_MT_NOT_SEARCHING_OP:
                case RegState.NOT_REG_MT_NOT_SEARCHING_OP_EM:
                    return NetworkRegistrationState.REG_STATE_NOT_REG_NOT_SEARCHING;
                case RegState.REG_HOME:
                    return NetworkRegistrationState.REG_STATE_HOME;
                case RegState.NOT_REG_MT_SEARCHING_OP:
                case RegState.NOT_REG_MT_SEARCHING_OP_EM:
                    return NetworkRegistrationState.REG_STATE_NOT_REG_SEARCHING;
                case RegState.REG_DENIED:
                case RegState.REG_DENIED_EM:
                    return NetworkRegistrationState.REG_STATE_DENIED;
                case RegState.UNKNOWN:
                case RegState.UNKNOWN_EM:
                    return NetworkRegistrationState.REG_STATE_UNKNOWN;
                case RegState.REG_ROAMING:
                    return NetworkRegistrationState.REG_STATE_ROAMING;
                default:
                    return NetworkRegistrationState.REG_STATE_NOT_REG_NOT_SEARCHING;
            }
        }

        private boolean isEmergencyOnly(int halRegState) {
            switch (halRegState) {
                case RegState.NOT_REG_MT_NOT_SEARCHING_OP_EM:
                case RegState.NOT_REG_MT_SEARCHING_OP_EM:
                case RegState.REG_DENIED_EM:
                case RegState.UNKNOWN_EM:
                    return true;
                case RegState.NOT_REG_MT_NOT_SEARCHING_OP:
                case RegState.REG_HOME:
                case RegState.NOT_REG_MT_SEARCHING_OP:
                case RegState.REG_DENIED:
                case RegState.UNKNOWN:
                case RegState.REG_ROAMING:
                default:
                    return false;
            }
        }

        private List<Integer> getAvailableServices(int regState, int domain,
                boolean emergencyOnly) {
            List<Integer> availableServices = new ArrayList<>();

            // In emergency only states, only SERVICE_TYPE_EMERGENCY is available.
            // Otherwise, certain services are available only if it's registered on home or roaming
            // network.
            if (emergencyOnly) {
                availableServices.add(NetworkRegistrationState.SERVICE_TYPE_EMERGENCY);
            } else if (regState == NetworkRegistrationState.REG_STATE_ROAMING
                    || regState != NetworkRegistrationState.REG_STATE_HOME) {
                if (domain == NetworkRegistrationState.DOMAIN_PS) {
                    availableServices.add(NetworkRegistrationState.SERVICE_TYPE_DATA);
                } else if (domain == NetworkRegistrationState.DOMAIN_CS) {
                    availableServices.add(NetworkRegistrationState.SERVICE_TYPE_VOICE);
                    availableServices.add(NetworkRegistrationState.SERVICE_TYPE_SMS);
                    availableServices.add(NetworkRegistrationState.SERVICE_TYPE_VIDEO);
                }
            }

            return availableServices;
        }

        private int getAccessNetworkTechnologyFromRat(int rilRat) {
            return ServiceState.rilRadioTechnologyToNetworkType(rilRat);
        }

        private NetworkRegistrationState getRegistrationStateFromResult(
                Object result, int domain) {

            if (result == null) {
                return null;
            }

            // TODO: unify when voiceRegStateResult and DataRegStateResult are unified.
            if (domain == NetworkRegistrationState.DOMAIN_CS) {
                VoiceRegStateResult voiceRegState = (VoiceRegStateResult) result;
                int transportType = NetworkRegistrationState.TRANSPORT_TYPE_WWAN;
                int regState = getRegStateFromHalRegState(voiceRegState.regState);
                int accessNetworkTechnology = getAccessNetworkTechnologyFromRat(voiceRegState.rat);
                int reasonForDenial = voiceRegState.reasonForDenial;
                boolean emergencyOnly = isEmergencyOnly(voiceRegState.regState);
                List<Integer> availableServices = getAvailableServices(
                        regState, domain, emergencyOnly);
                CellIdentity cellIdentity =
                        convertHalCellIdentityToCellIdentity(voiceRegState.cellIdentity);
                return new NetworkRegistrationState(transportType, domain, regState,
                        accessNetworkTechnology, reasonForDenial, emergencyOnly,
                        availableServices, cellIdentity);
            } else if (domain == NetworkRegistrationState.DOMAIN_PS) {
                DataRegStateResult dataRegState = (DataRegStateResult) result;
                int transportType = NetworkRegistrationState.TRANSPORT_TYPE_WWAN;
                int regState = getRegStateFromHalRegState(dataRegState.regState);
                int accessNetworkTechnology = getAccessNetworkTechnologyFromRat(dataRegState.rat);
                int reasonForDenial = dataRegState.reasonDataDenied;
                boolean emergencyOnly = isEmergencyOnly(dataRegState.regState);
                List<Integer> availableServices = getAvailableServices(
                        regState, domain, emergencyOnly);
                CellIdentity cellIdentity =
                        convertHalCellIdentityToCellIdentity(dataRegState.cellIdentity);
                return new NetworkRegistrationState(transportType, domain, regState,
                        accessNetworkTechnology, reasonForDenial, emergencyOnly,
                        availableServices, cellIdentity);
            } else {
                return null;
            }
        }

        private CellIdentity convertHalCellIdentityToCellIdentity(
                android.hardware.radio.V1_0.CellIdentity cellIdentity) {
            CellIdentity result = null;
            switch(cellIdentity.cellInfoType) {
                case CellInfoType.GSM: {
                    if (cellIdentity.cellIdentityGsm.size() == 1) {
                        android.hardware.radio.V1_0.CellIdentityGsm cellIdentityGsm =
                                cellIdentity.cellIdentityGsm.get(0);
                        result = new CellIdentityGsm(cellIdentityGsm.lac, cellIdentityGsm.cid,
                                cellIdentityGsm.arfcn, cellIdentityGsm.bsic, cellIdentityGsm.mcc,
                                cellIdentityGsm.mnc, null, null);
                    }
                    break;
                }
                case CellInfoType.WCDMA: {
                    if (cellIdentity.cellIdentityWcdma.size() == 1) {
                        android.hardware.radio.V1_0.CellIdentityWcdma cellIdentityWcdma =
                                cellIdentity.cellIdentityWcdma.get(0);
                        result = new CellIdentityWcdma(cellIdentityWcdma.lac, cellIdentityWcdma.cid,
                                cellIdentityWcdma.psc, cellIdentityWcdma.uarfcn,
                                cellIdentityWcdma.mcc, cellIdentityWcdma.mnc, null, null);
                    }
                    break;
                }
                case CellInfoType.TD_SCDMA: {
                    if (cellIdentity.cellIdentityTdscdma.size() == 1) {
                        android.hardware.radio.V1_0.CellIdentityTdscdma cellIdentityTdscdma =
                                cellIdentity.cellIdentityTdscdma.get(0);
                        result = new  CellIdentityTdscdma(cellIdentityTdscdma.mcc,
                                cellIdentityTdscdma.mnc, cellIdentityTdscdma.lac,
                                cellIdentityTdscdma.cid, cellIdentityTdscdma.cpid);
                    }
                    break;
                }
                case CellInfoType.LTE: {
                    if (cellIdentity.cellIdentityLte.size() == 1) {
                        android.hardware.radio.V1_0.CellIdentityLte cellIdentityLte =
                                cellIdentity.cellIdentityLte.get(0);

                        result = new CellIdentityLte(cellIdentityLte.ci, cellIdentityLte.pci,
                                cellIdentityLte.tac, cellIdentityLte.earfcn, cellIdentityLte.mcc,
                                cellIdentityLte.mnc, null, null);
                    }
                    break;
                }
                case CellInfoType.CDMA: {
                    if (cellIdentity.cellIdentityCdma.size() == 1) {
                        android.hardware.radio.V1_0.CellIdentityCdma cellIdentityCdma =
                                cellIdentity.cellIdentityCdma.get(0);

                        result = new CellIdentityCdma(cellIdentityCdma.networkId,
                                cellIdentityCdma.systemId, cellIdentityCdma.baseStationId,
                                cellIdentityCdma.longitude, cellIdentityCdma.latitude);
                    }
                    break;
                }
                case CellInfoType.NONE:
                default:
                    break;
            }

            return result;
        }

        public void getNetworkRegistrationState(int domain, NetworkServiceCallback callback) {
            if (DBG) log("getNetworkRegistrationState " + domain);
            Message message = null;

            if (domain == NetworkRegistrationState.DOMAIN_CS) {
                message = Message.obtain(mHandler, GET_CS_REGISTRATION_STATE_DONE);
                mPhone.mCi.getDataRegistrationState(message);
            } else if (domain == NetworkRegistrationState.DOMAIN_PS) {
                message = Message.obtain(mHandler, GET_PS_REGISTRATION_STATE_DONE);
                mPhone.mCi.getVoiceRegistrationState(message);
            } else {
                loge("getNetworkRegistrationState invalid domain " + domain);
            }

            if (message != null) {
                mCallbackMap.put(message, callback);
            }
        }
    }

    public CellularNetworkService() {
        super();
    }

    @Override
    protected NetworkServiceProvider createNetworkService(int slotId) {
        if (DBG) log("Cellular data service created for slot " + slotId);
        return new CellularNetworkServiceProvider(slotId);
    }

    private void log(String s) {
        Rlog.d(TAG, s);
    }

    private void loge(String s) {
        Rlog.e(TAG, s);
    }
}
