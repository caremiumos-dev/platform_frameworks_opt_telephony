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

package com.android.internal.telephony.uicc.euicc.apdu;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.annotation.Nullable;
import android.os.AsyncResult;
import android.os.Message;
import android.telephony.IccOpenLogicalChannelResponse;

import com.android.internal.telephony.uicc.IccIoResult;
import com.android.internal.telephony.uicc.IccUtils;
import com.android.internal.telephony.uicc.UiccProfile;

import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;

/** Utility to set up mocks for communication with UICC through logical channel. */
public final class LogicalChannelMocker {
    private static final int LOGICAL_CHANNEL = 1;

    /**
     * @param responseObject Can be either a string or an exception.
     * @return The mock channel number.
     */
    public static int mockOpenLogicalChannelResponse(
            UiccProfile mockProfile, int status, @Nullable Object responseObject) {
        boolean isException = responseObject instanceof Throwable;
        IccOpenLogicalChannelResponse mockResponse =
                isException ? null : mock(IccOpenLogicalChannelResponse.class);
        if (mockResponse != null) {
            when(mockResponse.getChannel()).thenReturn(LOGICAL_CHANNEL);
            when(mockResponse.getStatus()).thenReturn(status);
            when(mockResponse.getSelectResponse())
                    .thenReturn(IccUtils.hexStringToBytes(responseObject.toString()));
        }
        Throwable exception = isException ? (Throwable) responseObject : null;

        ArgumentCaptor<Message> response = ArgumentCaptor.forClass(Message.class);
        doAnswer(
                (Answer<Void>) invocation -> {
                    Message msg = response.getValue();
                    msg.obj = new AsyncResult(msg.obj, mockResponse, exception);
                    msg.sendToTarget();
                    return null;
                })
                .when(mockProfile)
                .iccOpenLogicalChannel(anyString(), anyInt(), response.capture());
        return LOGICAL_CHANNEL;
    }

    /**
     * @param responseObjects Can be either a string or an exception. For string, the last 4
     *         digits are the status (sw1, sw1).
     */
    public static void mockSendToLogicalChannel(
            UiccProfile mockProfile, int channel, Object... responseObjects) {
        for (Object responseObject : responseObjects) {
            boolean isException = responseObject instanceof Throwable;
            int sw1 = 0;
            int sw2 = 0;
            String hex = responseObject.toString();
            if (!isException) {
                hex = hex.substring(hex.length() - 4);
                sw1 = Integer.parseInt(hex.substring(hex.length() - 4, hex.length() - 2), 16);
                sw2 = Integer.parseInt(hex.substring(hex.length() - 2), 16);
            }
            IccIoResult result = isException ? null : new IccIoResult(sw1, sw2, hex);
            Throwable exception = isException ? (Throwable) responseObject : null;

            ArgumentCaptor<Message> response = ArgumentCaptor.forClass(Message.class);
            doAnswer(
                    (Answer<Void>) invocation -> {
                        Message msg = response.getValue();
                        msg.obj = new AsyncResult(msg.obj, result, exception);
                        msg.sendToTarget();
                        return null;
                    })
                    .when(mockProfile)
                    .iccTransmitApduLogicalChannel(
                            eq(channel),
                            anyInt(),
                            anyInt(),
                            anyInt(),
                            anyInt(),
                            anyInt(),
                            anyString(),
                            response.capture());
        }
    }

    public static void mockCloseLogicalChannel(UiccProfile mockProfile, int channel) {
        ArgumentCaptor<Message> response = ArgumentCaptor.forClass(Message.class);
        doAnswer(
                (Answer<Void>) invocation -> {
                    Message msg = response.getValue();
                    msg.obj = new AsyncResult(msg.obj, null, null);
                    msg.sendToTarget();
                    return null;
                })
                .when(mockProfile)
                .iccCloseLogicalChannel(eq(channel), response.capture());
    }

    private LogicalChannelMocker() {
    }
}
