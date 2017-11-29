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

import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.telephony.TelephonyManager;
import com.android.euicc.EuiccApplication;
import com.android.euicc.asn1.Asn1Converter;
import com.android.euicc.asn1.Asn1Decoder;
import com.android.euicc.asn1.Asn1Node;
import com.android.euicc.asn1.InvalidAsn1DataException;
import com.android.euicc.asn1.TagNotFoundException;
import com.android.euicc.card.EuiccCardErrorException.DeleteProfileCode;
import com.android.euicc.card.EuiccCardErrorException.DisableProfileCode;
import com.android.euicc.card.EuiccCardErrorException.GetProfileCode;
import com.android.euicc.card.EuiccCardErrorException.OperationCode;
import com.android.euicc.card.EuiccCardErrorException.RemoveNotificationFromListCode;
import com.android.euicc.card.EuiccCardErrorException.ResetMemoryCode;
import com.android.euicc.card.EuiccCardErrorException.RetrieveNotificationCode;
import com.android.euicc.card.EuiccCardErrorException.SetDefaultSmdpAddressCode;
import com.android.euicc.card.EuiccCardErrorException.SetNicknameCode;
import com.android.euicc.card.EuiccCardErrorException.SwitchToProfileCode;
import com.android.euicc.data.CarrierPrivilegeRule;
import com.android.euicc.data.EuiccNotification;
import com.android.euicc.data.EuiccProfile;
import com.android.euicc.data.EuiccRat;
import com.android.euicc.data.EuiccSpecVersion;
import com.android.euicc.data.OperatorId;
import com.android.euicc.logging.EuiccLog;
import com.android.euicc.logging.Pii;
import com.android.euicc.telephony.DeviceCapabilitiesProvider;
import com.android.euicc.telephony.UiccChannelException;
import com.android.euicc.telephony.UiccLogicalChannel;
import java.util.Arrays;
import java.util.List;

/** {@link EuiccCard} implementation for GSMA SPG.22 v2.0+. */
public class EuiccCardImplV2 extends EuiccCard {
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

    private final TelephonyManager mTm;

    // Cached eUICC states.
    private EuiccProfile[] mProfiles;
    private String mEnabledIccid;

    private String mEid;
    private String mDefaultSmdpAddress;
    private String mSmdsAddress;

    public EuiccCardImplV2(
            Looper handleThreadLooper, EuiccSpecVersion cardVersion, TelephonyManager tm) {
        super(handleThreadLooper, cardVersion);
        mTm = tm;
    }

