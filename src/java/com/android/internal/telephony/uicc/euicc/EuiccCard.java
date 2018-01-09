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

package com.android.internal.telephony.uicc.euicc;

import android.annotation.IntDef;
import android.annotation.Nullable;
import android.content.Context;
import android.os.Handler;
import android.service.carrier.CarrierIdentifier;
import android.service.euicc.EuiccProfileInfo;
import android.telephony.Rlog;
import android.telephony.UiccAccessRule;
import android.telephony.euicc.EuiccNotification;
import android.telephony.euicc.EuiccRat;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.uicc.IccCardStatus;
import com.android.internal.telephony.uicc.IccUtils;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.asn1.Asn1Decoder;
import com.android.internal.telephony.uicc.asn1.Asn1Node;
import com.android.internal.telephony.uicc.asn1.InvalidAsn1DataException;
import com.android.internal.telephony.uicc.asn1.TagNotFoundException;
import com.android.internal.telephony.uicc.euicc.apdu.ApduException;
import com.android.internal.telephony.uicc.euicc.apdu.ApduSender;
import com.android.internal.telephony.uicc.euicc.apdu.RequestBuilder;
import com.android.internal.telephony.uicc.euicc.apdu.RequestProvider;
import com.android.internal.telephony.uicc.euicc.async.AsyncResultCallback;
import com.android.internal.telephony.uicc.euicc.async.AsyncResultHelper;
import com.android.internal.telephony.util.IccIdFormatter;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

/**
 * This represents an eUICC card to perform profile management operations asynchronously. This class
 * includes methods defined by different versions of GSMA Spec (SGP.22).
 *
 * @hide
 */
public class EuiccCard extends UiccCard {
    private static final String LOG_TAG = "EuiccCard";
    private static final boolean DBG = true;

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

    private static final String ISD_R_AID = "A0000005591010FFFFFFFF8900000100";

    // ASN.1 tags for commands
    private static final int TAG_GET_PROFILES = 0xBF2D;
    private static final int TAG_DISABLE_PROFILE = 0xBF32;
    private static final int TAG_ENABLE_PROFILE = 0xBF31;
    private static final int TAG_GET_EID = 0xBF3E;
    private static final int TAG_SET_NICKNAME = 0xBF29;
    private static final int TAG_DELETE_PROFILE = 0xBF33;
    private static final int TAG_GET_CONFIGURED_ADDRESSES = 0xBF3C;
    private static final int TAG_SET_DEFAULT_SMDP_ADDRESS = 0xBF3F;
    private static final int TAG_GET_RAT = 0xBF43;
    private static final int TAG_EUICC_MEMORY_RESET = 0xBF34;
    private static final int TAG_GET_EUICC_CHALLENGE = 0xBF2E;
    private static final int TAG_GET_EUICC_INFO_1 = 0xBF20;
    private static final int TAG_GET_EUICC_INFO_2 = 0xBF22;
    private static final int TAG_LIST_NOTIFICATION = 0xBF28;
    private static final int TAG_RETRIEVE_NOTIFICATIONS_LIST = 0xBF2B;
    private static final int TAG_REMOVE_NOTIFICATION_FROM_LIST = 0xBF30;
    private static final int TAG_AUTHENTICATE_SERVER = 0xBF38;
    private static final int TAG_PREPARE_DOWNLOAD = 0xBF21;
    private static final int TAG_INITIALISE_SECURE_CHANNEL = 0xBF23;

    // Universal tags
    private static final int TAG_UNI_2 = 0x02;
    private static final int TAG_UNI_4 = 0x04;
    // Context tags for primitive types
    private static final int TAG_CTX_0 = 0x80;
    private static final int TAG_CTX_1 = 0x81;
    private static final int TAG_CTX_2 = 0x82;
    private static final int TAG_CTX_6 = 0x86;
    private static final int TAG_CTX_8 = 0x88;
    // Context tags for constructed (compound) types
    private static final int TAG_CTX_COMP_0 = 0xA0;
    private static final int TAG_CTX_COMP_1 = 0xA1;
    private static final int TAG_CTX_COMP_2 = 0xA2;
    private static final int TAG_CTX_COMP_3 = 0xA3;

    // Command data tags
    private static final int TAG_PROFILE_INSTALLATION_RESULT = 0xBF37;
    private static final int TAG_PROFILE_INSTALLATION_RESULT_DATA = 0xBF27;
    private static final int TAG_NOTIFICATION_METADATA = 0xBF2F;
    private static final int TAG_SEQ = TAG_CTX_0;
    private static final int TAG_TARGET_ADDR = 0x0C;
    private static final int TAG_EVENT = TAG_CTX_1;
    private static final int TAG_CANCEL_SESSION = 0xBF41;
    private static final int TAG_PROFILE_INFO = 0xE3;
    private static final int TAG_TAG_LIST = 0x5C;
    private static final int TAG_EID = 0x5A;
    private static final int TAG_NICKNAME = 0x90;
    private static final int TAG_ICCID = 0x5A;
    private static final int TAG_PROFILE_STATE = 0x9F70;
    private static final int TAG_SERVICE_PROVIDER_NAME = 0x91;
    private static final int TAG_PROFILE_CLASS = 0x95;
    private static final int TAG_PROFILE_POLICY_RULE = 0x99;
    private static final int TAG_PROFILE_NAME = 0x92;
    private static final int TAG_OPERATOR_ID = 0xB7;
    private static final int TAG_CARRIER_PRIVILEGE_RULES = 0xBF76;

    // Tags from the RefArDo data standard - https://source.android.com/devices/tech/config/uicc
    private static final int TAG_REF_AR_DO = 0xE2;
    private static final int TAG_REF_DO = 0xE1;
    private static final int TAG_DEVICE_APP_ID_REF_DO = 0xC1;
    private static final int TAG_PKG_REF_DO = 0xCA;
    private static final int TAG_AR_DO = 0xE3;
    private static final int TAG_PERM_AR_DO = 0xDB;

