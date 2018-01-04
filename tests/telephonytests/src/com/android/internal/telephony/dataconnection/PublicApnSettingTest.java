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

package com.android.internal.telephony.dataconnection;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;

import static org.junit.Assert.assertEquals;

import android.os.PersistableBundle;
import android.telephony.ApnSetting;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.InetAddress;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

public class PublicApnSettingTest extends TelephonyTest {

    private PersistableBundle mBundle;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mBundle = mContextFixture.getCarrierConfigBundle();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    static ApnSetting createApnSetting(String[] apnTypes) {
        return createApnSettingInternal(apnTypes, true);
    }

    private static ApnSetting createDisabledApnSetting(String[] apnTypes) {
        return createApnSettingInternal(apnTypes, false);
    }

    private static ApnSetting createApnSettingInternal(String[] apnTypes, boolean carrierEnabled) {
        return ApnSetting.makeApnSetting(
                2163,                   // id
                "44010",                // numeric
                "sp-mode",              // name
                "spmode.ne.jp",         // apn
                null,                   // proxy
                -1,                     // port
                null,                   // mmsc
                null,                   // mmsproxy
                -1,                     // mmsport
                "",                     // user
                "",                     // password
                -1,                     // authtype
                Arrays.asList(apnTypes), // types
                "IP",                   // protocol
                "IP",                   // roaming_protocol
                carrierEnabled,         // carrier_enabled
                0,                      // bearer
                0,                      // bearer_bitmask
                0,                      // profile_id
                false,                  // modem_cognitive
                0,                      // max_conns
                0,                      // wait_time
                0,                      // max_conns_time
                0,                      // mtu
                "",                     // mvno_type
                "");                    // mnvo_match_data
    }

    private static void assertApnSettingsEqual(List<ApnSetting> a1, List<ApnSetting> a2) {
        assertEquals(a1.size(), a2.size());
        for (int i = 0; i < a1.size(); ++i) {
            assertApnSettingEqual(a1.get(i), a2.get(i));
        }
    }

    private static void assertApnSettingEqual(ApnSetting a1, ApnSetting a2) {
        assertEquals(a1.getEntryName(), a2.getEntryName());
        assertEquals(a1.getApnName(), a2.getApnName());
        assertEquals(a1.getProxy(), a2.getProxy());
        assertEquals(a1.getPort(), a2.getPort());
        assertEquals(a1.getMmsc(), a2.getMmsc());
        assertEquals(a1.getMmsProxy(), a2.getMmsProxy());
        assertEquals(a1.getMmsPort(), a2.getMmsPort());
        assertEquals(a1.getUser(), a2.getUser());
        assertEquals(a1.getPassword(), a2.getPassword());
        assertEquals(a1.getAuthType(), a2.getAuthType());
        assertEquals(a1.getId(), a2.getId());
        assertEquals(a1.getOperatorNumeric(), a2.getOperatorNumeric());
        assertEquals(a1.getProtocol(), a2.getProtocol());
        assertEquals(a1.getRoamingProtocol(), a2.getRoamingProtocol());
        List<String> types1 = a1.getTypes();
        List<String> types2 = a2.getTypes();
        assertEquals(types1.size(), types2.size());
        for (int i = 0; i < types1.size(); i++) {
            assertEquals(types1.get(i), types2.get(i));
        }
        assertEquals(a1.isEnabled(), a2.isEnabled());
        assertEquals(a1.getBearerBitmask(), a2.getBearerBitmask());
        assertEquals(a1.getProfileId(), a2.getProfileId());
        assertEquals(a1.getModemCognitive(), a2.getModemCognitive());
        assertEquals(a1.getMaxConns(), a2.getMaxConns());
        assertEquals(a1.getWaitTime(), a2.getWaitTime());
        assertEquals(a1.getMaxConnsTime(), a2.getMaxConnsTime());
        assertEquals(a1.getMtu(), a2.getMtu());
        assertEquals(a1.getMvnoType(), a2.getMvnoType());
        assertEquals(a1.getMvnoMatchData(), a2.getMvnoMatchData());
    }

    @Test
    @SmallTest
    public void testToString() throws Exception {
        String[] types = {"default", "*"};
        ApnSetting apn = ApnSetting.makeApnSetting(
                99, "12345", "Name", "apn", null, -1,
                null, null, -1, "user", "password", 0,
                Arrays.asList(types), "IPV6", "IP", true, 14, 0, 0, false, 0, 0, 0, 0, "", "");
        String expected = "[ApnSettingV3] Name, 99, 12345, apn, , "
                + ", , , , 0, default | *, "
                + "IPV6, IP, true, 14, 8192, 0, false, 0, 0, 0, 0, , , false";
        assertEquals(expected, apn.toString());
    }

