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

import static android.telephony.TelephonyManager.CAPABILITY_ALLOWED_NETWORK_TYPES_USED;
import static android.telephony.TelephonyManager
        .CAPABILITY_NR_DUAL_CONNECTIVITY_CONFIGURATION_AVAILABLE;
import static android.telephony.TelephonyManager.CAPABILITY_PHYSICAL_CHANNEL_CONFIG_1_6_SUPPORTED;
import static android.telephony.TelephonyManager.CAPABILITY_SECONDARY_LINK_BANDWIDTH_VISIBLE;
import static android.telephony.TelephonyManager.CAPABILITY_SIM_PHONEBOOK_IN_MODEM;
import static android.telephony.TelephonyManager.CAPABILITY_THERMAL_MITIGATION_DATA_THROTTLING;
import static android.telephony.TelephonyManager.RadioInterfaceCapability;

import android.os.RemoteException;
import android.telephony.ModemInfo;
import android.telephony.PhoneCapability;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.uicc.IccSlotStatus;
import com.android.telephony.Rlog;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * This class is the implementation of IRadioConfigResponse interface.
 */
public class RadioConfigResponse {
    private static final String TAG = "RadioConfigResponse";

    private final RadioConfig mRadioConfig;
    private final HalVersion mHalVersion;

    private android.hardware.radio.config.V1_3.IRadioConfigResponse mRadioConfigResponseV1 = null;
    private android.hardware.radio.config.IRadioConfigResponse mRadioConfigResponseV2 = null;

    public RadioConfigResponse(RadioConfig radioConfig, HalVersion halVersion) {
        mRadioConfig = radioConfig;
        mHalVersion = halVersion;

        if (mHalVersion.greaterOrEqual(RIL.RADIO_HAL_VERSION_2_0)) {
            mRadioConfigResponseV2 = new RadioConfigResponseV2();
        } else {
            mRadioConfigResponseV1 = new RadioConfigResponseV1();
        }
    }

    public android.hardware.radio.config.V1_3.IRadioConfigResponse getV1() {
        return mRadioConfigResponseV1;
    }

    public android.hardware.radio.config.IRadioConfigResponse getV2() {
        return mRadioConfigResponseV2;
    }

