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

/** ASN1 tags used by {@link EuiccCard} implementation. */
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
}