    // TAG list for Euicc Profile
    private static final byte[] EUICC_PROFILE_TAGS =
            new byte[] {
                    TAG_ICCID,
                    (byte) TAG_NICKNAME,
                    (byte) TAG_SERVICE_PROVIDER_NAME,
                    (byte) TAG_PROFILE_NAME,
                    (byte) TAG_OPERATOR_ID,
                    (byte) (TAG_PROFILE_STATE / 256),
                    (byte) (TAG_PROFILE_STATE % 256),
                    (byte) TAG_PROFILE_CLASS,
                    (byte) TAG_PROFILE_POLICY_RULE,
                    (byte) (TAG_CARRIER_PRIVILEGE_RULES / 256),
                    (byte) (TAG_CARRIER_PRIVILEGE_RULES % 256)
            };

    // APDU status for SIM refresh
    private static final int APDU_ERROR_SIM_REFRESH = 0x6F00;

    private static final EuiccSpecVersion SGP_1_1 = new EuiccSpecVersion(1, 1, 0);
    private static final EuiccSpecVersion SGP_2_0 = new EuiccSpecVersion(2, 0, 0);

    // ASN.1 Tags
    private static final int TAG_ISD_R_APP_TEMPLATE = 0xE0;
    private static final int TAG_VERSION = 0x82;

    private static EuiccSpecVersion parseSpecVersion(byte[] selectResponse) {
        Asn1Node node;
        try {
            Asn1Decoder decoder = new Asn1Decoder(selectResponse);
            if (!decoder.hasNextNode()) {
                return null;
            }
            node = decoder.nextNode();
        } catch (InvalidAsn1DataException e) {
            loge("Cannot parse the select response of ISD-R.", e);
            return null;
        }
        try {
            byte[] versionType;
            if (node.getTag() == TAG_ISD_R_APP_TEMPLATE) {
                versionType = node.getChild(TAG_VERSION).asBytes();
            } else {
                versionType =
                        node.getChild(TAG_ISD_R_APP_TEMPLATE, TAG_VERSION).asBytes();
            }
            if (versionType.length == 3) {
                return new EuiccSpecVersion(versionType);
            } else {
                loge("Cannot parse select response of ISD-R: " + node.toHex());
            }
        } catch (InvalidAsn1DataException | TagNotFoundException e) {
            loge("Cannot parse select response of ISD-R: " + node.toHex());
        }
        return null;
    }

    private static void logd(String message) {
        if (DBG) {
            Rlog.d(LOG_TAG, message);
        }
    }

    private static void loge(String message) {
        Rlog.e(LOG_TAG, message);
    }

    private static void loge(String message, Throwable e) {
        Rlog.e(LOG_TAG, message, e);
    }

    private static String pii(Object pii) {
        return Rlog.pii(LOG_TAG, pii);
    }

    private final ApduSender mApduSender;
    private final Object mLock = new Object();
    private EuiccSpecVersion mSpecVersion;

    private interface ApduRequestBuilder {
        void build(RequestBuilder requestBuilder)
                throws EuiccCardException, TagNotFoundException, InvalidAsn1DataException;
    }

    private interface ApduResponseHandler<T> {
        T handleResult(byte[] response)
                throws EuiccCardException, TagNotFoundException, InvalidAsn1DataException;
    }

    private interface ApduExceptionHandler {
        void handleException(Throwable e);
    }

    private final class SpecBasedRequestProvider implements RequestProvider {
        private final ApduRequestBuilder mBuilderForSpec1Dot1;
        private final ApduRequestBuilder mBuilderForSpec2Dot0;

        /**
         * Don't use this constructor to create instances of {@link SpecBasedRequestProvider}. Use
         * the factory methods {@code newRequestProviderForSpec_X_Y()} instead.
         *
         * <p>When adding code for a new spec version, a final member {@code mBuilderForSpec_X_Y}
         * should be added to this class and initialized by this constructor, and related factory
         * methods {@code newRequestProviderForSpec_X_Y()} need to be modified respectively.
         */
        SpecBasedRequestProvider(
                ApduRequestBuilder builderForSpec1Dot1,
                ApduRequestBuilder builderForSpec2Dot0) {
            mBuilderForSpec1Dot1 = builderForSpec1Dot1;
            mBuilderForSpec2Dot0 = builderForSpec2Dot0;
        }

        @Override
        public void buildRequest(byte[] selectResponse, RequestBuilder requestBuilder)
                throws EuiccCardException {
            EuiccSpecVersion ver = getOrExtractSpecVersion(selectResponse);
            if (ver == null) {
                // SGP 1.1 doesn't have a select response containing the spec version.
                ver = SGP_1_1;
            }
            try {
                if (ver.compareTo(SGP_2_0) >= 0 && mBuilderForSpec2Dot0 != null) {
                    mBuilderForSpec2Dot0.build(requestBuilder);
                } else if (ver.compareTo(SGP_1_1) >= 0 && mBuilderForSpec1Dot1 != null) {
                    mBuilderForSpec1Dot1.build(requestBuilder);
                } else {
                    throw new EuiccCardException(
                            "eUICC spec version < 1.1 is unsupported: read version: " + ver);
                }
            } catch (InvalidAsn1DataException | TagNotFoundException e) {
                throw new EuiccCardException("Cannot parse ASN1 to build request.", e);
            }
        }
    }

    private RequestProvider newRequestProviderForAll(ApduRequestBuilder builder) {
        return new SpecBasedRequestProvider(builder, null);
    }

    private RequestProvider newRequestProviderForSpec2Dot0(ApduRequestBuilder builder) {
        return new SpecBasedRequestProvider(null, builder);
    }

