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

import static android.telephony.PhoneCapability.DEVICE_NR_CAPABILITY_NSA;
import static android.telephony.PhoneCapability.DEVICE_NR_CAPABILITY_SA;

import static com.android.internal.telephony.RILConstants.RADIO_NOT_AVAILABLE;
import static com.android.internal.telephony.RILConstants.REQUEST_NOT_SUPPORTED;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_GET_HAL_DEVICE_CAPABILITIES;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_GET_PHONE_CAPABILITY;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_GET_SLOT_STATUS;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SET_LOGICAL_TO_PHYSICAL_SLOT_MAPPING;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SET_PREFERRED_DATA_MODEM;
import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SWITCH_DUAL_SIM_CONFIG;

import android.content.Context;
import android.hardware.radio.RadioResponseInfo;
import android.hardware.radio.RadioResponseType;
import android.hardware.radio.config.IRadioConfig;
import android.hardware.radio.config.SimSlotStatus;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Registrant;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.WorkSource;
import android.telephony.TelephonyManager;
import android.util.SparseArray;

import com.android.internal.telephony.uicc.IccSlotStatus;
import com.android.telephony.Rlog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class provides wrapper APIs for IRadioConfig interface.
 */
public class RadioConfig extends Handler {
    private static final String TAG = "RadioConfig";
    private static final boolean DBG = true;
    private static final boolean VDBG = false;   //STOPSHIP if true

    private static final int EVENT_SERVICE_DEAD = 1;

    private final boolean mIsMobileNetworkSupported;
    private volatile IRadioConfig mRadioConfigProxy = null;
    // IRadioConfig version
    private final RadioConfigDeathRecipient mRadioConfigDeathRecipient;
    private final AtomicInteger mRadioConfigProxyCookie = new AtomicInteger(0);
    private final RadioConfigResponse mRadioConfigResponse;
    private final RadioConfigIndication mRadioConfigIndication;
    private final SparseArray<RILRequest> mRequestList = new SparseArray<RILRequest>();
    /* default work source which will blame phone process */
    private final WorkSource mDefaultWorkSource;
    private final int[] mDeviceNrCapabilities;
    private static RadioConfig sRadioConfig;
    private static final Object sLock = new Object();

    protected Registrant mSimSlotStatusRegistrant;

    private final class RadioConfigDeathRecipient implements IBinder.DeathRecipient {
        private IBinder mBinder;

        public void linkToDeath(IBinder service) throws RemoteException {
            if (service != null) {
                mBinder = service;
                mBinder.linkToDeath(this, mRadioConfigProxyCookie.incrementAndGet());
            }
        }

        public synchronized void unlinkToDeath() {
            if (mBinder != null) {
                mBinder.unlinkToDeath(this, 0);
                mBinder = null;
            }
        }

        @Override
        public void binderDied() {
            logd("RadioConfigService has died.");
            unlinkToDeath();
        }

    }

    private boolean isMobileDataCapable(Context context) {
        final TelephonyManager tm = context.getSystemService(TelephonyManager.class);
        if (tm == null) {
            return false;
        }
        return tm.isDataCapable();
    }

    private RadioConfig(Context context, HalVersion radioHalVersion) {
        mIsMobileNetworkSupported = isMobileDataCapable(context);

        mRadioConfigResponse = new RadioConfigResponse(this, radioHalVersion);
        mRadioConfigIndication = new RadioConfigIndication(this);
        mRadioConfigDeathRecipient = new RadioConfigDeathRecipient();

        mDefaultWorkSource = new WorkSource(context.getApplicationInfo().uid,
                context.getPackageName());

        boolean is5gStandalone = context.getResources().getBoolean(
                com.android.internal.R.bool.config_telephony5gStandalone);
        boolean is5gNonStandalone = context.getResources().getBoolean(
                com.android.internal.R.bool.config_telephony5gNonStandalone);

        if (!is5gStandalone && !is5gNonStandalone) {
            mDeviceNrCapabilities = new int[0];
        } else {
            List<Integer> list = new ArrayList<>();
            if (is5gNonStandalone) {
                list.add(DEVICE_NR_CAPABILITY_NSA);
            }
            if (is5gStandalone) {
                list.add(DEVICE_NR_CAPABILITY_SA);
            }
            mDeviceNrCapabilities = list.stream().mapToInt(Integer::valueOf).toArray();
        }
    }

    /**
     * Returns the singleton static instance of RadioConfig
     */
    public static RadioConfig getInstance() {
        synchronized (sLock) {
            if (sRadioConfig == null) {
                throw new RuntimeException(
                        "RadioConfig.getInstance can't be called before make()");
            }
            return sRadioConfig;
        }
    }