    @VisibleForTesting
    EuiccCardImplV2(EuiccSpecVersion cardVersion, TelephonyManager tm) {
        super(cardVersion);
        mTm = tm;
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

    @Override
    protected EuiccProfile[] getAllProfiles() throws UiccChannelException, EuiccCardException {
        loadProfiles();
        return Arrays.copyOf(mProfiles, mProfiles.length);
    }

    @Override
    protected EuiccProfile getProfile(String iccid)
            throws UiccChannelException, EuiccCardException {
        loadProfiles();
        int profileIndex = indexOfProfile(iccid);
        if (profileIndex < 0) {
            throw new EuiccCardErrorException(
                    OperationCode.GET_PROFILE, GetProfileCode.INCORRECT_INPUT_VALUES);
        }
        return mProfiles[profileIndex];
    }

    @Override
    protected void disableProfile(String iccid, boolean refresh)
            throws UiccChannelException, EuiccCardException {
        int profileIndex = -1;
        if (mProfiles != null) {
            profileIndex = indexOfProfile(iccid);
            if (profileIndex < 0) {
                throw new EuiccCardErrorException(
                        OperationCode.DISABLE_PROFILE, DisableProfileCode.ICCID_OR_AID_NOT_FOUND);
            }
            if (!iccid.equals(mEnabledIccid)) {
                EuiccLog.get().d("Profile is already disabled, iccid: %s", Pii.iccid(iccid));
                return;
            }
        }

        String command =
                Asn1Node.newBuilder(TAG_DISABLE_PROFILE)
                        .addChild(
                                Asn1Node.newBuilder(TAG_CTX_COMP_0)
                                        .addChildAsBytes(
                                                TAG_ICCID,
                                                Asn1Converter.bcdStringToBytes(
                                                        IccIdFormatter.padTrailingFs(iccid))))
                        .addChildAsBoolean(TAG_CTX_1, refresh)
                        .build()
                        .toHex();
        String response;
        int result;
        try (UiccLogicalChannel ch = UiccLogicalChannel.openIsdR(mTm)) {
            response = ch.execStoreData(command);
            // SGP.22 v2.0 DisableProfileResponse
            result = parseSimpleResult(response);
        } catch (UiccChannelException e) {
            // TODO(b/37688526): This is a workaround solution, remove it when final solution
            // applied
            if (e.getApduStatus() == APDU_ERROR_SIM_REFRESH) {
                EuiccLog.get().i("Sim is refreshed after disabling profile, no response got.");
                result = DisableProfileCode.OK;
            } else {
                throw e;
            }
        }

        switch (result) {
            case DisableProfileCode.OK:
                if (profileIndex >= 0) {
                    mEnabledIccid = null;
                    mProfiles[profileIndex] =
                            new EuiccProfile.Builder(mProfiles[profileIndex])
                                    .setState(EuiccProfile.ProfileState.DISABLED)
                                    .build();
                }
                break;
            case DisableProfileCode.PROFILE_NOT_IN_ENABLED_STATE:
                EuiccLog.get().d("Profile is already disabled, iccid: %s", Pii.iccid(iccid));
                return;
            case DisableProfileCode.ICCID_OR_AID_NOT_FOUND:
                throw new EuiccCardErrorException(OperationCode.DISABLE_PROFILE, result);
            default:
                throw new EuiccCardErrorException(OperationCode.DISABLE_PROFILE, result);
        }
    }

    @Override
    protected EuiccProfile switchToProfile(String iccid, boolean refresh)
            throws UiccChannelException, EuiccCardException {
        int profileIndex = -1;
        loadProfiles();
        profileIndex = indexOfProfile(iccid);
        if (profileIndex < 0) {
            throw new EuiccCardErrorException(
                    OperationCode.SWITCH_TO_PROFILE, SwitchToProfileCode.ICCID_OR_AID_NOT_FOUND);
        }
        if (iccid.equals(mEnabledIccid)) {
            EuiccLog.get().d("Profile is already enabled, iccid: %s", Pii.iccid(iccid));
            return mProfiles[profileIndex];
        }

        String command =
                Asn1Node.newBuilder(TAG_ENABLE_PROFILE)
                        .addChild(
                                Asn1Node.newBuilder(TAG_CTX_COMP_0)
                                        .addChildAsBytes(
                                                TAG_ICCID,
                                                Asn1Converter.bcdStringToBytes(
                                                        IccIdFormatter.padTrailingFs(iccid))))
                        .addChildAsBoolean(TAG_CTX_1, refresh)
                        .build()
                        .toHex();
        String response;
        int result;
        try (UiccLogicalChannel ch = UiccLogicalChannel.openIsdR(mTm)) {
            response = ch.execStoreData(command);
            // SGP.22 v2.0 EnableProfileResponse
            result = parseSimpleResult(response);
        } catch (UiccChannelException e) {
            // TODO(b/37688526): This is a workaround solution, remove it when final solution
            // applied
            if (e.getApduStatus() == APDU_ERROR_SIM_REFRESH) {
                EuiccLog.get().i("Sim is refreshed after disabling profile, no response got.");
                result = SwitchToProfileCode.OK;
            } else {
                throw e;
            }
        }
        switch (result) {
            case SwitchToProfileCode.OK:
                int prevEnabledIndex = indexOfProfile(mEnabledIccid);
                if (prevEnabledIndex >= 0) {
                    mProfiles[prevEnabledIndex] =
                            new EuiccProfile.Builder(mProfiles[prevEnabledIndex])
                                    .setState(EuiccProfile.ProfileState.DISABLED)
                                    .build();
                }
                mEnabledIccid = iccid;
                mProfiles[profileIndex] =
                        new EuiccProfile.Builder(mProfiles[profileIndex])
                                .setState(EuiccProfile.ProfileState.ENABLED)
                                .build();
                return mProfiles[profileIndex];
            case SwitchToProfileCode.PROFILE_NOT_IN_DISABLED_STATE:
                EuiccLog.get().d("Profile is already enabled, iccid: %s", Pii.iccid(iccid));
                return mProfiles[profileIndex];
            case SwitchToProfileCode.ICCID_OR_AID_NOT_FOUND:
                throw new EuiccCardErrorException(OperationCode.SWITCH_TO_PROFILE, result);
            default:
                throw new EuiccCardErrorException(OperationCode.SWITCH_TO_PROFILE, result);
        }
    }

    @Override
    protected String getEid() throws UiccChannelException, EuiccCardException {
        if (mEid == null) {
            String response;
            try (UiccLogicalChannel ch = UiccLogicalChannel.openIsdR(mTm)) {
                String command =
                        Asn1Node.newBuilder(TAG_GET_EID)
                                .addChildAsBytes(TAG_TAG_LIST, new byte[] {TAG_EID})
                                .build()
                                .toHex();
                response = ch.execStoreData(command);
            }
            Asn1Node eidAsn1Node = parseResponse(response);
            try {
                mEid = Asn1Converter.bytesToHex(eidAsn1Node.getChild(TAG_EID).asBytes());
            } catch (TagNotFoundException | InvalidAsn1DataException e) {
                throw new EuiccCardInvalidResponseException(
                        "Cannot get EID: response = " + response, e);
            }
        }
        return mEid;
    }

    @Override
    protected void setNickname(String iccid, String nickname)
            throws UiccChannelException, EuiccCardException {
        int profileIndex = -1;
        if (mProfiles != null) {
            profileIndex = indexOfProfile(iccid);
            if (profileIndex < 0) {
                throw new EuiccCardErrorException(
                        OperationCode.SET_NICKNAME, SetNicknameCode.ICCID_NOT_FOUND);
            }
        }

        String command =
                Asn1Node.newBuilder(TAG_SET_NICKNAME)
                        .addChildAsBytes(
                                TAG_ICCID,
                                Asn1Converter.bcdStringToBytes(IccIdFormatter.padTrailingFs(iccid)))
                        .addChildAsString(TAG_NICKNAME, nickname)
                        .build()
                        .toHex();
        String response;
        try (UiccLogicalChannel ch = UiccLogicalChannel.openIsdR(mTm)) {
            response = ch.execStoreData(command);
        }
        // SGP.22 v2.0 SetNicknameResponse
        int result = parseSimpleResult(response);
        switch (result) {
            case SetNicknameCode.OK:
                if (profileIndex >= 0) {
                    mProfiles[profileIndex] =
                            new EuiccProfile.Builder(mProfiles[profileIndex])
                                    .setNickname(nickname)
                                    .build();
                }
                break;
            case SetNicknameCode.ICCID_NOT_FOUND:
                EuiccLog.get()
                        .wtf(
                                "Got error code %d from eUICC. "
                                        + "Profile in memory is out of sync with eUICC: iccid=%s",
                                result, Pii.iccid(iccid));
                throw new EuiccCardErrorException(OperationCode.SET_NICKNAME, result);
            default:
                throw new EuiccCardErrorException(OperationCode.SET_NICKNAME, result);
        }
    }

    @Override
    protected void deleteProfile(String iccid) throws UiccChannelException, EuiccCardException {
        loadProfiles();
        int profileIndex = indexOfProfile(iccid);
        if (profileIndex < 0) {
            throw new EuiccCardErrorException(
                    OperationCode.DELETE_PROFILE, DeleteProfileCode.ICCID_OR_AID_NOT_FOUND);
        }

        if (mProfiles[profileIndex].getProfileClass() == EuiccProfile.ProfileClass.TESTING) {
            throw new EuiccCardException("Cannot delete test profile.");
        }

        String command =
                Asn1Node.newBuilder(TAG_DELETE_PROFILE)
                        .addChildAsBytes(
                                TAG_ICCID,
                                Asn1Converter.bcdStringToBytes(IccIdFormatter.padTrailingFs(iccid)))
                        .build()
                        .toHex();
        String response;
        try (UiccLogicalChannel ch = UiccLogicalChannel.openIsdR(mTm)) {
            response = ch.execStoreData(command);
        }
        // SGP.22 v2.0 DeleteProfileRequest
        int result = parseSimpleResult(response);
        switch (result) {
            case DeleteProfileCode.OK:
                if (profileIndex >= 0) {
                    if (iccid.equals(mEnabledIccid)) {
                        mEnabledIccid = null;
                    }
                    removeProfileFromCache(iccid);
                }
                break;
            case DeleteProfileCode.ICCID_OR_AID_NOT_FOUND:
                EuiccLog.get()
                        .wtf(
                                "Got error code %d from eUICC. "
                                        + "Profile in memory is out of sync with eUICC: iccid=%s",
                                result, Pii.iccid(iccid));
                throw new EuiccCardErrorException(OperationCode.DELETE_PROFILE, result);
            default:
                throw new EuiccCardErrorException(OperationCode.DELETE_PROFILE, result);
        }
    }

    @Override
    protected String getDefaultSmdpAddress() throws UiccChannelException, EuiccCardException {
        if (mDefaultSmdpAddress == null || mSmdsAddress == null) {
            String response;
            try (UiccLogicalChannel ch = UiccLogicalChannel.openIsdR(mTm)) {
                response =
                        ch.execStoreData(
                                Asn1Node.newBuilder(TAG_GET_CONFIGURED_ADDRESSES).build().toHex());
            }
            try {
                Asn1Node root = parseResponse(response);
                mDefaultSmdpAddress = root.getChild(TAG_CTX_0).asString();
                mSmdsAddress = root.getChild(TAG_CTX_1).asString();
            } catch (TagNotFoundException | InvalidAsn1DataException e) {
                throw new EuiccCardInvalidResponseException(
                        "Cannot parse response: " + response, e);
            }
        }
        return mDefaultSmdpAddress;
    }

    @Override
    public String getSmdsAddress() throws UiccChannelException, EuiccCardException {
        if (mSmdsAddress == null) {
            getDefaultSmdpAddress();
        }
        return mSmdsAddress;
    }

    @Override
    protected void setDefaultSmdpAddress(String defaultSmdpAddress)
            throws UiccChannelException, EuiccCardException {
        String command =
                Asn1Node.newBuilder(TAG_SET_DEFAULT_SMDP_ADDRESS)
                        .addChildAsString(TAG_CTX_0, defaultSmdpAddress)
                        .build()
                        .toHex();
        String response;
        try (UiccLogicalChannel ch = UiccLogicalChannel.openIsdR(mTm)) {
            response = ch.execStoreData(command);
        }
        // SGP.22 v2.0 SetDefaultDpAddressResponse
        int result = parseSimpleResult(response);
        switch (result) {
            case SetDefaultSmdpAddressCode.OK:
                break;
            default:
                throw new EuiccCardErrorException(OperationCode.SET_DEFAULT_SMDP_ADDRESS, result);
        }
        mDefaultSmdpAddress = defaultSmdpAddress;
    }

    @Override
    protected EuiccRat getRat() throws UiccChannelException, EuiccCardException {
        String command = Asn1Node.newBuilder(TAG_GET_RAT).build().toHex();
        String response;
        try (UiccLogicalChannel ch = UiccLogicalChannel.openIsdR(mTm)) {
            response = ch.execStoreData(command);
        }
        Asn1Node root = parseResponse(response);
        try {
            List<Asn1Node> nodes = root.getChildren(TAG_CTX_COMP_0);
            EuiccRat.Builder builder = new EuiccRat.Builder(nodes.size());
            int size = nodes.size();
            for (int i = 0; i < size; i++) {
                Asn1Node node = nodes.get(i);
                List<Asn1Node> opIdNodes = node.getChild(TAG_CTX_COMP_1).getChildren();
                int opIdSize = opIdNodes.size();
                OperatorId[] opIds = new OperatorId[opIdSize];
                for (int j = 0; j < opIdSize; j++) {
                    opIds[j] = buildOperatorId(opIdNodes.get(j));
                }
                builder.add(
                        node.getChild(TAG_CTX_0).asBits(),
                        opIds,
                        node.getChild(TAG_CTX_2).asBits());
            }
            return builder.build();
        } catch (TagNotFoundException | InvalidAsn1DataException e) {
            throw new EuiccCardInvalidResponseException("Cannot parse response: " + response, e);
        }
    }

    @Override
    protected void resetMemory(@ResetOption int options)
            throws UiccChannelException, EuiccCardException {
        String command =
                Asn1Node.newBuilder(TAG_EUICC_MEMORY_RESET)
                        .addChildAsBits(TAG_CTX_2, options)
                        .build()
                        .toHex();
        String response;
        int result;
        try (UiccLogicalChannel ch = UiccLogicalChannel.openIsdR(mTm)) {
            response = ch.execStoreData(command);
            // SGP.22 v2.0 EuiccMemoryResetResponse
            result = parseSimpleResult(response);
        } catch (UiccChannelException e) {
            // TODO(b/37688526): This is a workaround solution, remove it when final solution
            // applied
            if (e.getApduStatus() == APDU_ERROR_SIM_REFRESH) {
                EuiccLog.get().i("Sim is refreshed after resetting memory, no response got.");
                result = ResetMemoryCode.OK;
            } else {
                throw e;
            }
        }

        switch (result) {
            case ResetMemoryCode.OK:
                mProfiles = null;
                mEnabledIccid = null;
                mDefaultSmdpAddress = null;
                break;
            case ResetMemoryCode.NOTHING_TO_DELETE:
                break;
            default:
                throw new EuiccCardErrorException(OperationCode.RESET_MEMORY, result);
        }
    }

    @Override
    protected byte[] getEuiccChallenge() throws UiccChannelException, EuiccCardException {
        String response;
        try (UiccLogicalChannel ch = UiccLogicalChannel.openIsdR(mTm)) {
            response =
                    ch.execStoreData(Asn1Node.newBuilder(TAG_GET_EUICC_CHALLENGE).build().toHex());
        }
        Asn1Node node = parseResponse(response);
        try {
            return node.getChild(TAG_CTX_0).asBytes();
        } catch (TagNotFoundException | InvalidAsn1DataException e) {
            throw new EuiccCardInvalidResponseException("Cannot parse response: " + response, e);
        }
    }

    @Override
    protected Asn1Node getEuiccInfo1() throws UiccChannelException, EuiccCardException {
        String response;
        try (UiccLogicalChannel ch = UiccLogicalChannel.openIsdR(mTm)) {
            response = ch.execStoreData(Asn1Node.newBuilder(TAG_GET_EUICC_INFO_1).build().toHex());
        }
        return parseResponse(response);
    }

    @Override
    protected Asn1Node getEuiccInfo2() throws UiccChannelException, EuiccCardException {
        String response;
        try (UiccLogicalChannel ch = UiccLogicalChannel.openIsdR(mTm)) {
            response = ch.execStoreData(Asn1Node.newBuilder(TAG_GET_EUICC_INFO_2).build().toHex());
        }
        return parseResponse(response);
    }

    @Override
    protected Asn1Node authenticateServer(
            String matchingId,
            Asn1Node serverSigned1,
            Asn1Node serverSignature1,
            Asn1Node euiccCiPkIdToBeUsed,
            Asn1Node serverCertificate)
            throws UiccChannelException, EuiccCardException {
        byte[] imeiBytes = new byte[8];
        Asn1Converter.bcdStringToBytes(mTm.getDeviceId(), imeiBytes);

        // TAC is the first 8 digits (4 bytes) of IMEI.
        byte[] tacBytes = new byte[4];
        System.arraycopy(imeiBytes, 0, tacBytes, 0, 4);

        Asn1Node.Builder devCapsBuilder = Asn1Node.newBuilder(TAG_CTX_COMP_1);
        DeviceCapabilitiesProvider devCap = EuiccApplication.get().getDeviceCapabilitiesProvider();
        addDeviceCapability(devCapsBuilder, devCap.getGsmSupportedRelease(), TAG_CTX_0);
        addDeviceCapability(devCapsBuilder, devCap.getUtranSupportedRelease(), TAG_CTX_1);
        addDeviceCapability(devCapsBuilder, devCap.getCdma2000OneXSupportedRelease(), TAG_CTX_2);
        addDeviceCapability(devCapsBuilder, devCap.getCdma2000HrpdSupportedRelease(), TAG_CTX_3);
        addDeviceCapability(devCapsBuilder, devCap.getCdma2000EhrpdSupportedRelease(), TAG_CTX_4);
        addDeviceCapability(devCapsBuilder, devCap.getEutranSupportedRelease(), TAG_CTX_5);
        addDeviceCapability(devCapsBuilder, devCap.getContactlessSupportedRelease(), TAG_CTX_6);
        addDeviceCapability(devCapsBuilder, devCap.getRspCrlSupportedVersion(), TAG_CTX_7);

        Asn1Node.Builder ctxParams1Builder =
                Asn1Node.newBuilder(TAG_CTX_COMP_0)
                        .addChildAsString(TAG_CTX_0, matchingId)
                        .addChild(
                                Asn1Node.newBuilder(TAG_CTX_COMP_1)
                                        .addChildAsBytes(TAG_CTX_0, tacBytes)
                                        .addChild(devCapsBuilder)
                                        .addChildAsBytes(TAG_CTX_2, imeiBytes));

        String command =
                Asn1Node.newBuilder(TAG_AUTHENTICATE_SERVER)
                        .addChild(serverSigned1)
                        .addChild(serverSignature1)
                        .addChild(euiccCiPkIdToBeUsed)
                        .addChild(serverCertificate)
                        .addChild(ctxParams1Builder)
                        .build()
                        .toHex();
        String response;
        try (UiccLogicalChannel ch = UiccLogicalChannel.openIsdR(mTm)) {
            response = ch.execStoreData(command);
        }
        Asn1Node root = parseResponse(response);
        try {
            if (root.hasChild(TAG_CTX_1)) {
                throw new EuiccCardErrorException(
                        OperationCode.AUTHENTICATE_SERVER, root.getChild(TAG_CTX_1).asInteger());
            }
        } catch (TagNotFoundException | InvalidAsn1DataException e) {
            throw new EuiccCardInvalidResponseException("Cannot parse response: " + response, e);
        }
        return root;
    }

    @Override
    protected Asn1Node prepareDownload(
            @Nullable byte[] hashCc,
            Asn1Node smdpSigned2,
            Asn1Node smdpSignature2,
            Asn1Node smdpCertificate)
            throws UiccChannelException, EuiccCardException {
        Asn1Node.Builder builder =
                Asn1Node.newBuilder(TAG_PREPARE_DOWNLOAD)
                        .addChild(smdpSigned2)
                        .addChild(smdpSignature2);
        if (hashCc != null) {
            builder.addChildAsBytes(TAG_UNI_4, hashCc);
        }
        String command = builder.addChild(smdpCertificate).build().toHex();
        String response;
        try (UiccLogicalChannel ch = UiccLogicalChannel.openIsdR(mTm)) {
            response = ch.execStoreData(command);
        }
        Asn1Node root = parseResponse(response);
        try {
            if (root.hasChild(TAG_CTX_COMP_1, TAG_UNI_2)) {
                throw new EuiccCardErrorException(
                        OperationCode.PREPARE_DOWNLOAD,
                        root.getChild(TAG_CTX_COMP_1, TAG_UNI_2).asInteger());
            }
        } catch (TagNotFoundException | InvalidAsn1DataException e) {
            throw new EuiccCardInvalidResponseException("Cannot parse response: " + response, e);
        }
        return root;
    }

    @Override
    protected Asn1Node loadBoundProfilePackage(Asn1Node boundProfilePackage)
            throws UiccChannelException, EuiccCardException {
        String response;
        try (UiccLogicalChannel ch = UiccLogicalChannel.openIsdR(mTm)) {
            // initialiseSecureChannelRequest (ES8+.InitialiseSecureChannel)
            Asn1Node initialiseSecureChannelRequest =
                    boundProfilePackage.getChild(TAG_INITIALISE_SECURE_CHANNEL);
            // firstSequenceOf87 (ES8+.ConfigureISDP)
            Asn1Node firstSequenceOf87 = boundProfilePackage.getChild(TAG_CTX_COMP_0);
            // sequenceOf88 (ES8+.StoreMetadata)
            Asn1Node sequenceOf88 = boundProfilePackage.getChild(TAG_CTX_COMP_1);
            List<Asn1Node> metaDataSeqs = sequenceOf88.getChildren(TAG_CTX_8);
            // sequenceOf86 (ES8+.LoadProfileElements #1)
            Asn1Node sequenceOf86 = boundProfilePackage.getChild(TAG_CTX_COMP_3);
            List<Asn1Node> elementSeqs = sequenceOf86.getChildren(TAG_CTX_6);

            ch.execStoreData(
                    boundProfilePackage.getHeadAsHex() + initialiseSecureChannelRequest.toHex());

            ch.execStoreData(firstSequenceOf87.toHex());

            ch.execStoreData(sequenceOf88.getHeadAsHex());
            int size = metaDataSeqs.size();
            for (int i = 0; i < size; i++) {
                ch.execStoreData(metaDataSeqs.get(i).toHex());
            }

            if (boundProfilePackage.hasChild(TAG_CTX_COMP_2)) {
                ch.execStoreData(boundProfilePackage.getChild(TAG_CTX_COMP_2).toHex());
            }

            response = ch.execStoreData(sequenceOf86.getHeadAsHex());
            size = elementSeqs.size();
            for (int i = 0; i < size; i++) {
                response = ch.execStoreData(elementSeqs.get(i).toHex());
            }
        } catch (TagNotFoundException | InvalidAsn1DataException e) {
            throw new EuiccCardInvalidResponseException("Cannot parse bound profile package.", e);
        }

        // SGP.22 v2.0 ErrorResult
        Asn1Node root = parseResponse(response);
        try {
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
                        OperationCode.LOAD_BOUND_PROFILE_PACKAGE, errorNode.asInteger(), errorNode);
            }
            mProfiles = null;
            mEnabledIccid = null;
        } catch (TagNotFoundException | InvalidAsn1DataException e) {
            throw new EuiccCardInvalidResponseException("Cannot parse response: " + response, e);
        }
        return root;
    }

    @Override
    protected Asn1Node cancelSession(byte[] transactionId, int reason)
            throws UiccChannelException, EuiccCardException {
        String command =
                Asn1Node.newBuilder(TAG_CANCEL_SESSION)
                        .addChildAsBytes(TAG_CTX_0, transactionId)
                        .addChildAsInteger(TAG_CTX_1, reason)
                        .build()
                        .toHex();
        String response;
        try (UiccLogicalChannel ch = UiccLogicalChannel.openIsdR(mTm)) {
            response = ch.execStoreData(command);
        }
        Asn1Node root = parseResponse(response);
        try {
            if (root.hasChild(TAG_CTX_1)) {
                throw new EuiccCardErrorException(
                        OperationCode.CANCEL_SESSION, root.getChild(TAG_CTX_1).asInteger());
            }
        } catch (TagNotFoundException | InvalidAsn1DataException e) {
            throw new EuiccCardInvalidResponseException("Cannot parse response: " + response, e);
        }
        return root;
    }

    @Override
    protected EuiccNotification[] listNotifications(@EuiccNotification.Event int events)
            throws UiccChannelException, EuiccCardException {
        String command =
                Asn1Node.newBuilder(TAG_LIST_NOTIFICATION)
                        .addChildAsBits(TAG_CTX_1, events)
                        .build()
                        .toHex();
        String response;
        try (UiccLogicalChannel ch = UiccLogicalChannel.openIsdR(mTm)) {
            response = ch.execStoreData(command);
        }
        Asn1Node root = parseResponse(response);
        try {
            if (root.hasChild(TAG_CTX_1)) {
                throw new EuiccCardErrorException(
                        OperationCode.LIST_NOTIFICATIONS, root.getChild(TAG_CTX_1).asInteger());
            }
            List<Asn1Node> nodes = root.getChild(TAG_CTX_COMP_0).getChildren();
            EuiccNotification[] notifications = new EuiccNotification[nodes.size()];
            for (int i = 0; i < notifications.length; ++i) {
                notifications[i] = createNotification(nodes.get(i));
            }
            return notifications;
        } catch (TagNotFoundException | InvalidAsn1DataException e) {
            throw new EuiccCardInvalidResponseException("Cannot parse response: " + response, e);
        }
    }

    @Override
    protected EuiccNotification[] retrieveNotificationList(@EuiccNotification.Event int events)
            throws UiccChannelException, EuiccCardException {
        String command =
                Asn1Node.newBuilder(TAG_RETRIEVE_NOTIFICATIONS_LIST)
                        .addChild(
                                Asn1Node.newBuilder(TAG_CTX_COMP_0)
                                        .addChildAsBits(TAG_CTX_1, events))
                        .build()
                        .toHex();
        String response;
        try (UiccLogicalChannel ch = UiccLogicalChannel.openIsdR(mTm)) {
            response = ch.execStoreData(command);
        }
        Asn1Node root = parseResponse(response);
        try {
            if (root.hasChild(TAG_CTX_1)) {
                // SGP.22 v2.0 RetrieveNotificationsListResponse
                int error = root.getChild(TAG_CTX_1).asInteger();
                switch (error) {
                    case RetrieveNotificationCode.NO_RESULT_AVAILABLE:
                        return new EuiccNotification[0];
                    default:
                        throw new EuiccCardErrorException(
                                OperationCode.RETRIEVE_NOTIFICATION, error);
                }
            }
            List<Asn1Node> nodes = root.getChild(TAG_CTX_COMP_0).getChildren();
            EuiccNotification[] notifications = new EuiccNotification[nodes.size()];
            for (int i = 0; i < notifications.length; ++i) {
                notifications[i] = createNotification(nodes.get(i));
            }
            return notifications;
        } catch (TagNotFoundException | InvalidAsn1DataException e) {
            throw new EuiccCardInvalidResponseException("Cannot parse response: " + response, e);
        }
    }

    @Override
    @Nullable
    protected EuiccNotification retrieveNotification(int seqNumber)
            throws UiccChannelException, EuiccCardException {
        String command =
                Asn1Node.newBuilder(TAG_RETRIEVE_NOTIFICATIONS_LIST)
                        .addChild(
                                Asn1Node.newBuilder(TAG_CTX_COMP_0)
                                        .addChildAsInteger(TAG_CTX_0, seqNumber))
                        .build()
                        .toHex();
        String response;
        try (UiccLogicalChannel ch = UiccLogicalChannel.openIsdR(mTm)) {
            response = ch.execStoreData(command);
        }
        Asn1Node root = parseResponse(response);
        try {
            if (root.hasChild(TAG_CTX_1)) {
                // SGP.22 v2.0 RetrieveNotificationsListResponse
                throw new EuiccCardErrorException(
                        OperationCode.RETRIEVE_NOTIFICATION, root.getChild(TAG_CTX_1).asInteger());
            }
            List<Asn1Node> nodes = root.getChild(TAG_CTX_COMP_0).getChildren();
            if (nodes.size() > 0) {
                return createNotification(nodes.get(0));
            }
        } catch (TagNotFoundException | InvalidAsn1DataException e) {
            throw new EuiccCardInvalidResponseException("Cannot parse response: " + response, e);
        }
        return null;
    }

    @Override
    protected void removeNotificationFromList(int seqNumber)
            throws UiccChannelException, EuiccCardException {
        String command =
                Asn1Node.newBuilder(TAG_REMOVE_NOTIFICATION_FROM_LIST)
                        .addChildAsInteger(TAG_CTX_0, seqNumber)
                        .build()
                        .toHex();
        String response;
        try (UiccLogicalChannel ch = UiccLogicalChannel.openIsdR(mTm)) {
            response = ch.execStoreData(command);
        }
        // SGP.22 v2.0 NotificationSentResponse
        int result = parseSimpleResult(response);
        switch (result) {
            case RemoveNotificationFromListCode.OK:
            case RemoveNotificationFromListCode.NOTHING_TO_DELETE:
                break;
            default:
                throw new EuiccCardErrorException(
                        OperationCode.REMOVE_NOTIFICATION_FROM_LIST, result);
        }
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

    /** Returns the first CONTEXT [0] as an integer. */
    private static int parseSimpleResult(String response) throws EuiccCardException {
        try {
            return parseResponse(response).getChild(TAG_CTX_0).asInteger();
        } catch (TagNotFoundException | InvalidAsn1DataException e) {
            throw new EuiccCardInvalidResponseException("Cannot parse response: " + response, e);
        }
    }

    private static Asn1Node parseResponse(String response) throws EuiccCardException {
        Asn1Decoder decoder = new Asn1Decoder(response);
        if (!decoder.hasNextNode()) {
            throw new EuiccCardInvalidResponseException("Empty response", null);
        }
        try {
            return decoder.nextNode();
        } catch (InvalidAsn1DataException e) {
            throw new EuiccCardInvalidResponseException("Cannot parse response: " + response, e);
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

    private void removeProfileFromCache(String iccid) {
        int index = indexOfProfile(iccid);
        if (index < 0) {
            return;
        }
        EuiccProfile[] profiles = new EuiccProfile[mProfiles.length - 1];
        System.arraycopy(mProfiles, 0, profiles, 0, index);
        System.arraycopy(mProfiles, index + 1, profiles, index, mProfiles.length - index - 1);
        mProfiles = profiles;
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

    private int indexOfProfile(String iccid) {
        for (int i = 0; i < mProfiles.length; i++) {
            if (mProfiles[i].getIccid().equals(iccid)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Sets a device capability version as the child of the given device capability ASN1 node
     * builder.
     *
     * @param devCap The ASN1 node builder to modify.
     * @param supportedRelease The version to set. It can be {@code null} or a 3-byte array. If this
     *     is {@code null} or invalid, {@code devCap} won't be modified.
     * @param tag The tag of the device capability.
     */
    private void addDeviceCapability(
            Asn1Node.Builder devCap, @Nullable byte[] supportedRelease, int tag) {
        if (supportedRelease == null || supportedRelease.length != 3) {
            return;
        }
        devCap.addChildAsBytes(tag, supportedRelease);
    }
}