    private class RadioConfigResponseV1
            extends android.hardware.radio.config.V1_3.IRadioConfigResponse.Stub {
        /**
         * Response function for IRadioConfig.getSimSlotsStatus().
         */
        @Override
        public void getSimSlotsStatusResponse(
                android.hardware.radio.V1_0.RadioResponseInfo info,
                ArrayList<android.hardware.radio.config.V1_0.SimSlotStatus> slotStatus)
                throws RemoteException {
            RILRequest rr = mRadioConfig.processResponse(info);

            if (rr != null) {
                ArrayList<IccSlotStatus> ret = RadioConfig.convertHalSlotStatus(slotStatus);
                if (info.error == android.hardware.radio.V1_0.RadioError.NONE) {
                    // send response
                    RadioResponse.sendMessageResponse(rr.mResult, ret);
                    logd(rr, RadioConfig.requestToString(rr.mRequest) + " " + ret.toString());
                } else {
                    rr.onError(info.error, ret);
                    loge(rr, RadioConfig.requestToString(rr.mRequest) + " error " + info.error);
                }
            } else {
                loge("getSimSlotsStatusResponse: Error " + info.toString());
            }
        }

        /**
         * Response function for IRadioConfig.setSimSlotsMapping().
         */
        @Override
        public void setSimSlotsMappingResponse(android.hardware.radio.V1_0.RadioResponseInfo info)
                throws RemoteException {
            RILRequest rr = mRadioConfig.processResponse(info);
            if (rr != null) {
                if (info.error == android.hardware.radio.V1_0.RadioError.NONE) {
                    // send response
                    RadioResponse.sendMessageResponse(rr.mResult, null);
                    logd(rr, RadioConfig.requestToString(rr.mRequest));
                } else {
                    rr.onError(info.error, null);
                    loge(rr, RadioConfig.requestToString(rr.mRequest) + " error " + info.error);
                }
            } else {
                loge("setSimSlotsMappingResponse: Error " + info.toString());
            }
        }

        /**
         * Response function for IRadioConfig.getPhoneCapability().
         */
        @Override
        public void getPhoneCapabilityResponse(android.hardware.radio.V1_0.RadioResponseInfo info,
                android.hardware.radio.config.V1_1.PhoneCapability phoneCapability)
                throws RemoteException {
            RILRequest rr = mRadioConfig.processResponse(info);
            if (rr != null) {
                PhoneCapability ret = convertHalPhoneCapability(phoneCapability);
                if (info.error == android.hardware.radio.V1_0.RadioError.NONE) {
                    // send response
                    RadioResponse.sendMessageResponse(rr.mResult, ret);
                    logd(rr, RadioConfig.requestToString(rr.mRequest) + " " + ret.toString());
                } else {
                    rr.onError(info.error, ret);
                    loge(rr, RadioConfig.requestToString(rr.mRequest) + " error " + info.error);
                }
            } else {
                loge("getPhoneCapabilityResponse: Error " + info.toString());
            }
        }

        private PhoneCapability convertHalPhoneCapability(
                android.hardware.radio.config.V1_1.PhoneCapability phoneCapability) {
            // TODO b/121394331: clean up V1_1.PhoneCapability fields.
            int maxActiveVoiceCalls = 0;
            int maxActiveData = phoneCapability.maxActiveData;
            boolean validationBeforeSwitchSupported = phoneCapability.isInternetLingeringSupported;
            List<ModemInfo> logicalModemList = new ArrayList<>();
            for (android.hardware.radio.config.V1_1.ModemInfo
                    modemInfo : phoneCapability.logicalModemList) {
                logicalModemList.add(new ModemInfo(modemInfo.modemId));
            }
            return new PhoneCapability(maxActiveVoiceCalls, maxActiveData, logicalModemList,
                    validationBeforeSwitchSupported, mRadioConfig.getDeviceNrCapabilities());
        }

        /**
         * Response function for IRadioConfig.setPreferredDataModem().
         */
        @Override
        public void setPreferredDataModemResponse(
                android.hardware.radio.V1_0.RadioResponseInfo info) throws RemoteException {
            RILRequest rr = mRadioConfig.processResponse(info);
            if (rr != null) {
                if (info.error == android.hardware.radio.V1_0.RadioError.NONE) {
                    // send response
                    RadioResponse.sendMessageResponse(rr.mResult, null);
                    logd(rr, RadioConfig.requestToString(rr.mRequest));
                } else {
                    rr.onError(info.error, null);
                    loge(rr, RadioConfig.requestToString(rr.mRequest) + " error " + info.error);
                }
            } else {
                loge("setPreferredDataModemResponse: Error " + info.toString());
            }
        }

        /**
         * Response function for IRadioConfig.setModemsConfigResponse()
         * Currently this is being used as the callback for RadioConfig.setModemsConfig() method
         */
        @Override
        public void setModemsConfigResponse(android.hardware.radio.V1_0.RadioResponseInfo info)
                throws RemoteException {
            RILRequest rr = mRadioConfig.processResponse(info);
            if (rr != null) {
                if (info.error == android.hardware.radio.V1_0.RadioError.NONE) {
                    // send response
                    RadioResponse.sendMessageResponse(rr.mResult, rr.mRequest);
                    logd(rr, RadioConfig.requestToString(rr.mRequest));
                } else {
                    rr.onError(info.error, null);
                    loge(rr, RadioConfig.requestToString(rr.mRequest) + " error " + info.error);
                }
            } else {
                loge("setModemsConfigResponse: Error " + info.toString());
            }
        }

        /**
         * Response function for IRadioConfig.getModemsConfigResponse()
         */
        @Override
        public void getModemsConfigResponse(android.hardware.radio.V1_0.RadioResponseInfo info,
                android.hardware.radio.config.V1_1.ModemsConfig modemsConfig)
                throws RemoteException {
            RILRequest rr = mRadioConfig.processResponse(info);
            if (rr != null) {
                if (info.error == android.hardware.radio.V1_0.RadioError.NONE) {
                    // send response
                    RadioResponse.sendMessageResponse(rr.mResult, modemsConfig);
                    logd(rr, RadioConfig.requestToString(rr.mRequest));
                } else {
                    rr.onError(info.error, modemsConfig);
                    loge(rr, RadioConfig.requestToString(rr.mRequest) + " error " + info.error);
                }
            } else {
                loge("getModemsConfigResponse: Error " + info.toString());
            }
        }

        /**
         * Response function for IRadioConfig.getSimSlotsStatus().
         */
        @Override
        public void getSimSlotsStatusResponse_1_2(
                android.hardware.radio.V1_0.RadioResponseInfo info,
                ArrayList<android.hardware.radio.config.V1_2.SimSlotStatus> slotStatus)
                throws RemoteException {
            RILRequest rr = mRadioConfig.processResponse(info);
            if (rr != null) {
                ArrayList<IccSlotStatus> ret = RadioConfig.convertHalSlotStatus_1_2(slotStatus);
                if (info.error == android.hardware.radio.V1_0.RadioError.NONE) {
                    // send response
                    RadioResponse.sendMessageResponse(rr.mResult, ret);
                    logd(rr, RadioConfig.requestToString(rr.mRequest) + " " + ret.toString());
                } else {
                    rr.onError(info.error, ret);
                    loge(rr, RadioConfig.requestToString(rr.mRequest) + " error " + info.error);
                }
            } else {
                loge("getSimSlotsStatusResponse_1_2: Error " + info.toString());
            }
        }

        /**
         * Response function IRadioConfig.getHalDeviceCapabilities()
         */
        @Override
        public void getHalDeviceCapabilitiesResponse(
                android.hardware.radio.V1_6.RadioResponseInfo info, boolean modemReducedFeatureSet1)
                throws RemoteException {
            // convert hal device capabilities to RadioInterfaceCapabilities
            RILRequest rr = mRadioConfig.processResponse_1_6(info);
            if (rr != null) {
                // The response is compatible with Radio 1.6, it means the modem
                // supports setAllowedNetworkTypeBitmap.
                final Set<String> ret = getCaps(mHalVersion, modemReducedFeatureSet1);
                if (info.error == android.hardware.radio.V1_0.RadioError.NONE) {
                    // send response
                    RadioResponse.sendMessageResponse(rr.mResult, ret);
                    logd(rr, RadioConfig.requestToString(rr.mRequest));
                } else {
                    rr.onError(info.error, ret);
                    loge(rr, RadioConfig.requestToString(rr.mRequest) + " error " + info.error);
                }
            } else {
                loge("getHalDeviceCapabilities: Error " + info.toString());
            }
        }
    }