    public EuiccCard(Context c, CommandsInterface ci, IccCardStatus ics, int phoneId) {
        super(c, ci, ics, phoneId);
        // TODO: Set supportExtendedApdu based on ATR.
        mApduSender = new ApduSender(ci, ISD_R_AID, false /* supportExtendedApdu */);
    }

    /**
     * A wrapper on {@link ApduSender#send(RequestProvider, AsyncResultCallback, Handler)} to
     * leverage lambda to simplify the sending APDU code.
     *
     * @param requestBuilder Builds the request of APDU commands.
     * @param responseHandler Converts the APDU response from bytes to expected result.
     * @param <T> Type of the originally expected result.
     */
    private <T> void sendApdu(
            RequestProvider requestBuilder,
            ApduResponseHandler<T> responseHandler,
            AsyncResultCallback<T> callback,
            Handler handler) {
        sendApdu(
                requestBuilder,
                responseHandler,
                (e) ->
                        callback.onException(new EuiccCardException("Cannot send APDU.", e)),
                callback,
                handler);
    }

    /**
     * TODO: This is a workaround solution to the bug that a SIM refresh may interrupt the modem to
     * return the reset of responses of the original APDU command. This applies to disable profile,
     * switch profile, and reset eUICC memory.
     *
     * <p> Use {@link #sendApdu(RequestProvider, ApduResponseHandler, AsyncResultCallback, Handler)}
     * when this workaround is not needed.
     */
    private void sendApduWithSimResetErrorWorkaround(
            RequestProvider requestBuilder,
            ApduResponseHandler<Void> responseHandler,
            AsyncResultCallback<Void> callback,
            Handler handler) {
        sendApdu(
                requestBuilder,
                responseHandler,
                (e) -> {
                    if (e instanceof ApduException
                            && ((ApduException) e).getApduStatus() == APDU_ERROR_SIM_REFRESH) {
                        callback.onResult(null);
                    } else {
                        callback.onException(new EuiccCardException("Cannot send APDU.", e));
                    }
                },
                callback,
                handler);
    }

    private <T> void sendApdu(
            RequestProvider requestBuilder,
            ApduResponseHandler<T> responseHandler,
            ApduExceptionHandler exceptionHandler,
            AsyncResultCallback<T> callback,
            Handler handler) {
        mApduSender.send(
                requestBuilder,
                new AsyncResultCallback<byte[]>() {
                    @Override
                    public void onResult(byte[] response) {
                        try {
                            callback.onResult(responseHandler.handleResult(response));
                        } catch (EuiccCardException e) {
                            callback.onException(e);
                        } catch (InvalidAsn1DataException | TagNotFoundException e) {
                            callback.onException(
                                    new EuiccCardException(
                                            "Cannot parse response: "
                                                    + IccUtils.bytesToHexString(response),
                                            e));
                        }
                    }

                    @Override
                    public void onException(Throwable e) {
                        exceptionHandler.handleException(e);
                    }
                },
                handler);
    }

    private void buildProfile(Asn1Node profileNode, EuiccProfileInfo.Builder profileBuilder)
            throws TagNotFoundException, InvalidAsn1DataException {
        String strippedIccIdString =
                IccIdFormatter.stripTrailingFs(profileNode.getChild(TAG_ICCID).asBytes());
        profileBuilder.setIccid(strippedIccIdString);

        if (profileNode.hasChild(TAG_NICKNAME)) {
            profileBuilder.setNickname(profileNode.getChild(TAG_NICKNAME).asString());
        }

        if (profileNode.hasChild(TAG_SERVICE_PROVIDER_NAME)) {
            profileBuilder.setServiceProviderName(
                    profileNode.getChild(TAG_SERVICE_PROVIDER_NAME).asString());
        }

        if (profileNode.hasChild(TAG_PROFILE_NAME)) {
            profileBuilder.setProfileName(profileNode.getChild(TAG_PROFILE_NAME).asString());
        }

        if (profileNode.hasChild(TAG_OPERATOR_ID)) {
            profileBuilder.setCarrierIdentifier(
                    buildCarrierIdentifier(profileNode.getChild(TAG_OPERATOR_ID)));
        }

        if (profileNode.hasChild(TAG_PROFILE_STATE)) {
            // noinspection WrongConstant
            profileBuilder.setState(profileNode.getChild(TAG_PROFILE_STATE).asInteger());
        } else {
            profileBuilder.setState(EuiccProfileInfo.PROFILE_STATE_DISABLED);
        }

        if (profileNode.hasChild(TAG_PROFILE_CLASS)) {
            // noinspection WrongConstant
            profileBuilder.setProfileClass(profileNode.getChild(TAG_PROFILE_CLASS).asInteger());
        } else {
            profileBuilder.setProfileClass(EuiccProfileInfo.PROFILE_CLASS_OPERATIONAL);
        }

        if (profileNode.hasChild(TAG_PROFILE_POLICY_RULE)) {
            // noinspection WrongConstant
            profileBuilder.setPolicyRules(profileNode.getChild(TAG_PROFILE_POLICY_RULE).asBits());
        }

        if (profileNode.hasChild(TAG_CARRIER_PRIVILEGE_RULES)) {
            profileBuilder.setUiccAccessRule(
                    buildUiccAccessRule(
                            profileNode
                                    .getChild(TAG_CARRIER_PRIVILEGE_RULES)
                                    .getChildren(TAG_REF_AR_DO)));
        }
    }

    private EuiccSpecVersion getOrExtractSpecVersion(byte[] selectResponse) {
        // Uses the cached version.
        synchronized (mLock) {
            if (mSpecVersion != null) {
                return mSpecVersion;
            }
        }
        // Parses and caches the version.
        EuiccSpecVersion ver = parseSpecVersion(selectResponse);
        if (ver != null) {
            synchronized (mLock) {
                if (mSpecVersion == null) {
                    mSpecVersion = ver;
                }
            }
        }
        return ver;
    }

