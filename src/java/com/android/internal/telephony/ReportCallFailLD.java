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

package com.android.internal.telephony;

import android.content.Context;
import android.content.Intent;
import android.telecom.XtcSecurePii;
import android.telephony.TelephonyManager;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * XTC 差异：通话失败的大数据上报（含对端号码、网络类型、小区等）。
 * 对端号码用 {@link XtcSecurePii#encrypt} 做可逆编码（并非真正加密）后经广播交给厂商
 * 大数据应用。此为实现厂商遥测的代码，仅记录行为并予以谴责。
 */
public class ReportCallFailLD {
    private static final String REPORT_CALL_FAIL_ACTION =
            "com.xtc.largedata.REPORT_CALL_FAIL_ACTION";
    private String callFailCauseCodeLD;
    private String callFailStartTimeLD;
    private String callFailVendorCauseLD;
    private TelephonyManager mTelephonyManager;
    private String netWorkType;
    private String otherNumberLD;
    private String selfCellIdLD;
    private String selfDialModeLD;
    private String voiceNetWorkTypeLD;

    public interface DataColumns {
        String CALL_FAIL_CAUSE_CODE = "call_fail_cause_code";
        String CALL_FAIL_START_TIME = "call_fail_start_time";
        String CALL_FAIL_VENDOR_CAUSE = "call_fail_vendor_cause";
        String GSMCDMA_OR_IMS = "gsmCdma_or_ims";
        String OTHER_NUMBER = "other_number";
        String SELF_CELL_ID = "self_cell_id";
        String SELF_DIAL_MODE = "self_dial_mode";
        String VOICE_NETWORK_TYPE = "voice_network_type";
        String _ID = "id";
    }

    public ReportCallFailLD(Context context) {
        mTelephonyManager = (TelephonyManager) context.getSystemService("phone");
    }

    private static String hashMapToJson(Map<?, ?> map) {
        String result = "{";
        Iterator<? extends Map.Entry<?, ?>> it = map.entrySet().iterator();
        while (true) {
            String current = result;
            if (!it.hasNext()) {
                return current.substring(0, current.lastIndexOf(",")) + "}";
            }
            Map.Entry<?, ?> entry = it.next();
            result = (current + "\"" + entry.getKey() + "\":") + "\"" + entry.getValue() + "\",";
        }
    }

    private void sendBroadcast(Context context, String json) {
        if (context == null) {
            return;
        }
        Intent intent = new Intent();
        intent.setAction(REPORT_CALL_FAIL_ACTION);
        intent.putExtra("function_name", "call_fail_ld");
        intent.putExtra("map", json);
        context.sendBroadcast(intent);
    }

    public String getCallFailCauseCode() {
        return callFailCauseCodeLD;
    }

    public String getCallFailStartTime() {
        return callFailStartTimeLD;
    }

    public String getCallFailVendorCause() {
        return callFailVendorCauseLD;
    }

    public String getOtherNumber() {
        return otherNumberLD;
    }

    public String getSelfCellId() {
        return selfCellIdLD;
    }

    public String getSelfDialMode() {
        return selfDialModeLD;
    }

    public String getVoiceNetWorkType() {
        return voiceNetWorkTypeLD;
    }

    public void report2App(Context context) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put(DataColumns.OTHER_NUMBER, XtcSecurePii.encrypt(otherNumberLD));
        map.put(DataColumns.VOICE_NETWORK_TYPE, voiceNetWorkTypeLD);
        map.put(DataColumns.GSMCDMA_OR_IMS, netWorkType);
        map.put(DataColumns.SELF_DIAL_MODE, selfDialModeLD);
        map.put(DataColumns.CALL_FAIL_START_TIME, callFailStartTimeLD);
        map.put(DataColumns.CALL_FAIL_CAUSE_CODE, callFailCauseCodeLD);
        map.put(DataColumns.CALL_FAIL_VENDOR_CAUSE, callFailVendorCauseLD);
        map.put(DataColumns.SELF_CELL_ID, selfCellIdLD);
        sendBroadcast(context, hashMapToJson(map));
    }

    public void setCallFailCauseCode(String value) {
        callFailCauseCodeLD = value;
    }

    public void setCallFailStartTime(String value) {
        callFailStartTimeLD = value;
    }

    public void setCallFailVendorCause(String value) {
        callFailVendorCauseLD = value;
    }

    public void setNetWorkType(String value) {
        netWorkType = value;
    }

    public void setOtherNumber(String value) {
        otherNumberLD = value;
    }

    public void setSelfCellId(String value) {
        selfCellIdLD = value;
    }

    public void setSelfDialMode(String value) {
        selfDialModeLD = value;
    }

    public void setVoiceNetWorkType() {
        if (mTelephonyManager == null) {
            voiceNetWorkTypeLD = "UnKnow";
        }
        if (mTelephonyManager.isVolteAvailable() && mTelephonyManager.isImsRegistered()) {
            voiceNetWorkTypeLD = "VoLte";
            return;
        }
        switch (TelephonyManager.getNetworkClass(mTelephonyManager.getVoiceNetworkType())) {
            case TelephonyManager.NETWORK_CLASS_2_G:
                voiceNetWorkTypeLD = "2G";
                break;
            case TelephonyManager.NETWORK_CLASS_3_G:
                voiceNetWorkTypeLD = "3G";
                break;
            case TelephonyManager.NETWORK_CLASS_4_G:
                voiceNetWorkTypeLD = "4G";
                break;
            default:
                voiceNetWorkTypeLD = "UnKnow";
                break;
        }
    }

    @Override
    public String toString() {
        return "ReportCallFailLD { function_name='call_fail_ld'otherNumberLD='" + otherNumberLD
                + "', voiceNetWorkTypeLD='" + voiceNetWorkTypeLD
                + "', gsmCdma_or_ims='" + netWorkType
                + "', selfDialModeLD='" + selfDialModeLD
                + "', callFailStartTimeLD='" + callFailStartTimeLD
                + "', callFailCauseCodeLD='" + callFailCauseCodeLD
                + "', callFailVendorCauseLD='" + callFailVendorCauseLD
                + "', selfCellIdLD='" + selfCellIdLD + "'}";
    }
}
