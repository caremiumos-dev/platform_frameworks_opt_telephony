/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.internal.telephony.cdnr;

import android.annotation.NonNull;
import android.content.Context;
import android.content.res.Resources;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.ServiceState;
import android.text.TextUtils;
import android.util.LocalLog;

import com.android.internal.telephony.GsmCdmaPhone;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppState;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.IccRecords.OperatorPlmnInfo;
import com.android.internal.telephony.uicc.IccRecords.PlmnNetworkName;
import com.android.internal.telephony.uicc.SIMRecords;
import com.android.internal.telephony.util.ArrayUtils;
import com.android.internal.util.IndentingPrintWriter;
import com.android.telephony.Rlog;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Carrier display name resolver. */
public class CarrierDisplayNameResolver {
    private static final boolean DBG = true;
    private static final String TAG = "CDNR";

    private final LocalLog mLocalLog;
    private final Context mContext;
    private final GsmCdmaPhone mPhone;
    private final CarrierConfigManager mCCManager;

    private CarrierDisplayNameData mCarrierDisplayNameData;

    private SimEfData mSimEfData;
    private SIMRecords mSim;
    private PersistableBundle mConfig;
    private String mBrandOverrideName;


    public CarrierDisplayNameResolver(GsmCdmaPhone phone) {
        mLocalLog = new LocalLog(32);
        mContext = phone.getContext();
        mPhone = phone;
        mCCManager = (CarrierConfigManager) mContext.getSystemService(
                Context.CARRIER_CONFIG_SERVICE);
        mConfig = getCarrierConfig();
    }

   /**
     * Update the ef from Sim.
     *
     * @param sim SIMRecords.
     */
    public void updateEfFromSim(SIMRecords sim) {
        mSim = sim;
        if (mSim != null && mConfig != null) {
            mSimEfData = new SimEfData(mSim, mConfig);
        } else {
            mSimEfData = null;
        }
    }

    /**
     * Update the ef from carrier config.
     *
     * @param config carrier config.
     */
    public void updateEfFromCarrierConfig(PersistableBundle config) {
        mConfig = config;
        if (mSim != null && mConfig != null) {
            mSimEfData = new SimEfData(mSim, mConfig);
        } else {
            mSimEfData = null;
        }
    }

    /**
     * Update brandOverride name string.
     *
     * @param brandOverrideName operator name from brand override.
     */
    public void updateBrandOverride(String brandOverrideName) {
        if (brandOverrideName == null || TextUtils.isEmpty(brandOverrideName)) {
            mBrandOverrideName = null;
        } else {
            mBrandOverrideName = brandOverrideName;
        }
    }

    private CarrierDisplayNameData getCarrierDisplayNameFromCellular() {
        String registeredPlmnNumeric = getServiceState().getOperatorNumeric();
        String spnStr = null;
        String dataSpnStr = null;
        boolean showSpn = false;
        String plmnStr = null;
        boolean showPlmn = false;

        // Carrier display name from brandoverride
        if (mBrandOverrideName != null) {
            return new CarrierDisplayNameData(
                    mBrandOverrideName, mBrandOverrideName, true, null, false);
        }

        // Carrier display name from SIM Resolution
        CarrierDisplayNameData curCarrierDisplayName = getCarrierDisplayNamefromSim();
        if (curCarrierDisplayName.shouldShowSpn() || curCarrierDisplayName.shouldShowPlmn()) {
            return curCarrierDisplayName;
        }

        // Carrier display name from modem using Signaling Resolution.
        plmnStr = getServiceState().getOperatorAlpha();
        if (plmnStr != registeredPlmnNumeric) {
            return new CarrierDisplayNameData(null, null, false, plmnStr, true);
        }

        // TODO: Search carrier_list.textpb for the carrier name corresponding to the numeric PLMN

        // Directly use numeric PLMN
        return new CarrierDisplayNameData(null, null, false, registeredPlmnNumeric, true);
    }