    /**
     * Makes the radio config based on the context and the radio hal version passed in
     */
    public static RadioConfig make(Context c, HalVersion radioHalVersion) {
        synchronized (sLock) {
            if (sRadioConfig != null) {
                throw new RuntimeException("RadioConfig.make() should only be called once");
            }
            sRadioConfig = new RadioConfig(c, radioHalVersion);
            return sRadioConfig;
        }
    }

    @Override
    public void handleMessage(Message message) {
        switch (message.what) {
            case EVENT_SERVICE_DEAD:
                logd("handleMessage: EVENT_SERVICE_DEAD cookie = " + message.obj
                        + " mRadioConfigProxyCookie = " + mRadioConfigProxyCookie.get());
                if ((long) message.obj == mRadioConfigProxyCookie.get()) {
                    resetProxyAndRequestList("EVENT_SERVICE_DEAD", null);
                }
                break;
        }
    }

    /**
     * Release each request in mRequestList then clear the list
     * @param error is the RIL_Errno sent back
     * @param loggable true means to print all requests in mRequestList
     */
    private void clearRequestList(int error, boolean loggable) {
        RILRequest rr;
        synchronized (mRequestList) {
            int count = mRequestList.size();
            if (DBG && loggable) {
                logd("clearRequestList: mRequestList=" + count);
            }

            for (int i = 0; i < count; i++) {
                rr = mRequestList.valueAt(i);
                if (DBG && loggable) {
                    logd(i + ": [" + rr.mSerial + "] " + requestToString(rr.mRequest));
                }
                rr.onError(error, null);
                rr.release();
            }
            mRequestList.clear();
        }
    }

    private void resetProxyAndRequestList(String caller, Exception e) {
        loge(caller + ": " + e);
        mRadioConfigProxy = null;

        // increment the cookie so that death notification can be ignored
        mRadioConfigProxyCookie.incrementAndGet();

        RILRequest.resetSerial();
        // Clear request list on close
        clearRequestList(RADIO_NOT_AVAILABLE, false);

        getRadioConfigProxy(null);
    }

    /** Returns a {@link IRadioConfig} instance or null if the service is not available. */
    public IRadioConfig getRadioConfigProxy(Message result) {
        if (!mIsMobileNetworkSupported) {
            if (VDBG) logd("getRadioConfigProxy: Not calling getService(): wifi-only");
            if (result != null) {
                AsyncResult.forMessage(result, null,
                        CommandException.fromRilErrno(RADIO_NOT_AVAILABLE));
                result.sendToTarget();
            }
            return null;
        }

        if (mRadioConfigProxy != null) {
            return mRadioConfigProxy;
        }

        updateRadioConfigProxy();

        if (mRadioConfigProxy == null) {
            if (result != null) {
                AsyncResult.forMessage(result, null,
                        CommandException.fromRilErrno(RADIO_NOT_AVAILABLE));
                result.sendToTarget();
            }
        }

        return mRadioConfigProxy;
    }

    private void updateRadioConfigProxy() {
        try {
            // Try to get service binder
            IBinder service = ServiceManager.waitForDeclaredService(
                    IRadioConfig.DESCRIPTOR + "/default");
            if (service == null) {
                loge("getRadioConfigProxy: mRadioConfigProxy == null");
                return;
            }

            // Convert to proxy
            mRadioConfigProxy = IRadioConfig.Stub.asInterface(service);

            // Link to death
            mRadioConfigDeathRecipient.linkToDeath(service);

            // Set response. If fails, set proxy to null and return.
            mRadioConfigProxy.setResponseFunctions(mRadioConfigResponse, mRadioConfigIndication);
        } catch (NoSuchElementException | RemoteException e) {
            mRadioConfigProxy = null;
            loge("getRadioConfigProxy: RadioConfigProxy setResponseFunctions: " + e);
        }
    }

    private RILRequest obtainRequest(int request, Message result, WorkSource workSource) {
        RILRequest rr = RILRequest.obtain(request, result, workSource);
        synchronized (mRequestList) {
            mRequestList.append(rr.mSerial, rr);
        }
        return rr;
    }

    private RILRequest findAndRemoveRequestFromList(int serial) {
        RILRequest rr;
        synchronized (mRequestList) {
            rr = mRequestList.get(serial);
            if (rr != null) {
                mRequestList.remove(serial);
            }
        }

        return rr;
    }

    /**
     * This is a helper function to be called when a RadioConfigResponse callback is called.
     * It finds and returns RILRequest corresponding to the response if one is found.
     * @param responseInfo RadioResponseInfo received in response callback
     * @return RILRequest corresponding to the response
     */
    public RILRequest processResponse(RadioResponseInfo responseInfo) {
        int serial = responseInfo.serial;
        int error = responseInfo.error;
        int type = responseInfo.type;

        if (type != RadioResponseType.SOLICITED) {
            loge("processResponse: Unexpected response type " + type);
        }

        RILRequest rr = findAndRemoveRequestFromList(serial);
        if (rr == null) {
            loge("processResponse: Unexpected response! serial: " + serial + " error: " + error);
            return null;
        }

        return rr;
    }