    @Test
    @SmallTest
    public void testCanHandleType() throws Exception {
        String[] types = {"mms"};

        // empty string replaced with ALL ('*') when loaded to db
        assertFalse(createApnSetting(new String[]{})
                .canHandleType(ApnSetting.TYPE_MMS));

        assertTrue(createApnSetting(new String[]{ApnSetting.TYPE_ALL})
                .canHandleType(ApnSetting.TYPE_MMS));

        assertFalse(createApnSetting(new String[]{ApnSetting.TYPE_DEFAULT})
                .canHandleType(ApnSetting.TYPE_MMS));

        assertTrue(createApnSetting(new String[]{"DEfAULT"})
                .canHandleType("defAult"));

        // Hipri is asymmetric
        assertTrue(createApnSetting(new String[]{ApnSetting.TYPE_DEFAULT})
                .canHandleType(ApnSetting.TYPE_HIPRI));
        assertFalse(createApnSetting(new String[]{ApnSetting.TYPE_HIPRI})
                .canHandleType(ApnSetting.TYPE_DEFAULT));

        assertTrue(createApnSetting(new String[]{ApnSetting.TYPE_DEFAULT, ApnSetting.TYPE_MMS})
                .canHandleType(ApnSetting.TYPE_DEFAULT));

        assertTrue(createApnSetting(new String[]{ApnSetting.TYPE_DEFAULT, ApnSetting.TYPE_MMS})
                .canHandleType(ApnSetting.TYPE_MMS));

        assertFalse(createApnSetting(new String[]{ApnSetting.TYPE_DEFAULT, ApnSetting.TYPE_MMS})
                .canHandleType(ApnSetting.TYPE_SUPL));

        // special IA case - doesn't match wildcards
        assertFalse(createApnSetting(new String[]{ApnSetting.TYPE_DEFAULT, ApnSetting.TYPE_MMS})
                .canHandleType(ApnSetting.TYPE_IA));
        assertFalse(createApnSetting(new String[]{ApnSetting.TYPE_ALL})
                .canHandleType(ApnSetting.TYPE_IA));
        assertFalse(createApnSetting(new String[]{ApnSetting.TYPE_ALL})
                .canHandleType("iA"));
        assertTrue(createApnSetting(new String[]{ApnSetting.TYPE_DEFAULT,
                ApnSetting.TYPE_MMS, ApnSetting.TYPE_IA})
                .canHandleType(ApnSetting.TYPE_IA));

        // check carrier disabled
        assertFalse(createDisabledApnSetting(new String[]{ApnSetting.TYPE_ALL})
                .canHandleType(ApnSetting.TYPE_MMS));
        assertFalse(createDisabledApnSetting(new String[]{"DEfAULT"})
                .canHandleType("defAult"));
        assertFalse(createDisabledApnSetting(new String[]{ApnSetting.TYPE_DEFAULT})
                .canHandleType(ApnSetting.TYPE_HIPRI));
        assertFalse(createDisabledApnSetting(new String[]{ApnSetting.TYPE_DEFAULT,
                ApnSetting.TYPE_MMS}).canHandleType(ApnSetting.TYPE_DEFAULT));
        assertFalse(createDisabledApnSetting(new String[]{ApnSetting.TYPE_DEFAULT,
                ApnSetting.TYPE_MMS}).canHandleType(ApnSetting.TYPE_MMS));
        assertFalse(createDisabledApnSetting(new String[]
                {ApnSetting.TYPE_DEFAULT, ApnSetting.TYPE_MMS, ApnSetting.TYPE_IA})
                .canHandleType(ApnSetting.TYPE_IA));
    }

    @Test
    @SmallTest
    public void testEquals() throws Exception {
        final int dummyInt = 1;
        final String dummyString = "dummy";
        final String[] dummyStringArr = new String[] {"dummy"};
        URL dummyURL = new URL("https://www.google.com");
        InetAddress dummyInetAddress = InetAddress.getByName("google.com");
        // base apn
        ApnSetting baseApn = createApnSetting(new String[] {"mms", "default"});
        Field[] fields = ApnSetting.class.getDeclaredFields();
        for (Field f : fields) {
            int modifiers = f.getModifiers();
            if (Modifier.isStatic(modifiers) || !Modifier.isFinal(modifiers)) {
                continue;
            }
            f.setAccessible(true);
            ApnSetting testApn = null;
            if (int.class.equals(f.getType())) {
                testApn = ApnSetting.makeApnSetting(baseApn);
                f.setInt(testApn, dummyInt + f.getInt(testApn));
            } else if (boolean.class.equals(f.getType())) {
                testApn = ApnSetting.makeApnSetting(baseApn);
                f.setBoolean(testApn, !f.getBoolean(testApn));
            } else if (String.class.equals(f.getType())) {
                testApn = ApnSetting.makeApnSetting(baseApn);
                f.set(testApn, dummyString);
            } else if (List.class.equals(f.getType())) {
                testApn = ApnSetting.makeApnSetting(baseApn);
                f.set(testApn, Arrays.asList(dummyStringArr));
            } else if (URL.class.equals(f.getType())) {
                testApn = ApnSetting.makeApnSetting(baseApn);
                f.set(testApn, dummyURL);
            } else if (InetAddress.class.equals(f.getType())) {
                testApn = ApnSetting.makeApnSetting(baseApn);
                f.set(testApn, dummyInetAddress);
            } else {
                fail("Unsupported field:" + f.getName());
            }
            if (testApn != null) {
                assertFalse(f.getName() + " is NOT checked", testApn.equals(baseApn));
            }
        }
    }

    @Test
    @SmallTest
    public void testEqualsRoamingProtocol() throws Exception {
        ApnSetting apn1 = ApnSetting.makeApnSetting(
                1234,
                "310260",
                "",
                "ims",
                null,
                -1,
                null,
                null,
                -1,
                "",
                "",
                -1,
                Arrays.asList(new String[]{"ims"}),
                "IPV6",
                "",
                true,
                0,
                131071,
                0,
                false,
                0,
                0,
                0,
                1440,
                "",
                "");

        ApnSetting apn2 = ApnSetting.makeApnSetting(
                1235,
                "310260",
                "",
                "ims",
                null,
                -1,
                null,
                null,
                -1,
                "",
                "",
                -1,
                Arrays.asList(new String[]{"ims"}),
                "IPV6",
                "IPV6",
                true,
                0,
                131072,
                0,
                false,
                0,
                0,
                0,
                1440,
                "",
                "");

        assertTrue(apn1.equals(apn2, false));
        assertFalse(apn1.equals(apn2, true));
    }
}
