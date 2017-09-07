/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.internal.telephony.uicc;

import static org.junit.Assert.assertEquals;

import android.test.suitebuilder.annotation.SmallTest;

import com.android.internal.telephony.TelephonyTest;

import org.junit.Test;


public class UiccPkcs15Test extends TelephonyTest {

    private String getPath(String data) {
        // Continue to parse the remains/value until there's a TAG_ASN_OCTET_STRING with length of
        // two. And if there's remains, ignore the value and continue to parse the remains.
        String ret = null;
        String tmp = null;
        String rule = data;
        String tag = null;
        while (!rule.isEmpty()) {
            // Gets the first two characters as TAG.
            tag = rule.substring(0, 2);
            if (tag.equals("04")) {
                UiccCarrierPrivilegeRules.TLV tlvPath = new UiccCarrierPrivilegeRules.TLV("04");

                try {
                    android.util.Log.e("rule: ", "true");
                    tlvPath.parse(rule, true);
                    if (tlvPath.parseLength(rule).equals("02")) {
                        // Returns the value if the tag is TAG_ASN_OCTET_STRING and length is 2.
                        ret = tlvPath.getValue();
                        return ret;
                    }
                } catch (IllegalArgumentException e) {

                }
            }
            UiccCarrierPrivilegeRules.TLV tlv = new UiccCarrierPrivilegeRules.TLV(tag);
            android.util.Log.e("rule: ", rule);

            try {
                android.util.Log.e("rule: ", "true");
                tmp = tlv.parse(rule, true);
            } catch (IllegalArgumentException e) {
                // Did not consume all.
                android.util.Log.e("rule: ", "false");
                tmp = tlv.parse(rule, false);
            }

            if (tmp.isEmpty()) {
                rule = tlv.getValue();
            } else {
                rule = tmp;
            }
        }
        return ret;
    }
    @Test
    @SmallTest
    public void testGetPath() {
        assertEquals("5207", getPath("A706300404025207"));
        assertEquals("4200", getPath("A1293000300F0C0D4750205345204163632043746CA1143012"
                + "060A2A864886FC6B81480101300404024200"));
        assertEquals("4300", getPath("301004080102030405060708300404024300"));
        assertEquals("4310", getPath("3010A0080406A00000015101300404024310"));
//        assertEquals(null, getPath("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"));
    }
}
