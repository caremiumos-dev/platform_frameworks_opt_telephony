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

import static junit.framework.Assert.assertTrue;

import static org.junit.Assert.assertFalse;

import android.telephony.RadioInterfaceCapabilities;
import android.telephony.TelephonyManager;
import android.testing.AndroidTestingRunner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidTestingRunner.class)
public class RadioConfigCapabilitiesResponseTest extends TelephonyTest {
    @Before
    public void setUp() throws Exception {
        super.setUp(RadioConfigCapabilitiesResponseTest.class.getSimpleName());
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testVersion_1_5() {
        final RadioConfigCapabilitiesResponse response =
                new RadioConfigCapabilitiesResponse(false);
        final RadioInterfaceCapabilities caps =
                response.createRadioHalDeviceCapabilities(RIL.RADIO_HAL_VERSION_1_5);
        assertFalse(
                caps.isSupported(TelephonyManager.CAPABILITY_SECONDARY_LINK_BANDWIDTH_VISIBLE));
        assertFalse(
                caps.isSupported(TelephonyManager.CAPABILITY_ALLOWED_NETWORK_TYPES_USED));
    }

    @Test
    public void testReducedFeatureSet() {
        final RadioConfigCapabilitiesResponse response =
                new RadioConfigCapabilitiesResponse(true);
        final RadioInterfaceCapabilities caps =
                response.createRadioHalDeviceCapabilities(RIL.RADIO_HAL_VERSION_1_6);
        assertFalse(
                caps.isSupported(TelephonyManager.CAPABILITY_SECONDARY_LINK_BANDWIDTH_VISIBLE));
        assertTrue(
                caps.isSupported(TelephonyManager.CAPABILITY_ALLOWED_NETWORK_TYPES_USED));
    }

    @Test
    public void testNonReducedFeatureSet() {
        final RadioConfigCapabilitiesResponse response =
                new RadioConfigCapabilitiesResponse(false);
        final RadioInterfaceCapabilities caps =
                response.createRadioHalDeviceCapabilities(RIL.RADIO_HAL_VERSION_1_6);
        assertTrue(
                caps.isSupported(TelephonyManager.CAPABILITY_SECONDARY_LINK_BANDWIDTH_VISIBLE));
        assertTrue(
                caps.isSupported(TelephonyManager.CAPABILITY_ALLOWED_NETWORK_TYPES_USED));
    }
}
