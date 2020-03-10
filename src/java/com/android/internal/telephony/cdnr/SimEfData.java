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

import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.text.TextUtils;

import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.IccRecords.OperatorPlmnInfo;
import com.android.internal.telephony.uicc.IccRecords.PlmnNetworkName;
import com.android.internal.telephony.uicc.SIMRecords;
import com.android.internal.telephony.util.ArrayUtils;
import com.android.telephony.Rlog;

import java.util.ArrayList;
import java.util.List;

/** Ef data from SIM. */
public final class SimEfData {
    protected static final String LOG_TAG = "SimEfData";
    private final SIMRecords mSim;
    private final PersistableBundle mConfig;

    public SimEfData(SIMRecords sim, PersistableBundle config) {
        mSim = sim;
        mConfig = config;
    }

    /**
    * Get SPN from config and SIM
    */
    public String getServiceProviderName() {
        String spn = mSim != null ? mSim.getServiceProviderName() : null;
        if (mConfig != null) {
            if (mConfig.getBoolean(CarrierConfigManager.KEY_CARRIER_NAME_OVERRIDE_BOOL)
                    || TextUtils.isEmpty(spn)) {
                return mConfig.getString(CarrierConfigManager.KEY_CARRIER_NAME_STRING);
            }
        }
        return spn;
    }

    /**
    * Get SPN display conditiobn from config and SIM
    */
    public int getServiceProviderNameDisplayCondition() {
        // from carrier config
        if (mConfig != null) {
            int condition = mConfig.getInt(
                    CarrierConfigManager.KEY_SPN_DISPLAY_CONDITION_OVERRIDE_INT,
                    IccRecords.INVALID_CARRIER_NAME_DISPLAY_CONDITION_BITMASK);
            if (condition != IccRecords.INVALID_CARRIER_NAME_DISPLAY_CONDITION_BITMASK) {
                return condition;
            }
        }

        // from SIM
        return mSim != null ? mSim.getCarrierNameDisplayCondition()
                : IccRecords.INVALID_CARRIER_NAME_DISPLAY_CONDITION_BITMASK;
    }

    /**
    * Get SPDI from config and SIM
    */
    public String[] getServiceProviderDisplayInformation() {
        String[] spdi;

        if (mConfig != null) {
            spdi = mConfig.getStringArray(
                    CarrierConfigManager.KEY_SPDI_OVERRIDE_STRING_ARRAY);
            if (spdi != null) return spdi;
        }

        spdi = mSim != null ? mSim.getServiceProviderDisplayInformation() : null;
        return spdi != null ? spdi : null;
    }

    /**
    * Get EHPLMN from config and SIM
    */
    public String[] getEhplmnList() {
        String[] ehplmns;

        if (mConfig != null) {
            ehplmns = mConfig.getStringArray(
                    CarrierConfigManager.KEY_EHPLMN_OVERRIDE_STRING_ARRAY);
            if (ehplmns != null) return ehplmns;
        }

        ehplmns = mSim != null ? mSim.getEhplmns() : null;
        return ehplmns != null ? ehplmns : null;
    }

    /**
    * Get PNN from config and SIM
    */
    public List<PlmnNetworkName> getPlmnNetworkNameList() {
        List<PlmnNetworkName> pnnList = null;

        // from carrier config
        if (mConfig != null) {
            String[] pnn = mConfig.getStringArray(
                    CarrierConfigManager.KEY_PNN_OVERRIDE_STRING_ARRAY);
            if (pnn != null) {
                pnnList = new ArrayList<>(pnn.length);
                for (String pnnStr : pnn) {
                    try {
                        String[] names = pnnStr.split("\\s*,\\s*");
                        String alphal = names[0];
                        String alphas = names.length > 1 ? names[1] : "";
                        pnnList.add(new PlmnNetworkName(alphal, alphas));
                    } catch (Exception ex) {
                        Rlog.e(LOG_TAG, "CarrierConfig wrong pnn format, pnnStr = " + pnnStr);
                    }
                }
            }
            if (pnnList != null) return pnnList;
        }

        // from sim
        // TODO: Update the whole list rather than the pnn home name when IccRecords can
        // read all pnn records.

        return null;
    }

    /**
    * Get OPL from config and SIM
    */
    public List<OperatorPlmnInfo> getOperatorPlmnList() {
        List<OperatorPlmnInfo> oplList = null;

        // from carrier config
        if (mConfig != null) {
            // OPL
            String[] opl = mConfig.getStringArray(
                    CarrierConfigManager.KEY_OPL_OVERRIDE_STRING_ARRAY);
            if (opl != null) {
                oplList = new ArrayList<>(opl.length);
                for (String oplStr : opl) {
                    try {
                        String[] info = oplStr.split("\\s*,\\s*");
                        oplList.add(new OperatorPlmnInfo(
                                info[0] /* plmn */,
                                Integer.parseInt(info[1]) /* lactac_start */,
                                Integer.parseInt(info[2]) /* lactac_end */,
                                Integer.parseInt(info[3]) /* pnn index */));
                    } catch (Exception ex) {
                        Rlog.e(LOG_TAG, "CarrierConfig wrong opl format, oplStr = " + oplStr);
                    }
                }
            }
            if (oplList != null) return oplList;
        }

        // TODO: update the OPL when SIMRecords supports it.
        return null;
    }

    /**
    * Get Home PLMNs with IMSI, EHPLM, EHPLMN, and SPDI
    * The logic is the same as {@code IccRecords#getHomePlmns}
    */
    public String[] getHomePlmns() {
        if (mSim == null) return null;

        // hplmn from imsi.
        String hplmn = mSim.getOperatorNumeric();

        // hplmn from ehplmn list.
        String[] hplmns = getEhplmnList();

        // plmn from ef_spdi.
        String[] spdi = getServiceProviderDisplayInformation();

        // Use the plmn from imsi as the hplmn if Ehplmn not present.
        if (ArrayUtils.isEmpty(hplmns)) {
            hplmns = new String[] {hplmn};
        }

        if (!ArrayUtils.isEmpty(spdi)) {
            hplmns = ArrayUtils.concatElements(String.class, hplmns, spdi);
        }
        // If hplmns don't contain hplmn, we need to add hplmn to hplmns
        hplmns = ArrayUtils.appendElement(String.class, hplmns, hplmn);
        return hplmns;
    }
}