    /**
    * Carrier display name resolution based on 3GPP TS 31.102
    */
    private CarrierDisplayNameData getCarrierDisplayNamefromSim() {
        String spnStr = null;
        String dataSpnStr = null;
        boolean showSpn = false;
        String plmnStr = null;
        boolean showPlmn = false;

        if (mSimEfData == null) {
            return new CarrierDisplayNameData(spnStr, dataSpnStr, showSpn, plmnStr, showPlmn);
        }

        String spn = mSimEfData.getServiceProviderName();
        int displayConditionBitmask = mSimEfData.getServiceProviderNameDisplayCondition();
        if (!isRoamingForCarrierDisplayName()) {
            //home network
            List<PlmnNetworkName> efPnn = mSimEfData.getPlmnNetworkNameList();
            List<OperatorPlmnInfo> efOpl = mSimEfData.getOperatorPlmnList();

            String plmn = null;
            if (efOpl.isEmpty()) {
                // If the EF_OPL is not present, then the first record in EF_PNN is used for the
                // default network name when registered in the HPLMN or an EHPLMN(if the EHPLMN list
                // is present).
                spnStr = efPnn.isEmpty() ? null : getPlmnNetworkName(efPnn.get(0));
                if (spnStr != null) {
                    dataSpnStr = spnStr;
                    showSpn = true;
                    return new CarrierDisplayNameData(spnStr, dataSpnStr, showSpn,
                            plmnStr, showPlmn);
                }
            }

            if (spn != null && !TextUtils.isEmpty(spn)) {
                spnStr = spn;
                dataSpnStr = spn;
                showSpn = true;
            }

            boolean shouldShowPlmn = (displayConditionBitmask
                    != IccRecords.INVALID_CARRIER_NAME_DISPLAY_CONDITION_BITMASK)
                    && ((displayConditionBitmask
                        & IccRecords.CARRIER_NAME_DISPLAY_CONDITION_BITMASK_PLMN) == 1);

            if (!showSpn || shouldShowPlmn) {
                CarrierDisplayNameData simRoamingData = simResolutionInRoaming();
                plmnStr = simRoamingData.getPlmn();
                showPlmn = simRoamingData.shouldShowPlmn();
            }
            return new CarrierDisplayNameData(spnStr, dataSpnStr, showSpn, plmnStr, showPlmn);
        } else {
            //roaming network
            CarrierDisplayNameData simRoamingData = simResolutionInRoaming();
            if (!simRoamingData.shouldShowPlmn()) {
                return new CarrierDisplayNameData(spnStr, dataSpnStr, showSpn, plmnStr, showPlmn);
            }
            plmnStr = simRoamingData.getPlmn();
            showPlmn = simRoamingData.shouldShowPlmn();

            boolean shouldShowSpn = (displayConditionBitmask
                    != IccRecords.INVALID_CARRIER_NAME_DISPLAY_CONDITION_BITMASK)
                    && ((displayConditionBitmask
                        & IccRecords.CARRIER_NAME_DISPLAY_CONDITION_BITMASK_SPN) == 0);

            if (shouldShowSpn && spn != null) {
                spnStr = spn;
                dataSpnStr = spn;
                showSpn = true;
            }
            return new CarrierDisplayNameData(spnStr, dataSpnStr, showSpn, plmnStr, showPlmn);
        }
    }

    /**
    * Get roaming state for carrier display name.
    * The logic is the same as {@code ServiceStateTracker#getCarrierNameDisplayBitmask}, while
    * carrier config override for EHPLMN and SPDI is also used here.
    */
    private boolean isRoamingForCarrierDisplayName() {
        boolean isRoaming = true;

        boolean useRoamingFromServiceState;
        if (mConfig != null) {
            useRoamingFromServiceState = mConfig.getBoolean(
                CarrierConfigManager.KEY_SPN_DISPLAY_RULE_USE_ROAMING_FROM_SERVICE_STATE_BOOL);
            if (useRoamingFromServiceState) return getServiceState().getRoaming();
        }
        if (mSimEfData != null) {
            String[] hplmns = mSimEfData.getHomePlmns();
            isRoaming = !ArrayUtils.contains(hplmns, getServiceState().getOperatorNumeric());
        }
        return isRoaming;
    }

    private CarrierDisplayNameData simResolutionInRoaming() {
        String spnStr = null;
        String dataSpnStr = null;
        boolean showSpn = false;
        String plmnStr = null;
        boolean showPlmn = false;

        // TODO: Check the TAC/LAC & registered PLMN numeric in OPL list to determine which
        // PLMN name should be used to override the current one.

        return new CarrierDisplayNameData(spnStr, dataSpnStr, showSpn, plmnStr, showPlmn);
    }

    /** Get the resolved carrier display name. */
    public CarrierDisplayNameData getCarrierDisplayNameData() {
        resolveCarrierDisplayName();
        return mCarrierDisplayNameData;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        final SimEfData p = mSimEfData;
        sb.append("{spnDisplayCondition = " + p.getServiceProviderNameDisplayCondition()
                + ", spn = " + p.getServiceProviderName()
                + ", spdiList = " + p.getServiceProviderDisplayInformation()
                + ", pnnList = " + p.getPlmnNetworkNameList()
                + ", oplList = " + p.getOperatorPlmnList()
                + ", ehplmn = " + p.getEhplmnList()
                + "}, ");
        sb.append(", roamingFromSS = " + getServiceState().getRoaming());
        sb.append(", registeredPLMN = " + getServiceState().getOperatorNumeric());
        return sb.toString();
    }