    /**
     * Gets the GSMA RSP specification version supported by this eUICC. This may return null if the
     * version cannot be read.
     */
    public void getSpecVersion(AsyncResultCallback<EuiccSpecVersion> callback, Handler handler) {
        synchronized (mLock) {
            if (mSpecVersion != null) {
                AsyncResultHelper.returnResult(mSpecVersion, callback, handler);
                return;
            }
        }
        sendApdu(
                newRequestProviderForAll((RequestBuilder requestBuilder) -> { /* Do nothing */ }),
                (byte[] response) -> mSpecVersion,
                callback,
                handler);
    }

    /**
     * Gets a map from ICCIDs to user-visible profiles.
     *
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 1.1.0 [GSMA SGP.22]
     */
    public void getAllProfiles(AsyncResultCallback<EuiccProfileInfo[]> callback, Handler handler) {
        sendApdu(
                newRequestProviderForAll(
                        (RequestBuilder requestBuilder) ->
                            requestBuilder.addStoreData(
                                    Asn1Node.newBuilder(TAG_GET_PROFILES)
                                            .addChildAsBytes(TAG_TAG_LIST, EUICC_PROFILE_TAGS)
                                            .build()
                                            .toHex())),
                (byte[] response) -> {
                    List<Asn1Node> profileNodes =
                            new Asn1Decoder(response)
                                    .nextNode()
                                    .getChild(TAG_CTX_COMP_0)
                                    .getChildren(TAG_PROFILE_INFO);
                    int size = profileNodes.size();
                    EuiccProfileInfo[] profiles = new EuiccProfileInfo[size];
                    int profileCount = 0;
                    for (int i = 0; i < size; i++) {
                        Asn1Node profileNode = profileNodes.get(i);
                        if (!profileNode.hasChild(TAG_ICCID)) {
                            loge("Profile must have an ICCID.");
                            continue;
                        }
                        EuiccProfileInfo.Builder profileBuilder = new EuiccProfileInfo.Builder();
                        buildProfile(profileNode, profileBuilder);

                        EuiccProfileInfo profile = profileBuilder.build();
                        profiles[profileCount++] = profile;
                    }
                    return profiles;
                },
                callback,
                handler);
    }

    /**
     * Gets a profile.
     *
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 1.1.0 [GSMA SGP.22]
     */
    public final void getProfile(
            String iccid, AsyncResultCallback<EuiccProfileInfo> callback, Handler handler) {
        getAllProfiles(
                new AsyncResultCallback<EuiccProfileInfo[]>() {
                    @Override
                    public void onResult(EuiccProfileInfo[] euiccProfiles) {
                        for (int i = 0; i < euiccProfiles.length; i++) {
                            if (euiccProfiles[i].getIccid().equals(iccid)) {
                                callback.onResult(euiccProfiles[i]);
                            }
                        }
                        callback.onException(
                                new EuiccCardErrorException(
                                        OPERATION_GET_PROFILE,
                                        CODE_INCORRECT_INPUT_VALUES));
                    }

                    @Override
                    public void onException(Throwable e) {
                        callback.onException(e);
                    }
                },
                handler);
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
        sendApduWithSimResetErrorWorkaround(
                newRequestProviderForAll(
                        (RequestBuilder requestBuilder) -> {
                            byte[] iccidBytes =
                                    IccUtils.bcdToBytes(IccIdFormatter.padTrailingFs(iccid));
                            requestBuilder.addStoreData(
                                    Asn1Node.newBuilder(TAG_DISABLE_PROFILE)
                                            .addChild(
                                                    Asn1Node.newBuilder(TAG_CTX_COMP_0)
                                                            .addChildAsBytes(TAG_ICCID, iccidBytes))
                                            .addChildAsBoolean(TAG_CTX_1, refresh)
                                            .build()
                                            .toHex());
                        }),
                (byte[] response) -> {
                    int result;
                    // SGP.22 v2.0 DisableProfileResponse
                    result = parseSimpleResult(response);
                    switch (result) {
                        case CODE_OK:
                            return null;
                        case CODE_PROFILE_NOT_IN_EXPECTED_STATE:
                            logd("Profile is already disabled, iccid: " + pii(iccid));
                            return null;
                        default:
                            throw new EuiccCardErrorException(
                                    OPERATION_DISABLE_PROFILE, result);
                    }
                },
                callback,
                handler);
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
            AsyncResultCallback<Void> callback,
            Handler handler) {
        sendApduWithSimResetErrorWorkaround(
                newRequestProviderForAll(
                        (RequestBuilder requestBuilder) -> {
                            byte[] iccidBytes =
                                    IccUtils.bcdToBytes(IccIdFormatter.padTrailingFs(iccid));
                            requestBuilder.addStoreData(
                                    Asn1Node.newBuilder(TAG_ENABLE_PROFILE)
                                            .addChild(
                                                    Asn1Node.newBuilder(TAG_CTX_COMP_0)
                                                            .addChildAsBytes(TAG_ICCID, iccidBytes))
                                            .addChildAsBoolean(TAG_CTX_1, refresh)
                                            .build()
                                            .toHex());
                        }),
                (byte[] response) -> {
                    int result;
                    // SGP.22 v2.0 EnableProfileResponse
                    result = parseSimpleResult(response);
                    switch (result) {
                        case CODE_OK:
                            return null;
                        case CODE_PROFILE_NOT_IN_EXPECTED_STATE:
                            logd("Profile is already enabled, iccid: " + pii(iccid));
                            return null;
                        default:
                            throw new EuiccCardErrorException(
                                    OPERATION_SWITCH_TO_PROFILE, result);
                    }
                },
                callback,
                handler);
    }

