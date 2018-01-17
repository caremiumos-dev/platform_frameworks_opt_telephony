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
import android.os.Looper;
import android.os.Message;
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import com.android.euicc.EuiccApplication;
import com.android.euicc.asn1.Asn1Node;
import com.android.euicc.asn1.InvalidAsn1DataException;
import com.android.euicc.asn1.TagNotFoundException;
import com.android.euicc.async.AsyncResultCallback;
import com.android.euicc.async.AsyncResultHelper;
import com.android.euicc.data.EuiccNotification;
import com.android.euicc.data.EuiccNotification.Event;
import com.android.euicc.data.EuiccProfile;
import com.android.euicc.data.EuiccRat;
import com.android.euicc.data.EuiccSpecVersion;
import com.android.euicc.storage.EuiccPrefs;
import com.android.euicc.telephony.UiccChannelException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

/**
 * This is the abstraction of an eUICC card to maintain the states of an eUICC in Android and
 * performs profile management operations synchronously. This interface includes methods defined by
 * different versions of GSMA Spec (SGP.22). Public methods are asynchronous.
 *
 * <p>When any changes causing the eUICC unavailable or the supported eUICC version updated, the
 * {@link #close()} method will be called, and then all eUICC operation methods will throw an {@link
 * EuiccCardException} on being called.
 *
 * <p>A subclass of this interface implements a specific version of GSMA spec by overriding those
 * synchronous methods. These synchronous methods are guaranteed to be called in the same thread
 * sequentially. We don't support concurrent access to an eUICC, and subclasses are supposed to be
 * thread-safe. {@link UnsupportedOperationException} should be thrown if the subclass doesn't
 * support a method.
 */
public abstract class EuiccCard implements Handler.Callback {

    /** @return The GSMA SGP.22 spec version that this card supports. */
    public EuiccSpecVersion getCardVersion() {
        return mCardVersion;
    }

    /**
     * Gets a map from ICCIDs to user-visible profiles.
     *
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since SGP.22 v1.1.0
     */
    public final void getAllProfiles(
            AsyncResultCallback<EuiccProfile[]> callback, Handler handler) {
    }

    /**
     * Gets a profile.
     *
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 1.1.0 [GSMA SGP.22]
     */
    public final void getProfile(
            String iccid, AsyncResultCallback<EuiccProfile> callback, Handler handler) {
    }

