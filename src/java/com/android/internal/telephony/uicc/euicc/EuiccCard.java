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

import android.annotation.Nullable;
import android.content.Context;
import android.os.Handler;
import android.service.carrier.CarrierIdentifier;
import android.service.euicc.EuiccProfileInfo;
import android.telephony.Rlog;
import android.telephony.SubscriptionInfo;
import android.telephony.UiccAccessRule;
import android.telephony.euicc.EuiccCardManager;
import android.telephony.euicc.EuiccNotification;
import android.telephony.euicc.EuiccRat;
import android.text.TextUtils;

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
import com.android.internal.telephony.uicc.euicc.EuiccCardErrorException.OperationCode;
import com.android.internal.telephony.uicc.euicc.apdu.ApduException;
import com.android.internal.telephony.uicc.euicc.apdu.ApduSender;
import com.android.internal.telephony.uicc.euicc.apdu.RequestBuilder;
import com.android.internal.telephony.uicc.euicc.apdu.RequestProvider;
import com.android.internal.telephony.uicc.euicc.async.AsyncResultCallback;
import com.android.internal.telephony.uicc.euicc.async.AsyncResultHelper;

import java.util.List;

/**
 * This represents an eUICC card to perform profile management operations asynchronously. This class
 * includes methods defined by different versions of GSMA Spec (SGP.22).
 *
 * @hide
 */
public class EuiccCard extends UiccCard {
    private static final String ISD_R_AID = "A0000005591010FFFFFFFF8900000100";

    private static final EuiccSpecVersion SGP_2_0 = new EuiccSpecVersion(2, 0, 0);

    // These interfaces are used for simplifying the code by leveraging lambdas.
    private interface ApduRequestBuilder {
        void build(RequestBuilder requestBuilder)
                throws EuiccCardException, TagNotFoundException, InvalidAsn1DataException;
    }

    private interface ApduResponseHandler<T> {
        T handleResult(byte[] response)
                throws EuiccCardException, TagNotFoundException, InvalidAsn1DataException;
    }

    private final ApduSender mApduSender;
    private final Object mLock = new Object();
    private EuiccSpecVersion mSpecVersion;

    public EuiccCard(Context c, CommandsInterface ci, IccCardStatus ics, int phoneId) {
        super(c, ci, ics, phoneId);
        // TODO: Set supportExtendedApdu based on ATR.
        mApduSender = new ApduSender(ci, ISD_R_AID, false /* supportExtendedApdu */);
    }

    /**
     * Gets the GSMA RSP specification version supported by this eUICC. This may return null if the
     * version cannot be read.
     */
    public void getSpecVersion(AsyncResultCallback<EuiccSpecVersion> callback, Handler handler) {
        if (mSpecVersion != null) {
            AsyncResultHelper.returnResult(mSpecVersion, callback, handler);
            return;
        }

        sendApdu(newRequestProvider((RequestBuilder requestBuilder) -> { /* Do nothing */ }),
                (byte[] response) -> mSpecVersion, callback, handler);
    }

    /**
     * Gets a list of user-visible profiles.
     *
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 1.1.0 [GSMA SGP.22]
     */
    public void getAllProfiles(AsyncResultCallback<EuiccProfileInfo[]> callback, Handler handler) {
        // TODO: to be implemented.
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

    /**
     * Gets a profile.
     *
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 1.1.0 [GSMA SGP.22]
     */
    public final void getProfile(String iccid, AsyncResultCallback<EuiccProfileInfo> callback,
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
    public void disableProfile(String iccid, boolean refresh, AsyncResultCallback<Void> callback,
            Handler handler) {
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
     * Resets the eUICC memory (e.g., remove all profiles).
     *
     * @param options Bits of the options of resetting which parts of the eUICC memory.
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 1.1.0 [GSMA SGP.22]
     */
    public void resetMemory(@EuiccCardManager.ResetOption int options,
            AsyncResultCallback<Void> callback, Handler handler) {
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
    public void setDefaultSmdpAddress(String defaultSmdpAddress, AsyncResultCallback<Void> callback,
            Handler handler) {
        // TODO: to be implemented.
    }

    /**
     * Gets Rules Authorisation Table.
     *
     * @param callback The callback to get the result.
     * @param handler The handler to run the callback.
     * @since 2.0.0 [GSMA SGP.22]
     */
    public void getRulesAuthTable(AsyncResultCallback<EuiccRat> callback, Handler handler) {
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
    public void cancelSession(byte[] transactionId, @EuiccCardManager.CancelReason int reason,
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
     * Retrieves contents of all notification of the given {@code events}.
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

    private RequestProvider newRequestProvider(ApduRequestBuilder builder) {
        return (selectResponse, requestBuilder) -> {
            EuiccSpecVersion ver = getOrExtractSpecVersion(selectResponse);
            if (ver == null) {
                throw new EuiccCardException("Cannot get eUICC spec version.");
            }
            try {
                if (ver.compareTo(SGP_2_0) < 0) {
                    throw new EuiccCardException("eUICC spec version is unsupported: " + ver);
                }
                builder.build(requestBuilder);
            } catch (InvalidAsn1DataException | TagNotFoundException e) {
                throw new EuiccCardException("Cannot parse ASN1 to build request.", e);
            }
        };
    }

    private EuiccSpecVersion getOrExtractSpecVersion(byte[] selectResponse) {
        // Uses the cached version.
        if (mSpecVersion != null) {
            return mSpecVersion;
        }
        // Parses and caches the version.
        EuiccSpecVersion ver = EuiccSpecVersion.fromOpenChannelResponse(selectResponse);
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
     * A wrapper on {@link ApduSender#send(RequestProvider, AsyncResultCallback, Handler)} to
     * leverage lambda to simplify the sending APDU code.EuiccCardErrorException.
     *
     * @param requestBuilder Builds the request of APDU commands.
     * @param responseHandler Converts the APDU response from bytes to expected result.
     * @param <T> Type of the originally expected result.
     */
    private <T> void sendApdu(RequestProvider requestBuilder,
            ApduResponseHandler<T> responseHandler, AsyncResultCallback<T> callback,
            Handler handler) {
        mApduSender.send(requestBuilder, new AsyncResultCallback<byte[]>() {
            @Override
            public void onResult(byte[] response) {
                try {
                    callback.onResult(responseHandler.handleResult(response));
                } catch (EuiccCardException e) {
                    callback.onException(e);
                } catch (InvalidAsn1DataException | TagNotFoundException e) {
                    callback.onException(new EuiccCardException(
                            "Cannot parse response: " + IccUtils.bytesToHexString(response), e));
                }
            }

            @Override
            public void onException(Throwable e) {
                callback.onException(new EuiccCardException("Cannot send APDU.", e));
            }
        }, handler);
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

    /** Strip all the trailing Fs of an iccId. */
    public static String stripTrailingFs(byte[] iccId) {
        String iccIdString = Asn1Converter.bcdBytesToString(iccId);
        if (iccIdString.endsWith("F")) {
            iccIdString = iccIdString.substring(0, iccIdString.indexOf('F'));
        }
        return iccIdString;
    }
}
