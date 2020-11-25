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

package com.android.internal.telephony.d2d;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.Handler;
import android.telephony.ims.RtpHeaderExtension;
import android.telephony.ims.RtpHeaderExtensionType;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.ArraySet;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Set;

@RunWith(AndroidJUnit4.class)
public class RtpTransportTest {
    private static final int CALL_STATE_LOCAL_IDENTIFIER = 1;
    private static final int DEVICE_STATE_LOCAL_IDENTIFIER = 2;

    private static final ArraySet<RtpHeaderExtensionType> ALL_HEADER_EXTENSION_TYPES =
            new ArraySet<>();
    static {
        ALL_HEADER_EXTENSION_TYPES.add(new RtpHeaderExtensionType(CALL_STATE_LOCAL_IDENTIFIER,
                RtpTransport.CALL_STATE_RTP_HEADER_EXTENSION));
        ALL_HEADER_EXTENSION_TYPES.add(new RtpHeaderExtensionType(DEVICE_STATE_LOCAL_IDENTIFIER,
                RtpTransport.DEVICE_STATE_RTP_HEADER_EXTENSION));
    }
    private static final ArraySet<RtpHeaderExtensionType> NO_SUPPORTED_HEADER_EXTENSION_TYPES =
            new ArraySet<>();

    private RtpTransport mRtpTransport;
    @Mock private Timeouts.Adapter mTimeoutsAdapter;
    @Mock private RtpAdapter mRtpAdapter;
    @Mock private Handler mHandler;
    @Mock private TransportProtocol.Callback mCallback;
    @Captor private ArgumentCaptor<Set<RtpHeaderExtension>> mHeaderExtensionCaptor;
    @Captor private ArgumentCaptor<Set<Communicator.Message>> mMessagesCaptor;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mRtpTransport = new RtpTransport(mRtpAdapter, mTimeoutsAdapter, mHandler);
        mRtpTransport.setCallback(mCallback);
    }

    /**
     * Nominal case; assume the remote side accepted all RTP header extension types we need for D2D
     * communications.  The should get an instant negotiation success.
     */
    @SmallTest
    @Test
    public void testAllHeaderExtensionsSupported() {
        when(mRtpAdapter.getAcceptedRtpHeaderExtensions()).thenReturn(ALL_HEADER_EXTENSION_TYPES);
        mRtpTransport.startNegotiation();
        verify(mCallback).onNegotiationSuccess(any());
        verify(mCallback, never()).onNegotiationFailed(any());
    }

    /**
     * Verify the case where the RTP header extensions are not supported.
     */
    @SmallTest
    @Test
    public void testRtpHeaderExtensionsNotSupported() {
        when(mRtpAdapter.getAcceptedRtpHeaderExtensions()).thenReturn(
                NO_SUPPORTED_HEADER_EXTENSION_TYPES);
        mRtpTransport.startNegotiation();
        verify(mCallback, never()).onNegotiationSuccess(any());
        verify(mCallback).onNegotiationFailed(any());
    }

    /**
     * Verify generation of RTP header extension data for messages.
     */
    @SmallTest
    @Test
    public void testSendMessages() {
        testAllHeaderExtensionsSupported();

        // Build test messages.
        ArraySet<Communicator.Message> messages = new ArraySet<>();
        messages.add(new Communicator.Message(Communicator.MESSAGE_CALL_AUDIO_CODEC,
                Communicator.AUDIO_CODEC_EVS));
        messages.add(new Communicator.Message(Communicator.MESSAGE_CALL_AUDIO_CODEC,
                Communicator.AUDIO_CODEC_AMR_WB));
        messages.add(new Communicator.Message(Communicator.MESSAGE_CALL_AUDIO_CODEC,
                Communicator.AUDIO_CODEC_AMR_NB));
        messages.add(new Communicator.Message(Communicator.MESSAGE_CALL_RADIO_ACCESS_TYPE,
                Communicator.RADIO_ACCESS_TYPE_LTE));
        messages.add(new Communicator.Message(Communicator.MESSAGE_CALL_RADIO_ACCESS_TYPE,
                Communicator.RADIO_ACCESS_TYPE_IWLAN));
        messages.add(new Communicator.Message(Communicator.MESSAGE_CALL_RADIO_ACCESS_TYPE,
                Communicator.RADIO_ACCESS_TYPE_NR));
        messages.add(new Communicator.Message(Communicator.MESSAGE_DEVICE_BATTERY_STATE,
                Communicator.BATTERY_STATE_LOW));
        messages.add(new Communicator.Message(Communicator.MESSAGE_DEVICE_BATTERY_STATE,
                Communicator.BATTERY_STATE_GOOD));
        messages.add(new Communicator.Message(Communicator.MESSAGE_DEVICE_BATTERY_STATE,
                Communicator.BATTERY_STATE_CHARGING));
        messages.add(new Communicator.Message(Communicator.MESSAGE_DEVICE_NETWORK_COVERAGE,
                Communicator.COVERAGE_POOR));
        messages.add(new Communicator.Message(Communicator.MESSAGE_DEVICE_NETWORK_COVERAGE,
                Communicator.COVERAGE_GOOD));

        // Send and capture the generated RTP header extensions.
        mRtpTransport.sendMessages(messages);
        verify(mRtpAdapter).sendRtpHeaderExtensions(mHeaderExtensionCaptor.capture());
        Set<RtpHeaderExtension> extensions = mHeaderExtensionCaptor.getValue();
        assertEquals(11, extensions.size());

        assertTrue(extensions.contains(new RtpHeaderExtension(CALL_STATE_LOCAL_IDENTIFIER,
                new byte[] {0b00100010}))); // AMR_WB - Codec
        assertTrue(extensions.contains(new RtpHeaderExtension(CALL_STATE_LOCAL_IDENTIFIER,
                new byte[] {0b00110010}))); // AMR_NB - Codec
        assertTrue(extensions.contains(new RtpHeaderExtension(CALL_STATE_LOCAL_IDENTIFIER,
                new byte[] {0b00010010}))); // EVS - Codec
        assertTrue(extensions.contains(new RtpHeaderExtension(CALL_STATE_LOCAL_IDENTIFIER,
                new byte[] {0b00010001}))); // LTE - RAT
        assertTrue(extensions.contains(new RtpHeaderExtension(CALL_STATE_LOCAL_IDENTIFIER,
                new byte[] {0b00100001}))); // IWLAN - RAT
        assertTrue(extensions.contains(new RtpHeaderExtension(CALL_STATE_LOCAL_IDENTIFIER,
                new byte[] {0b00110001}))); // NR - RAT
        assertTrue(extensions.contains(new RtpHeaderExtension(DEVICE_STATE_LOCAL_IDENTIFIER,
                new byte[] {0b00000001}))); // Low - Battery
        assertTrue(extensions.contains(new RtpHeaderExtension(DEVICE_STATE_LOCAL_IDENTIFIER,
                new byte[] {0b00010001}))); // Good - Battery
        assertTrue(extensions.contains(new RtpHeaderExtension(DEVICE_STATE_LOCAL_IDENTIFIER,
                new byte[] {0b00110001}))); // Charging - Battery
        assertTrue(extensions.contains(new RtpHeaderExtension(DEVICE_STATE_LOCAL_IDENTIFIER,
                new byte[] {0b00000010}))); // Poor - Coverage
        assertTrue(extensions.contains(new RtpHeaderExtension(DEVICE_STATE_LOCAL_IDENTIFIER,
                new byte[] {0b00010010}))); // Good - Coverage
    }

    /**
     * Verify translation from raw RTP data into message/value pairs.
     */
    @SmallTest
    @Test
    public void testReceiveMessage() {
        testAllHeaderExtensionsSupported();

        ArraySet<RtpHeaderExtension> extensions = new ArraySet<>();
        extensions.add(new RtpHeaderExtension(CALL_STATE_LOCAL_IDENTIFIER,
                new byte[] {0b00100010})); // AMR_WB - Codec
        extensions.add((new RtpHeaderExtension(CALL_STATE_LOCAL_IDENTIFIER,
                new byte[] {0b00110010}))); // AMR_NB - Codec
        extensions.add((new RtpHeaderExtension(CALL_STATE_LOCAL_IDENTIFIER,
                new byte[] {0b00010010}))); // EVS - Codec
        extensions.add((new RtpHeaderExtension(CALL_STATE_LOCAL_IDENTIFIER,
                new byte[] {0b00010001}))); // LTE - RAT
        extensions.add((new RtpHeaderExtension(CALL_STATE_LOCAL_IDENTIFIER,
                new byte[] {0b00100001}))); // IWLAN - RAT
        extensions.add((new RtpHeaderExtension(CALL_STATE_LOCAL_IDENTIFIER,
                new byte[] {0b00110001}))); // NR - RAT
        extensions.add((new RtpHeaderExtension(DEVICE_STATE_LOCAL_IDENTIFIER,
                new byte[] {0b00000001}))); // Low - Battery
        extensions.add((new RtpHeaderExtension(DEVICE_STATE_LOCAL_IDENTIFIER,
                new byte[] {0b00010001}))); // Good - Battery
        extensions.add((new RtpHeaderExtension(DEVICE_STATE_LOCAL_IDENTIFIER,
                new byte[] {0b00110001}))); // Charging - Battery
        extensions.add((new RtpHeaderExtension(DEVICE_STATE_LOCAL_IDENTIFIER,
                new byte[] {0b00000010}))); // Poor - Coverage
        extensions.add((new RtpHeaderExtension(DEVICE_STATE_LOCAL_IDENTIFIER,
                new byte[] {0b00010010}))); // Good - Coverage
        mRtpTransport.onRtpHeaderExtensionsReceived(extensions);

        verify(mCallback).onMessagesReceived(mMessagesCaptor.capture());
        Set<Communicator.Message> messages = mMessagesCaptor.getValue();

        assertTrue(messages.contains(new Communicator.Message(Communicator.MESSAGE_CALL_AUDIO_CODEC,
                Communicator.AUDIO_CODEC_EVS)));
        assertTrue(messages.contains(new Communicator.Message(Communicator.MESSAGE_CALL_AUDIO_CODEC,
                Communicator.AUDIO_CODEC_AMR_WB)));
        assertTrue(messages.contains(new Communicator.Message(Communicator.MESSAGE_CALL_AUDIO_CODEC,
                Communicator.AUDIO_CODEC_AMR_NB)));
        assertTrue(messages.contains(
                new Communicator.Message(Communicator.MESSAGE_CALL_RADIO_ACCESS_TYPE,
                        Communicator.RADIO_ACCESS_TYPE_LTE)));
        assertTrue(messages.contains(
                new Communicator.Message(Communicator.MESSAGE_CALL_RADIO_ACCESS_TYPE,
                        Communicator.RADIO_ACCESS_TYPE_IWLAN)));
        assertTrue(messages.contains(
                new Communicator.Message(Communicator.MESSAGE_CALL_RADIO_ACCESS_TYPE,
                        Communicator.RADIO_ACCESS_TYPE_NR)));
        assertTrue(messages.contains(
                new Communicator.Message(Communicator.MESSAGE_DEVICE_BATTERY_STATE,
                        Communicator.BATTERY_STATE_LOW)));
        assertTrue(messages.contains(
                new Communicator.Message(Communicator.MESSAGE_DEVICE_BATTERY_STATE,
                        Communicator.BATTERY_STATE_GOOD)));
        assertTrue(messages.contains(
                new Communicator.Message(Communicator.MESSAGE_DEVICE_BATTERY_STATE,
                        Communicator.BATTERY_STATE_CHARGING)));
        assertTrue(messages.contains(
                new Communicator.Message(Communicator.MESSAGE_DEVICE_NETWORK_COVERAGE,
                        Communicator.COVERAGE_POOR)));
        assertTrue(messages.contains(
                new Communicator.Message(Communicator.MESSAGE_DEVICE_NETWORK_COVERAGE,
                        Communicator.COVERAGE_GOOD)));
    }

    /**
     * Verifies that unrecognized RTP header extensions are ignored.
     */
    @SmallTest
    @Test
    public void testIgnoreInvalidMessage() {
        testAllHeaderExtensionsSupported();

        ArraySet<RtpHeaderExtension> extensions = new ArraySet<>();
        // Invalid because it has an unrecognized local identifier
        extensions.add(new RtpHeaderExtension(1, new byte[] {0b01010101}));
        // Invalid because it has an unknown message type within a valid identifier.
        extensions.add(new RtpHeaderExtension(CALL_STATE_LOCAL_IDENTIFIER,
                new byte[] {0b00101111}));
        // Invalid because it has an unknown message value within a valid type.
        extensions.add(new RtpHeaderExtension(CALL_STATE_LOCAL_IDENTIFIER,
                new byte[] {0b01110010}));
        mRtpTransport.onRtpHeaderExtensionsReceived(extensions);
        verify(mCallback, never()).onMessagesReceived(any());
    }
}
