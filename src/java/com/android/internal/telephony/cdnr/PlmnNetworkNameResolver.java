/*
 * Copyright (C) 2020 The Android Open Source Project
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

import android.annotation.Nullable;
import android.content.Context;
import android.os.PersistableBundle;
import android.text.TextUtils;

import com.android.internal.telephony.CarrierResolver;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.IccRecords.CarrierNameDisplayConditionBitmask;
import com.android.internal.telephony.uicc.IccRecords.OperatorPlmnInfo;
import com.android.internal.telephony.uicc.IccRecords.PlmnNetworkName;
import com.android.internal.telephony.uicc.SIMRecords;
import com.android.internal.telephony.util.ArrayUtils;
import com.android.telephony.Rlog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Resolve network name for PLMN from SIM with Carrier Config Override.
 *
 * @hide
 */
public final class PlmnNetworkNameResolver {
    private static final boolean DBG = false;
    protected static final String LOG_TAG = "PlmnNetworkNameResolver";
    private final Context mContext;
    private SIMRecords mSim;
    private CarrierConfigEfData mCarrierConfigEfData;
    private String mSpn;
    private @CarrierNameDisplayConditionBitmask int mDisplayCondition;
    private List<String> mSpdi;
    private List<PlmnNetworkName> mPnns;
    private List<OperatorPlmnInfo> mOpl;
    // Equivalent Home PLMNs
    private List<String> mEhplmns;
    // Home PLMNs
    private List<String> mHplmns;

    public PlmnNetworkNameResolver(Context context) {
        mContext = context;
    }

    /**
     * Update all fields from SimRecords.
     */
    public void updateSimRecords(SIMRecords sim) {
        if (sim == null) return;
        mSim = sim;
        updateAllFields();
    }

    /**
     * Update all fields from Carrier Config.
     */
    public void updateCarrierConfig(PersistableBundle carrierConfig) {
        if (carrierConfig == null) return;
        mCarrierConfigEfData = new CarrierConfigEfData(carrierConfig);
        updateAllFields();
    }

    /**
     * Update all fields.
     */
    public void updateAllFields() {
        updateSpn();
        updateServiceProviderNameDisplayCondition();
        updateSpdi();
        updateEhplmnList();
        updatePnns();
        updateOpl();
        updateAllHplmns();
    }

    @Nullable public String getSpn() {
        return mSpn;
    }

    @Nullable public int getServiceProviderNameDisplayCondition() {
        return mDisplayCondition;
    }

    @Nullable public List<String> getSpdi() {
        return mSpdi;
    }

    @Nullable public List<PlmnNetworkName> getPnns() {
        return mPnns;
    }

    @Nullable public List<OperatorPlmnInfo> getOpl() {
        return mOpl;
    }

    @Nullable public List<String> getEhplmnList() {
        return mEhplmns;
    }

    @Nullable public List<String> getHomePlmns() {
        return mHplmns;
    }


    /**
     * Get network name for the provided PLMN and LAC/TAC
     *
     * <p>Resolves network name based on available information sources including the
     * CarrierConfigManager, the contents of the SIM card, and the
     * android.provider.Telephony.CarrierId. If the name cannot be resolved via
     * one or more of these sources, then this API returns null.
     * @param plmn PLMN
     * @param lacTac LAC or TAC
     * @return network name of the provided PLMN and lacTac
     */
    @Nullable public String getNetworkNameForPlmn(String plmn, int lacTac) {
        if (plmn == null || plmn.length() < IccRecords.PLMN_MIN_LENGTH
                || plmn.length() > IccRecords.PLMN_MAX_LENGTH) {
            return null;
        }

        String networkName = null;
        boolean isRoaming = mHplmns != null ? !ArrayUtils.contains(mHplmns, plmn) : true;
        if (isRoaming) {
            if (mOpl != null && mPnns != null) {
                networkName = IccRecords.getNetworkNameForPlmnFromPnnOpl(
                        mPnns.toArray(new PlmnNetworkName[0]),
                        mOpl.toArray(new OperatorPlmnInfo[0]),
                        plmn, lacTac);
            }
        } else {
            if (!TextUtils.isEmpty(mSpn)) {
                networkName = mSpn;
            } else if (mPnns != null && mPnns.size() != 0 && mPnns.get(0) != null) {
                networkName = mPnns.get(0).getName();
            }
        }
        if (!TextUtils.isEmpty(networkName)) return networkName;

        networkName = CarrierResolver.getCarrierNameFromMccMnc(mContext, plmn);
        if (DBG) {
            Rlog.d(LOG_TAG, "getNetworkNameForPlmn: PLMN " + plmn
                    + " name from Carrier ID: " + networkName);
        }
        return networkName;
    }

