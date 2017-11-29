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
import android.os.Looper;
import android.os.Message;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.uicc.euicc.asn1.Asn1Node;
import com.android.internal.telephony.uicc.euicc.asn1.InvalidAsn1DataException;
import com.android.internal.telephony.uicc.euicc.asn1.TagNotFoundException;
import com.android.internal.telephony.uicc.euicc.async.AsyncResultCallback;
import com.android.internal.telephony.uicc.euicc.async.AsyncResultHelper;
import com.android.internal.telephony.uicc.euicc.data.EuiccNotification;
import com.android.internal.telephony.uicc.euicc.data.EuiccNotification.Event;
import com.android.internal.telephony.uicc.euicc.data.EuiccProfile;
import com.android.internal.telephony.uicc.euicc.data.EuiccRat;
import com.android.internal.telephony.uicc.euicc.data.EuiccSpecVersion;
import com.android.internal.telephony.uicc.euicc.telephony.UiccChannelException;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

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
 *
 * @hide
 */
public abstract class EuiccCard implements Handler.Callback {
    /** Options for resetting eUICC memory */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
        value = {
            ResetOption.DELETE_OPERATIONAL_PROFILES,
            ResetOption.DELETE_FIELD_LOADED_TEST_PROFILES,
            ResetOption.RESET_DEFAULT_SMDP_ADDRESS
        },
        flag = true
    )
    public @interface ResetOption {
        /** Deletes all operational profiles. */
        int DELETE_OPERATIONAL_PROFILES = 1;
        /** Deletes all field-loaded testing profiles. */
        int DELETE_FIELD_LOADED_TEST_PROFILES = 1 << 1;
        /** Resets the default SM-DP+ address. */
        int RESET_DEFAULT_SMDP_ADDRESS = 1 << 2;
    }

    /** Reason for canceling a profile download session */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
        CancelReason.END_USER_REJECTED,
        CancelReason.POSTPONED,
        CancelReason.TIMEOUT,
        CancelReason.PPR_NOT_ALLOWED
    })
    public @interface CancelReason {
        /**
         * The end user has rejected the download. The profile will be put into the error state and
         * cannot be downloaded again without the operator's change.
         */
        int END_USER_REJECTED = 0;
        /** The download has been postponed and can be restarted later. */
        int POSTPONED = 1;
        /** The download has been timed out and can be restarted later. */
        int TIMEOUT = 2;
        /**
         * The profile to be downloaded cannot be installed due to its policy rule is not allowed by
         * the RAT (Rules Authorisation Table) on the eUICC or by other installed profiles. The
         * download can be restarted later.
         */
        int PPR_NOT_ALLOWED = 3;
    }

    private static class BaseRequest<T> {
        AsyncResultCallback<T> mResultCallback;
        Handler mResultHandler;
    }

    private static class ProfileRequest<T> extends BaseRequest<T> {
        String mIccid;
    }

    private static class SwitchProfileRequest<T> extends BaseRequest<T> {
        String mIccid;
        boolean mRefresh;
    }

    private static class ResetMemoryRequest extends BaseRequest<Void> {
        int mResetMemoryOptions;
    }

    private static class SetNicknameRequest extends BaseRequest<Void> {
        String mIccid;
        String mNickname;
    }

    private static class SetDefaultSmdpAddressRequest extends BaseRequest<Void> {
        String mDefaultSmdpAddress;
    }

    private static class AuthenticateServerRequest extends BaseRequest<Asn1Node> {
        String mMatchingId;
        Asn1Node mServerSigned1;
        Asn1Node mServerSignature1;
        Asn1Node mEuiccCiPkIdToBeUsed;
        Asn1Node mServerCertificate;
    }

    private static class PrepareDownloadRequest extends BaseRequest<Asn1Node> {
        byte[] mHashCc;
        Asn1Node mSmdpSigned2;
        Asn1Node mSmdpSignature2;
        Asn1Node mSmdpCertificate;
    }

    private static class LoadBoundProfilePackageRequest extends BaseRequest<Asn1Node> {
        Asn1Node mBoundProfilePackage;
    }

    private static class CancelSessionRequest extends BaseRequest<Asn1Node> {
        byte[] mTransactionId;
        int mCancelReason;
    }

    private static class ListNotificationsRequest extends BaseRequest<EuiccNotification[]> {
        int mNotificationEvents;
    }

    private static class NotificationRequest<T> extends BaseRequest<T> {
        int mNotificationSeqNumber;
    }

    private static final int MSG_GET_ALL_PROFILES = 1;
    private static final int MSG_GET_PROFILE = 2;
    private static final int MSG_DISABLE_PROFILE = 3;
    private static final int MSG_SWITCH_TO_PROFILE = 4;
    private static final int MSG_GET_EID = 5;
    private static final int MSG_SET_NICK_NAME = 6;
    private static final int MSG_DELETE_PROFILE = 7;
    private static final int MSG_RESET_MEMORY = 8;
    private static final int MSG_GET_DEFAULT_SMDP_ADDRESS = 9;
    private static final int MSG_GET_SMDS_ADDRESS = 10;
    private static final int MSG_SET_DEFAULT_SMDP_ADDRESS = 11;
    private static final int MSG_GET_RAT = 12;
    private static final int MSG_GET_EUICC_CHALLENGE = 13;
    private static final int MSG_GET_EUICC_INFO_1 = 14;
    private static final int MSG_GET_EUICC_INFO_2 = 15;
    private static final int MSG_AUTHENTICATE_SERVER = 16;
    private static final int MSG_PREPARE_DOWNLOAD = 17;
    private static final int MSG_LOAD_BOUND_PROFILE_PACKAGE = 18;
    private static final int MSG_CANCEL_SESSION = 19;
    private static final int MSG_LIST_NOTIFICATIONS = 20;
    private static final int MSG_RETRIEVE_NOTIFICATION_LIST = 21;
    private static final int MSG_RETRIEVE_NOTIFICATION = 22;
    private static final int MSG_REMOVE_NOTIFICATION_FROM_LIST = 23;

    private static <ResultType, RequestType extends BaseRequest<ResultType>>
            RequestType initRequest(
                    RequestType request,
                    AsyncResultCallback<ResultType> callback,
                    Handler handler) {
        request.mResultCallback = callback;
        request.mResultHandler = handler;
        return request;
    }

    private static <T extends BaseRequest<?>> T getRequest(Message msg) {
        // noinspection unchecked
        return (T) msg.obj;
    }

    private final Handler mHandler;
    protected final EuiccSpecVersion mCardVersion;
    private boolean mClosed;

    /**
     * Creates an instance.
     *
     * @param handlerThreadLooper The looper to run this handler. A single thread looper can handle
     *     all eUICCs in the system to ensure the commands are sending to the modem sequentially.
     * @param cardVersion The GSMA RSP spec version that the eUICC supports.
     */
    protected EuiccCard(Looper handlerThreadLooper, EuiccSpecVersion cardVersion) {
        mHandler = new Handler(handlerThreadLooper, this);
        mCardVersion = cardVersion;
    }

    @VisibleForTesting
    EuiccCard(EuiccSpecVersion cardVersion) {
        mHandler = null;
        mCardVersion = cardVersion;
    }

    /** @return The GSMA SGP.22 spec version that this card supports. */
    public EuiccSpecVersion getCardVersion() {
        return mCardVersion;
    }

    /** Builds a profile from a profile node. */
    public abstract void buildProfile(Asn1Node profileNode, EuiccProfile.Builder profileBuilder)
            throws TagNotFoundException, InvalidAsn1DataException;

    /**
     * Whether this instance has been closed. A closed instance will throw exception on all
     * operations.
     */
    public boolean isClosed() {
        return mClosed;
    }

    /**
     * Closes this eUICC card interface. A closed instance will throw exception on all operations.
     */
    public void close() {
        mClosed = true;
    }

    @Override
    public boolean handleMessage(Message msg) {
        try {
            checkClosed();
            switch (msg.what) {
                case MSG_GET_ALL_PROFILES:
                {
                    BaseRequest<EuiccProfile[]> r = getRequest(msg);
                    EuiccProfile[] result = getAllProfiles();
                    AsyncResultHelper.returnResult(result, r.mResultCallback, r.mResultHandler);
                    break;
                }
                case MSG_GET_PROFILE:
                {
                    ProfileRequest<EuiccProfile> r = getRequest(msg);
                    EuiccProfile result = getProfile(r.mIccid);
                    AsyncResultHelper.returnResult(result, r.mResultCallback, r.mResultHandler);
                    break;
                }
                case MSG_DISABLE_PROFILE:
                {
                    SwitchProfileRequest<Void> r = (SwitchProfileRequest) msg.obj;
                    disableProfile(r.mIccid, r.mRefresh);
                    AsyncResultHelper.returnResult(null, r.mResultCallback, r.mResultHandler);
                    break;
                }
                case MSG_SWITCH_TO_PROFILE:
                {
                    SwitchProfileRequest<EuiccProfile> r = getRequest(msg);
                    EuiccProfile result = switchToProfile(r.mIccid, r.mRefresh);
                    AsyncResultHelper.returnResult(result, r.mResultCallback, r.mResultHandler);
                    break;
                }
                case MSG_GET_EID:
                {
                    BaseRequest<String> r = getRequest(msg);
                    String result = getEid();
                    AsyncResultHelper.returnResult(result, r.mResultCallback, r.mResultHandler);
                    break;
                }
                case MSG_SET_NICK_NAME:
                {
                    SetNicknameRequest r = getRequest(msg);
                    setNickname(r.mIccid, r.mNickname);
                    AsyncResultHelper.returnResult(null, r.mResultCallback, r.mResultHandler);
                    break;
                }
                case MSG_DELETE_PROFILE:
                {
                    ProfileRequest<Void> r = getRequest(msg);
                    deleteProfile(r.mIccid);
                    AsyncResultHelper.returnResult(null, r.mResultCallback, r.mResultHandler);
                    break;
                }
                case MSG_RESET_MEMORY:
                {
                    ResetMemoryRequest r = getRequest(msg);
                    resetMemory(r.mResetMemoryOptions);
                    AsyncResultHelper.returnResult(null, r.mResultCallback, r.mResultHandler);
                    break;
                }
                case MSG_GET_DEFAULT_SMDP_ADDRESS:
                {
                    BaseRequest<String> r = getRequest(msg);
                    String result = getDefaultSmdpAddress();
                    AsyncResultHelper.returnResult(result, r.mResultCallback, r.mResultHandler);
                    break;
                }
                case MSG_GET_SMDS_ADDRESS:
                {
                    BaseRequest<String> r = getRequest(msg);
                    String result = getSmdsAddress();
                    AsyncResultHelper.returnResult(result, r.mResultCallback, r.mResultHandler);
                    break;
                }
                case MSG_SET_DEFAULT_SMDP_ADDRESS:
                {
                    SetDefaultSmdpAddressRequest r = getRequest(msg);
                    setDefaultSmdpAddress(r.mDefaultSmdpAddress);
                    AsyncResultHelper.returnResult(null, r.mResultCallback, r.mResultHandler);
                    break;
                }
                case MSG_GET_RAT:
                {
                    BaseRequest<EuiccRat> r = getRequest(msg);
                    EuiccRat result = getRat();
                    AsyncResultHelper.returnResult(result, r.mResultCallback, r.mResultHandler);
                    break;
                }
                case MSG_GET_EUICC_CHALLENGE:
                {
                    BaseRequest<byte[]> r = getRequest(msg);
                    byte[] result = getEuiccChallenge();
                    AsyncResultHelper.returnResult(result, r.mResultCallback, r.mResultHandler);
                    break;
                }
                case MSG_GET_EUICC_INFO_1:
                {
                    BaseRequest<Asn1Node> r = getRequest(msg);
                    Asn1Node result = getEuiccInfo1();
                    AsyncResultHelper.returnResult(result, r.mResultCallback, r.mResultHandler);
                    break;
                }
                case MSG_GET_EUICC_INFO_2:
                {
                    BaseRequest<Asn1Node> r = getRequest(msg);
                    Asn1Node result = getEuiccInfo2();
                    AsyncResultHelper.returnResult(result, r.mResultCallback, r.mResultHandler);
                    break;
                }
                case MSG_AUTHENTICATE_SERVER:
                {
                    AuthenticateServerRequest r = getRequest(msg);
                    Asn1Node result =
                            authenticateServer(
                                    r.mMatchingId,
                                    r.mServerSigned1,
                                    r.mServerSignature1,
                                    r.mEuiccCiPkIdToBeUsed,
                                    r.mServerCertificate);
                    AsyncResultHelper.returnResult(result, r.mResultCallback, r.mResultHandler);
                    break;
                }
                case MSG_PREPARE_DOWNLOAD:
                {
                    PrepareDownloadRequest r = getRequest(msg);
                    Asn1Node result =
                            prepareDownload(
                                    r.mHashCc,
                                    r.mSmdpSigned2,
                                    r.mSmdpSignature2,
                                    r.mSmdpCertificate);
                    AsyncResultHelper.returnResult(result, r.mResultCallback, r.mResultHandler);
                    break;
                }
                case MSG_LOAD_BOUND_PROFILE_PACKAGE:
                {
                    LoadBoundProfilePackageRequest r = getRequest(msg);
                    Asn1Node result = loadBoundProfilePackage(r.mBoundProfilePackage);
                    AsyncResultHelper.returnResult(result, r.mResultCallback, r.mResultHandler);
                    break;
                }
                case MSG_CANCEL_SESSION:
                {
                    CancelSessionRequest r = getRequest(msg);
                    Asn1Node result = cancelSession(r.mTransactionId, r.mCancelReason);
                    AsyncResultHelper.returnResult(result, r.mResultCallback, r.mResultHandler);
                    break;
                }
                case MSG_LIST_NOTIFICATIONS:
                {
                    ListNotificationsRequest r = getRequest(msg);
                    EuiccNotification[] result = listNotifications(r.mNotificationEvents);
                    AsyncResultHelper.returnResult(result, r.mResultCallback, r.mResultHandler);
                    break;
                }
                case MSG_RETRIEVE_NOTIFICATION_LIST:
                {
                    ListNotificationsRequest r = getRequest(msg);
                    EuiccNotification[] result = retrieveNotificationList(r.mNotificationEvents);
                    AsyncResultHelper.returnResult(result, r.mResultCallback, r.mResultHandler);
                    break;
                }
                case MSG_RETRIEVE_NOTIFICATION:
                {
                    NotificationRequest<EuiccNotification> r = getRequest(msg);
                    EuiccNotification result = retrieveNotification(r.mNotificationSeqNumber);
                    AsyncResultHelper.returnResult(result, r.mResultCallback, r.mResultHandler);
                    break;
                }
                case MSG_REMOVE_NOTIFICATION_FROM_LIST:
                {
                    NotificationRequest<Void> r = getRequest(msg);
                    removeNotificationFromList(r.mNotificationSeqNumber);
                    AsyncResultHelper.returnResult(null, r.mResultCallback, r.mResultHandler);
                    break;
                }
                default:
                    throw new UnsupportedOperationException("Unknown eUICC operation: " + msg.what);
            }
        } catch (UnsupportedOperationException | EuiccCardException | UiccChannelException e) {
            BaseRequest r = getRequest(msg);
            AsyncResultHelper.throwException(e, r.mResultCallback, r.mResultHandler);
        }
        return true;
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
        BaseRequest request = initRequest(new BaseRequest<EuiccProfile[]>(), callback, handler);
        sendRequest(MSG_GET_ALL_PROFILES, request);
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
        ProfileRequest<EuiccProfile> request =
                initRequest(new ProfileRequest<EuiccProfile>(), callback, handler);
        request.mIccid = iccid;
        sendRequest(MSG_GET_PROFILE, request);
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
        SwitchProfileRequest<Void> request =
                initRequest(new SwitchProfileRequest<Void>(), callback, handler);
        request.mIccid = iccid;
        request.mRefresh = refresh;
        sendRequest(MSG_DISABLE_PROFILE, request);
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
        SwitchProfileRequest<EuiccProfile> request =
                initRequest(new SwitchProfileRequest<EuiccProfile>(), callback, handler);
        request.mIccid = iccid;
        request.mRefresh = refresh;
        sendRequest(MSG_SWITCH_TO_PROFILE, request);
    }

    /**
     * Gets the EID of the eUICC.
     *
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 1.1.0 [GSMA SGP.22]
     */
    public final void getEid(AsyncResultCallback<String> callback, Handler handler) {
        BaseRequest request = initRequest(new BaseRequest<String>(), callback, handler);
        sendRequest(MSG_GET_EID, request);
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
        SetNicknameRequest request = initRequest(new SetNicknameRequest(), callback, handler);
        request.mIccid = iccid;
        request.mNickname = nickname;
        sendRequest(MSG_SET_NICK_NAME, request);
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
        ProfileRequest<Void> request = initRequest(new ProfileRequest<Void>(), callback, handler);
        request.mIccid = iccid;
        sendRequest(MSG_DELETE_PROFILE, request);
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
        ResetMemoryRequest request = initRequest(new ResetMemoryRequest(), callback, handler);
        request.mResetMemoryOptions = options;
        sendRequest(MSG_RESET_MEMORY, request);
    }

    /**
     * Gets the default SM-DP+ address from eUICC.
     *
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 2.0.0 [GSMA SGP.22]
     */
    public final void getDefaultSmdpAddress(AsyncResultCallback<String> callback, Handler handler) {
        BaseRequest request = initRequest(new BaseRequest<String>(), callback, handler);
        sendRequest(MSG_GET_DEFAULT_SMDP_ADDRESS, request);
    }

    /**
     * Gets the SM-DS address from eUICC.
     *
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 2.0.0 [GSMA SGP.22]
     */
    public final void getSmdsAddress(AsyncResultCallback<String> callback, Handler handler) {
        BaseRequest request = initRequest(new BaseRequest<String>(), callback, handler);
        sendRequest(MSG_GET_SMDS_ADDRESS, request);
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
        SetDefaultSmdpAddressRequest request =
                initRequest(new SetDefaultSmdpAddressRequest(), callback, handler);
        request.mDefaultSmdpAddress = defaultSmdpAddress;
        sendRequest(MSG_SET_DEFAULT_SMDP_ADDRESS, request);
    }

    /**
     * Gets Rules Authorisation Table.
     *
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 2.0.0 [GSMA SGP.22]
     */
    public final void getRat(AsyncResultCallback<EuiccRat> callback, Handler handler) {
        BaseRequest request = initRequest(new BaseRequest<EuiccRat>(), callback, handler);
        sendRequest(MSG_GET_RAT, request);
    }

    /**
     * Gets the eUICC challenge for new profile downloading.
     *
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 2.0.0 [GSMA SGP.22]
     */
    public final void getEuiccChallenge(AsyncResultCallback<byte[]> callback, Handler handler) {
        BaseRequest request = initRequest(new BaseRequest<byte[]>(), callback, handler);
        sendRequest(MSG_GET_EUICC_CHALLENGE, request);
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
        BaseRequest request = initRequest(new BaseRequest<Asn1Node>(), callback, handler);
        sendRequest(MSG_GET_EUICC_INFO_1, request);
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
        BaseRequest request = initRequest(new BaseRequest<Asn1Node>(), callback, handler);
        sendRequest(MSG_GET_EUICC_INFO_2, request);
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
        AuthenticateServerRequest request =
                initRequest(new AuthenticateServerRequest(), callback, handler);
        request.mMatchingId = matchingId;
        request.mServerSigned1 = serverSigned1;
        request.mServerSignature1 = serverSignature1;
        request.mEuiccCiPkIdToBeUsed = euiccCiPkIdToBeUsed;
        request.mServerCertificate = serverCertificate;
        sendRequest(MSG_AUTHENTICATE_SERVER, request);
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
        PrepareDownloadRequest request =
                initRequest(new PrepareDownloadRequest(), callback, handler);
        request.mHashCc = hashCc;
        request.mSmdpSigned2 = smdpSigned2;
        request.mSmdpSignature2 = smdpSignature2;
        request.mSmdpCertificate = smdpCertificate;
        sendRequest(MSG_PREPARE_DOWNLOAD, request);
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
        LoadBoundProfilePackageRequest request =
                initRequest(new LoadBoundProfilePackageRequest(), callback, handler);
        request.mBoundProfilePackage = boundProfilePackage;
        sendRequest(MSG_LOAD_BOUND_PROFILE_PACKAGE, request);
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
        CancelSessionRequest request = initRequest(new CancelSessionRequest(), callback, handler);
        request.mTransactionId = transactionId;
        request.mCancelReason = reason;
        sendRequest(MSG_CANCEL_SESSION, request);
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
        ListNotificationsRequest request =
                initRequest(new ListNotificationsRequest(), callback, handler);
        request.mNotificationEvents = events;
        sendRequest(MSG_LIST_NOTIFICATIONS, request);
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
        ListNotificationsRequest request =
                initRequest(new ListNotificationsRequest(), callback, handler);
        request.mNotificationEvents = events;
        sendRequest(MSG_RETRIEVE_NOTIFICATION_LIST, request);
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
        NotificationRequest<EuiccNotification> request =
                initRequest(new NotificationRequest<EuiccNotification>(), callback, handler);
        request.mNotificationSeqNumber = seqNumber;
        sendRequest(MSG_RETRIEVE_NOTIFICATION, request);
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
        NotificationRequest<Void> request =
                initRequest(new NotificationRequest<Void>(), callback, handler);
        request.mNotificationSeqNumber = seqNumber;
        sendRequest(MSG_REMOVE_NOTIFICATION_FROM_LIST, request);
    }

    /**
     * Gets a map from ICCIDs to user-visible profiles.
     *
     * @throws UiccChannelException If any error happens when executing the command on eUICC.
     * @throws EuiccCardException If any error is returned by eUICC or happens during parsing the
     *     response.
     * @since 1.1.0 [GSMA SGP.22]
     * @hide
     */
    public abstract EuiccProfile[] getAllProfiles()
            throws UiccChannelException, EuiccCardException;

    /**
     * Gets a profile.
     *
     * @throws UiccChannelException If any error happens when executing the command on eUICC.
     * @throws EuiccCardException If any error is returned by eUICC or happens during parsing the
     *     response.
     * @since 1.1.0 [GSMA SGP.22]
     * @hide
     */
    public abstract EuiccProfile getProfile(String iccid)
            throws UiccChannelException, EuiccCardException;

    /**
     * Disables a profile of the given {@code iccid}.
     *
     * @param refresh Whether sending the REFRESH command to modem.
     * @throws UiccChannelException If any error happens when executing the command on eUICC.
     * @throws EuiccCardException If any error is returned by eUICC or happens during parsing the
     *     response.
     * @since 1.1.0 [GSMA SGP.22]
     * @hide
     */
    public abstract void disableProfile(String iccid, boolean refresh)
            throws UiccChannelException, EuiccCardException;

    /**
     * Switches from the current profile to another profile. The current profile will be disabled
     * and the specified profile will be enabled.
     *
     * @param refresh Whether sending the REFRESH command to modem.
     * @return The enabled EuiccProfile.
     * @throws UiccChannelException If any error happens when executing the command on eUICC.
     * @throws EuiccCardException If any error is returned by eUICC or happens during parsing the
     *     response.
     * @since 1.1.0 [GSMA SGP.22]
     * @hide
     */
    public abstract EuiccProfile switchToProfile(String iccid, boolean refresh)
            throws UiccChannelException, EuiccCardException;

    /**
     * Gets the EID of the eUICC.
     *
     * @throws UiccChannelException If any error happens when executing the command on eUICC.
     * @throws EuiccCardException If any error is returned by eUICC or happens during parsing the
     *     response.
     * @since 1.1.0 [GSMA SGP.22]
     * @hide
     */
    public abstract String getEid() throws UiccChannelException, EuiccCardException;

    /**
     * Sets the nickname of a profile.
     *
     * @throws UiccChannelException If any error happens when executing the command on eUICC.
     * @throws EuiccCardException If any error is returned by eUICC or happens during parsing the
     *     response.
     * @since 1.1.0 [GSMA SGP.22]
     * @hide
     */
    public abstract void setNickname(String iccid, String nickname)
            throws UiccChannelException, EuiccCardException;

    /**
     * Deletes a profile from eUICC.
     *
     * @throws UiccChannelException If any error happens when executing the command on eUICC.
     * @throws EuiccCardException If any error is returned by eUICC or happens during parsing the
     *     response.
     * @since 1.1.0 [GSMA SGP.22]
     * @hide
     */
    public abstract void deleteProfile(String iccid)
            throws UiccChannelException, EuiccCardException;

    /**
     * Resets the eUICC memory (i.e., remove all profiles).
     *
     * @param options Bits of the options of resetting which parts of the eUICC memory. You can
     *     check out subclasses to see details.
     * @throws UiccChannelException If any error happens when executing the command on eUICC.
     * @throws EuiccCardException If any error is returned by eUICC or happens during parsing the
     *     response.
     * @since 1.1.0 [GSMA SGP.22]
     * @hide
     */
    public abstract void resetMemory(@ResetOption int options)
            throws UiccChannelException, EuiccCardException;

    /**
     * Gets the default SM-DP+ address from eUICC.
     *
     * @throws UiccChannelException If any error happens when executing the command on eUICC.
     * @throws EuiccCardException If any error is returned by eUICC or happens during parsing the
     *     response.
     * @since 2.0.0 [GSMA SGP.22]
     * @hide
     */
    public abstract String getDefaultSmdpAddress()
            throws UiccChannelException, EuiccCardException;

    /**
     * Gets the SM-DS address from eUICC.
     *
     * @throws UiccChannelException If any error happens when executing the command on eUICC.
     * @throws EuiccCardException If any error is returned by eUICC or happens during parsing the
     *     response.
     * @since 2.0.0 [GSMA SGP.22]
     * @hide
     */
    public abstract String getSmdsAddress() throws UiccChannelException, EuiccCardException;

    /**
     * Sets the default SM-DP+ address of eUICC.
     *
     * @throws UiccChannelException If any error happens when executing the command on eUICC.
     * @throws EuiccCardException If any error is returned by eUICC or happens during parsing the
     *     response.
     * @since 2.0.0 [GSMA SGP.22]
     * @hide
     */
    public abstract void setDefaultSmdpAddress(String defaultSmdpAddress)
            throws UiccChannelException, EuiccCardException;

    /**
     * Gets Rules Authorisation Table.
     *
     * @throws UiccChannelException If any error happens when executing the command on eUICC.
     * @throws EuiccCardException If any error is returned by eUICC or happens during parsing the
     *     response.
     * @since 2.0.0 [GSMA SGP.22]
     * @hide
     */
    public abstract EuiccRat getRat() throws UiccChannelException, EuiccCardException;

    /**
     * Gets the eUICC challenge for new profile downloading.
     *
     * @return The bytes of eUICC challenge content.
     * @throws UiccChannelException If any error happens when executing the command on eUICC.
     * @throws EuiccCardException If any error is returned by eUICC or happens during parsing the
     *     response.
     * @since 2.0.0 [GSMA SGP.22]
     * @hide
     */
    public abstract byte[] getEuiccChallenge() throws UiccChannelException, EuiccCardException;

    /**
     * Gets the eUICC info1 for new profile downloading.
     *
     * @return The {@link Asn1Node} instance which represents an {@code EUICCInfo2} defined in GSMA
     *     RSP v2.0+.
     * @throws UiccChannelException If any error happens when executing the command on eUICC.
     * @throws EuiccCardException If any error is returned by eUICC or happens during parsing the
     *     response.
     * @since 2.0.0 [GSMA SGP.22]
     * @hide
     */
    public abstract Asn1Node getEuiccInfo1() throws UiccChannelException, EuiccCardException;

    /**
     * Gets the eUICC info2 for new profile downloading.
     *
     * @return The {@link Asn1Node} instance which represents an {@code EUICCInfo1} defined in GSMA
     *     RSP v2.0+.
     * @throws UiccChannelException If any error happens when executing the command on eUICC.
     * @throws EuiccCardException If any error is returned by eUICC or happens during parsing the
     *     response.
     * @since 2.0.0 [GSMA SGP.22]
     * @hide
     */
    public abstract Asn1Node getEuiccInfo2() throws UiccChannelException, EuiccCardException;

    /**
     * Authenticates the SM-DP+ server by the eUICC. The parameters {@code serverSigned1}, {@code
     * serverSignature1}, {@code euiccCiPkIdToBeUsed}, and {@code serverCertificate} are the ASN.1
     * data returned by SM-DP+ server.
     *
     * @param matchingId The activation code or an empty string.
     * @return The {@link Asn1Node} instance which represents an {@code AuthenticateServerResponse}
     *     defined in GSMA RSP v2.0+.
     * @throws UiccChannelException If any error happens when executing the command on eUICC.
     * @throws EuiccCardException If any error is returned by eUICC or happens during parsing the
     *     response.
     * @since 2.0.0 [GSMA SGP.22]
     * @hide
     */
    public abstract Asn1Node authenticateServer(
            String matchingId,
            Asn1Node serverSigned1,
            Asn1Node serverSignature1,
            Asn1Node euiccCiPkIdToBeUsed,
            Asn1Node serverCertificate)
            throws UiccChannelException, EuiccCardException;

    /**
     * Prepares the profile download request sent to SM-DP+. The parameters {@code smdpSigned2},
     * {@code smdpSignature2}, and {@code smdpCertificate} are the ASN.1 data returned by SM-DP+
     * server.
     *
     * @param hashCc The hash of confirmation code. It can be null if there is no confirmation code
     *     required.
     * @return The {@link Asn1Node} instance which represents an {@code PrepareDownloadResponse}
     *     defined in GSMA RSP v2.0+.
     * @throws UiccChannelException If any error happens when executing the command on eUICC.
     * @throws EuiccCardException If any error is returned by eUICC or happens during parsing the
     *     response.
     * @since 2.0.0 [GSMA SGP.22]
     * @hide
     */
    public abstract Asn1Node prepareDownload(
            @Nullable byte[] hashCc,
            Asn1Node smdpSigned2,
            Asn1Node smdpSignature2,
            Asn1Node smdpCertificate)
            throws UiccChannelException, EuiccCardException;

    /**
     * Loads a downloaded bound profile package onto the eUICC.
     *
     * @param boundProfilePackage The Bound Profile Package data returned by SM-DP+ server.
     * @return The {@link Asn1Node} instance which represents an {@code PrepareDownloadResponse}
     *     defined in GSMA RSP v2.0+.
     * @throws UiccChannelException If any error happens when executing the command on eUICC.
     * @throws EuiccCardException If any error is returned by eUICC or happens during parsing the
     *     response.
     * @since 2.0.0 [GSMA SGP.22]
     * @hide
     */
    public abstract Asn1Node loadBoundProfilePackage(Asn1Node boundProfilePackage)
            throws UiccChannelException, EuiccCardException;

    /**
     * Cancels the current profile download session.
     *
     * @param transactionId The transaction ID returned by SM-DP+ server.
     * @return The {@link Asn1Node} instance which represents an {@code CancelSessionResponse}
     *     defined in GSMA RSP v2.0+.
     * @throws UiccChannelException If any error happens when executing the command on eUICC.
     * @throws EuiccCardException If any error is returned by eUICC or happens during parsing the
     *     response.
     * @since 2.0.0 [GSMA SGP.22]
     * @hide
     */
    public abstract Asn1Node cancelSession(byte[] transactionId, @CancelReason int reason)
            throws UiccChannelException, EuiccCardException;

    /**
     * Lists all notifications of the given {@code notificationEvents}.
     *
     * @param events Bits of the event types ({@link Event}) to list.
     * @throws UiccChannelException If any error happens when executing the command on eUICC.
     * @throws EuiccCardException If any error is returned by eUICC or happens during parsing the
     *     response.
     * @since 2.0.0 [GSMA SGP.22]
     * @hide
     */
    public abstract EuiccNotification[] listNotifications(@Event int events)
            throws UiccChannelException, EuiccCardException;

    /**
     * Retrieves contents of all notification of the given {@code notificationEvents}.
     *
     * @param events Bits of the event types ({@link Event}) to list.
     * @throws UiccChannelException If any error happens when executing the command on eUICC.
     * @throws EuiccCardException If any error is returned by eUICC or happens during parsing the
     *     response.
     * @since 2.0.0 [GSMA SGP.22]
     * @hide
     */
    public abstract EuiccNotification[] retrieveNotificationList(@Event int events)
            throws UiccChannelException, EuiccCardException;

    /**
     * Retrieves the content of a notification of the given {@code seqNumber}.
     *
     * @param seqNumber The sequence number of the notification.
     * @throws UiccChannelException If any error happens when executing the command on eUICC.
     * @throws EuiccCardException If any error is returned by eUICC or happens during parsing the
     *     response.
     * @since 2.0.0 [GSMA SGP.22]
     * @hide
     */
    @Nullable
    public abstract EuiccNotification retrieveNotification(int seqNumber)
            throws UiccChannelException, EuiccCardException;

    /**
     * Removes a notification from eUICC.
     *
     * @param seqNumber The sequence number of the notification.
     * @throws UiccChannelException If any error happens when executing the command on eUICC.
     * @throws EuiccCardException If any error is returned by eUICC or happens during parsing the
     *     response.
     * @since 2.0.0 [GSMA SGP.22]
     * @hide
     */
    public abstract void removeNotificationFromList(int seqNumber)
            throws UiccChannelException, EuiccCardException;

    private void sendRequest(int what, Object obj) {
        if (mHandler == null) {
            throw new IllegalStateException(
                    "EuiccCard(EuiccSpecVersion) should be called only in unit tests.");
        }
        Message requestMessage = mHandler.obtainMessage();
        requestMessage.what = what;
        requestMessage.obj = obj;
        requestMessage.sendToTarget();
    }

    private void checkClosed() throws EuiccCardException {
        if (mClosed) {
            throw new EuiccCardUnavailableException("EuiccCard instance has been closed.");
        }
    }
}
