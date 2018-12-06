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
package com.android.internal.telephony.dataconnection;

import android.content.Context;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Returned as the reason for a connection failure as defined
 * by RIL_DataCallFailCause in ril.h and some local errors.
 */
public enum DcFailCause {
    NONE(0),

    // This series of errors as specified by the standards
    // specified in ril.h
    OPERATOR_BARRED(0x08),                                      /* no retry */
    NAS_SIGNALLING(0x0E),
    LLC_SNDCP(0x19),
    INSUFFICIENT_RESOURCES(0x1A),
    MISSING_UNKNOWN_APN(0x1B),                                  /* no retry */
    UNKNOWN_PDP_ADDRESS_TYPE(0x1C),                             /* no retry */
    USER_AUTHENTICATION(0x1D),                                  /* no retry */
    ACTIVATION_REJECT_GGSN(0x1E),                               /* no retry */
    ACTIVATION_REJECT_UNSPECIFIED(0x1F),
    SERVICE_OPTION_NOT_SUPPORTED(0x20),                         /* no retry */
    SERVICE_OPTION_NOT_SUBSCRIBED(0x21),                        /* no retry */
    SERVICE_OPTION_OUT_OF_ORDER(0x22),
    NSAPI_IN_USE(0x23),                                         /* no retry */
    REGULAR_DEACTIVATION(0x24),    /* possibly restart radio, based on config */
    QOS_NOT_ACCEPTED(0x25),
    NETWORK_FAILURE(0x26),
    UMTS_REACTIVATION_REQ(0x27),
    FEATURE_NOT_SUPP(0x28),
    TFT_SEMANTIC_ERROR(0x29),
    TFT_SYTAX_ERROR(0x2A),
    UNKNOWN_PDP_CONTEXT(0x2B),
    FILTER_SEMANTIC_ERROR(0x2C),
    FILTER_SYTAX_ERROR(0x2D),
    PDP_WITHOUT_ACTIVE_TFT(0x2E),
    ACTIVATION_REJECTED_BCM_VIOLATION(0x30),
    ONLY_IPV4_ALLOWED(0x32),                                    /* no retry */
    ONLY_IPV6_ALLOWED(0x33),                                    /* no retry */
    ONLY_SINGLE_BEARER_ALLOWED(0x34),
    ESM_INFO_NOT_RECEIVED(0x35),
    PDN_CONN_DOES_NOT_EXIST(0x36),
    MULTI_CONN_TO_SAME_PDN_NOT_ALLOWED(0x37),
    COLLISION_WITH_NW_INIT_REQ(0x38),
    ONLY_IPV4V6_ALLOWED(0x39),                                  /* no retry */
    ONLY_NON_IP_ALLOWED(0x3A),                                  /* no retry */
    UNSUPPORTED_QCI_VALUE(0x3B),
    BEARER_HANDLING_NOT_SUPPORTED(0x3C),
    MAX_ACTIVE_PDP_CONTEXT_REACHED(0x41),
    UNSUPPORTED_APN_IN_CURRENT_PLMN(0x42),
    INVALID_TRANSACTION_ID(0x51),
    MESSAGE_INCORRECT_SEMANTIC(0x5F),
    INVALID_MANDATORY_INFO(0x60),
    MESSAGE_TYPE_UNSUPPORTED(0x61),
    MSG_TYPE_NONCOMPATIBLE_STATE(0x62),
    UNKNOWN_INFO_ELEMENT(0x63),
    CONDITIONAL_IE_ERROR(0x64),
    MSG_AND_PROTOCOL_STATE_UNCOMPATIBLE(0x65),
    PROTOCOL_ERRORS(0x6F),                                      /* no retry */
    APN_TYPE_CONFLICT(0x70),
    INVALID_PCSCF_ADDR(0x71),
    INTERNAL_CALL_PREEMPT_BY_HIGH_PRIO_APN(0x72),
    EMM_ACCESS_BARRED(0x73),
    EMERGENCY_IFACE_ONLY(0x74),
    IFACE_MISMATCH(0x75),
    COMPANION_IFACE_IN_USE(0x76),
    IP_ADDRESS_MISMATCH(0x77),
    IFACE_AND_POL_FAMILY_MISMATCH(0x78),
    EMM_ACCESS_BARRED_INFINITE_RETRY(0x79),
    AUTH_FAILURE_ON_EMERGENCY_CALL(0x7A),
    INVALID_DNS_ADDR(0x7B),
    INVALID_PCSCF_DNS_ADDR(0x7C),
    CALL_PREEMPT_BY_EMERGENCY_APN(0x7F),
    UE_INIT_DETACH_OR_DISCONNECT(0x80),

