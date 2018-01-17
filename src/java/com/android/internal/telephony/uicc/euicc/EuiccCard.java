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
    private static final int TAG_CTX_3 = 0x83;
    private static final int TAG_CTX_4 = 0x84;
    private static final int TAG_CTX_5 = 0x85;
    private static final int TAG_CTX_6 = 0x86;
    private static final int TAG_CTX_7 = 0x87;
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

    public EuiccCard(Context c, CommandsInterface ci, IccCardStatus ics, int phoneId) {
        super(c, ci, ics, phoneId);
    }

    /** @return The GSMA SGP.22 spec version that this card supports. */
    public EuiccSpecVersion getSpecVersion() {
        // TODO: to be implemented.
        return null;
    }

    @Override
    public void buildProfile(Asn1Node profileNode, EuiccProfile.Builder profileBuilder)
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
            profileBuilder.setOperatorId(buildOperatorId(profileNode.getChild(TAG_OPERATOR_ID)));
        }

        if (profileNode.hasChild(TAG_PROFILE_STATE)) {
            // noinspection WrongConstant
            profileBuilder.setState(profileNode.getChild(TAG_PROFILE_STATE).asInteger());
        } else {
            profileBuilder.setState(EuiccProfile.ProfileState.DISABLED);
        }

        if (profileNode.hasChild(TAG_PROFILE_CLASS)) {
            // noinspection WrongConstant
            profileBuilder.setProfileClass(profileNode.getChild(TAG_PROFILE_CLASS).asInteger());
        } else {
            profileBuilder.setProfileClass(EuiccProfile.ProfileClass.OPERATIONAL);
        }

        if (profileNode.hasChild(TAG_PROFILE_POLICY_RULE)) {
            // noinspection WrongConstant
            profileBuilder.setPolicyRules(profileNode.getChild(TAG_PROFILE_POLICY_RULE).asBits());
        }

        if (profileNode.hasChild(TAG_CARRIER_PRIVILEGE_RULES)) {
            profileBuilder.setCarrierPrivilegeRules(
                    buildCarrierPrivilegeRules(
                            profileNode
                                    .getChild(TAG_CARRIER_PRIVILEGE_RULES)
                                    .getChildren(TAG_REF_AR_DO)));
        }
    }

    private void loadProfiles() throws UiccChannelException, EuiccCardException {
        if (mProfiles == null) {
            String response;
            try (UiccLogicalChannel ch = UiccLogicalChannel.openIsdR(mTm)) {
                String command =
                        Asn1Node.newBuilder(TAG_GET_PROFILES)
                                .addChildAsBytes(TAG_TAG_LIST, EUICC_PROFILE_TAGS)
                                .build()
                                .toHex();
                response = ch.execStoreData(command);
            }
            try {
                List<Asn1Node> profileNodes =
                        new Asn1Decoder(response)
                                .nextNode()
                                .getChild(TAG_CTX_COMP_0)
                                .getChildren(TAG_PROFILE_INFO);
                int size = profileNodes.size();
                EuiccProfile[] profiles = new EuiccProfile[size];
                String enabledIccid = null;
                int profileCount = 0;
                for (int i = 0; i < size; i++) {
                    Asn1Node profileNode = profileNodes.get(i);
                    if (!profileNode.hasChild(TAG_ICCID)) {
                        EuiccLog.get().wtf("Profile must have an ICCID.");
                        continue;
                    }
                    EuiccProfile.Builder profileBuilder = new EuiccProfile.Builder();
                    buildProfile(profileNode, profileBuilder);

                    EuiccProfile profile = profileBuilder.build();
                    String iccidStr = profile.getIccid();
                    if (profile.getState() == EuiccProfile.ProfileState.ENABLED) {
                        enabledIccid = iccidStr;
                    }
                    profiles[profileCount++] = profile;
                }

                mEnabledIccid = enabledIccid;
                mProfiles = Arrays.copyOf(profiles, profiles.length);
            } catch (TagNotFoundException e) {
                throw new EuiccCardInvalidResponseException("Cannot find profile info tag.", e);
            } catch (InvalidAsn1DataException e) {
                throw new EuiccCardInvalidResponseException("Cannot parse profile info.", e);
            }
        }
    }

    private static OperatorId buildOperatorId(Asn1Node node)
            throws InvalidAsn1DataException, TagNotFoundException {
        byte[] gid1 = null;
        if (node.hasChild(TAG_CTX_1)) {
            gid1 = node.getChild(TAG_CTX_1).asBytes();
        }
        byte[] gid2 = null;
        if (node.hasChild(TAG_CTX_2)) {
            gid2 = node.getChild(TAG_CTX_2).asBytes();
        }
        return new OperatorId(node.getChild(TAG_CTX_0).asBytes(), gid1, gid2);
    }

    @Nullable
    private static CarrierPrivilegeRule[] buildCarrierPrivilegeRules(List<Asn1Node> nodes)
            throws InvalidAsn1DataException, TagNotFoundException {
        if (nodes.isEmpty()) {
            return null;
        }
        int count = nodes.size();
        CarrierPrivilegeRule[] rules = new CarrierPrivilegeRule[count];
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
            rules[i] = new CarrierPrivilegeRule(signature, packageName, accessType);
        }
        return rules;
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
    public void getProfile(String iccid, AsyncResultCallback<EuiccProfileInfo> callback,
            Handler handler) {
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
    public void disableProfile(String iccid, boolean refresh,
            AsyncResultCallback<Void> callback, Handler handler) {
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
    public void switchToProfile(String iccid, boolean refresh, AsyncResultCallback<Void> callback,
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
    public void setNickname(String iccid, String nickname, AsyncResultCallback<Void> callback,
            Handler handler) {
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
    public void authenticateServer(String matchingId, Asn1Node serverSigned1,
            Asn1Node serverSignature1, Asn1Node euiccCiPkIdToBeUsed, Asn1Node serverCertificate,
            AsyncResultCallback<Asn1Node> callback, Handler handler) {
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
    public void prepareDownload(@Nullable byte[] hashCc, Asn1Node smdpSigned2,
            Asn1Node smdpSignature2, Asn1Node smdpCertificate,
            AsyncResultCallback<Asn1Node> callback, Handler handler) {
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
    public void loadBoundProfilePackage(Asn1Node boundProfilePackage,
            AsyncResultCallback<Asn1Node> callback, Handler handler) {
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
    public void cancelSession(byte[] transactionId, @CancelReason int reason,
            AsyncResultCallback<Asn1Node> callback, Handler handler) {
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
    public void listNotifications(@EuiccNotification.Event int events,
            AsyncResultCallback<EuiccNotification[]> callback, Handler handler) {
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
    public void retrieveNotificationList(@EuiccNotification.Event int events,
            AsyncResultCallback<EuiccNotification[]> callback, Handler handler) {
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
    public void retrieveNotification(int seqNumber, AsyncResultCallback<EuiccNotification> callback,
            Handler handler) {
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
    public void removeNotificationFromList(int seqNumber, AsyncResultCallback<Void> callback,
            Handler handler) {
        // TODO: to be implemented.
    }
}