    /**
     * Update SPN from config and SIM
     */
    private void updateSpn() {
        mSpn = null;

        if (mSim != null) mSpn = mSim.getServiceProviderName();
        if (mCarrierConfigEfData != null) {
            // If #KEY_CARRIER_NAME_STRING value is true, then apply #KEY_CARRIER_NAME_STRING
            // even in the event that the value is empty.
            // If #KEY_CARRIER_NAME_OVERRIDE_BOOL value is false,
            // then #KEY_CARRIER_NAME_STRING will only be used if SPN in SIM is empty.
            if (mCarrierConfigEfData.getCarrierNameOverrideBool() || TextUtils.isEmpty(mSpn)) {
                mSpn = mCarrierConfigEfData.getServiceProviderName();
            }
        }
    }

    /**
     * Update SPN display condition from config and SIM
     */
    private void updateServiceProviderNameDisplayCondition() {
        mDisplayCondition = IccRecords.INVALID_CARRIER_NAME_DISPLAY_CONDITION_BITMASK;
        if (mCarrierConfigEfData != null) {
            mDisplayCondition = mCarrierConfigEfData.getServiceProviderNameDisplayCondition();
        }
        if (mDisplayCondition == IccRecords.INVALID_CARRIER_NAME_DISPLAY_CONDITION_BITMASK
                && mSim != null) {
            mDisplayCondition = mSim.getCarrierNameDisplayCondition();
        }
    }

    /**
     * Update SPDI from config and SIM
     */
    private void updateSpdi() {
        mSpdi = null;
        if (mCarrierConfigEfData != null) {
            mSpdi = mCarrierConfigEfData.getServiceProviderDisplayInformation();
        }
        if (mSpdi == null && mSim != null && mSim.getServiceProviderDisplayInformation() != null) {
            mSpdi = Arrays.asList(mSim.getServiceProviderDisplayInformation());
        }
    }

    /**
     * Update EHPLMN from config and SIM
     */
    private void updateEhplmnList() {
        mEhplmns = null;
        if (mCarrierConfigEfData != null) {
            mEhplmns = mCarrierConfigEfData.getEhplmnList();
        }
        if (mEhplmns == null && mSim != null && mSim.getEhplmns() != null) {
            mEhplmns = Arrays.asList(mSim.getEhplmns());
        }
    }

    /**
     * Update PNN from config and SIM
     */
    private void updatePnns() {
        mPnns = null;
        if (mCarrierConfigEfData != null) {
            mPnns = mCarrierConfigEfData.getPlmnNetworkNameList();
        }

        if (mPnns == null && mSim != null && mSim.getPnns() != null) {
            mPnns = Arrays.asList(mSim.getPnns());
        }
    }

    /**
     * Update OPL from config and SIM
     */
    private void updateOpl() {
        mOpl = null;
        if (mCarrierConfigEfData != null) {
            mOpl = mCarrierConfigEfData.getOperatorPlmnList();
        }

        if (mOpl == null && mSim != null && mSim.getOpl() != null) {
            mOpl = Arrays.asList(mSim.getOpl());
        }
    }

    /**
     * Update Home PLMNs with IMSI, EHPLM, EHPLMN, and SPDI
     */
    private void updateAllHplmns() {
        if (mSim == null) return;

        // Home PLMN from EHPLMN list.
        if (mEhplmns != null) {
            mHplmns = new ArrayList<String>(mEhplmns);
        } else {
            mHplmns = new ArrayList<String>();
        }

        // Home PLMN from IMSI.
        String hplmn = mSim.getOperatorNumeric();
        if  (!TextUtils.isEmpty(hplmn)) {
            mHplmns.add(hplmn);
        }
        // Home PLMN from SPDI
        if (mSpdi != null && mSpdi.size() != 0) {
            mHplmns.addAll(mSpdi);
        }

        if (mHplmns.size() == 0) mHplmns = null;
    }
}
