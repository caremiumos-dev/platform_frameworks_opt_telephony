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

package com.android.internal.telephony.uicc.euicc;

import android.annotation.IntDef;
import android.annotation.Nullable;
import android.content.Context;
import android.os.Handler;
import android.service.euicc.EuiccProfileInfo;
import android.telephony.euicc.EuiccNotification;
import android.telephony.euicc.EuiccRat;

import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.uicc.IccCardStatus;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.asn1.Asn1Node;
import com.android.internal.telephony.uicc.euicc.async.AsyncResultCallback;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * This represents an eUICC card to perform profile management operations asynchronously. This class
 * includes methods defined by different versions of GSMA Spec (SGP.22).
 *
 * @hide
 */
public class EuiccCard extends UiccCard {
    /** Operations */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = "OPERATION_", value = {
            OPERATION_UNKNOWN,
            OPERATION_GET_PROFILE,
            OPERATION_PREPARE_DOWNLOAD,
            OPERATION_AUTHENTICATE_SERVER,
            OPERATION_CANCEL_SESSION,
            OPERATION_LOAD_BOUND_PROFILE_PACKAGE,
            OPERATION_LIST_NOTIFICATIONS,
            OPERATION_SET_NICKNAME,
            OPERATION_RETRIEVE_NOTIFICATION,
            OPERATION_REMOVE_NOTIFICATION_FROM_LIST,
            OPERATION_SWITCH_TO_PROFILE,
            OPERATION_DISABLE_PROFILE,
            OPERATION_DELETE_PROFILE,
            OPERATION_RESET_MEMORY,
            OPERATION_SET_DEFAULT_SMDP_ADDRESS,
    })
    public @interface OperationCode {}

    public static final int OPERATION_UNKNOWN = 0;
    public static final int OPERATION_GET_PROFILE = 1;
    public static final int OPERATION_PREPARE_DOWNLOAD = 2;
    public static final int OPERATION_AUTHENTICATE_SERVER = 3;
    public static final int OPERATION_CANCEL_SESSION = 4;
    public static final int OPERATION_LOAD_BOUND_PROFILE_PACKAGE = 5;
    public static final int OPERATION_LIST_NOTIFICATIONS = 6;
    public static final int OPERATION_SET_NICKNAME = 7;
    public static final int OPERATION_RETRIEVE_NOTIFICATION = 8;
    public static final int OPERATION_REMOVE_NOTIFICATION_FROM_LIST = 9;
    public static final int OPERATION_SWITCH_TO_PROFILE = 10;
    public static final int OPERATION_DISABLE_PROFILE = 11;
    public static final int OPERATION_DELETE_PROFILE = 12;
    public static final int OPERATION_RESET_MEMORY = 13;
    public static final int OPERATION_SET_DEFAULT_SMDP_ADDRESS = 14;

    /** Options for resetting eUICC memory */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = "RESET_OPTION_", flag = true, value = {
            RESET_OPTION_DELETE_OPERATIONAL_PROFILES,
            RESET_OPTION_DELETE_FIELD_LOADED_TEST_PROFILES,
            RESET_OPTION_RESET_DEFAULT_SMDP_ADDRESS,
    })
    public @interface ResetOption {}

    /** Deletes all operational profiles. */
    public static final int RESET_OPTION_DELETE_OPERATIONAL_PROFILES = 1;

    /** Deletes all field-loaded testing profiles. */
    public static final int RESET_OPTION_DELETE_FIELD_LOADED_TEST_PROFILES = 1 << 1;

    /** Resets the default SM-DP+ address. */
    public static final int RESET_OPTION_RESET_DEFAULT_SMDP_ADDRESS = 1 << 2;

    /** Reason for canceling a profile download session */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = "CANCEL_REASON_", value = {
            CANCEL_REASON_END_USER_REJECTED,
            CANCEL_REASON_POSTPONED,
            CANCEL_REASON_TIMEOUT,
            CANCEL_REASON_PPR_NOT_ALLOWED
    })
    public @interface CancelReason {}

    /**
     * The end user has rejected the download. The profile will be put into the error state and
     * cannot be downloaded again without the operator's change.
     */
    public static final int CANCEL_REASON_END_USER_REJECTED = 0;

    /** The download has been postponed and can be restarted later. */
    public static final int CANCEL_REASON_POSTPONED = 1;

    /** The download has been timed out and can be restarted later. */
    public static final int CANCEL_REASON_TIMEOUT = 2;

    /** Error code for success. */
    public static final int CODE_OK = 0;

    /**
     * The profile to be downloaded cannot be installed due to its policy rule is not allowed by
     * the RAT (Rules Authorisation Table) on the eUICC or by other installed profiles. The
     * download can be restarted later.
     */
    public static final int CANCEL_REASON_PPR_NOT_ALLOWED = 3;

    /**
     * Error code for profile not in expected state for the operation. This error includes the case
     * that profile is not in disabled state when being enabled or deleted, and that profile is not
     * in enabled state when being disabled.
     */
    public static final int CODE_PROFILE_NOT_IN_EXPECTED_STATE = 2;

    /** Error code for nothing to delete when resetting eUICC memory or removing notifications. */
    public static final int CODE_NOTHING_TO_DELETE = 1;

    /** Error code for no result available when retrieving notifications. */
    public static final int CODE_NO_RESULT_AVAILABLE = 1;

    /** Error code for incorrect input values when getting profiles or loading profile package. */
    public static final int CODE_INCORRECT_INPUT_VALUES = 1;

    public EuiccCard(Context c, CommandsInterface ci, IccCardStatus ics, int phoneId) {
        super(c, ci, ics, phoneId);
    }

    /** @return The GSMA SGP.22 spec version that this card supports. */
    public EuiccSpecVersion getSpecVersion() {
        // TODO: to be implemented.
        return null;
    }

    /**
     * Gets a map from ICCIDs to user-visible profiles.
     *
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 1.1.0 [GSMA SGP.22]
     */
    public void getAllProfiles(AsyncResultCallback<EuiccProfileInfo[]> callback, Handler handler) {
        // TODO: to be implemented.
    }

    /**
     * Gets a profile.
     *
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 1.1.0 [GSMA SGP.22]
     */
    public void getProfile(
            String iccid, AsyncResultCallback<EuiccProfileInfo> callback, Handler handler) {
        // TODO: to be implemented.
    }

    /**
     * Disables a profile of the given {@code iccid}.
     *
     * @param refresh Whether sending the REFRESH command to modem.
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 1.1.0 [GSMA SGP.22]
     */
    public void disableProfile(
            String iccid, boolean refresh, AsyncResultCallback<Void> callback, Handler handler) {
        // TODO: to be implemented.
    }

    /**
     * Switches from the current profile to another profile. The current profile will be disabled
     * and the specified profile will be enabled.
     *
     * @param refresh Whether sending the REFRESH command to modem.
     * @param callback The callback to get the EuiccProfile enabled.
     * @param handler The handler to run the callback.
     * @since 1.1.0 [GSMA SGP.22]
     */
    public void switchToProfile(
            String iccid,
            boolean refresh,
            AsyncResultCallback<Void> callback,
            Handler handler) {
        // TODO: to be implemented.
    }

    /**
     * Gets the EID of the eUICC.
     *
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 1.1.0 [GSMA SGP.22]
     */
    public void getEid(AsyncResultCallback<String> callback, Handler handler) {
        // TODO: to be implemented.
    }

    /**
     * Sets the nickname of a profile.
     *
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 1.1.0 [GSMA SGP.22]
     */
    public void setNickname(
            String iccid, String nickname, AsyncResultCallback<Void> callback, Handler handler) {
        // TODO: to be implemented.
    }

    /**
     * Deletes a profile from eUICC.
     *
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 1.1.0 [GSMA SGP.22]
     */
    public void deleteProfile(String iccid, AsyncResultCallback<Void> callback, Handler handler) {
        // TODO: to be implemented.
    }

    /**
     * Resets the eUICC memory (i.e., remove all profiles).
     *
     * @param options Bits of the options of resetting which parts of the eUICC memory. You can
     *     check out subclasses to see details.
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 1.1.0 [GSMA SGP.22]
     */
    public void resetMemory(
            @ResetOption int options, AsyncResultCallback<Void> callback, Handler handler) {
        // TODO: to be implemented.
    }

    /**
     * Gets the default SM-DP+ address from eUICC.
     *
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 2.0.0 [GSMA SGP.22]
     */
    public void getDefaultSmdpAddress(AsyncResultCallback<String> callback, Handler handler) {
        // TODO: to be implemented.
    }

    /**
     * Gets the SM-DS address from eUICC.
     *
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 2.0.0 [GSMA SGP.22]
     */
    public void getSmdsAddress(AsyncResultCallback<String> callback, Handler handler) {
        // TODO: to be implemented.
    }

    /**
     * Sets the default SM-DP+ address of eUICC.
     *
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 2.0.0 [GSMA SGP.22]
     */
    public void setDefaultSmdpAddress(
            String defaultSmdpAddress, AsyncResultCallback<Void> callback, Handler handler) {
        // TODO: to be implemented.
    }

    /**
     * Gets Rules Authorisation Table.
     *
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 2.0.0 [GSMA SGP.22]
     */
    public void getRat(AsyncResultCallback<EuiccRat> callback, Handler handler) {
        // TODO: to be implemented.
    }

    /**
     * Gets the eUICC challenge for new profile downloading.
     *
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 2.0.0 [GSMA SGP.22]
     */
    public void getEuiccChallenge(AsyncResultCallback<byte[]> callback, Handler handler) {
        // TODO: to be implemented.
    }

    /**
     * Gets the eUICC info1 for new profile downloading.
     *
     * @param callback The callback to get the result, which represents an {@code EUICCInfo1}
     *     defined in GSMA RSP v2.0+.
     * @param handler The handler to run the callback.
     * @since 2.0.0 [GSMA SGP.22]
     */
    public void getEuiccInfo1(AsyncResultCallback<Asn1Node> callback, Handler handler) {
        // TODO: to be implemented.
    }

    /**
     * Gets the eUICC info2 for new profile downloading.
     *
     * @param callback The callback to get the result, which represents an {@code EUICCInfo2}
     *     defined in GSMA RSP v2.0+.
     * @param handler The handler to run the callback.
     * @since 2.0.0 [GSMA SGP.22]
     */
    public void getEuiccInfo2(AsyncResultCallback<Asn1Node> callback, Handler handler) {
        // TODO: to be implemented.
    }

    /**
     * Authenticates the SM-DP+ server by the eUICC. The parameters {@code serverSigned1}, {@code
     * serverSignature1}, {@code euiccCiPkIdToBeUsed}, and {@code serverCertificate} are the ASN.1
     * data returned by SM-DP+ server.
     *
     * @param matchingId The activation code or an empty string.
     * @param callback The callback to get the result, which represents an {@code
     *     AuthenticateServerResponse} defined in GSMA RSP v2.0+.
     * @param handler The handler to run the callback.
     * @since 2.0.0 [GSMA SGP.22]
     */
    public void authenticateServer(
            String matchingId,
            Asn1Node serverSigned1,
            Asn1Node serverSignature1,
            Asn1Node euiccCiPkIdToBeUsed,
            Asn1Node serverCertificate,
            AsyncResultCallback<Asn1Node> callback,
            Handler handler) {
        // TODO: to be implemented.
    }

    /**
     * Prepares the profile download request sent to SM-DP+. The parameters {@code smdpSigned2},
     * {@code smdpSignature2}, and {@code smdpCertificate} are the ASN.1 data returned by SM-DP+
     * server.
     *
     * @param hashCc The hash of confirmation code. It can be null if there is no confirmation code
     *     required.
     * @param callback The callback to get the result, which represents an {@code
     *     PrepareDownloadResponse} defined in GSMA RSP v2.0+.
     * @param handler The handler to run the callback.
     * @since 2.0.0 [GSMA SGP.22]
     */
    public void prepareDownload(
            @Nullable byte[] hashCc,
            Asn1Node smdpSigned2,
            Asn1Node smdpSignature2,
            Asn1Node smdpCertificate,
            AsyncResultCallback<Asn1Node> callback,
            Handler handler) {
        // TODO: to be implemented.
    }

    /**
     * Loads a downloaded bound profile package onto the eUICC.
     *
     * @param boundProfilePackage The Bound Profile Package data returned by SM-DP+ server.
     * @param callback The callback to get the result, which represents an {@code
     *     LoadBoundProfilePackageResponse} defined in GSMA RSP v2.0+.
     * @param handler The handler to run the callback.
     * @since 2.0.0 [GSMA SGP.22]
     */
    public void loadBoundProfilePackage(
            Asn1Node boundProfilePackage, AsyncResultCallback<Asn1Node> callback, Handler handler) {
        // TODO: to be implemented.
    }

    /**
     * Cancels the current profile download session.
     *
     * @param transactionId The transaction ID returned by SM-DP+ server.
     * @param callback The callback to get the result, which represents an {@code
     *     CancelSessionResponse} defined in GSMA RSP v2.0+.
     * @param handler The handler to run the callback.
     * @since 2.0.0 [GSMA SGP.22]
     */
    public void cancelSession(
            byte[] transactionId,
            @CancelReason int reason,
            AsyncResultCallback<Asn1Node> callback,
            Handler handler) {
        // TODO: to be implemented.
    }

    /**
     * Lists all notifications of the given {@code notificationEvents}.
     *
     * @param events Bits of the event types ({@link EuiccNotification.Event}) to list.
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 2.0.0 [GSMA SGP.22]
     */
    public void listNotifications(
            @EuiccNotification.Event int events,
            AsyncResultCallback<EuiccNotification[]> callback,
            Handler handler) {
        // TODO: to be implemented.
    }

    /**
     * Retrieves contents of all notification of the given {@code notificationEvents}.
     *
     * @param events Bits of the event types ({@link EuiccNotification.Event}) to list.
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 2.0.0 [GSMA SGP.22]
     */
    public void retrieveNotificationList(
            @EuiccNotification.Event int events,
            AsyncResultCallback<EuiccNotification[]> callback,
            Handler handler) {
        // TODO: to be implemented.
    }

    /**
     * Retrieves the content of a notification of the given {@code seqNumber}.
     *
     * @param seqNumber The sequence number of the notification.
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 2.0.0 [GSMA SGP.22]
     */
    public void retrieveNotification(
            int seqNumber, AsyncResultCallback<EuiccNotification> callback, Handler handler) {
        // TODO: to be implemented.
    }

    /**
     * Removes a notification from eUICC.
     *
     * @param seqNumber The sequence number of the notification.
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 2.0.0 [GSMA SGP.22]
     */
    public void removeNotificationFromList(
            int seqNumber, AsyncResultCallback<Void> callback, Handler handler) {
        // TODO: to be implemented.
    }
}