    MIP_FA_REASON_UNSPECIFIED(0x7D0),                           /* no retry */
    MIP_FA_ADMIN_PROHIBITED(0x7D1),                             /* no retry */
    MIP_FA_INSUFFICIENT_RESOURCES(0x7D2),                       /* no retry */
    MIP_FA_MOBILE_NODE_AUTH_FAILURE(0x7D3),                     /* no retry */
    MIP_FA_HA_AUTH_FAILURE(0x7D4),                              /* no retry */
    MIP_FA_REQ_LIFETIME_TOO_LONG(0x7D5),
    MIP_FA_MALFORMED_REQUEST(0x7D6),                            /* no retry */
    MIP_FA_MALFORMED_REPLY(0x7D7),                              /* no retry */
    MIP_FA_ENCAPSULATION_UNAVAILABLE(0x7D8),                    /* no retry */
    MIP_FA_VJHC_UNAVAILABLE(0x7D9),                             /* no retry */
    MIP_FA_REV_TUNNEL_UNAVAILABLE(0x7DA),                       /* no retry */
    MIP_FA_REV_TUNNEL_IS_MAND_AND_T_BIT_NOT_SET(0x7DB),         /* no retry */
    MIP_FA_DELIVERY_STYLE_NOT_SUPP(0x7DC),                      /* no retry */
    MIP_FA_MISSING_NAI(0x7DD),                                  /* no retry */
    MIP_FA_MISSING_HA(0x7DE),                                   /* no retry */
    MIP_FA_MISSING_HOME_ADDR(0x7DF),                            /* no retry */
    MIP_FA_UNKNOWN_CHALLENGE(0x7E0),
    MIP_FA_MISSING_CHALLENGE(0x7E1),                            /* no retry */
    MIP_FA_STALE_CHALLENGE(0x7E2),
    MIP_HA_REASON_UNSPECIFIED(0x7E3),                           /* no retry */
    MIP_HA_ADMIN_PROHIBITED(0x7E4),                             /* no retry */
    MIP_HA_INSUFFICIENT_RESOURCES(0x7E5),                       /* no retry */
    MIP_HA_MOBILE_NODE_AUTH_FAILURE(0x7E6),                     /* no retry */
    MIP_HA_FA_AUTH_FAILURE(0x7E7),                              /* no retry */
    MIP_HA_REGISTRATION_ID_MISMATCH(0x7E8),
    MIP_HA_MALFORMED_REQUEST(0x7E9),                            /* no retry */
    MIP_HA_UNKNOWN_HA_ADDR(0x7EA),                              /* no retry */
    MIP_HA_REV_TUNNEL_UNAVAILABLE(0x7EB),                       /* no retry */
    MIP_HA_REV_TUNNEL_IS_MANDATORY_AND_T_BIT_NOT_SET(0x7EC),    /* no retry */
    MIP_HA_ENCAPSULATION_UNAVAILABLE(0x7ED),                    /* no retry */
    CLOSE_IN_PROGRESS(0x7EE),
    NW_INITIATED_TERMINATION(0x7EF),                            /* no retry */
    MODEM_APP_PREEMPTED(0x7F0),
    ERR_PDN_IPV4_CALL_DISALLOWED(0x7F1),
    ERR_PDN_IPV4_CALL_THROTTLED(0x7F2),
    ERR_PDN_IPV6_CALL_DISALLOWED(0x7F3),
    ERR_PDN_IPV6_CALL_THROTTLED(0x7F4),
    MODEM_RESTART(0x7F5),
    PDP_PPP_NOT_SUPPORTED(0x7F6),
    UNPREFERRED_RAT(0x7F7),
    PHYS_LINK_CLOSE_IN_PROGRESS(0x7F8),
    APN_PENDING_HANDOVER(0x7F9),
    PROFILE_BEARER_INCOMPATIBLE(0x7FA),
    SIM_CARD_EVT(0x7FB),
    LPM_OR_PWR_DOWN(0x7FC),
    APN_DISABLED(0x7FD),
    MAX_PPP_INACTIVITY_TIMER_EXPIRED(0x7FE),
    IPV6_ADDR_TRANSFER_FAILED(0x7FF),
    TRAT_SWAP_FAILED(0x800),
    EHRPD_TO_HRPD_FALLBACK(0x801),
    MIP_CONFIG_FAILURE(0x802),                                  /* no retry */
    PDN_INACTIVITY_TIMER_EXPIRED(0x803),
    MAX_V4_CONNECTIONS(0x804),
    MAX_V6_CONNECTIONS(0x805),
    APN_MISMATCH(0x806),
    IP_VERSION_MISMATCH(0x807),
    DUN_CALL_DISALLOWED(0x808),
    INTERNAL_EPC_NONEPC_TRANSITION(0x809),
    IFACE_IN_USE(0x80A),
    APN_DISALLOWED_ON_ROAMING(0x80C),
    APN_PARAM_CHANGED(0x80D),
    NULL_APN_DISALLOWED(0x80E),
    THERMAL_MITIGATION(0x80F),
    DATA_SETTINGS_DISABLED(0x810),
    DATA_ROAMING_SETTINGS_DISABLED(0x811),
    DDS_CALL_ABORT(0x812),
    INVALID_APN_NAME(0x813),
    DDS_SWITCH_IN_PROGRESS(0x814),
    CALL_DISALLOWED_IN_ROAMING(0x815),
    NON_IP_NOT_SUPPORTED(0x816),                                /* no retry */
    ERR_PDN_NON_IP_CALL_THROTTLED(0x817),
    ERR_PDN_NON_IP_CALL_DISALLOWED(0x818),
    CDMA_LOCK(0x819),                                           /* no retry */
    CDMA_INTERCEPT(0x81A),
    CDMA_REORDER(0x81B),
    CDMA_REL_SO_REJ(0x81C),                                     /* no retry */
    CDMA_INCOM_CALL(0x81D),
    CDMA_ALERT_STOP(0x81E),
    CHANNEL_ACQUISITION_FAILURE(0x81F),
    MAX_ACCESS_PROBE(0x820),
    CCS_NOT_SUPPORTED_BY_BS(0x821),                             /* no retry */
    NO_RESPONSE_FROM_BS(0x822),
    REJECTED_BY_BS(0x823),                                      /* no retry */
    CCS_INCOMPATIBLE(0x824),                                    /* no retry */
    NO_CDMA_SRV(0x825),
    UIM_NOT_PRESENT(0x826),                                     /* no retry */
    CDMA_RETRY_ORDER(0x827),
    ACCESS_BLOCK(0x828),
    ACCESS_BLOCK_ALL(0x829),
    IS707B_MAX_ACC(0x82A),
    THERMAL_EMERGENCY(0x82B),                                   /* no retry */
    CCS_NOT_ALLOWED(0x82C),
    INCOM_REJ(0x82D),
    NO_GATEWAY_SRV(0x82E),
    NO_GPRS_CONTEXT(0x82F),
    ILLEGAL_MS(0x830),                                          /* no retry */
    ILLEGAL_ME(0x831),                                          /* no retry */
    GPRS_SERVICES_AND_NON_GPRS_SERVICES_NOT_ALLOWED(0x832),     /* no retry */
    GPRS_SERVICES_NOT_ALLOWED(0x833),                           /* no retry */
    MS_IDENTITY_CANNOT_BE_DERIVED_BY_THE_NETWORK(0x834),
    IMPLICITLY_DETACHED(0x835),
    PLMN_NOT_ALLOWED(0x836),                                    /* no retry */
    LA_NOT_ALLOWED(0x837),                                      /* no retry */
    GPRS_SERVICES_NOT_ALLOWED_IN_THIS_PLMN(0x838),              /* no retry */
    PDP_DUPLICATE(0x839),
    UE_RAT_CHANGE(0x83A),
    CONGESTION(0x83B),
    NO_PDP_CONTEXT_ACTIVATED(0x83C),
    ACCESS_CLASS_DSAC_REJECTION(0x83D),
    PDP_ACTIVATE_MAX_RETRY_FAILED(0x83E),
    RAB_FAILURE(0x83F),
    ESM_UNKNOWN_EPS_BEARER_CONTEXT(0x840),
    DRB_RELEASED_AT_RRC(0x841),
    NAS_SIG_CONN_RELEASED(0x842),
    EMM_DETACHED(0x843),
    EMM_ATTACH_FAILED(0x844),
    EMM_ATTACH_STARTED(0x845),
    LTE_NAS_SERVICE_REQ_FAILED(0x846),
    ESM_ACTIVE_DEDICATED_BEARER_REACTIVATED_BY_NW(0x847),
    ESM_LOWER_LAYER_FAILURE(0x848),
    ESM_SYNC_UP_WITH_NW(0x849),
    ESM_NW_ACTIVATED_DED_BEARER_WITH_ID_OF_DEF_BEARER(0x84A),
    ESM_BAD_OTA_MESSAGE(0x84B),
    ESM_DS_REJECTED_THE_CALL(0x84C),
    ESM_CONTEXT_TRANSFERED_DUE_TO_IRAT(0x84D),
    DS_EXPLICIT_DEACT(0x84E),
    ESM_LOCAL_CAUSE_NONE(0x84F),
    LTE_NAS_SERVICE_REQ_FAILED_NO_THROTTLE(0x850),
    ACCESS_CONTROL_LIST_CHECK_FAILURE(0x851),
    LTE_NAS_SERVICE_REQ_FAILED_DS_DISALLOW(0x852),
    EMM_T3417_EXPIRED(0x853),
    EMM_T3417_EXT_EXPIRED(0x854),
    LRRC_UL_DATA_CNF_FAILURE_TXN(0x855),
    LRRC_UL_DATA_CNF_FAILURE_HO(0x856),
    LRRC_UL_DATA_CNF_FAILURE_CONN_REL(0x857),
    LRRC_UL_DATA_CNF_FAILURE_RLF(0x858),
    LRRC_UL_DATA_CNF_FAILURE_CTRL_NOT_CONN(0x859),
    LRRC_CONN_EST_FAILURE(0x85A),
    LRRC_CONN_EST_FAILURE_ABORTED(0x85B),
    LRRC_CONN_EST_FAILURE_ACCESS_BARRED(0x85C),
    LRRC_CONN_EST_FAILURE_CELL_RESEL(0x85D),
    LRRC_CONN_EST_FAILURE_CONFIG_FAILURE(0x85E),
    LRRC_CONN_EST_FAILURE_TIMER_EXPIRED(0x85F),
    LRRC_CONN_EST_FAILURE_LINK_FAILURE(0x860),
    LRRC_CONN_EST_FAILURE_NOT_CAMPED(0x861),
    LRRC_CONN_EST_FAILURE_SI_FAILURE(0x862),
    LRRC_CONN_EST_FAILURE_CONN_REJECT(0x863),
    LRRC_CONN_REL_NORMAL(0x864),
    LRRC_CONN_REL_RLF(0x865),
    LRRC_CONN_REL_CRE_FAILURE(0x866),
    LRRC_CONN_REL_OOS_DURING_CRE(0x867),
    LRRC_CONN_REL_ABORTED(0x868),
    LRRC_CONN_REL_SIB_READ_ERROR(0x869),
    DETACH_WITH_REATTACH_LTE_NW_DETACH(0x86A),
    DETACH_WITHOUT_REATTACH_LTE_NW_DETACH(0x86B),
    ESM_PROC_TIME_OUT(0x86C),
    INVALID_CONNECTION_ID(0x86D),
    INVALID_NSAPI(0x86E),
    INVALID_PRI_NSAPI(0x86F),
    INVALID_FIELD(0x870),
    RAB_SETUP_FAILURE(0x871),
    PDP_ESTABLISH_MAX_TIMEOUT(0x872),
    PDP_MODIFY_MAX_TIMEOUT(0x873),
    PDP_INACTIVE_MAX_TIMEOUT(0x874),
    PDP_LOWERLAYER_ERROR(0x875),
    PDP_MODIFY_COLLISION(0x876),
    SM_NO_RADIO_AVAILABLE(0x877),
    SM_ABORT_SERVICE_NOT_AVAILABLE(0x878),
    MESSAGE_EXCEED_MAX_L2_LIMIT(0x879),
    SM_NAS_SRV_REQ_FAILURE(0x87A),
    RRC_CONN_EST_FAILURE_REQ_ERROR(0x87B),
    RRC_CONN_EST_FAILURE_TAI_CHANGE(0x87C),
    RRC_CONN_EST_FAILURE_RF_UNAVAILABLE(0x87D),
    RRC_CONN_REL_ABORTED_IRAT_SUCCESS(0x87E),
    RRC_CONN_REL_RLF_SEC_NOT_ACTIVE(0x87F),
    RRC_CONN_REL_IRAT_TO_LTE_ABORTED(0x880),
    RRC_CONN_REL_IRAT_FROM_LTE_TO_G_CCO_SUCCESS(0x881),
    RRC_CONN_REL_IRAT_FROM_LTE_TO_G_CCO_ABORTED(0x882),
    IMSI_UNKNOWN_IN_HSS(0x883),
    IMEI_NOT_ACCEPTED(0x884),                                   /* no retry */
    EPS_SERVICES_AND_NON_EPS_SERVICES_NOT_ALLOWED(0x885),       /* no retry */
    EPS_SERVICES_NOT_ALLOWED_IN_PLMN(0x886),                    /* no retry */
    MSC_TEMPORARILY_NOT_REACHABLE(0x887),
    CS_DOMAIN_NOT_AVAILABLE(0x888),
    ESM_FAILURE(0x889),
    MAC_FAILURE(0x88A),
    SYNCH_FAILURE(0x88B),
    UE_SECURITY_CAPABILITIES_MISMATCH(0x88C),
    SECURITY_MODE_REJ_UNSPECIFIED(0x88D),
    NON_EPS_AUTH_UNACCEPTABLE(0x88E),
    CS_FALLBACK_CALL_EST_NOT_ALLOWED(0x88F),
    NO_EPS_BEARER_CONTEXT_ACTIVATED(0x890),
    EMM_INVALID_STATE(0x891),
    NAS_LAYER_FAILURE(0x892),
    MULTI_PDN_NOT_ALLOWED(0x893),
    EMBMS_NOT_ENABLED(0x894),
    PENDING_REDIAL_CALL_CLEANUP(0x895),
    EMBMS_REGULAR_DEACTIVATION(0x896),
    TLB_REGULAR_DEACTIVATION(0x897),
    LOWER_LAYER_REGISTRATION_FAILURE(0x898),
    DETACH_EPS_SERVICES_NOT_ALLOWED(0x899),
    SM_INTERNAL_PDP_DEACTIVATION(0x89A),
    CD_GEN_OR_BUSY(0x89B),
    CD_BILL_OR_AUTH(0x89C),                                     /* no retry */
    HDR_CHANGED(0x89D),
    HDR_EXITED(0x89E),
    HDR_NO_SESSION(0x89F),
    HDR_ORIG_DURING_GPS_FIX(0x8A0),
    HDR_CS_TIMEOUT(0x8A1),
    COLLOC_ACQ_FAIL(0x8A2),
    OTASP_COMMIT_IN_PROG(0x8A3),
    NO_HYBR_HDR_SRV(0x8A4),
    HDR_NO_LOCK_GRANTED(0x8A5),
    HOLD_OTHER_IN_PROG(0x8A6),
    HDR_FADE(0x8A7),
    HDR_ACC_FAIL(0x8A8),
    UNSUPPORTED_1X_PREV(0x8A9),
    LOCAL_END(0x8AA),
    NO_SRV(0x8AB),
    FADE(0x8AC),
    REL_NORMAL(0x8AD),
    ACC_IN_PROG(0x8AE),
    ACC_FAIL(0x8AF),
    REDIR_OR_HANDOFF(0x8B0),
    EMERGENCY_MODE(0x8B1),                                      /* no retry */
    PHONE_IN_USE(0x8B2),
    INVALID_MODE(0x8B3),                                        /* no retry */
    INVALID_SIM_STATE(0x8B4),                                   /* no retry */
    NO_COLLOC_HDR(0x8B5),
    EMM_DETACHED_PSM(0x8B6),                                    /* no retry */
    DUAL_SWITCH(0x8B7),
    PPP_TIMEOUT(0x8B8),
    PPP_AUTH_FAILURE(0x8B9),                                    /* no retry */
    PPP_OPTION_MISMATCH(0x8BA),                                 /* no retry */
    PPP_PAP_FAILURE(0x8BB),                                     /* no retry */
    PPP_CHAP_FAILURE(0x8BC),                                    /* no retry */
    PPP_ERR_CLOSE_IN_PROGRESS(0x8BD),
    EHRPD_SUBS_LIMITED_TO_V4(0x8BE),                            /* no retry */
    EHRPD_SUBS_LIMITED_TO_V6(0x8BF),                            /* no retry */
    EHRPD_VSNCP_TIMEOUT(0x8C0),
    EHRPD_VSNCP_GEN_ERROR(0x8C1),                               /* no retry */
    EHRPD_VSNCP_UNAUTH_APN(0x8C2),                              /* no retry */
    EHRPD_VSNCP_PDN_LIMIT_EXCEED(0x8C3),                        /* no retry */
    EHRPD_VSNCP_NO_PDN_GW(0x8C4),                               /* no retry */
    EHRPD_VSNCP_PDN_GW_UNREACH(0x8C5),                          /* no retry */
    EHRPD_VSNCP_PDN_GW_REJ(0x8C6),                              /* no retry */
    EHRPD_VSNCP_INSUFF_PARAM(0x8C7),                            /* no retry */
    EHRPD_VSNCP_RESOURCE_UNAVAIL(0x8C8),                        /* no retry */
    EHRPD_VSNCP_ADMIN_PROHIBIT(0x8C9),                          /* no retry */
    EHRPD_VSNCP_PDN_ID_IN_USE(0x8CA),
    EHRPD_VSNCP_SUBSCR_LIMITATION(0x8CB),                       /* no retry */
    EHRPD_VSNCP_PDN_EXISTS_FOR_THIS_APN(0x8CC),                 /* no retry */
    EHRPD_VSNCP_RECONNECT_NOT_ALLOWED(0x8CD),                   /* no retry */
    IPV6_PREFIX_UNAVAILABLE(0x8CE),
    HANDOFF_PREF_SYS_BACK_TO_SRAT(0x8CF),

