/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.telephony.RadioInterfaceCapabilities;
import android.telephony.TelephonyManager;
import android.util.ArraySet;

import com.android.telephony.Rlog;

import java.util.Set;

/**
 * @hide
 */
public class RadioConfigCapabilitiesResponse {

    private final boolean mModemReducedFeatureSet1;

    public RadioConfigCapabilitiesResponse(final boolean modemReducedFeatureSet1) {
        mModemReducedFeatureSet1 = modemReducedFeatureSet1;
    }

    /**
     * Creates the final list of capabilities combining the feature sets and the hal version.
     */
    public RadioInterfaceCapabilities createRadioHalDeviceCapabilities(final HalVersion version) {
        if (version.equals(RIL.RADIO_HAL_VERSION_UNKNOWN)) {
            // If the radio hal version is UNKNOWN, no capabilities will present themselves.
            loge("Radio Hal Version is UNKNOWN!");
        }

        log("Radio Hal Version = " + version.toString());
        final Set<String> caps = new ArraySet<>();
        if (version.greaterOrEqual(RIL.RADIO_HAL_VERSION_1_6)) {
            caps.add(TelephonyManager.CAPABILITY_ALLOWED_NETWORK_TYPES_USED);
            log("CAPABILITY_ALLOWED_NETWORK_TYPES_USED");

            if (!mModemReducedFeatureSet1) {
                caps.add(TelephonyManager.CAPABILITY_SECONDARY_LINK_BANDWIDTH_VISIBLE);
                log("CAPABILITY_SECONDARY_LINK_BANDWIDTH_VISIBLE");
            }
        }
        return new RadioInterfaceCapabilities(caps);
    }

    private static void log(final String msg) {
        Rlog.d(RadioConfigCapabilitiesResponse.class.getSimpleName(), msg);
    }

    private static void loge(final String msg) {
        Rlog.e(RadioConfigCapabilitiesResponse.class.getSimpleName(), msg);
    }
}
