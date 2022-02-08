/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.internal.telephony.metrics;

import static android.telephony.PhoneNumberUtils.areSamePhoneNumber;
import static android.telephony.SubscriptionManager.PHONE_NUMBER_SOURCE_CARRIER;
import static android.telephony.SubscriptionManager.PHONE_NUMBER_SOURCE_IMS;
import static android.telephony.SubscriptionManager.PHONE_NUMBER_SOURCE_UICC;

import android.telephony.SubscriptionInfo;
import android.telephony.TelephonyManager;
import android.telephony.ims.ImsManager;
import android.telephony.ims.ImsMmTelManager;
import android.text.TextUtils;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.SubscriptionController;

import java.util.Optional;

/** Stores the per SIM status. */
public class PerSimStatus {
    private static final long BITMASK_2G =
            TelephonyManager.NETWORK_TYPE_BITMASK_GSM
                    | TelephonyManager.NETWORK_TYPE_BITMASK_GPRS
                    | TelephonyManager.NETWORK_TYPE_BITMASK_EDGE
                    | TelephonyManager.NETWORK_TYPE_BITMASK_CDMA
                    | TelephonyManager.NETWORK_TYPE_BITMASK_1xRTT;

    public int carrierId;
    public int phoneNumberSourceUicc;
    public int phoneNumberSourceCarrier;
    public int phoneNumberSourceIms;
    public boolean advancedCallingSettingEnabled;
    public boolean voWiFiSettingEnabled;
    public int voWiFiModeSetting;
    public int voWiFiRoamingModeSetting;
    public boolean vtSettingEnabled;
    public boolean dataRoamingEnabled;
    public long preferredNetworkType;
    public boolean disabled2g;

    /** Returns the current sim status of the given phone. */
    public static PerSimStatus getCurrentState(int phoneId) {
        SubscriptionController subscriptionController = SubscriptionController.getInstance();
        if (subscriptionController == null) {
            return null;
        }
        Phone phone = PhoneFactory.getPhone(phoneId);
        int subId = phone.getSubId();
        ImsManager imsManager = phone.getContext().getSystemService(ImsManager.class);
        if (imsManager == null) {
            return null;
        }
        ImsMmTelManager imsMmTelManager;
        try {
            imsMmTelManager = imsManager.getImsMmTelManager(subId);
        } catch (IllegalArgumentException e) {
            return null; // Invalid subId
        }

        String countryIso =
                Optional.ofNullable(subscriptionController.getSubscriptionInfo(subId))
                        .map(SubscriptionInfo::getCountryIso)
                        .orElse("");
        // number[]- hone numbers from each sources:
        // numberState[] - int representation for each number source used in the atom
        // index 0 - PHONE_NUMBER_SOURCE_UICC
        // index 1 - PHONE_NUMBER_SOURCE_CARRIER
        // index 2 - PHONE_NUMBER_SOURCE_IMS
        String[] number =
                new String[] {
                    subscriptionController.getPhoneNumber(subId, PHONE_NUMBER_SOURCE_UICC), // 0
                    subscriptionController.getPhoneNumber(subId, PHONE_NUMBER_SOURCE_CARRIER), // 1
                    subscriptionController.getPhoneNumber(subId, PHONE_NUMBER_SOURCE_IMS), // 2
                };
        int[] numberState = new int[number.length]; // default value 0
        for (int i = 0, stateForNextUniqueNumber = 1; i < numberState.length; i++) {
            if (TextUtils.isEmpty(number[i])) {
                // keep state 0 if number not available
                continue;
            }
            // the number is available:
            // try to find the same number from other sources and reuse the state
            for (int j = 0; j < i; j++) {
                if (!TextUtils.isEmpty(number[j])
                        && areSamePhoneNumber(number[i], number[j], countryIso)) {
                    numberState[i] = numberState[j];
                }
            }
            // didn't find same number (otherwise should not be state 0), assign a new state
            if (numberState[i] == 0) {
                numberState[i] = stateForNextUniqueNumber;
                stateForNextUniqueNumber++;
            }
        }
        int carrierId = phone.getCarrierId();
        int phoneNumberSourceUicc = numberState[0];
        int phoneNumberSourceCarrier = numberState[1];
        int phoneNumberSourceIms = numberState[2];
        boolean advancedCallingSettingEnabled = imsMmTelManager.isAdvancedCallingSettingEnabled();
        boolean voWiFiSettingEnabled = imsMmTelManager.isVoWiFiSettingEnabled();
        int voWiFiModeSetting = imsMmTelManager.getVoWiFiModeSetting();
        int voWiFiRoamingModeSetting = imsMmTelManager.getVoWiFiRoamingModeSetting();
        boolean vtSettingEnabled = imsMmTelManager.isVtSettingEnabled();
        boolean dataRoamingEnabled = phone.getDataRoamingEnabled();
        long preferredNetworkType =
                phone.getAllowedNetworkTypes(TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER);
        boolean disabled2g =
                (phone.getAllowedNetworkTypes(
                                        TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_ENABLE_2G)
                                & BITMASK_2G)
                        == 0;
        return new PerSimStatus(
                carrierId,
                phoneNumberSourceUicc,
                phoneNumberSourceCarrier,
                phoneNumberSourceIms,
                advancedCallingSettingEnabled,
                voWiFiSettingEnabled,
                voWiFiModeSetting,
                voWiFiRoamingModeSetting,
                vtSettingEnabled,
                dataRoamingEnabled,
                preferredNetworkType,
                disabled2g);
    }

    private PerSimStatus(
            int carrierId,
            int phoneNumberSourceUicc,
            int phoneNumberSourceCarrier,
            int phoneNumberSourceIms,
            boolean advancedCallingSettingEnabled,
            boolean voWiFiSettingEnabled,
            int voWiFiModeSetting,
            int voWiFiRoamingModeSetting,
            boolean vtSettingEnabled,
            boolean dataRoamingEnabled,
            long preferredNetworkType,
            boolean disabled2g) {
        this.carrierId = carrierId;
        this.phoneNumberSourceUicc = phoneNumberSourceUicc;
        this.phoneNumberSourceCarrier = phoneNumberSourceCarrier;
        this.phoneNumberSourceIms = phoneNumberSourceIms;
        this.advancedCallingSettingEnabled = advancedCallingSettingEnabled;
        this.voWiFiSettingEnabled = voWiFiSettingEnabled;
        this.voWiFiModeSetting = voWiFiModeSetting;
        this.voWiFiRoamingModeSetting = voWiFiRoamingModeSetting;
        this.vtSettingEnabled = vtSettingEnabled;
        this.dataRoamingEnabled = dataRoamingEnabled;
        this.preferredNetworkType = preferredNetworkType;
        this.disabled2g = disabled2g;
    }
}
