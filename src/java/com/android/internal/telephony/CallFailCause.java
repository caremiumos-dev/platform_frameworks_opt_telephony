/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.internal.telephony;

/**
 * Call fail causes from TS 24.008 .
 * These are mostly the cause codes we need to distinguish for the UI.
 * See 22.001 Annex F.4 for mapping of cause codes to local tones.
 *
 * CDMA call failure reasons are derived from the possible call failure scenarios described
 * in "CDMA IS2000 - Release A (C.S0005-A v6.0)" standard.
 *
 * {@hide}
 *
 */
public interface CallFailCause {
    // The disconnect cause is not valid (Not received a disconnect cause)
    int NOT_VALID = -1;

    // Unassigned/Unobtainable number
    int UNOBTAINABLE_NUMBER = 1;

    int NO_ROUTE_TO_DEST = 3;
    int CHANNEL_UNACCEPTABLE = 6;
    int OPERATOR_DETERMINED_BARRING = 8;
    int NORMAL_CLEARING     = 16;
    int USER_BUSY           = 17;
    int NO_USER_RESPONDING = 18;
    int USER_ALERTING_NO_ANSWER = 19;
    int CALL_REJECTED = 21;
    int NUMBER_CHANGED      = 22;
    int PRE_EMPTION = 25;
    int NON_SELECTED_USER_CLEARING = 26;
    int DESTINATION_OUT_OF_ORDER = 27;

    // Incomplete number
    int INVALID_NUMBER_FORMAT = 28;

    int FACILITY_REJECTED = 29;
    int STATUS_ENQUIRY      = 30;
    int NORMAL_UNSPECIFIED  = 31;
    int NO_CIRCUIT_AVAIL    = 34;
    int NETWORK_OUT_OF_ORDER = 38;
    int TEMPORARY_FAILURE   = 41;
    int SWITCHING_CONGESTION    = 42;
    int ACCESS_INFORMATION_DISCARDED = 43;
    int CHANNEL_NOT_AVAIL   = 44;
    int RESOURCES_UNAVAILABLE_UNSPECIFIED = 47;
    int QOS_NOT_AVAIL       = 49;
    int REQUESTED_FACILITY_NOT_SUBSCRIBED = 50;
    int INCOMING_CALL_BARRED_WITHIN_CUG = 55;
    int BEARER_CAPABILITY_NOT_AUTHORISED = 57;
    int BEARER_NOT_AVAIL    = 58;
    int SERVICE_OR_OPTION_NOT_AVAILABLE = 63;
    int BEARER_SERVICE_NOT_IMPLEMENTED = 65;
    int ACM_LIMIT_EXCEEDED = 68;
    int REQUESTED_FACILITY_NOT_IMPLEMENTED = 69;
    int ONLY_RESTRICTED_DIGITAL_INFO_BC_AVAILABLE = 70;
    int SERVICE_OR_OPTION_NOT_IMPLEMENTED = 79;
    int INVALID_TRANSACTION_ID_VALUE = 81;
    int USER_NOT_MEMBER_OF_CUG = 87;
    int INCOMPATIBLE_DESTINATION = 88;
    int INVALID_TRANSIT_NETWORK_SELECTION = 91;
    int SEMANTICALLY_INCORRECT_MESSAGE = 95;
    int INVALID_MANDATORY_INFORMATION = 96;
    int MESSAGE_TYPE_NON_EXISTENT = 97;
    int MESSAGE_TYPE_NOT_COMPATIBLE_WITH_PROT_STATE = 98;
    int IE_NON_EXISTENT_OR_NOT_IMPLEMENTED = 99;
    int CONDITIONAL_IE_ERROR = 100;
    int MESSAGE_NOT_COMPATIBLE_WITH_PROTOCOL_STATE = 101;
    int RECOVERY_ON_TIMER_EXPIRY = 102;
    int PROTOCOL_ERROR_UNSPECIFIED = 111;
    int INTERWORKING_UNSPECIFIED = 127;
    int CALL_BARRED        = 240;
    int FDN_BLOCKED        = 241;
    int IMEI_NOT_ACCEPTED  = 243;

    // Stk Call Control
    int DIAL_MODIFIED_TO_USSD = 244;
    int DIAL_MODIFIED_TO_SS   = 245;
    int DIAL_MODIFIED_TO_DIAL = 246;

    //Emergency Redial
    int EMERGENCY_TEMP_FAILURE = 325;
    int EMERGENCY_PERM_FAILURE = 326;

    int CDMA_LOCKED_UNTIL_POWER_CYCLE  = 1000;
    int CDMA_DROP                      = 1001;
    int CDMA_INTERCEPT                 = 1002;
    int CDMA_REORDER                   = 1003;
    int CDMA_SO_REJECT                 = 1004;
    int CDMA_RETRY_ORDER               = 1005;
    int CDMA_ACCESS_FAILURE            = 1006;
    int CDMA_PREEMPTED                 = 1007;

    // For non-emergency number dialed while in emergency callback mode.
    int CDMA_NOT_EMERGENCY             = 1008;

    // Access Blocked by CDMA Network.
    int CDMA_ACCESS_BLOCKED            = 1009;

    int ERROR_UNSPECIFIED = 0xffff;

}