    /**
     * Gets the EID of the eUICC.
     *
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 1.1.0 [GSMA SGP.22]
     */
    public final void getEid(AsyncResultCallback<String> callback, Handler handler) {
        sendApdu(
                newRequestProviderForAll(
                        (RequestBuilder requestBuilder) ->
                            requestBuilder.addStoreData(
                                    Asn1Node.newBuilder(TAG_GET_EID)
                                            .addChildAsBytes(TAG_TAG_LIST, new byte[] {TAG_EID})
                                            .build()
                                            .toHex())),
                (byte[] response) ->
                    IccUtils.bytesToHexString(parseResponse(response).getChild(TAG_EID).asBytes()),
                callback,
                handler);
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
        sendApdu(
                newRequestProviderForAll(
                        (RequestBuilder requestBuilder) ->
                            requestBuilder.addStoreData(
                                    Asn1Node.newBuilder(TAG_SET_NICKNAME)
                                            .addChildAsBytes(
                                                    TAG_ICCID,
                                                    IccUtils.bcdToBytes(
                                                            IccIdFormatter.padTrailingFs(iccid)))
                                            .addChildAsString(TAG_NICKNAME, nickname)
                                            .build()
                                            .toHex())),
                (byte[] response) -> {
                    // SGP.22 v2.0 SetNicknameResponse
                    int result = parseSimpleResult(response);
                    if (result != CODE_OK) {
                        throw new EuiccCardErrorException(OPERATION_SET_NICKNAME, result);
                    }
                    return null;
                },
                callback,
                handler);
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
        sendApdu(
                newRequestProviderForAll(
                        (RequestBuilder requestBuilder) -> {
                            byte[] iccidBytes =
                                    IccUtils.bcdToBytes(IccIdFormatter.padTrailingFs(iccid));
                            requestBuilder.addStoreData(
                                    Asn1Node.newBuilder(TAG_DELETE_PROFILE)
                                            .addChildAsBytes(TAG_ICCID, iccidBytes)
                                            .build()
                                            .toHex());
                        }),
                (byte[] response) -> {
                    // SGP.22 v2.0 DeleteProfileRequest
                    int result = parseSimpleResult(response);
                    if (result != CODE_OK) {
                        throw new EuiccCardErrorException(OPERATION_DELETE_PROFILE, result);
                    }
                    return null;
                },
                callback,
                handler);
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
        sendApduWithSimResetErrorWorkaround(
                newRequestProviderForAll(
                        (RequestBuilder requestBuilder) ->
                                requestBuilder.addStoreData(
                                        Asn1Node.newBuilder(TAG_EUICC_MEMORY_RESET)
                                                .addChildAsBits(TAG_CTX_2, options)
                                                .build()
                                                .toHex())),
                (byte[] response) -> {
                    int result = parseSimpleResult(response);
                    if (result != CODE_OK && result != CODE_NOTHING_TO_DELETE) {
                        throw new EuiccCardErrorException(OPERATION_RESET_MEMORY, result);
                    }
                    return null;
                },
                callback,
                handler);
    }

    /**
     * Gets the default SM-DP+ address from eUICC.
     *
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 2.0.0 [GSMA SGP.22]
     */
    public final void getDefaultSmdpAddress(AsyncResultCallback<String> callback, Handler handler) {
        sendApdu(
                newRequestProviderForSpec2Dot0(
                        (RequestBuilder requestBuilder) ->
                            requestBuilder.addStoreData(
                                    Asn1Node.newBuilder(TAG_GET_CONFIGURED_ADDRESSES)
                                            .build()
                                            .toHex())),
                (byte[] response) -> parseResponse(response).getChild(TAG_CTX_0).asString(),
                callback,
                handler);
    }

    /**
     * Gets the SM-DS address from eUICC.
     *
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 2.0.0 [GSMA SGP.22]
     */
    public final void getSmdsAddress(AsyncResultCallback<String> callback, Handler handler) {
        sendApdu(
                newRequestProviderForSpec2Dot0(
                        (RequestBuilder requestBuilder) ->
                            requestBuilder.addStoreData(
                                    Asn1Node.newBuilder(TAG_GET_CONFIGURED_ADDRESSES)
                                            .build()
                                            .toHex())),
                (byte[] response) -> parseResponse(response).getChild(TAG_CTX_1).asString(),
                callback,
                handler);
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
        sendApdu(
                newRequestProviderForSpec2Dot0(
                        (RequestBuilder requestBuilder) ->
                            requestBuilder.addStoreData(
                                    Asn1Node.newBuilder(TAG_SET_DEFAULT_SMDP_ADDRESS)
                                            .addChildAsString(TAG_CTX_0, defaultSmdpAddress)
                                            .build()
                                            .toHex())),
                (byte[] response) -> {
                    // SGP.22 v2.0 SetDefaultDpAddressResponse
                    int result = parseSimpleResult(response);
                    if (result != CODE_OK) {
                        throw new EuiccCardErrorException(
                                OPERATION_SET_DEFAULT_SMDP_ADDRESS, result);
                    }
                    return null;
                },
                callback,
                handler);
    }

    /**
     * Gets Rules Authorisation Table.
     *
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 2.0.0 [GSMA SGP.22]
     */
    public final void getRat(AsyncResultCallback<EuiccRat> callback, Handler handler) {
        sendApdu(
                newRequestProviderForSpec2Dot0(
                        (RequestBuilder requestBuilder) ->
                            requestBuilder.addStoreData(
                                    Asn1Node.newBuilder(TAG_GET_RAT).build().toHex())),
                (byte[] response) -> {
                    Asn1Node root = parseResponse(response);
                    List<Asn1Node> nodes = root.getChildren(TAG_CTX_COMP_0);
                    EuiccRat.Builder builder = new EuiccRat.Builder(nodes.size());
                    int size = nodes.size();
                    for (int i = 0; i < size; i++) {
                        Asn1Node node = nodes.get(i);
                        List<Asn1Node> opIdNodes = node.getChild(TAG_CTX_COMP_1).getChildren();
                        int opIdSize = opIdNodes.size();
                        CarrierIdentifier[] opIds = new CarrierIdentifier[opIdSize];
                        for (int j = 0; j < opIdSize; j++) {
                            opIds[j] = buildCarrierIdentifier(opIdNodes.get(j));
                        }
                        builder.add(
                                node.getChild(TAG_CTX_0).asBits(),
                                opIds,
                                node.getChild(TAG_CTX_2).asBits());
                    }
                    return builder.build();
                },
                callback,
                handler);
    }