    /**
     * Wrapper function for IRadioConfig.getSimSlotsStatus().
     */
    public void getSimSlotsStatus(Message result) {
        IRadioConfig radioConfigProxy = getRadioConfigProxy(result);
        if (radioConfigProxy != null) {
            RILRequest rr = obtainRequest(RIL_REQUEST_GET_SLOT_STATUS, result, mDefaultWorkSource);

            if (DBG) {
                logd(rr.serialString() + "> " + requestToString(rr.mRequest));
            }

            try {
                radioConfigProxy.getSimSlotsStatus(rr.mSerial);
            } catch (RemoteException | RuntimeException e) {
                resetProxyAndRequestList("getSimSlotsStatus", e);
            }
        }
    }

    /**
     * Wrapper function for IRadioConfig.setPreferredDataModem(int modemId).
     */
    public void setPreferredDataModem(int modemId, Message result) {
        if (!isSetPreferredDataCommandSupported()) {
            if (result != null) {
                AsyncResult.forMessage(result, null,
                        CommandException.fromRilErrno(REQUEST_NOT_SUPPORTED));
                result.sendToTarget();
            }
            return;
        }

        RILRequest rr = obtainRequest(RIL_REQUEST_SET_PREFERRED_DATA_MODEM,
                result, mDefaultWorkSource);

        if (DBG) {
            logd(rr.serialString() + "> " + requestToString(rr.mRequest));
        }

        try {
            mRadioConfigProxy.setPreferredDataModem(rr.mSerial, (byte) modemId);
        } catch (RemoteException | RuntimeException e) {
            resetProxyAndRequestList("setPreferredDataModem", e);
        }
    }

    /**
     * Wrapper function for IRadioConfig.getPhoneCapability().
     */
    public void getPhoneCapability(Message result) {
        if (getRadioConfigProxy(null) == null) {
            if (result != null) {
                AsyncResult.forMessage(result, null,
                        CommandException.fromRilErrno(REQUEST_NOT_SUPPORTED));
                result.sendToTarget();
            }
            return;
        }

        RILRequest rr = obtainRequest(RIL_REQUEST_GET_PHONE_CAPABILITY, result, mDefaultWorkSource);

        if (DBG) {
            logd(rr.serialString() + "> " + requestToString(rr.mRequest));
        }

        try {
            mRadioConfigProxy.getPhoneCapability(rr.mSerial);
        } catch (RemoteException | RuntimeException e) {
            resetProxyAndRequestList("getPhoneCapability", e);
        }
    }

    /**
     * @return whether current radio config version supports SET_PREFERRED_DATA_MODEM command.
     * If yes, we'll use RIL_REQUEST_SET_PREFERRED_DATA_MODEM to indicate which modem is preferred.
     * If not, we shall use RIL_REQUEST_ALLOW_DATA for on-demand PS attach / detach.
     * See PhoneSwitcher for more details.
     */
    public boolean isSetPreferredDataCommandSupported() {
        return getRadioConfigProxy(null) != null;
    }

    /**
     * Wrapper function for IRadioConfig.setSimSlotsMapping(int32_t serial, vec<uint32_t> slotMap).
     */
    public void setSimSlotsMapping(int[] physicalSlots, Message result) {
        IRadioConfig radioConfigProxy = getRadioConfigProxy(result);
        if (radioConfigProxy != null) {
            RILRequest rr = obtainRequest(
                    RIL_REQUEST_SET_LOGICAL_TO_PHYSICAL_SLOT_MAPPING, result, mDefaultWorkSource);

            if (DBG) {
                logd(rr.serialString() + "> " + requestToString(rr.mRequest)
                        + " " + Arrays.toString(physicalSlots));
            }

            try {
                radioConfigProxy.setSimSlotsMapping(rr.mSerial, physicalSlots);
            } catch (RemoteException | RuntimeException e) {
                resetProxyAndRequestList("setSimSlotsMapping", e);
            }
        }
    }

    static String requestToString(int request) {
        switch (request) {
            case RIL_REQUEST_GET_PHONE_CAPABILITY:
                return "GET_PHONE_CAPABILITY";
            case RIL_REQUEST_GET_SLOT_STATUS:
                return "GET_SLOT_STATUS";
            case RIL_REQUEST_SET_LOGICAL_TO_PHYSICAL_SLOT_MAPPING:
                return "SET_LOGICAL_TO_PHYSICAL_SLOT_MAPPING";
            case RIL_REQUEST_SET_PREFERRED_DATA_MODEM:
                return "SET_PREFERRED_DATA_MODEM";
            case RIL_REQUEST_SWITCH_DUAL_SIM_CONFIG:
                return "SWITCH_DUAL_SIM_CONFIG";
            case RIL_REQUEST_GET_HAL_DEVICE_CAPABILITIES:
                return "GET_HAL_DEVICE_CAPABILITIES";
            default:
                return "<unknown request " + request + ">";
        }
    }

