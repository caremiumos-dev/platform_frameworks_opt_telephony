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

package com.android.euicc.card;

import android.os.Handler;
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import com.android.euicc.asn1.Asn1Node;
import com.android.euicc.async.AsyncResultCallback;
import com.android.euicc.telephony.UiccChannelException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * The exception which is thrown when an error is returned in a successfully executed APDU command
 * sent to eUICC. This exception means the response status is no-error (0x9000), but the action is
 * failed due to eUICC specific logic, or we cannot parse the response from eUICC. This is different
 * from {@link UiccChannelException} which means a failure of opening a logical channel or an error
 * status (other than 0x9000) of the response.
 */
public class EuiccCardErrorException extends EuiccCardException {

    /** The operation code. */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            OperationCode.UNKNOWN,
            OperationCode.GET_PROFILE,
            OperationCode.PREPARE_DOWNLOAD,
            OperationCode.AUTHENTICATE_SERVER,
            OperationCode.CANCEL_SESSION,
            OperationCode.LOAD_BOUND_PROFILE_PACKAGE,
            OperationCode.LIST_NOTIFICATIONS,
            OperationCode.SET_NICKNAME,
            OperationCode.RETRIEVE_NOTIFICATION,
            OperationCode.REMOVE_NOTIFICATION_FROM_LIST,
            OperationCode.SWITCH_TO_PROFILE,
            OperationCode.DISABLE_PROFILE,
            OperationCode.DELETE_PROFILE,
            OperationCode.RESET_MEMORY,
            OperationCode.SET_DEFAULT_SMDP_ADDRESS,
    })
    public @interface OperationCode {
        int UNKNOWN = 0;
        int GET_PROFILE = 1;
        int PREPARE_DOWNLOAD = 2;
        int AUTHENTICATE_SERVER = 3;
        int CANCEL_SESSION = 4;
        int LOAD_BOUND_PROFILE_PACKAGE = 5;
        int LIST_NOTIFICATIONS = 6;
        int SET_NICKNAME = 7;
        int RETRIEVE_NOTIFICATION = 8;
        int REMOVE_NOTIFICATION_FROM_LIST = 9;
        int SWITCH_TO_PROFILE = 10;
        int DISABLE_PROFILE = 11;
        int DELETE_PROFILE = 12;
        int RESET_MEMORY = 13;
        int SET_DEFAULT_SMDP_ADDRESS = 14;
    }

    private final @OperationCode int mOperationCode;
    private final int mErrorCode;
    private final @Nullable Asn1Node mErrorDetails;

    public final int getEuiccServiceErrorCode() {
        return EUICC_SERVICE_ERROR_CODE_BASE
                + (mOperationCode << EUICC_SERVICE_ERROR_CODE_OPERATION_BITS_SHIFT)
                + mErrorCode;
    }

    /**
     * Creates an exception with an error code in the response of an APDU command.
     *
     * @param errorCode The meaning of the code depends on each APDU command. It should always be
     *     non-negative.
     */
    public EuiccCardErrorException(@OperationCode int operationCode, int errorCode) {
        mOperationCode = operationCode;
        mErrorCode = errorCode;
        mErrorDetails = null;
    }

    /**
     * Creates an exception with an error code and the error details in the response of an APDU
     * command.
     *
     * @param errorCode The meaning of the code depends on each APDU command. It should always be
     *     non-negative.
     * @param errorDetails The content of the details depends on each APDU command.
     */
    public EuiccCardErrorException(
            @OperationCode int operationCode, int errorCode, @Nullable Asn1Node errorDetails) {
        mOperationCode = operationCode;
        mErrorCode = errorCode;
        mErrorDetails = errorDetails;
    }

    /** @return The error code. The meaning of the code depends on each APDU command. */
    public int getErrorCode() {
        return mErrorCode;
    }

    /** @return The operation code. */
    public int getOperationCode() {
        return mOperationCode;
    }

    /** @return The error details. The meaning of the details depends on each APDU command. */
    @Nullable
    public Asn1Node getErrorDetails() {
        return mErrorDetails;
    }

    @Override
    public String getMessage() {
        return super.getMessage()
                + " (mOperatorCode="
                + mOperationCode
                + ", mErrorCode="
                + mErrorCode
                + ", errorDetails="
                + (mErrorDetails == null ? "null" : mErrorDetails.toHex())
                + ")";
    }
}
