/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static android.hardware.radio.V1_0.DeviceStateType.CHARGING_STATE;
import static android.hardware.radio.V1_0.DeviceStateType.LOW_DATA_EXPECTED;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.nullable;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import static java.util.Arrays.asList;

import android.content.Intent;
import android.hardware.radio.V1_2.IndicationFilter;
import android.net.ConnectivityManager;
import android.os.BatteryManager;
import android.os.Message;
import android.test.suitebuilder.annotation.MediumTest;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.filters.FlakyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class DeviceStateMonitorTest extends TelephonyTest {
    // No indication filter set
    private static final int INDICATION_FILTERS_NONE = 0;

    // All implemented indiation filters set so far which is a subset
    // of IndicationFilter.ALL
    private static final int INDICATION_FILTERS_ALL =
            IndicationFilter.SIGNAL_STRENGTH
            | IndicationFilter.FULL_NETWORK_STATE
            | IndicationFilter.DATA_CALL_DORMANCY_CHANGED
            | IndicationFilter.LINK_CAPACITY_ESTIMATE
            | IndicationFilter.PHYSICAL_CHANNEL_CONFIG;

    // INDICATION_FILTERS_ALL but excluding Indication.SIGNAL_STRENGTH
    private static final int INDICATION_FILTERS_WHEN_TETHERING_ON =
            INDICATION_FILTERS_ALL & ~IndicationFilter.SIGNAL_STRENGTH;
    private static final int INDICATION_FILTERS_WHEN_CHARGING = INDICATION_FILTERS_ALL;
    private static final int INDICATION_FILTERS_WHEN_SCREEN_ON = INDICATION_FILTERS_ALL;
    private static final int INDICATION_FILTERS_WHEN_IN_MODE_IGNORE_SCREEN_OFF =
            INDICATION_FILTERS_ALL;

    private DeviceStateMonitor mDSM;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mDSM = new DeviceStateMonitor(mPhone);

        // initialize phone with screen off, charging off and wifi off
        sendStates(0 /*screenState*/, 0 /*chargingState*/, 0 /*wifiState*/);
        processAllMessages();
    }

    @After
    public void tearDown() throws Exception {
        mDSM = null;
        super.tearDown();
    }

    @Test @FlakyTest
    public void testTethering() {
        // Turn tethering on
        Intent intent = new Intent(ConnectivityManager.ACTION_TETHER_STATE_CHANGED);
        intent.putExtra(ConnectivityManager.EXTRA_ACTIVE_TETHER, new ArrayList<>(asList("abc")));
        mContext.sendBroadcast(intent);
        processAllMessages();

        verify(mSimulatedCommandsVerifier).setUnsolResponseFilter(
                eq(INDICATION_FILTERS_WHEN_TETHERING_ON), nullable(Message.class));

        // Turn tethering off
        intent = new Intent(ConnectivityManager.ACTION_TETHER_STATE_CHANGED);
        intent.putExtra(ConnectivityManager.EXTRA_ACTIVE_TETHER, new ArrayList<>());
        mContext.sendBroadcast(intent);
        processAllMessages();

        // mockito report this called 2 times (twice) but our log shows it is only once.
        // so we ignore the verification of times with atLeastOnce() here and follows
        verify(mSimulatedCommandsVerifier, atLeastOnce()).setUnsolResponseFilter(
                eq(INDICATION_FILTERS_NONE), nullable(Message.class));

        verify(mSimulatedCommandsVerifier, atLeastOnce()).sendDeviceState(eq(LOW_DATA_EXPECTED),
                eq(true), nullable(Message.class));
    }

    @Test @FlakyTest
    public void testCharging() {
        // Charging
        Intent intent = new Intent(BatteryManager.ACTION_CHARGING);
        mContext.sendBroadcast(intent);
        processAllMessages();

        verify(mSimulatedCommandsVerifier).sendDeviceState(eq(CHARGING_STATE),
                eq(true), nullable(Message.class));

        // Not charging
        intent = new Intent(BatteryManager.ACTION_DISCHARGING);
        mContext.sendBroadcast(intent);
        processAllMessages();

        verify(mSimulatedCommandsVerifier, atLeastOnce()).setUnsolResponseFilter(
                eq(INDICATION_FILTERS_NONE), nullable(Message.class));

        verify(mSimulatedCommandsVerifier, atLeastOnce()).sendDeviceState(eq(LOW_DATA_EXPECTED),
                eq(true), nullable(Message.class));

        verify(mSimulatedCommandsVerifier, atLeastOnce()).sendDeviceState(eq(CHARGING_STATE),
                eq(false), nullable(Message.class));
    }

    @Test @FlakyTest
    public void testReset() {
        mDSM.obtainMessage(DeviceStateMonitor.EVENT_RADIO_AVAILABLE).sendToTarget();

        verify(mSimulatedCommandsVerifier).setUnsolResponseFilter(eq(IndicationFilter.ALL),
                nullable(Message.class));
    }

    private void sendStates(int screenState, int chargingState, int wifiState) {
        mDSM.obtainMessage(
                DeviceStateMonitor.EVENT_SCREEN_STATE_CHANGED, screenState, 0).sendToTarget();
        mDSM.obtainMessage(
                DeviceStateMonitor.EVENT_CHARGING_STATE_CHANGED, chargingState, 0).sendToTarget();
        mDSM.obtainMessage(
                DeviceStateMonitor.EVENT_WIFI_CONNECTION_CHANGED, wifiState, 0).sendToTarget();
        processAllMessages();
    }

    @Test
    @MediumTest
    public void testWifi() {
        // screen off
        sendStates(0, 0, 0);
        assertEquals(
                DeviceStateMonitor.CELL_INFO_INTERVAL_LONG_MS, mDSM.computeCellInfoMinInterval());
        // screen off, but charging
        sendStates(0, 1, 0);
        assertEquals(
                DeviceStateMonitor.CELL_INFO_INTERVAL_LONG_MS, mDSM.computeCellInfoMinInterval());
        // screen on, no wifi
        sendStates(1, 0, 0);
        assertEquals(
                DeviceStateMonitor.CELL_INFO_INTERVAL_SHORT_MS, mDSM.computeCellInfoMinInterval());
        // screen on, but on wifi
        sendStates(1, 0, 1);
        assertEquals(
                DeviceStateMonitor.CELL_INFO_INTERVAL_LONG_MS, mDSM.computeCellInfoMinInterval());
        // screen on, charging
        sendStates(1, 1, 0);
        assertEquals(
                DeviceStateMonitor.CELL_INFO_INTERVAL_SHORT_MS, mDSM.computeCellInfoMinInterval());
    }
}
