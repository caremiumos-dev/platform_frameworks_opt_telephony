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

package com.android.internal.telephony.uicc.euicc.apdu;

import android.os.AsyncResult;
import android.os.Message;
import android.telephony.Rlog;

import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.uicc.IccIoResult;
import com.android.internal.telephony.uicc.euicc.async.AsyncMessageInvocation;

/**
 * Invokes {@link CommandsInterface#iccTransmitApduLogicalChannel(int, int, int, int, int, int,
 * String, Message)}. This takes an APDU command as the input and return the response. The status of
 * returned response will be 0x6F00 if any error happens. No exception will be returned to the
 * result callback.
 *
 * @hide
 */
public class TransmitApduLogicalChannelInvocation
        extends AsyncMessageInvocation<ApduCommand, ApduResponse> {
    private static final String LOG_TAG = "TransApdu";

    private static void loge(String msg) {
        Rlog.e(LOG_TAG, msg);
    }

    private static void logv(String msg) {
        Rlog.v(LOG_TAG, msg);
    }

    private final CommandsInterface mCi;

    TransmitApduLogicalChannelInvocation(CommandsInterface ci) {
        mCi = ci;
    }

    @Override
    protected void sendRequestMessage(ApduCommand command, Message msg) {
        logv("Send: " + command);
        mCi.iccTransmitApduLogicalChannel(
                command.channel,
                command.cla | command.channel,
                command.ins,
                command.p1,
                command.p2,
                command.p3,
                command.cmdHex,
                msg);
    }

    @Override
    protected ApduResponse parseResult(AsyncResult ar) {
        ApduResponse response = new ApduResponse();
        if (ar.exception == null && ar.result != null) {
            IccIoResult result = (IccIoResult) ar.result;
            response.payload = result.payload;
            response.sw1 = result.sw1;
            response.sw2 = result.sw2;
        } else {
            if (ar.result == null) {
                loge("Empty response");
            } else if (ar.exception instanceof CommandException) {
                loge("CommandException: " + ar.exception);
            } else {
                loge("Unknown exception");
            }
            response.sw1 = 0x6F;
        }

        logv("Response: " + response);
        return response;
    }
}