    /**
     * Disables a profile of the given {@code iccid}.
     *
     * @param refresh Whether sending the REFRESH command to modem.
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 1.1.0 [GSMA SGP.22]
     */
    public final void disableProfile(
            String iccid, boolean refresh, AsyncResultCallback<Void> callback, Handler handler) {
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
    public final void switchToProfile(
            String iccid,
            boolean refresh,
            AsyncResultCallback<EuiccProfile> callback,
            Handler handler) {
    }

    /**
     * Gets the EID of the eUICC.
     *
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 1.1.0 [GSMA SGP.22]
     */
    public final void getEid(AsyncResultCallback<String> callback, Handler handler) {
    }

    /**
     * Sets the nickname of a profile.
     *
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 1.1.0 [GSMA SGP.22]
     */
    public final void setNickname(
            String iccid, String nickname, AsyncResultCallback<Void> callback, Handler handler) {
    }

    /**
     * Deletes a profile from eUICC.
     *
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 1.1.0 [GSMA SGP.22]
     */
    public final void deleteProfile(
            String iccid, AsyncResultCallback<Void> callback, Handler handler) {
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
    public final void resetMemory(
            @ResetOption int options, AsyncResultCallback<Void> callback, Handler handler) {
    }

    /**
     * Gets the default SM-DP+ address from eUICC.
     *
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 2.0.0 [GSMA SGP.22]
     */
    public final void getDefaultSmdpAddress(AsyncResultCallback<String> callback, Handler handler) {
    }

    /**
     * Gets the SM-DS address from eUICC.
     *
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 2.0.0 [GSMA SGP.22]
     */
    public final void getSmdsAddress(AsyncResultCallback<String> callback, Handler handler) {
    }

    /**
     * Sets the default SM-DP+ address of eUICC.
     *
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 2.0.0 [GSMA SGP.22]
     */
    public final void setDefaultSmdpAddress(
            String defaultSmdpAddress, AsyncResultCallback<Void> callback, Handler handler) {
    }

    /**
     * Gets Rules Authorisation Table.
     *
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 2.0.0 [GSMA SGP.22]
     */
    public final void getRat(AsyncResultCallback<EuiccRat> callback, Handler handler) {
    }

    /**
     * Gets the eUICC challenge for new profile downloading.
     *
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 2.0.0 [GSMA SGP.22]
     */
    public final void getEuiccChallenge(AsyncResultCallback<byte[]> callback, Handler handler) {
    }

    /**
     * Gets the eUICC info1 for new profile downloading.
     *
     * @param callback The callback to get the result, which represents an {@code EUICCInfo1}
     *     defined in GSMA RSP v2.0+.
     * @param handler The handler to run the callback.
     * @since 2.0.0 [GSMA SGP.22]
     */
    public final void getEuiccInfo1(AsyncResultCallback<Asn1Node> callback, Handler handler) {
    }

    /**
     * Gets the eUICC info2 for new profile downloading.
     *
     * @param callback The callback to get the result, which represents an {@code EUICCInfo2}
     *     defined in GSMA RSP v2.0+.
     * @param handler The handler to run the callback.
     * @since 2.0.0 [GSMA SGP.22]
     */
    public final void getEuiccInfo2(AsyncResultCallback<Asn1Node> callback, Handler handler) {
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
    public final void authenticateServer(
            String matchingId,
            Asn1Node serverSigned1,
            Asn1Node serverSignature1,
            Asn1Node euiccCiPkIdToBeUsed,
            Asn1Node serverCertificate,
            AsyncResultCallback<Asn1Node> callback,
            Handler handler) {
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
    public final void prepareDownload(
            @Nullable byte[] hashCc,
            Asn1Node smdpSigned2,
            Asn1Node smdpSignature2,
            Asn1Node smdpCertificate,
            AsyncResultCallback<Asn1Node> callback,
            Handler handler) {
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
    public final void loadBoundProfilePackage(
            Asn1Node boundProfilePackage, AsyncResultCallback<Asn1Node> callback, Handler handler) {
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
    public final void cancelSession(
            byte[] transactionId,
            @CancelReason int reason,
            AsyncResultCallback<Asn1Node> callback,
            Handler handler) {
    }

    /**
     * Lists all notifications of the given {@code notificationEvents}.
     *
     * @param events Bits of the event types ({@link Event}) to list.
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 2.0.0 [GSMA SGP.22]
     */
    public final void listNotifications(
            @Event int events, AsyncResultCallback<EuiccNotification[]> callback, Handler handler) {
    }

    /**
     * Retrieves contents of all notification of the given {@code notificationEvents}.
     *
     * @param events Bits of the event types ({@link Event}) to list.
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 2.0.0 [GSMA SGP.22]
     */
    public final void retrieveNotificationList(
            @Event int events, AsyncResultCallback<EuiccNotification[]> callback, Handler handler) {
    }

    /**
     * Retrieves the content of a notification of the given {@code seqNumber}.
     *
     * @param seqNumber The sequence number of the notification.
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 2.0.0 [GSMA SGP.22]
     */
    public final void retrieveNotification(
            int seqNumber, AsyncResultCallback<EuiccNotification> callback, Handler handler) {
    }

    /**
     * Removes a notification from eUICC.
     *
     * @param seqNumber The sequence number of the notification.
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 2.0.0 [GSMA SGP.22]
     */
    public final void removeNotificationFromList(
            int seqNumber, AsyncResultCallback<Void> callback, Handler handler) {
    }
}
