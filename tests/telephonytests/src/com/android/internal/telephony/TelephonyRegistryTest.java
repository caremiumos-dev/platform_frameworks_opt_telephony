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

package com.android.server;

import static junit.framework.Assert.assertEquals;

import android.os.Bundle;
import android.os.HandlerThread;
import android.telephony.CellInfo;
import android.telephony.DataConnectionRealTimeInfo;
import android.telephony.PhoneStateListener;
import android.telephony.PhysicalChannelConfig;
import android.telephony.PreciseCallState;
import android.telephony.PreciseDataConnectionState;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.VoLteServiceState;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.internal.telephony.IPhoneStateListener;
import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class TelephonyRegistryTest extends TelephonyTest {

    TelephonyRegistryHandler mTelephonyRegistryHandler;
    TelephonyRegistry mRegistryUT;

    /** Empty IPhoneStateListener.Stub so that individual tests can override it  easily */
    public class TestPhoneStateListener extends IPhoneStateListener.Stub {
        @Override
        public void onServiceStateChanged(ServiceState serviceState) {
        }

        @Override
        public void onSignalStrengthChanged(int asu) {
        }

        @Override
        public void onMessageWaitingIndicatorChanged(boolean mwi) {
        }

        @Override
        public void onCallForwardingIndicatorChanged(boolean cfi) {
        }

        @Override
        public void onCellLocationChanged(Bundle bundle) {
        }

        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
        }

        @Override
        public void onDataConnectionStateChanged(int state, int networkType) {
        }

        @Override
        public void onDataActivity(int direction) {
        }

        @Override
        public void onSignalStrengthsChanged(SignalStrength signalStrength) {
        }

        @Override
        public void onOtaspChanged(int otaspMode) {
        }

        @Override
        public void onCellInfoChanged(List<CellInfo> cellInfo) {
        }

        @Override
        public void onPreciseCallStateChanged(PreciseCallState callState) {
        }

        @Override
        public void onPreciseDataConnectionStateChanged(
                PreciseDataConnectionState dataConnectionState) {
        }

        @Override
        public void onDataConnectionRealTimeInfoChanged(DataConnectionRealTimeInfo dcRtInfo) {
        }

        @Override
        public void onVoLteServiceStateChanged(VoLteServiceState lteState) {
        }

        @Override
        public void onVoiceActivationStateChanged(int activationState) {
        }

        @Override
        public void onDataActivationStateChanged(int activationState) {
        }

        @Override
        public void onUserMobileDataStateChanged(boolean enabled) {
        }

        @Override
        public void onCarrierNetworkChange(boolean active) {
        }

        @Override
        public void onPhysicalChannelConfigurationChanged(List<PhysicalChannelConfig> configs) {
        }

        @Override
        public void onOemHookRawEvent(byte[] rawdata) {
        }
    };

    private class TelephonyRegistryHandler extends HandlerThread {
        private TelephonyRegistryHandler(String name) {
            super(name);
        }

        @Override
        public void onLooperPrepared() {
            mRegistryUT = new TelephonyRegistry(mContext);
            setReady(true);
        }
    };

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mTelephonyRegistryHandler = new TelephonyRegistryHandler("TelephonyRegistryHandlerThread");
        mTelephonyRegistryHandler.start();
    }

    @After
    public void tearDown() throws Exception {
        mTelephonyRegistryHandler.quitSafely();
        super.tearDown();
    }

    // class members so they can be accessed from the inner class; sigh
    private BlockingQueue<List<PhysicalChannelConfig>> mConfigs = new ArrayBlockingQueue<>(1);

    @Test
    @SmallTest
    public void testDefaultPhysicalChannelConfigNotNull() throws Exception {
        waitUntilReady();
        IPhoneStateListener.Stub testListener = new TestPhoneStateListener() {
            @Override
            public void onPhysicalChannelConfigurationChanged(List<PhysicalChannelConfig> c) {
                if (c != null) {
                    mConfigs.add(c);
                }
            }
        };

        mRegistryUT.listen("testPackageName", testListener,
                PhoneStateListener.LISTEN_PHYSICAL_CHANNEL_CONFIGURATION, true);
        List<PhysicalChannelConfig> empty = mConfigs.poll(5, TimeUnit.SECONDS);
        assertEquals(empty.size(), 0);
    }
}