    // OEM sepecific error codes. To be used by OEMs when they don't
    // want to reveal error code which would be replaced by ERROR_UNSPECIFIED
    OEM_DCFAILCAUSE_1(0x1001),
    OEM_DCFAILCAUSE_2(0x1002),
    OEM_DCFAILCAUSE_3(0x1003),
    OEM_DCFAILCAUSE_4(0x1004),
    OEM_DCFAILCAUSE_5(0x1005),
    OEM_DCFAILCAUSE_6(0x1006),
    OEM_DCFAILCAUSE_7(0x1007),
    OEM_DCFAILCAUSE_8(0x1008),
    OEM_DCFAILCAUSE_9(0x1009),
    OEM_DCFAILCAUSE_10(0x100A),
    OEM_DCFAILCAUSE_11(0x100B),
    OEM_DCFAILCAUSE_12(0x100C),
    OEM_DCFAILCAUSE_13(0x100D),
    OEM_DCFAILCAUSE_14(0x100E),
    OEM_DCFAILCAUSE_15(0x100F),

    // Local errors generated by Vendor RIL
    // specified in ril.h
    REGISTRATION_FAIL(-1),
    GPRS_REGISTRATION_FAIL(-2),
    SIGNAL_LOST(-3),                                            /* no retry */
    PREF_RADIO_TECH_CHANGED(-4),
    RADIO_POWER_OFF(-5),                                        /* no retry */
    TETHERED_CALL_ACTIVE(-6),                                   /* no retry */
    ERROR_UNSPECIFIED(0xFFFF),