    private class RadioConfigResponseV2
            extends android.hardware.radio.config.IRadioConfigResponse.Stub {
        /**
         * Response function IRadioConfig.getHalDeviceCapabilities()
         */
        @Override
        public void getHalDeviceCapabilitiesResponse(
                android.hardware.radio.RadioResponseInfo info,
                boolean modemReducedFeatureSet1) throws RemoteException {
            // convert hal device capabilities to RadioInterfaceCapabilities
            RILRequest rr = mRadioConfig.processResponse(info);
            if (rr != null) {
                final Set<String> ret = getCaps(mHalVersion, modemReducedFeatureSet1);
                if (info.error == android.hardware.radio.RadioError.NONE) {
                    // send response
                    RadioResponse.sendMessageResponse(rr.mResult, ret);
                    logd(rr, RadioConfig.requestToString(rr.mRequest));
                } else {
                    rr.onError(info.error, ret);
                    loge(rr, RadioConfig.requestToString(rr.mRequest) + " error " + info.error);
                }
            } else {
                loge("getHalDeviceCapabilities: Error " + info.toString());
            }
        }

        /**
         * Response function for IRadioConfig.getNumOfLiveModemsResponse()
         */
        @Override
        public void getNumOfLiveModemsResponse(
                android.hardware.radio.RadioResponseInfo info, byte numOfLiveModems)
                throws RemoteException {
            RILRequest rr = mRadioConfig.processResponse(info);
            if (rr != null) {
                if (info.error == android.hardware.radio.RadioError.NONE) {
                    // send response
                    RadioResponse.sendMessageResponse(rr.mResult, numOfLiveModems);
                    logd(rr, RadioConfig.requestToString(rr.mRequest));
                } else {
                    rr.onError(info.error, numOfLiveModems);
                    loge(rr, RadioConfig.requestToString(rr.mRequest) + " error " + info.error);
                }
            } else {
                loge("getNumOfLiveModemsResponse: Error " + info.toString());
            }
        }

        /**
         * Response function for IRadioConfig.getPhoneCapability().
         */
        @Override
        public void getPhoneCapabilityResponse(
                android.hardware.radio.RadioResponseInfo info,
                android.hardware.radio.config.PhoneCapability phoneCapability)
                throws RemoteException {
            RILRequest rr = mRadioConfig.processResponse(info);
            if (rr != null) {
                PhoneCapability ret = convertHalPhoneCapability(phoneCapability);
                if (info.error == android.hardware.radio.RadioError.NONE) {
                    // send response
                    RadioResponse.sendMessageResponse(rr.mResult, ret);
                    logd(rr, RadioConfig.requestToString(rr.mRequest) + " " + ret.toString());
                } else {
                    rr.onError(info.error, ret);
                    loge(rr, RadioConfig.requestToString(rr.mRequest) + " error " + info.error);
                }
            } else {
                loge("getPhoneCapabilityResponse: Error " + info.toString());
            }
        }

        private PhoneCapability convertHalPhoneCapability(
                android.hardware.radio.config.PhoneCapability phoneCapability) {
            // TODO b/121394331: clean up PhoneCapability fields.
            int maxActiveVoiceCalls = 0;
            int maxActiveData = phoneCapability.maxActiveData;
            boolean validationBeforeSwitchSupported = phoneCapability.isInternetLingeringSupported;
            List<ModemInfo> logicalModemList = new ArrayList<>();
            for (int modemId : phoneCapability.logicalModemIds) {
                logicalModemList.add(new ModemInfo(modemId));
            }
            return new PhoneCapability(maxActiveVoiceCalls, maxActiveData, logicalModemList,
                    validationBeforeSwitchSupported, mRadioConfig.getDeviceNrCapabilities());
        }

        /**
         * Response function for IRadioConfig.getSimSlotsStatus().
         */
        @Override
        public void getSimSlotsStatusResponse(
                android.hardware.radio.RadioResponseInfo info,
                android.hardware.radio.config.SimSlotStatus[] slotStatus)
                throws RemoteException {
            RILRequest rr = mRadioConfig.processResponse(info);
            if (rr != null) {
                ArrayList<IccSlotStatus> ret = RadioConfig.convertHalSlotStatus(slotStatus);
                if (info.error == android.hardware.radio.RadioError.NONE) {
                    // send response
                    RadioResponse.sendMessageResponse(rr.mResult, ret);
                    logd(rr, RadioConfig.requestToString(rr.mRequest) + " " + ret.toString());
                } else {
                    rr.onError(info.error, ret);
                    loge(rr, RadioConfig.requestToString(rr.mRequest) + " error " + info.error);
                }
            } else {
                loge("getSimSlotsStatusResponse: Error " + info.toString());
            }
        }

        /**
         * Response function for IRadioConfig.setNumOfLiveModemsResponse()
         * Currently this is being used as the callback for RadioConfig.setNumOfLiveModems() method
         */
        @Override
        public void setNumOfLiveModemsResponse(
                android.hardware.radio.RadioResponseInfo info) throws RemoteException {
            RILRequest rr = mRadioConfig.processResponse(info);
            if (rr != null) {
                if (info.error == android.hardware.radio.RadioError.NONE) {
                    // send response
                    RadioResponse.sendMessageResponse(rr.mResult, rr.mRequest);
                    logd(rr, RadioConfig.requestToString(rr.mRequest));
                } else {
                    rr.onError(info.error, null);
                    loge(rr, RadioConfig.requestToString(rr.mRequest) + " error " + info.error);
                }
            } else {
                loge("setModemsConfigResponse: Error " + info.toString());
            }
        }

        /**
         * Response function for IRadioConfig.setPreferredDataModem().
         */
        @Override
        public void setPreferredDataModemResponse(
                android.hardware.radio.RadioResponseInfo info) throws RemoteException {
            RILRequest rr = mRadioConfig.processResponse(info);
            if (rr != null) {
                if (info.error == android.hardware.radio.RadioError.NONE) {
                    // send response
                    RadioResponse.sendMessageResponse(rr.mResult, null);
                    logd(rr, RadioConfig.requestToString(rr.mRequest));
                } else {
                    rr.onError(info.error, null);
                    loge(rr, RadioConfig.requestToString(rr.mRequest) + " error " + info.error);
                }
            } else {
                loge("setPreferredDataModemResponse: Error " + info.toString());
            }
        }

        /**
         * Response function for IRadioConfig.setSimSlotsMapping().
         */
        @Override
        public void setSimSlotsMappingResponse(
                android.hardware.radio.RadioResponseInfo info) throws RemoteException {
            RILRequest rr = mRadioConfig.processResponse(info);
            if (rr != null) {
                if (info.error == android.hardware.radio.RadioError.NONE) {
                    // send response
                    RadioResponse.sendMessageResponse(rr.mResult, null);
                    logd(rr, RadioConfig.requestToString(rr.mRequest));
                } else {
                    rr.onError(info.error, null);
                    loge(rr, RadioConfig.requestToString(rr.mRequest) + " error " + info.error);
                }
            } else {
                loge("setSimSlotsMappingResponse: Error " + info.toString());
            }
        }
    }

