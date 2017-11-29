/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.internal.telephony.uicc.euicc.telephony;

import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import android.telephony.IccOpenLogicalChannelResponse;
import android.telephony.TelephonyManager;

import com.android.internal.telephony.uicc.euicc.asn1.Asn1Converter;

import org.mockito.Mockito;

/**
 * Utility to set up mocks for communication with eUICC through basic or logical channel.
 */
public final class UiccChannelMocker {
    private static final int LOGICAL_CHANNEL = 1;

    /** @return The mock channel number. */
    public static int mockOpenLogicalChannelResponse(
            TelephonyManager mockTm, int status, String responseHex) {
        IccOpenLogicalChannelResponse mockResponse =
                Mockito.mock(IccOpenLogicalChannelResponse.class);
        when(mockResponse.getChannel()).thenReturn(LOGICAL_CHANNEL);
        when(mockResponse.getStatus()).thenReturn(status);
        when(mockResponse.getSelectResponse()).thenReturn(Asn1Converter.hexToBytes(responseHex));
        when(mockTm.iccOpenLogicalChannel(anyString())).thenReturn(mockResponse);
        return LOGICAL_CHANNEL;
    }

    public static void mockSendToLogicalChannel(
            TelephonyManager mockTm, int channel, String response, String... moreResponse) {
        when(mockTm.iccTransmitApduLogicalChannel(
                        eq(channel), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyString()))
                .thenReturn(response, moreResponse);
    }

    public static void mockCloseLogicalChannel(TelephonyManager mockTm, int channel) {
        when(mockTm.iccCloseLogicalChannel(eq(channel))).thenReturn(true);
    }

    private UiccChannelMocker() {}
}
