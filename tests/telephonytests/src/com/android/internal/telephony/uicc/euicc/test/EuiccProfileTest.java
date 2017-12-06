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

package com.android.internal.telephony.uicc.euicc.data;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class EuiccProfileTest {
    @Test
    public void testBuilderAndGetters() {
        EuiccProfile p =
                new EuiccProfile.Builder()
                        .setIccid("21430000000000006587")
                        .setNickname("profile nickname")
                        .setProfileName("profile name")
                        .setServiceProviderName("service provider")
                        .setOperatorId(
                                new OperatorId(
                                        new byte[] {0x23, 0x45, 0x67},
                                        new byte[] {1, 2, 3},
                                        new byte[] {4, 5}))
                        .setState(EuiccProfile.ProfileState.ENABLED)
                        .setProfileClass(EuiccProfile.ProfileClass.OPERATIONAL)
                        .setPolicyRules(EuiccProfile.PolicyRule.DO_NOT_DELETE)
                        .setCarrierPrivilegeRules(
                                new CarrierPrivilegeRule[] {
                                    new CarrierPrivilegeRule(new byte[0], null, 0)
                                })
                        .build();

        assertEquals("21430000000000006587", p.getIccid());
        assertEquals("profile nickname", p.getNickname());
        assertEquals("profile name", p.getProfileName());
        assertEquals("service provider", p.getServiceProviderName());
        assertEquals("325", p.getOperatorId().getMcc());
        assertEquals("764", p.getOperatorId().getMnc());
        assertArrayEquals(new byte[] {1, 2, 3}, p.getOperatorId().getGid1());
        assertArrayEquals(new byte[] {4, 5}, p.getOperatorId().getGid2());
        assertEquals(EuiccProfile.ProfileState.ENABLED, p.getState());
        assertEquals(EuiccProfile.ProfileClass.OPERATIONAL, p.getProfileClass());
        assertEquals(EuiccProfile.PolicyRule.DO_NOT_DELETE, p.getPolicyRules());
        assertTrue(p.hasPolicyRules());
        assertTrue(p.hasPolicyRule(EuiccProfile.PolicyRule.DO_NOT_DELETE));
        assertFalse(p.hasPolicyRule(EuiccProfile.PolicyRule.DO_NOT_DISABLE));
        assertArrayEquals(
                new CarrierPrivilegeRule[] {new CarrierPrivilegeRule(new byte[0], null, 0)},
                p.getCarrierPrivilegeRules());
    }

    @Test
    public void testBuilder_BasedOnAnotherProfile() {
        EuiccProfile p =
                new EuiccProfile.Builder()
                        .setIccid("21430000000000006587")
                        .setNickname("profile nickname")
                        .setProfileName("profile name")
                        .setServiceProviderName("service provider")
                        .setOperatorId(
                                new OperatorId(
                                        new byte[] {0x23, 0x45, 0x67},
                                        new byte[] {1, 2, 3},
                                        new byte[] {4, 5}))
                        .setState(EuiccProfile.ProfileState.ENABLED)
                        .setProfileClass(EuiccProfile.ProfileClass.OPERATIONAL)
                        .setPolicyRules(EuiccProfile.PolicyRule.DO_NOT_DELETE)
                        .setCarrierPrivilegeRules(
                                new CarrierPrivilegeRule[] {
                                    new CarrierPrivilegeRule(new byte[0], null, 0)
                                })
                        .build();

        EuiccProfile copied = new EuiccProfile.Builder(p).build();

        assertEquals(p, copied);
        assertEquals(p.hashCode(), copied.hashCode());
    }

    @Test
    public void testEqualsHashCode() {
        EuiccProfile p =
                new EuiccProfile.Builder()
                        .setIccid("21430000000000006587")
                        .setNickname("profile nickname")
                        .setProfileName("profile name")
                        .setServiceProviderName("service provider")
                        .setOperatorId(
                                new OperatorId(
                                        new byte[] {0x23, 0x45, 0x67},
                                        new byte[] {1, 2, 3},
                                        new byte[] {4, 5}))
                        .setState(EuiccProfile.ProfileState.ENABLED)
                        .setProfileClass(EuiccProfile.ProfileClass.OPERATIONAL)
                        .setPolicyRules(EuiccProfile.PolicyRule.DO_NOT_DELETE)
                        .setCarrierPrivilegeRules(
                                new CarrierPrivilegeRule[] {
                                    new CarrierPrivilegeRule(new byte[0], null, 0)
                                })
                        .build();

        assertTrue(p.equals(p));
        assertFalse(p.equals(new Object()));

        EuiccProfile t = null;
        assertFalse(p.equals(t));

        t = new EuiccProfile.Builder(p).setIccid("21").build();
        assertFalse(p.equals(t));
        assertNotEquals(p.hashCode(), t.hashCode());

        t = new EuiccProfile.Builder(p).setNickname(null).build();
        assertFalse(p.equals(t));
        assertNotEquals(p.hashCode(), t.hashCode());

        t = new EuiccProfile.Builder(p).setProfileName(null).build();
        assertFalse(p.equals(t));
        assertNotEquals(p.hashCode(), t.hashCode());

        t = new EuiccProfile.Builder(p).setServiceProviderName(null).build();
        assertFalse(p.equals(t));
        assertNotEquals(p.hashCode(), t.hashCode());

        t = new EuiccProfile.Builder(p).setOperatorId(null).build();
        assertFalse(p.equals(t));
        assertNotEquals(p.hashCode(), t.hashCode());

        t = new EuiccProfile.Builder(p).setState(EuiccProfile.ProfileState.DISABLED).build();
        assertFalse(p.equals(t));
        assertNotEquals(p.hashCode(), t.hashCode());

        t = new EuiccProfile.Builder(p).setProfileClass(EuiccProfile.ProfileClass.TESTING).build();
        assertFalse(p.equals(t));
        assertNotEquals(p.hashCode(), t.hashCode());

        t = new EuiccProfile.Builder(p).setPolicyRules(0).build();
        assertFalse(p.equals(t));
        assertNotEquals(p.hashCode(), t.hashCode());

        t = new EuiccProfile.Builder(p).setCarrierPrivilegeRules(null).build();
        assertFalse(p.equals(t));
        assertNotEquals(p.hashCode(), t.hashCode());
    }

    @Test(expected = IllegalStateException.class)
    public void testBuilderBuild_NoIccid() {
        new EuiccProfile.Builder().build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuilderSetOperatorMccMnc_Illegal() {
        new EuiccProfile.Builder()
                .setOperatorId(new OperatorId(new byte[] {1, 2, 3, 4}, null, null));
    }

    @Test
    public void testCreatorNewArray() {
        EuiccProfile[] profiles = EuiccProfile.CREATOR.newArray(123);
        assertEquals(123, profiles.length);
    }
}