    /**
     * Gets the eUICC challenge for new profile downloading.
     *
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 2.0.0 [GSMA SGP.22]
     */
    public final void getEuiccChallenge(AsyncResultCallback<byte[]> callback, Handler handler) {
        sendApdu(
                newRequestProviderForSpec2Dot0(
                        (RequestBuilder requestBuilder) ->
                            requestBuilder.addStoreData(
                                    Asn1Node.newBuilder(TAG_GET_EUICC_CHALLENGE).build().toHex())),
                (byte[] response) -> parseResponse(response).getChild(TAG_CTX_0).asBytes(),
                callback,
                handler);
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
        sendApdu(
                newRequestProviderForSpec2Dot0(
                        (RequestBuilder requestBuilder) ->
                            requestBuilder.addStoreData(
                                    Asn1Node.newBuilder(TAG_GET_EUICC_INFO_1).build().toHex())),
                EuiccCard::parseResponse,
                callback,
                handler);
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
        sendApdu(
                newRequestProviderForSpec2Dot0(
                        (RequestBuilder requestBuilder) ->
                            requestBuilder.addStoreData(
                                    Asn1Node.newBuilder(TAG_GET_EUICC_INFO_2).build().toHex())),
                EuiccCard::parseResponse,
                callback,
                handler);
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
        sendApdu(
                newRequestProviderForSpec2Dot0(
                        (RequestBuilder requestBuilder) -> {
                            byte[] imeiBytes = getDeviceId();
                            // TAC is the first 8 digits (4 bytes) of IMEI.
                            byte[] tacBytes = new byte[4];
                            System.arraycopy(imeiBytes, 0, tacBytes, 0, 4);

                            // TODO: Get device capabilities.
                            Asn1Node.Builder devCapsBuilder = Asn1Node.newBuilder(TAG_CTX_COMP_1);

                            Asn1Node.Builder ctxParams1Builder =
                                    Asn1Node.newBuilder(TAG_CTX_COMP_0)
                                            .addChildAsString(TAG_CTX_0, matchingId)
                                            .addChild(
                                                    Asn1Node.newBuilder(TAG_CTX_COMP_1)
                                                            .addChildAsBytes(TAG_CTX_0, tacBytes)
                                                            .addChild(devCapsBuilder)
                                                            .addChildAsBytes(TAG_CTX_2, imeiBytes));

                            requestBuilder.addStoreData(
                                    Asn1Node.newBuilder(TAG_AUTHENTICATE_SERVER)
                                            .addChild(serverSigned1)
                                            .addChild(serverSignature1)
                                            .addChild(euiccCiPkIdToBeUsed)
                                            .addChild(serverCertificate)
                                            .addChild(ctxParams1Builder)
                                            .build()
                                            .toHex());
                        }),
                (byte[] response) ->
                        parseResponseAndCheckSimpleError(
                                response, OPERATION_AUTHENTICATE_SERVER),
                callback,
                handler);
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
        sendApdu(
                newRequestProviderForSpec2Dot0(
                        (RequestBuilder requestBuilder) -> {
                            Asn1Node.Builder builder =
                                    Asn1Node.newBuilder(TAG_PREPARE_DOWNLOAD)
                                            .addChild(smdpSigned2)
                                            .addChild(smdpSignature2);
                            if (hashCc != null) {
                                builder.addChildAsBytes(TAG_UNI_4, hashCc);
                            }
                            requestBuilder.addStoreData(
                                    builder.addChild(smdpCertificate).build().toHex());
                        }),
                (byte[] response) -> {
                    Asn1Node root = parseResponse(response);
                    if (root.hasChild(TAG_CTX_COMP_1, TAG_UNI_2)) {
                        throw new EuiccCardErrorException(
                                OPERATION_PREPARE_DOWNLOAD,
                                root.getChild(TAG_CTX_COMP_1, TAG_UNI_2).asInteger());
                    }
                    return root;
                },
                callback,
                handler);
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
        sendApdu(
                newRequestProviderForSpec2Dot0(
                        (RequestBuilder requestBuilder) -> {
                            // initialiseSecureChannelRequest (ES8+.InitialiseSecureChannel)
                            Asn1Node initialiseSecureChannelRequest =
                                    boundProfilePackage.getChild(TAG_INITIALISE_SECURE_CHANNEL);
                            // firstSequenceOf87 (ES8+.ConfigureISDP)
                            Asn1Node firstSequenceOf87 =
                                    boundProfilePackage.getChild(TAG_CTX_COMP_0);
                            // sequenceOf88 (ES8+.StoreMetadata)
                            Asn1Node sequenceOf88 =
                                    boundProfilePackage.getChild(TAG_CTX_COMP_1);
                            List<Asn1Node> metaDataSeqs = sequenceOf88.getChildren(TAG_CTX_8);
                            // sequenceOf86 (ES8+.LoadProfileElements #1)
                            Asn1Node sequenceOf86 =
                                    boundProfilePackage.getChild(TAG_CTX_COMP_3);
                            List<Asn1Node> elementSeqs = sequenceOf86.getChildren(TAG_CTX_6);

                            requestBuilder.addStoreData(
                                    boundProfilePackage.getHeadAsHex()
                                            + initialiseSecureChannelRequest.toHex());

                            requestBuilder.addStoreData(firstSequenceOf87.toHex());

                            requestBuilder.addStoreData(sequenceOf88.getHeadAsHex());
                            int size = metaDataSeqs.size();
                            for (int i = 0; i < size; i++) {
                                requestBuilder.addStoreData(metaDataSeqs.get(i).toHex());
                            }

                            if (boundProfilePackage.hasChild(TAG_CTX_COMP_2)) {
                                requestBuilder.addStoreData(
                                        boundProfilePackage.getChild(TAG_CTX_COMP_2).toHex());
                            }

                            requestBuilder.addStoreData(sequenceOf86.getHeadAsHex());
                            size = elementSeqs.size();
                            for (int i = 0; i < size; i++) {
                                requestBuilder.addStoreData(elementSeqs.get(i).toHex());
                            }
                        }),
                (byte[] response) -> {
                    // SGP.22 v2.0 ErrorResult
                    Asn1Node root = parseResponse(response);
                    if (root.hasChild(
                            TAG_PROFILE_INSTALLATION_RESULT_DATA,
                            TAG_CTX_COMP_2,
                            TAG_CTX_COMP_1,
                            TAG_CTX_1)) {
                        Asn1Node errorNode =
                                root.getChild(
                                        TAG_PROFILE_INSTALLATION_RESULT_DATA,
                                        TAG_CTX_COMP_2,
                                        TAG_CTX_COMP_1,
                                        TAG_CTX_1);
                        throw new EuiccCardErrorException(
                                OPERATION_LOAD_BOUND_PROFILE_PACKAGE,
                                errorNode.asInteger(),
                                errorNode);
                    }
                    return root;
                },
                callback,
                handler);
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
        sendApdu(
                newRequestProviderForSpec2Dot0(
                        (RequestBuilder requestBuilder) ->
                            requestBuilder.addStoreData(
                                    Asn1Node.newBuilder(TAG_CANCEL_SESSION)
                                            .addChildAsBytes(TAG_CTX_0, transactionId)
                                            .addChildAsInteger(TAG_CTX_1, reason)
                                            .build()
                                            .toHex())),
                (byte[] response) ->
                        parseResponseAndCheckSimpleError(
                                response, OPERATION_CANCEL_SESSION),
                callback,
                handler);
    }