    /**
     * Returns all capabilities supported in the most recent radio hal version.
     * <p/>
     * Used in the {@link RILConstants.REQUEST_NOT_SUPPORTED} case.
     *
     * @return all capabilities
     */
    @RadioInterfaceCapability
    public Set<String> getFullCapabilitySet() {
        return getCaps(mHalVersion, false);
    }

    /**
     * Create capabilities based off of the radio hal version and feature set configurations.
     */
    @VisibleForTesting
    public static Set<String> getCaps(HalVersion radioHalVersion, boolean modemReducedFeatureSet1) {
        final Set<String> caps = new HashSet<>();

        if (radioHalVersion.equals(RIL.RADIO_HAL_VERSION_UNKNOWN)) {
            // If the Radio HAL is UNKNOWN, no capabilities will present themselves.
            loge("Radio Hal Version is UNKNOWN!");
        }

        logd("Radio Hal Version = " + radioHalVersion.toString());
        if (radioHalVersion.greaterOrEqual(RIL.RADIO_HAL_VERSION_1_6)) {
            caps.add(CAPABILITY_ALLOWED_NETWORK_TYPES_USED);
            logd("CAPABILITY_ALLOWED_NETWORK_TYPES_USED");

            if (!modemReducedFeatureSet1) {
                caps.add(CAPABILITY_SECONDARY_LINK_BANDWIDTH_VISIBLE);
                logd("CAPABILITY_SECONDARY_LINK_BANDWIDTH_VISIBLE");
                caps.add(CAPABILITY_NR_DUAL_CONNECTIVITY_CONFIGURATION_AVAILABLE);
                logd("CAPABILITY_NR_DUAL_CONNECTIVITY_CONFIGURATION_AVAILABLE");
                caps.add(CAPABILITY_THERMAL_MITIGATION_DATA_THROTTLING);
                logd("CAPABILITY_THERMAL_MITIGATION_DATA_THROTTLING");
                caps.add(CAPABILITY_SIM_PHONEBOOK_IN_MODEM);
                logd("CAPABILITY_SIM_PHONEBOOK_IN_MODEM");
                caps.add(CAPABILITY_PHYSICAL_CHANNEL_CONFIG_1_6_SUPPORTED);
                logd("CAPABILITY_PHYSICAL_CHANNEL_CONFIG_1_6_SUPPORTED");
            }
        }
        return caps;
    }

    private static void logd(String log) {
        Rlog.d(TAG, log);
    }

    private static void loge(String log) {
        Rlog.e(TAG, log);
    }

    private static void logd(RILRequest rr, String log) {
        logd(rr.serialString() + "< " + log);
    }

    private static void loge(RILRequest rr, String log) {
        loge(rr.serialString() + "< " + log);
    }
}