    // Errors generated by the Framework
    // specified here
    UNKNOWN(0x10000),
    RADIO_NOT_AVAILABLE(0x10001),                               /* no retry */
    UNACCEPTABLE_NETWORK_PARAMETER(0x10002),                    /* no retry */
    CONNECTION_TO_DATACONNECTIONAC_BROKEN(0x10003),
    LOST_CONNECTION(0x10004),
    RESET_BY_FRAMEWORK(0x10005);

    private final int mErrorCode;
    private static final HashMap<Integer, DcFailCause> sErrorCodeToFailCauseMap;
    static {
        sErrorCodeToFailCauseMap = new HashMap<Integer, DcFailCause>();
        for (DcFailCause fc : values()) {
            sErrorCodeToFailCauseMap.put(fc.getErrorCode(), fc);
        }
    }

    /**
     * Map of subId -> set of data call setup permanent failure for the carrier.
     */
    private static final HashMap<Integer, HashSet<DcFailCause>> sPermanentFailureCache =
            new HashMap<>();

    DcFailCause(int errorCode) {
        mErrorCode = errorCode;
    }

    public int getErrorCode() {
        return mErrorCode;
    }

    /**
     * Returns whether or not the fail cause is a failure that requires a modem restart
     *
     * @param context device context
     * @param subId subscription index
     * @return true if the fail cause code needs platform to trigger a modem restart.
     */
    public boolean isRadioRestartFailure(Context context, int subId) {
        CarrierConfigManager configManager = (CarrierConfigManager)
                context.getSystemService(Context.CARRIER_CONFIG_SERVICE);
        if (configManager != null) {
            PersistableBundle b = configManager.getConfigForSubId(subId);

            if (b != null) {
                if (this == REGULAR_DEACTIVATION
                        && b.getBoolean(CarrierConfigManager
                        .KEY_RESTART_RADIO_ON_PDP_FAIL_REGULAR_DEACTIVATION_BOOL)) {
                    // This is for backward compatibility support. We need to continue support this
                    // old configuration until it gets removed in the future.
                    return true;
                }
                // Check the current configurations.
                int[] causeCodes = b.getIntArray(CarrierConfigManager
                        .KEY_RADIO_RESTART_FAILURE_CAUSES_INT_ARRAY);
                if (causeCodes != null) {
                    return Arrays.stream(causeCodes).anyMatch(i -> i == getErrorCode());
                }
            }
        }

        return false;
    }

