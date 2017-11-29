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

package com.android.internal.telephony.uicc.euicc.card;

import android.annotation.IntDef;
import android.annotation.Nullable;
import android.os.Handler;

import com.android.internal.telephony.uicc.euicc.asn1.Asn1Node;
import com.android.internal.telephony.uicc.euicc.async.AsyncResultCallback;
import com.android.internal.telephony.uicc.euicc.telephony.UiccChannelException;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * The exception which is thrown when an error is returned in a successfully executed APDU command
 * sent to eUICC. This exception means the response status is no-error (0x9000), but the action is
 * failed due to eUICC specific logic, or we cannot parse the response from eUICC. This is different
 * from {@link UiccChannelException} which means a failure of opening a logical channel or an error
 * status (other than 0x9000) of the response.
 *
 * @hide
 */
public class EuiccCardErrorException extends EuiccCardException {

    // The eUICC service error code of an EuiccCardErrorException is from 0x80000 to 0x8FFFF.
    private static final int EUICC_SERVICE_ERROR_CODE_BASE = 0x80000;
    private static final int EUICC_SERVICE_ERROR_CODE_OPERATION_BITS_SHIFT = 8;

    // The following enums are the error code defined by the GSMA SGP.22 spec.

    /**
     * Error code of {@link EuiccCard#getAllProfiles(AsyncResultCallback, Handler)} and {@link
     * EuiccCard#getProfile(String, AsyncResultCallback, Handler)}.
     *
     * @since 1.1.0 [GSMA SGP.22] (ProfileInfoListError)
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
        GetProfileCode.INCORRECT_INPUT_VALUES,
        GetProfileCode.UNDEFINED_ERROR,
    })
    public @interface GetProfileCode {
        int INCORRECT_INPUT_VALUES = 1;
        int UNDEFINED_ERROR = 127;
    }

    /**
     * Error code of {@link EuiccCard#prepareDownload(byte[], Asn1Node, Asn1Node, Asn1Node,
     * AsyncResultCallback, Handler)}.
     *
     * @since 2.0.0 [GSMA SGP.22] (DownloadErrorCode)
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
        PrepareDownloadCode.INVALID_CERTIFICATE,
        PrepareDownloadCode.INVALID_SIGNATURE,
        PrepareDownloadCode.UNSUPPORTED_CURVE,
        PrepareDownloadCode.NO_SESSION_CONTEXT,
        PrepareDownloadCode.INVALID_TRANSACTION_ID,
        PrepareDownloadCode.UNDEFINED_ERROR
    })
    public @interface PrepareDownloadCode {
        int INVALID_CERTIFICATE = 1;
        int INVALID_SIGNATURE = 2;
        int UNSUPPORTED_CURVE = 3;
        int NO_SESSION_CONTEXT = 4;
        int INVALID_TRANSACTION_ID = 5;
        int UNDEFINED_ERROR = 127;
    }

    /**
     * Error code of {@link EuiccCard#authenticateServer(String, Asn1Node, Asn1Node, Asn1Node,
     * Asn1Node, AsyncResultCallback, Handler)}.
     *
     * @since 2.0.0 [GSMA SGP.22] (AuthenticateErrorCode)
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
        AuthenticateServerCode.INVALID_CERTIFICATE,
        AuthenticateServerCode.INVALID_SIGNATURE,
        AuthenticateServerCode.UNSUPPORTED_CURVE,
        AuthenticateServerCode.NO_SESSION_CONTEXT,
        AuthenticateServerCode.INVALID_OID,
        AuthenticateServerCode.EUICC_CHALLENGE_MISMATCH,
        AuthenticateServerCode.CI_PK_UNKNOWN,
        AuthenticateServerCode.UNDEFINED_ERROR
    })
    public @interface AuthenticateServerCode {
        int INVALID_CERTIFICATE = 1;
        int INVALID_SIGNATURE = 2;
        int UNSUPPORTED_CURVE = 3;
        int NO_SESSION_CONTEXT = 4;
        int INVALID_OID = 5;
        int EUICC_CHALLENGE_MISMATCH = 6;
        int CI_PK_UNKNOWN = 7;
        int UNDEFINED_ERROR = 127;
    }

    /**
     * Error code of {@link EuiccCard#cancelSession(byte[], int, AsyncResultCallback, Handler)}.
     *
     * @since 2.0.0 [GSMA SGP.22] (CancelSessionResponse.cancelSessionResponseError)
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({CancelSessionCode.INVALID_TRANSACTION_ID, CancelSessionCode.UNDEFINED_ERROR})
    public @interface CancelSessionCode {
        int INVALID_TRANSACTION_ID = 5;
        int UNDEFINED_ERROR = 127;
    }

    /**
     * Error code of {@link EuiccCard#loadBoundProfilePackage(Asn1Node, AsyncResultCallback,
     * Handler)}.
     *
     * @since 2.0.0 [GSMA SGP.22] (ErrorReason)
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
        LoadBoundProfilePackageCode.INCORRECT_INPUT_VALUES,
        LoadBoundProfilePackageCode.INVALID_SIGNATURE,
        LoadBoundProfilePackageCode.INVALID_TRANSACTION_ID,
        LoadBoundProfilePackageCode.UNSUPPORTED_CRT_VALUES,
        LoadBoundProfilePackageCode.UNSUPPORTED_REMOTE_OPERATION_TYPE,
        LoadBoundProfilePackageCode.UNSUPPORTED_PROFILE_CLASS,
        LoadBoundProfilePackageCode.SCP03T_STRUCTURE_ERROR,
        LoadBoundProfilePackageCode.SCP03T_SECURITY_ERROR,
        LoadBoundProfilePackageCode.INSTALL_FAILED_DUE_TO_ICCID_ALREADY_EXISTS_ON_EUICC,
        LoadBoundProfilePackageCode.INSTALL_FAILED_DUE_TO_INSUFFICIENT_MEMORY_FOR_PROFILE,
        LoadBoundProfilePackageCode.INSTALL_FAILED_DUE_TO_INTERRUPTION,
        LoadBoundProfilePackageCode.INSTALL_FAILED_DUE_TO_PE_PROCESSING_ERROR,
        LoadBoundProfilePackageCode.INSTALL_FAILED_DUE_TO_ICCID_MISMATCH,
        LoadBoundProfilePackageCode.TEST_PROFILE_INSTALL_FAILED_DUE_TO_INVALID_NAA_KEY,
        LoadBoundProfilePackageCode.PPR_NOT_ALLOWED,
        LoadBoundProfilePackageCode.INSTALL_FAILED_DUE_TO_UNKNOWN_ERROR,
    })
    public @interface LoadBoundProfilePackageCode {
        int INCORRECT_INPUT_VALUES = 1;
        int INVALID_SIGNATURE = 2;
        int INVALID_TRANSACTION_ID = 3;
        int UNSUPPORTED_CRT_VALUES = 4;
        int UNSUPPORTED_REMOTE_OPERATION_TYPE = 5;
        int UNSUPPORTED_PROFILE_CLASS = 6;
        int SCP03T_STRUCTURE_ERROR = 7;
        int SCP03T_SECURITY_ERROR = 8;
        int INSTALL_FAILED_DUE_TO_ICCID_ALREADY_EXISTS_ON_EUICC = 9;
        int INSTALL_FAILED_DUE_TO_INSUFFICIENT_MEMORY_FOR_PROFILE = 10;
        int INSTALL_FAILED_DUE_TO_INTERRUPTION = 11;
        int INSTALL_FAILED_DUE_TO_PE_PROCESSING_ERROR = 12;
        int INSTALL_FAILED_DUE_TO_ICCID_MISMATCH = 13;
        int TEST_PROFILE_INSTALL_FAILED_DUE_TO_INVALID_NAA_KEY = 14;
        int PPR_NOT_ALLOWED = 15;
        int INSTALL_FAILED_DUE_TO_UNKNOWN_ERROR = 127;
    }

    /**
     * Error code of {@link EuiccCard#listNotifications(int, AsyncResultCallback, Handler)}.
     *
     * @since 2.0.0 [GSMA SGP.22] (ListNotificationResponse.listNotificationsResultError)
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({ListNotificationsCode.UNDEFINED_ERROR})
    public @interface ListNotificationsCode {
        int UNDEFINED_ERROR = 127;
    }

    /**
     * Error code of {@link EuiccCard#setNickname(String, String, AsyncResultCallback, Handler)}.
     *
     * @since 1.1.0 [GSMA SGP.22] (SetNicknameResponse.setNicknameResult)
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({SetNicknameCode.OK, SetNicknameCode.ICCID_NOT_FOUND, SetNicknameCode.UNDEFINED_ERROR})
    public @interface SetNicknameCode {
        int OK = 0;
        int ICCID_NOT_FOUND = 1;
        int UNDEFINED_ERROR = 127;
    }

    /**
     * Error code of {@link EuiccCard#retrieveNotification(int, AsyncResultCallback, Handler)} and
     * {@link EuiccCard#retrieveNotificationList(int, AsyncResultCallback, Handler)}.
     *
     * @since 2.0.0 [GSMA SGP.22] (RetrieveNotificationsListResponse.notificationsListResultError)
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
        RetrieveNotificationCode.NO_RESULT_AVAILABLE,
        RetrieveNotificationCode.UNDEFINED_ERROR
    })
    public @interface RetrieveNotificationCode {
        int NO_RESULT_AVAILABLE = 1;
        int UNDEFINED_ERROR = 127;
    }

    /**
     * Error code of {@link EuiccCard#removeNotificationFromList(int, AsyncResultCallback,
     * Handler)}.
     *
     * @since 2.0.0 [GSMA SGP.22] (NotificationSentResponse.deleteNotificationStatus)
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
        RemoveNotificationFromListCode.OK,
        RemoveNotificationFromListCode.NOTHING_TO_DELETE,
        RemoveNotificationFromListCode.UNDEFINED_ERROR
    })
    public @interface RemoveNotificationFromListCode {
        int OK = 0;
        int NOTHING_TO_DELETE = 1;
        int UNDEFINED_ERROR = 127;
    }

    /**
     * Error code of {@link EuiccCard#switchToProfile(String, boolean, AsyncResultCallback,
     * Handler)}.
     *
     * @since 1.1.0 [GSMA SGP.22] (EnableProfileResponse.enableResult)
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
        SwitchToProfileCode.OK,
        SwitchToProfileCode.ICCID_OR_AID_NOT_FOUND,
        SwitchToProfileCode.PROFILE_NOT_IN_DISABLED_STATE,
        SwitchToProfileCode.DISALLOWED_BY_POLICY,
        SwitchToProfileCode.WRONG_PROFILE_REENABLING,
        SwitchToProfileCode.UNDEFINED_ERROR,
    })
    public @interface SwitchToProfileCode {
        int OK = 0;
        int ICCID_OR_AID_NOT_FOUND = 1;
        int PROFILE_NOT_IN_DISABLED_STATE = 2;
        int DISALLOWED_BY_POLICY = 3;
        int WRONG_PROFILE_REENABLING = 4;
        int UNDEFINED_ERROR = 127;
    }

    /**
     * Error code of {@link EuiccCard#disableProfile(String, boolean, AsyncResultCallback,
     * Handler)}.
     *
     * @since 1.1.0 [GSMA SGP.22] (DisableProfileResponse.disableResult)
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
        DisableProfileCode.OK,
        DisableProfileCode.ICCID_OR_AID_NOT_FOUND,
        DisableProfileCode.PROFILE_NOT_IN_ENABLED_STATE,
        DisableProfileCode.DISALLOWED_BY_POLICY,
        DisableProfileCode.UNDEFINED_ERROR,
    })
    public @interface DisableProfileCode {
        int OK = 0;
        int ICCID_OR_AID_NOT_FOUND = 1;
        int PROFILE_NOT_IN_ENABLED_STATE = 2;
        int DISALLOWED_BY_POLICY = 3;
        int UNDEFINED_ERROR = 127;
    }

    /**
     * Error code of {@link EuiccCard#deleteProfile(String, AsyncResultCallback, Handler)}.
     *
     * @since 1.1.0 [GSMA SGP.22] (DeleteProfileResponse.deleteResult)
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
        DeleteProfileCode.OK,
        DeleteProfileCode.ICCID_OR_AID_NOT_FOUND,
        DeleteProfileCode.PROFILE_NOT_IN_DISABLED_STATE,
        DeleteProfileCode.DISALLOWED_BY_POLICY,
        DeleteProfileCode.UNDEFINED_ERROR
    })
    public @interface DeleteProfileCode {
        int OK = 0;
        int ICCID_OR_AID_NOT_FOUND = 1;
        int PROFILE_NOT_IN_DISABLED_STATE = 2;
        int DISALLOWED_BY_POLICY = 3;
        int UNDEFINED_ERROR = 127;
    }

    /**
     * Error code of {@link EuiccCard#resetMemory(int, AsyncResultCallback, Handler)}.
     *
     * @since 2.0.0 [GSMA SGP.22] (EuiccMemoryResetResponse.resetResult)
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
        ResetMemoryCode.OK,
        ResetMemoryCode.NOTHING_TO_DELETE,
        ResetMemoryCode.UNDEFINED_ERROR
    })
    public @interface ResetMemoryCode {
        int OK = 0;
        int NOTHING_TO_DELETE = 1;
        int UNDEFINED_ERROR = 127;
    }

    /**
     * Error code of {@link EuiccCard#setDefaultSmdpAddress(String, AsyncResultCallback, Handler)}.
     *
     * @since 2.0.0 [GSMA SGP.22] (SetDefaultDpAddressResponse.setDefaultDpAddressResult)
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({SetDefaultSmdpAddressCode.OK, SetDefaultSmdpAddressCode.UNDEFINED_ERROR})
    public @interface SetDefaultSmdpAddressCode {
        int OK = 0;
        int UNDEFINED_ERROR = 127;
    }

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