    /**
     * Dumps information for carrier display name resolver.
     * @param pw information printer.
     */
    public void dump(IndentingPrintWriter pw) {
        pw.println("CDNR:");
        pw.increaseIndent();
        pw.println("fields = " + toString());
        pw.println("carrierDisplayNameData = " + mCarrierDisplayNameData);
        pw.decreaseIndent();

        pw.println("CDNR local log:");
        pw.increaseIndent();
        mLocalLog.dump(pw);
        pw.decreaseIndent();
    }

    @NonNull
    private PersistableBundle getCarrierConfig() {
        PersistableBundle config = mCCManager.getConfigForSubId(mPhone.getSubId());
        if (config == null) config = CarrierConfigManager.getDefaultConfig();
        return config;
    }

    private CarrierDisplayNameData getCarrierDisplayNameFromWifiCallingOverride(
            CarrierDisplayNameData rawCarrierDisplayNameData) {
        PersistableBundle config = getCarrierConfig();
        boolean useRootLocale = config.getBoolean(CarrierConfigManager.KEY_WFC_SPN_USE_ROOT_LOCALE);
        Resources r = mContext.getResources();
        if (useRootLocale) r.getConfiguration().setLocale(Locale.ROOT);
        String[] wfcSpnFormats = r.getStringArray(com.android.internal.R.array.wfcSpnFormats);
        WfcCarrierNameFormatter wfcFormatter = new WfcCarrierNameFormatter(config, wfcSpnFormats,
                getServiceState().getState() == ServiceState.STATE_POWER_OFF);

        // Override the spn, data spn, plmn by wifi-calling
        String wfcSpn = wfcFormatter.formatVoiceName(rawCarrierDisplayNameData.getSpn());
        String wfcDataSpn = wfcFormatter.formatDataName(rawCarrierDisplayNameData.getSpn());
        String wfcPlmn = wfcFormatter.formatVoiceName(rawCarrierDisplayNameData.getPlmn());
        CarrierDisplayNameData result = rawCarrierDisplayNameData;
        if (!TextUtils.isEmpty(wfcSpn) && !TextUtils.isEmpty(wfcDataSpn)) {
            result = new CarrierDisplayNameData.Builder()
                    .setSpn(wfcSpn)
                    .setDataSpn(wfcDataSpn)
                    .setShowSpn(true)
                    .build();
        } else if (!TextUtils.isEmpty(wfcPlmn)) {
            result = new CarrierDisplayNameData.Builder()
                    .setPlmn(wfcPlmn)
                    .setShowPlmn(true)
                    .build();
        }
        return result;
    }

    /**
     * Override the given carrier display name data {@code data} by out of service rule.
     * @param data the carrier display name data need to be overridden.
     * @return overridden carrier display name data.
     */
    private CarrierDisplayNameData getOutOfServiceDisplayName(CarrierDisplayNameData data) {
        // Out of service/Power off/Emergency Only override
        // 1) In flight mode(service state is ServiceState.STATE_POWER_OFF), or the service
        //    state is ServiceState.STATE_OUT_OF_SERVICE but emergency call is not allowed.
        //    showPlmn = true
        //    Only show "No Service" as PLMN
        //
        // 2) Out of service but emergency call is allowed.
        //    showPlmn = true
        //    Only show "Emergency call only" as PLMN
        String plmn = null;
        boolean isSimReady = mPhone.getUiccCardApplication() != null
                && mPhone.getUiccCardApplication().getState() == AppState.APPSTATE_READY;
        boolean forceDisplayNoService =
                mPhone.getServiceStateTracker().shouldForceDisplayNoService() && !isSimReady;
        ServiceState ss = getServiceState();
        if (ss.getState() == ServiceState.STATE_POWER_OFF
                || forceDisplayNoService || !Phone.isEmergencyCallOnly()) {
            plmn = mContext.getResources().getString(
                    com.android.internal.R.string.lockscreen_carrier_default);
        } else {
            plmn = mContext.getResources().getString(
                    com.android.internal.R.string.emergency_calls_only);
        }
        return new CarrierDisplayNameData.Builder()
                .setSpn(data.getSpn())
                .setDataSpn(data.getDataSpn())
                .setShowSpn(data.shouldShowSpn())
                .setPlmn(plmn)
                .setShowPlmn(true)
                .build();
    }