    public boolean isPermanentFailure(Context context, int subId) {

        synchronized (sPermanentFailureCache) {

            HashSet<DcFailCause> permanentFailureSet = sPermanentFailureCache.get(subId);

            // In case of cache miss, we need to look up the settings from carrier config.
            if (permanentFailureSet == null) {
                // Retrieve the permanent failure from carrier config
                CarrierConfigManager configManager = (CarrierConfigManager)
                        context.getSystemService(Context.CARRIER_CONFIG_SERVICE);
                if (configManager != null) {
                    PersistableBundle b = configManager.getConfigForSubId(subId);
                    if (b != null) {
                        String[] permanentFailureStrings = b.getStringArray(CarrierConfigManager.
                                KEY_CARRIER_DATA_CALL_PERMANENT_FAILURE_STRINGS);

                        if (permanentFailureStrings != null) {
                            permanentFailureSet = new HashSet<>();
                            for (String failure : permanentFailureStrings) {
                                permanentFailureSet.add(DcFailCause.valueOf(failure));
                            }
                        }
                    }
                }

                // If we are not able to find the configuration from carrier config, use the default
                // ones.
                if (permanentFailureSet == null) {
                    permanentFailureSet = new HashSet<DcFailCause>() {
                        {
                            add(OPERATOR_BARRED);
                            add(MISSING_UNKNOWN_APN);
                            add(UNKNOWN_PDP_ADDRESS_TYPE);
                            add(USER_AUTHENTICATION);
                            add(ACTIVATION_REJECT_GGSN);
                            add(SERVICE_OPTION_NOT_SUPPORTED);
                            add(SERVICE_OPTION_NOT_SUBSCRIBED);
                            add(NSAPI_IN_USE);
                            add(ONLY_IPV4_ALLOWED);
                            add(ONLY_IPV6_ALLOWED);
                            add(ONLY_IPV4V6_ALLOWED);
                            add(ONLY_NON_IP_ALLOWED);
                            add(PROTOCOL_ERRORS);
                            add(MIP_FA_REASON_UNSPECIFIED);
                            add(MIP_FA_ADMIN_PROHIBITED);
                            add(MIP_FA_INSUFFICIENT_RESOURCES);
                            add(MIP_FA_MOBILE_NODE_AUTH_FAILURE);
                            add(MIP_FA_HA_AUTH_FAILURE);
                            add(MIP_FA_MALFORMED_REQUEST);
                            add(MIP_FA_MALFORMED_REPLY);
                            add(MIP_FA_ENCAPSULATION_UNAVAILABLE);
                            add(MIP_FA_VJHC_UNAVAILABLE);
                            add(MIP_FA_REV_TUNNEL_UNAVAILABLE);
                            add(MIP_FA_REV_TUNNEL_IS_MAND_AND_T_BIT_NOT_SET);
                            add(MIP_FA_DELIVERY_STYLE_NOT_SUPP);
                            add(MIP_FA_MISSING_NAI);
                            add(MIP_FA_MISSING_HA);
                            add(MIP_FA_MISSING_HOME_ADDR);
                            add(MIP_FA_MISSING_CHALLENGE);
                            add(MIP_HA_REASON_UNSPECIFIED);
                            add(MIP_HA_ADMIN_PROHIBITED);
                            add(MIP_HA_INSUFFICIENT_RESOURCES);
                            add(MIP_HA_MOBILE_NODE_AUTH_FAILURE);
                            add(MIP_HA_FA_AUTH_FAILURE);
                            add(MIP_HA_MALFORMED_REQUEST);
                            add(MIP_HA_UNKNOWN_HA_ADDR);
                            add(MIP_HA_REV_TUNNEL_UNAVAILABLE);
                            add(MIP_HA_REV_TUNNEL_IS_MANDATORY_AND_T_BIT_NOT_SET);
                            add(MIP_HA_ENCAPSULATION_UNAVAILABLE);
                            add(NW_INITIATED_TERMINATION);
                            add(MIP_CONFIG_FAILURE);
                            add(NON_IP_NOT_SUPPORTED);
                            add(CDMA_LOCK);
                            add(CDMA_REL_SO_REJ);
                            add(CCS_NOT_SUPPORTED_BY_BS);
                            add(REJECTED_BY_BS);
                            add(CCS_INCOMPATIBLE);
                            add(UIM_NOT_PRESENT);
                            add(THERMAL_EMERGENCY);
                            add(ILLEGAL_MS);
                            add(ILLEGAL_ME);
                            add(GPRS_SERVICES_AND_NON_GPRS_SERVICES_NOT_ALLOWED);
                            add(GPRS_SERVICES_NOT_ALLOWED);
                            add(PLMN_NOT_ALLOWED);
                            add(LA_NOT_ALLOWED);
                            add(GPRS_SERVICES_NOT_ALLOWED_IN_THIS_PLMN);
                            add(IMEI_NOT_ACCEPTED);
                            add(EPS_SERVICES_AND_NON_EPS_SERVICES_NOT_ALLOWED);
                            add(EPS_SERVICES_NOT_ALLOWED_IN_PLMN);
                            add(CD_BILL_OR_AUTH);
                            add(EMERGENCY_MODE);
                            add(INVALID_MODE);
                            add(INVALID_SIM_STATE);
                            add(EMM_DETACHED_PSM);
                            add(PPP_AUTH_FAILURE);
                            add(PPP_OPTION_MISMATCH);
                            add(PPP_PAP_FAILURE);
                            add(PPP_CHAP_FAILURE);
                            add(EHRPD_SUBS_LIMITED_TO_V4);
                            add(EHRPD_SUBS_LIMITED_TO_V6);
                            add(EHRPD_VSNCP_GEN_ERROR);
                            add(EHRPD_VSNCP_UNAUTH_APN);
                            add(EHRPD_VSNCP_PDN_LIMIT_EXCEED);
                            add(EHRPD_VSNCP_NO_PDN_GW);
                            add(EHRPD_VSNCP_PDN_GW_UNREACH);
                            add(EHRPD_VSNCP_PDN_GW_REJ);
                            add(EHRPD_VSNCP_INSUFF_PARAM);
                            add(EHRPD_VSNCP_RESOURCE_UNAVAIL);
                            add(EHRPD_VSNCP_ADMIN_PROHIBIT);
                            add(EHRPD_VSNCP_SUBSCR_LIMITATION);
                            add(EHRPD_VSNCP_PDN_EXISTS_FOR_THIS_APN);
                            add(EHRPD_VSNCP_RECONNECT_NOT_ALLOWED);
                            add(RADIO_POWER_OFF);
                            add(TETHERED_CALL_ACTIVE);
                            add(RADIO_NOT_AVAILABLE);
                            add(UNACCEPTABLE_NETWORK_PARAMETER);
                            add(SIGNAL_LOST);
                        }
                    };
                }

                sPermanentFailureCache.put(subId, permanentFailureSet);
            }

            return permanentFailureSet.contains(this);
        }
    }

    public boolean isEventLoggable() {
        return (this == OPERATOR_BARRED) || (this == INSUFFICIENT_RESOURCES) ||
                (this == UNKNOWN_PDP_ADDRESS_TYPE) || (this == USER_AUTHENTICATION) ||
                (this == ACTIVATION_REJECT_GGSN) || (this == ACTIVATION_REJECT_UNSPECIFIED) ||
                (this == SERVICE_OPTION_NOT_SUBSCRIBED) ||
                (this == SERVICE_OPTION_NOT_SUPPORTED) ||
                (this == SERVICE_OPTION_OUT_OF_ORDER) || (this == NSAPI_IN_USE) ||
                (this == ONLY_IPV4_ALLOWED) || (this == ONLY_IPV6_ALLOWED) ||
                (this == PROTOCOL_ERRORS) || (this == SIGNAL_LOST) ||
                (this == RADIO_POWER_OFF) || (this == TETHERED_CALL_ACTIVE) ||
                (this == UNACCEPTABLE_NETWORK_PARAMETER);
    }

    public static DcFailCause fromInt(int errorCode) {
        DcFailCause fc = sErrorCodeToFailCauseMap.get(errorCode);
        if (fc == null) {
            fc = UNKNOWN;
        }
        return fc;
    }
}