    /**
     * Wrapper function for using IRadioConfig.setNumOfLiveModems(int32_t serial,
     * byte modemsConfig) to switch between single-sim and multi-sim.
     */
    public void setModemsConfig(int numOfLiveModems, Message result) {
        IRadioConfig radioConfigProxy = getRadioConfigProxy(result);
        if (radioConfigProxy != null) {
            RILRequest rr = obtainRequest(
                    RIL_REQUEST_SWITCH_DUAL_SIM_CONFIG, result, mDefaultWorkSource);

            if (DBG) {
                logd(rr.serialString() + "> " + requestToString(rr.mRequest)
                        + ", numOfLiveModems = " + numOfLiveModems);
            }

            try {
                radioConfigProxy.setNumOfLiveModems(rr.mSerial, (byte) numOfLiveModems);
            } catch (RemoteException | RuntimeException e) {
                resetProxyAndRequestList("setNumOfLiveModems", e);
            }
        }
    }

    // TODO: not needed for now, but if we don't want to use System Properties any more,
    // we need to implement a wrapper function for getModemsConfig as well

    /**
     * Register a handler to get SIM slot status changed notifications.
     */
    public void registerForSimSlotStatusChanged(Handler h, int what, Object obj) {
        mSimSlotStatusRegistrant = new Registrant(h, what, obj);
    }

    /**
     * Unregister corresponding to registerForSimSlotStatusChanged().
     */
    public void unregisterForSimSlotStatusChanged(Handler h) {
        if (mSimSlotStatusRegistrant != null && mSimSlotStatusRegistrant.getHandler() == h) {
            mSimSlotStatusRegistrant.clear();
            mSimSlotStatusRegistrant = null;
        }
    }

    /**
     * Gets the hal capabilities from the device.
     */
    public void getHalDeviceCapabilities(Message result) {
        IRadioConfig radioConfigProxy = getRadioConfigProxy(Message.obtain(result));
        if (radioConfigProxy != null) {
            RILRequest rr = obtainRequest(
                    RIL_REQUEST_GET_HAL_DEVICE_CAPABILITIES, result, mDefaultWorkSource);

            if (DBG) {
                logd(rr.serialString() + "> " + requestToString(rr.mRequest));
            }

            try {
                radioConfigProxy.getHalDeviceCapabilities(rr.mSerial);
            } catch (RemoteException | RuntimeException e) {
                resetProxyAndRequestList("getHalDeviceCapabilities", e);
            }
        } else {
            if (result != null) {
                if (DBG) {
                    logd("RIL_REQUEST_GET_HAL_DEVICE_CAPABILITIES > REQUEST_NOT_SUPPORTED");
                }
                AsyncResult.forMessage(result,
                        /* Send response such that all capabilities are supported (depending on
                           the hal version of course.) */
                        mRadioConfigResponse.getFullCapabilitySet(),
                        CommandException.fromRilErrno(REQUEST_NOT_SUPPORTED));
                result.sendToTarget();
            } else {
                if (DBG) {
                    logd("RIL_REQUEST_GET_HAL_DEVICE_CAPABILITIES > REQUEST_NOT_SUPPORTED "
                            + "on complete message not set.");
                }
            }
        }
    }

    /**
     * Returns the device's nr capability.
     */
    public int[] getDeviceNrCapabilities() {
        return mDeviceNrCapabilities;
    }

    static ArrayList<IccSlotStatus> convertHalSlotStatus(SimSlotStatus[] halSlotStatusList) {
        ArrayList<IccSlotStatus> response = new ArrayList<>(halSlotStatusList.length);
        for (SimSlotStatus slotStatus : halSlotStatusList) {
            IccSlotStatus iccSlotStatus = new IccSlotStatus();
            iccSlotStatus.setCardState(slotStatus.cardState);
            iccSlotStatus.setSlotState(slotStatus.slotState);
            iccSlotStatus.logicalSlotIndex = slotStatus.logicalSlotId;
            iccSlotStatus.atr = slotStatus.atr;
            iccSlotStatus.iccid = slotStatus.iccid;
            iccSlotStatus.eid = slotStatus.eid;
            response.add(iccSlotStatus);
        }
        return response;
    }

    private static void logd(String log) {
        Rlog.d(TAG, log);
    }

    private static void loge(String log) {
        Rlog.e(TAG, log);
    }
}