    /**
     * Lists all notifications of the given {@code notificationEvents}.
     *
     * @param events Bits of the event types ({@link EuiccNotification.Event}) to list.
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 2.0.0 [GSMA SGP.22]
     */
    public final void listNotifications(
            @EuiccNotification.Event int events,
            AsyncResultCallback<EuiccNotification[]> callback,
            Handler handler) {
        sendApdu(
                newRequestProviderForSpec2Dot0(
                        (RequestBuilder requestBuilder) ->
                            requestBuilder.addStoreData(
                                    Asn1Node.newBuilder(TAG_LIST_NOTIFICATION)
                                            .addChildAsBits(TAG_CTX_1, events)
                                            .build()
                                            .toHex())),
                (byte[] response) -> {
                    Asn1Node root =
                            parseResponseAndCheckSimpleError(
                                    response, OPERATION_LIST_NOTIFICATIONS);
                    List<Asn1Node> nodes = root.getChild(TAG_CTX_COMP_0).getChildren();
                    EuiccNotification[] notifications = new EuiccNotification[nodes.size()];
                    for (int i = 0; i < notifications.length; ++i) {
                        notifications[i] = createNotification(nodes.get(i));
                    }
                    return notifications;
                },
                callback,
                handler);
    }

    /**
     * Retrieves contents of all notification of the given {@code notificationEvents}.
     *
     * @param events Bits of the event types ({@link EuiccNotification.Event}) to list.
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 2.0.0 [GSMA SGP.22]
     */
    public final void retrieveNotificationList(
            @EuiccNotification.Event int events,
            AsyncResultCallback<EuiccNotification[]> callback,
            Handler handler) {
        sendApdu(
                newRequestProviderForSpec2Dot0(
                        (RequestBuilder requestBuilder) ->
                            requestBuilder.addStoreData(
                                    Asn1Node.newBuilder(TAG_RETRIEVE_NOTIFICATIONS_LIST)
                                            .addChild(
                                                    Asn1Node.newBuilder(TAG_CTX_COMP_0)
                                                            .addChildAsBits(TAG_CTX_1, events))
                                            .build()
                                            .toHex())),
                (byte[] response) -> {
                    Asn1Node root = parseResponse(response);
                    if (root.hasChild(TAG_CTX_1)) {
                        // SGP.22 v2.0 RetrieveNotificationsListResponse
                        int error = root.getChild(TAG_CTX_1).asInteger();
                        switch (error) {
                            case CODE_NO_RESULT_AVAILABLE:
                                return new EuiccNotification[0];
                            default:
                                throw new EuiccCardErrorException(
                                        OPERATION_RETRIEVE_NOTIFICATION, error);
                        }
                    }
                    List<Asn1Node> nodes = root.getChild(TAG_CTX_COMP_0).getChildren();
                    EuiccNotification[] notifications = new EuiccNotification[nodes.size()];
                    for (int i = 0; i < notifications.length; ++i) {
                        notifications[i] = createNotification(nodes.get(i));
                    }
                    return notifications;
                },
                callback,
                handler);
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
        sendApdu(
                newRequestProviderForSpec2Dot0(
                        (RequestBuilder requestBuilder) ->
                            requestBuilder.addStoreData(
                                    Asn1Node.newBuilder(TAG_RETRIEVE_NOTIFICATIONS_LIST)
                                            .addChild(
                                                    Asn1Node.newBuilder(TAG_CTX_COMP_0)
                                                            .addChildAsInteger(
                                                                    TAG_CTX_0, seqNumber))
                                            .build()
                                            .toHex())),
                (byte[] response) -> {
                    Asn1Node root =
                            parseResponseAndCheckSimpleError(
                                    response, OPERATION_RETRIEVE_NOTIFICATION);
                    List<Asn1Node> nodes = root.getChild(TAG_CTX_COMP_0).getChildren();
                    if (nodes.size() > 0) {
                        return createNotification(nodes.get(0));
                    }
                    return null;
                },
                callback,
                handler);
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
        sendApdu(
                newRequestProviderForSpec2Dot0(
                        (RequestBuilder requestBuilder) ->
                                requestBuilder.addStoreData(
                                        Asn1Node.newBuilder(TAG_REMOVE_NOTIFICATION_FROM_LIST)
                                                .addChildAsInteger(TAG_CTX_0, seqNumber)
                                                .build()
                                                .toHex())),
                (byte[] response) -> {
                    // SGP.22 v2.0 NotificationSentResponse
                    int result = parseSimpleResult(response);
                    if (result != CODE_OK && result != CODE_NOTHING_TO_DELETE) {
                        throw new EuiccCardErrorException(
                                OPERATION_REMOVE_NOTIFICATION_FROM_LIST, result);
                    }
                    return null;
                },
                callback,
                handler);
    }

