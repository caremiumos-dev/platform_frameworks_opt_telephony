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

package com.android.internal.telephony.d2d;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

import android.test.suitebuilder.annotation.SmallTest;

import com.android.internal.telephony.TestExecutorService;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Verifies bidirectional conversion between {@link Communicator.Message}s and DTMF character
 * sequences.
 * This class verifies only the conversion between the two and not the actual sending/receipt.  See
 * {@link RtpTransportTest} for verification of the send/receive of messages using DTMF.
 */
@RunWith(Parameterized.class)
public class DtmfTransportConversionTest {
    private static class TestParams {
        public Communicator.Message commMessage;
        public String dtmfDigits;

        TestParams(Communicator.Message theMessage, String theDigits) {
            commMessage = theMessage;
            dtmfDigits = theDigits;
        }

        public String toString() {
            return "Params{msg = " + commMessage + ", digits = " + dtmfDigits + "}";
        }
    }

    @Mock
    private DtmfAdapter mDtmfAdapter;
    @Mock
    private TransportProtocol.Callback mCallback;
    @Mock
    private Timeouts.Adapter mTimeouts;
    private TestExecutorService mScheduledExecutorService = new TestExecutorService();

    private final TestParams mParams;
    private DtmfTransport mDtmfTransport;

    public DtmfTransportConversionTest(DtmfTransportConversionTest.TestParams params) {
        mParams = params;
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mDtmfTransport = new DtmfTransport(mDtmfAdapter, mTimeouts, mScheduledExecutorService);
        mDtmfTransport.setCallback(mCallback);
    }

    /**
     * Setup the test cases.
     *
     * @return the test cases
     */
    @Parameterized.Parameters(name = "{0}")
    public static Collection<DtmfTransportConversionTest.TestParams> generateTestCases() {
        List<DtmfTransportConversionTest.TestParams> result = new ArrayList<>();
        result.add(new TestParams(
                new Communicator.Message(Communicator.MESSAGE_CALL_RADIO_ACCESS_TYPE,
                        Communicator.RADIO_ACCESS_TYPE_LTE),
                "AAA" /* DtmfTransport.DTMF_MESSAGE_CALL_RAT_LTE */
        ));
        result.add(new TestParams(
                new Communicator.Message(Communicator.MESSAGE_CALL_RADIO_ACCESS_TYPE,
                        Communicator.RADIO_ACCESS_TYPE_IWLAN),
                "AAB" /* DtmfTransport.DTMF_MESSAGE_CALL_RAT_IWLAN */
        ));
        result.add(new TestParams(
                new Communicator.Message(Communicator.MESSAGE_CALL_RADIO_ACCESS_TYPE,
                        Communicator.RADIO_ACCESS_TYPE_NR),
                "AAC" /* DtmfTransport.DTMF_MESSAGE_CALL_RAT_NR */
        ));

        result.add(new TestParams(
                new Communicator.Message(Communicator.MESSAGE_CALL_AUDIO_CODEC,
                        Communicator.AUDIO_CODEC_EVS),
                "ABA" /* DtmfTransport.DTMF_MESSAGE_CALL_AUDIO_CODEC_EVS */
        ));
        result.add(new TestParams(
                new Communicator.Message(Communicator.MESSAGE_CALL_AUDIO_CODEC,
                        Communicator.AUDIO_CODEC_AMR_WB),
                "ABB" /* DtmfTransport.DTMF_MESSAGE_CALL_AUDIO_CODEC_AMR_WB */
        ));
        result.add(new TestParams(
                new Communicator.Message(Communicator.MESSAGE_CALL_AUDIO_CODEC,
                        Communicator.AUDIO_CODEC_AMR_NB),
                "ABC" /* DtmfTransport.DTMF_MESSAGE_CALL_AUDIO_CODEC_AMR_NB */
        ));

        result.add(new TestParams(
                new Communicator.Message(Communicator.MESSAGE_DEVICE_BATTERY_STATE,
                        Communicator.BATTERY_STATE_LOW),
                "BAA" /* DtmfTransport.DTMF_MESSAGE_DEVICE_BATTERY_LOW */
        ));
        result.add(new TestParams(
                new Communicator.Message(Communicator.MESSAGE_DEVICE_BATTERY_STATE,
                        Communicator.BATTERY_STATE_GOOD),
                "BAB" /* DtmfTransport.DTMF_MESSAGE_DEVICE_BATTERY_GOOD */
        ));
        result.add(new TestParams(
                new Communicator.Message(Communicator.MESSAGE_DEVICE_BATTERY_STATE,
                        Communicator.BATTERY_STATE_CHARGING),
                "BAC" /* DtmfTransport.DTMF_MESSAGE_DEVICE_BATTERY_CHARGING */
        ));

        result.add(new TestParams(
                new Communicator.Message(Communicator.MESSAGE_DEVICE_NETWORK_COVERAGE,
                        Communicator.COVERAGE_GOOD),
                "BBA" /* DtmfTransport.DTMF_MESSAGE_DEVICE_SERVICE_GOOD */
        ));
        result.add(new TestParams(
                new Communicator.Message(Communicator.MESSAGE_DEVICE_NETWORK_COVERAGE,
                        Communicator.COVERAGE_POOR),
                "BBB" /* DtmfTransport.DTMF_MESSAGE_DEVICE_SERVICE_POOR */
        ));

        return result;
    }

    /**
     * Verify generation of DTMF digits for messages.
     */
    @SmallTest
    @Test
    public void testMessageToDtmf() {
        char[] dtmfSequence = mDtmfTransport.getDtmfString(mParams.commMessage);
        assertTrue(areSame(mParams.dtmfDigits.toCharArray(), dtmfSequence));
    }

    /**
     * Verify generation of messages from DTMF digits
     */
    @SmallTest
    @Test
    public void testDtmfToMessage() {
        Communicator.Message message = mDtmfTransport.extractMessage(mParams.dtmfDigits);
        assertEquals(mParams.commMessage, message);
    }

    /**
     * Check two char arrays to see if they're the same (ie same chars).
     * @param ar1 the first array
     * @param ar2 the second array
     * @return you can probably guess
     */
    private boolean areSame(char[] ar1, char[] ar2) {
        if ((ar1 == null) != (ar2 == null)) {
            return false;
        }
        if (ar1.length != ar2.length) {
            return false;
        }
        for (int ix = 0; ix < ar1.length; ix++) {
            if (ar1[ix] != ar2[ix]) {
                return false;
            }
        }
        return true;
    }
}
