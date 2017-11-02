/*
 * Copyright 2017 The Android Open Source Project
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

package com.android.internal.telephony.cdma;

import static org.junit.Assert.assertArrayEquals;

import android.test.AndroidTestCase;

import com.android.internal.telephony.cdma.sms.CdmaSmsAddress;
import com.android.internal.telephony.cdma.sms.UserData;

import org.junit.Test;

/**
 * Tests for {@link CdmaSmsAddress}.
 */
public class CdmaSmsAddressTest extends AndroidTestCase {
    @Test
    public void testNumberAddress() {
        String address = "3141592653";
        CdmaSmsAddress cdmaAddress = CdmaSmsAddress.parse(address);
        assertEquals(address, cdmaAddress.address);
        assertEquals(CdmaSmsAddress.DIGIT_MODE_4BIT_DTMF, cdmaAddress.digitMode);
        assertEquals(CdmaSmsAddress.NUMBER_MODE_NOT_DATA_NETWORK, cdmaAddress.numberMode);
        assertArrayEquals(parseToDtmf(address), cdmaAddress.origBytes);
    }

    @Test
    public void testNumberWithSugar() {
        String address = "(314)1592653";
        CdmaSmsAddress cdmaAddress = CdmaSmsAddress.parse(address);
        assertEquals(address, cdmaAddress.address);
        assertEquals(CdmaSmsAddress.DIGIT_MODE_4BIT_DTMF, cdmaAddress.digitMode);
        assertEquals(CdmaSmsAddress.NUMBER_MODE_NOT_DATA_NETWORK, cdmaAddress.numberMode);
        assertArrayEquals(parseToDtmf("3141592653"), cdmaAddress.origBytes);
    }

    @Test
    public void testInternationalAddress() {
        String address = "+10068";
        CdmaSmsAddress cdmaAddress = CdmaSmsAddress.parse(address);
        assertEquals(address, cdmaAddress.address);
        assertEquals(CdmaSmsAddress.DIGIT_MODE_8BIT_CHAR, cdmaAddress.digitMode);
        assertEquals(CdmaSmsAddress.NUMBER_MODE_NOT_DATA_NETWORK, cdmaAddress.numberMode);
        assertEquals(CdmaSmsAddress.NUMBERING_PLAN_ISDN_TELEPHONY, cdmaAddress.numberPlan);
        assertEquals(CdmaSmsAddress.TON_INTERNATIONAL, cdmaAddress.ton);
        assertArrayEquals(UserData.stringToAscii(address), cdmaAddress.origBytes);
    }

    @Test
    public void testEmailAddress() {
        String address = "fb769394+10086@tvb.com";
        CdmaSmsAddress cdmaAddress = CdmaSmsAddress.parse(address);
        assertEquals(address, cdmaAddress.address);
        assertEquals(CdmaSmsAddress.DIGIT_MODE_8BIT_CHAR, cdmaAddress.digitMode);
        assertEquals(CdmaSmsAddress.NUMBER_MODE_DATA_NETWORK, cdmaAddress.numberMode);
        assertEquals(CdmaSmsAddress.TON_NATIONAL_OR_EMAIL, cdmaAddress.ton);
        assertArrayEquals(UserData.stringToAscii(address), cdmaAddress.origBytes);
    }

    private static byte[] parseToDtmf(String address) {
        int digits = address.length();
        byte[] result = new byte[digits];
        for (int i = 0; i < digits; i++) {
            char c = address.charAt(i);
            int val;
            if ((c >= '1') && (c <= '9')) {
                val = c - '0';
            } else if (c == '0') {
                val = 10;
            } else if (c == '*') {
                val = 11;
            } else if (c == '#') {
                val = 12;
            } else {
                return null;
            }
            result[i] = (byte) val;
        }
        return result;
    }
}