    /**
     * Creates an instance from the ASN.1 data.
     *
     * @param node This should be either {@code NotificationMetadata} or {@code PendingNotification}
     *     defined by SGP.22 v2.0.
     * @throws TagNotFoundException If no notification tag is found in the bytes.
     * @throws InvalidAsn1DataException If no valid data is found in the bytes.
     */
    private EuiccNotification createNotification(Asn1Node node)
            throws TagNotFoundException, InvalidAsn1DataException {
        Asn1Node metadataNode;
        if (node.getTag() == TAG_NOTIFICATION_METADATA) {
            metadataNode = node;
        } else if (node.getTag() == TAG_PROFILE_INSTALLATION_RESULT) {
            metadataNode =
                    node.getChild(TAG_PROFILE_INSTALLATION_RESULT_DATA, TAG_NOTIFICATION_METADATA);
        } else {
            // Other signed notification
            metadataNode = node.getChild(TAG_NOTIFICATION_METADATA);
        }
        // noinspection WrongConstant
        return new EuiccNotification(
                metadataNode.getChild(TAG_SEQ).asInteger(),
                metadataNode.getChild(TAG_TARGET_ADDR).asString(),
                metadataNode.getChild(TAG_EVENT).asBits(),
                node.getTag() == TAG_NOTIFICATION_METADATA ? null : node.toBytes());
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    protected byte[] getDeviceId() {
        byte[] imeiBytes = new byte[8];
        Phone phone = PhoneFactory.getPhone(0);
        if (phone != null) {
            IccUtils.bcdToBytes(phone.getDeviceId(), imeiBytes);
        }
        return imeiBytes;
    }

    /** Returns the first CONTEXT [0] as an integer. */
    private static int parseSimpleResult(byte[] response)
            throws EuiccCardException, TagNotFoundException, InvalidAsn1DataException {
        return parseResponse(response).getChild(TAG_CTX_0).asInteger();
    }

    private static Asn1Node parseResponse(byte[] response)
            throws EuiccCardException, InvalidAsn1DataException {
        Asn1Decoder decoder = new Asn1Decoder(response);
        if (!decoder.hasNextNode()) {
            throw new EuiccCardException("Empty response", null);
        }
        return decoder.nextNode();
    }

    /**
     * Parses the bytes into an ASN1 node and check if there is an error code represented at the
     * context 1 tag. If there is an error code, an {@link EuiccCardErrorException} will be thrown
     * with the given operation code.
     */
    private static Asn1Node parseResponseAndCheckSimpleError(
            byte[] response, @OperationCode int opCode)
            throws EuiccCardException, InvalidAsn1DataException, TagNotFoundException {
        Asn1Node root = parseResponse(response);
        if (root.hasChild(TAG_CTX_1)) {
            throw new EuiccCardErrorException(opCode, root.getChild(TAG_CTX_1).asInteger());
        }
        return root;
    }

    private static CarrierIdentifier buildCarrierIdentifier(Asn1Node node)
            throws InvalidAsn1DataException, TagNotFoundException {
        String gid1 = null;
        if (node.hasChild(TAG_CTX_1)) {
            gid1 = IccUtils.bytesToHexString(node.getChild(TAG_CTX_1).asBytes());
        }
        String gid2 = null;
        if (node.hasChild(TAG_CTX_2)) {
            gid2 = IccUtils.bytesToHexString(node.getChild(TAG_CTX_2).asBytes());
        }
        return new CarrierIdentifier(node.getChild(TAG_CTX_0).asBytes(), gid1, gid2);
    }

    @Nullable
    private static UiccAccessRule[] buildUiccAccessRule(List<Asn1Node> nodes)
            throws InvalidAsn1DataException, TagNotFoundException {
        if (nodes.isEmpty()) {
            return null;
        }
        int count = nodes.size();
        UiccAccessRule[] rules = new UiccAccessRule[count];
        for (int i = 0; i < count; i++) {
            Asn1Node node = nodes.get(i);
            Asn1Node refDoNode = node.getChild(TAG_REF_DO);
            byte[] signature = refDoNode.getChild(TAG_DEVICE_APP_ID_REF_DO).asBytes();

            String packageName = null;
            if (refDoNode.hasChild(TAG_PKG_REF_DO)) {
                packageName = refDoNode.getChild(TAG_PKG_REF_DO).asString();
            }
            long accessType = 0;
            if (node.hasChild(TAG_AR_DO, TAG_PERM_AR_DO)) {
                Asn1Node permArDoNode = node.getChild(TAG_AR_DO, TAG_PERM_AR_DO);
                accessType = permArDoNode.asRawLong();
            }
            rules[i] = new UiccAccessRule(signature, packageName, accessType);
        }
        return rules;
    }
}