    private void resolveCarrierDisplayName() {
        CarrierDisplayNameData data = getCarrierDisplayNameFromCellular();
        if (DBG) Rlog.d(TAG, "CarrierName from EF: " + data);
        if (getCombinedRegState(getServiceState()) == ServiceState.STATE_IN_SERVICE) {
            if (mPhone.isWifiCallingEnabled()) {
                data = getCarrierDisplayNameFromWifiCallingOverride(data);
                if (DBG) {
                    Rlog.d(TAG, "CarrierName override by wifi-calling " + data);
                }
            }
        } else {
            data = getOutOfServiceDisplayName(data);
            if (DBG) Rlog.d(TAG, "Out of service carrierName " + data);
        }

        if (!Objects.equals(mCarrierDisplayNameData, data)) {
            mLocalLog.log(String.format("ResolveCarrierDisplayName: %s", data.toString()));
        }

        mCarrierDisplayNameData = data;
    }

    /**
     * Get the PLMN network name from the {@link PlmnNetworkName} object.
     * @param name the {@link PlmnNetworkName} object may contain the full and short version of PLMN
     * network name.
     * @return full/short version PLMN network name if one of those is existed, otherwise return an
     * empty string.
     */
    private static String getPlmnNetworkName(PlmnNetworkName name) {
        if (name == null) return "";
        if (!TextUtils.isEmpty(name.fullName)) return name.fullName;
        if (!TextUtils.isEmpty(name.shortName)) return name.shortName;
        return "";
    }

    private ServiceState getServiceState() {
        return mPhone.getServiceStateTracker().getServiceState();
    }

    /**
     * WiFi-Calling formatter for carrier name.
     */
    private static final class WfcCarrierNameFormatter {
        final String mVoiceFormat;
        final String mDataFormat;

        WfcCarrierNameFormatter(@NonNull PersistableBundle config,
                @NonNull String[] wfcFormats, boolean inFlightMode) {
            int voiceIdx = config.getInt(CarrierConfigManager.KEY_WFC_SPN_FORMAT_IDX_INT);
            int dataIdx = config.getInt(CarrierConfigManager.KEY_WFC_DATA_SPN_FORMAT_IDX_INT);
            int flightModeIdx = config.getInt(
                    CarrierConfigManager.KEY_WFC_FLIGHT_MODE_SPN_FORMAT_IDX_INT);

            if (voiceIdx < 0 || voiceIdx >= wfcFormats.length) {
                Rlog.e(TAG, "updateSpnDisplay: KEY_WFC_SPN_FORMAT_IDX_INT out of bounds: "
                        + voiceIdx);
                voiceIdx = 0;
            }

            if (dataIdx < 0 || dataIdx >= wfcFormats.length) {
                Rlog.e(TAG, "updateSpnDisplay: KEY_WFC_DATA_SPN_FORMAT_IDX_INT out of bounds: "
                        + dataIdx);
                dataIdx = 0;
            }

            if (flightModeIdx < 0 || flightModeIdx >= wfcFormats.length) {
                // KEY_WFC_FLIGHT_MODE_SPN_FORMAT_IDX_INT out of bounds. Use the value from
                // voiceIdx.
                flightModeIdx = voiceIdx;
            }

            // flight mode
            if (inFlightMode) {
                voiceIdx = flightModeIdx;
            }

            mVoiceFormat = voiceIdx != -1 ? wfcFormats[voiceIdx] : "";
            mDataFormat = dataIdx != -1 ? wfcFormats[dataIdx] : "";
        }

        /**
         * Format the given {@code name} using wifi-calling voice name formatter.
         * @param name the string need to be formatted.
         * @return formatted string if {@code name} is not empty, otherwise return {@code name}.
         */
        public String formatVoiceName(String name) {
            if (TextUtils.isEmpty(name)) return name;
            return String.format(mVoiceFormat, name.trim());
        }

        /**
         * Format the given {@code name} using wifi-calling data name formatter.
         * @param name the string need to be formatted.
         * @return formatted string if {@code name} is not empty, otherwise return {@code name}.
         */
        public String formatDataName(String name) {
            if (TextUtils.isEmpty(name)) return name;
            return String.format(mDataFormat, name.trim());
        }
    }

    /**
     * Consider dataRegState if voiceRegState is OOS to determine SPN to be displayed.
     * @param ss service state.
     */
    private static int getCombinedRegState(ServiceState ss) {
        if (ss.getState() != ServiceState.STATE_IN_SERVICE) return ss.getDataRegistrationState();
        return ss.getState();
    }
}