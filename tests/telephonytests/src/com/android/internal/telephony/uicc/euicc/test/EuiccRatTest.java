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
 * limitations under the License
 */

package com.android.euicc.data;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EuiccRatTest {
    @Test
    public void testFindIndex() {
        OperatorId opA = new OperatorId(new byte[] {0x21, 0x63, 0x54}, null, new byte[] {4});
        OperatorId opB = new OperatorId(new byte[] {0x21, 0x69, 0x54}, new byte[] {4}, null);
        EuiccRat rat =
                new EuiccRat.Builder(4)
                        .add(
                                EuiccProfile.PolicyRule.DO_NOT_DELETE
                                        | EuiccProfile.PolicyRule.DO_NOT_DISABLE,
                                new OperatorId[] {}, // Matches none
                                EuiccRat.PolicyRuleFlag.CONSENT_REQUIRED)
                        .add(
                                EuiccProfile.PolicyRule.DO_NOT_DELETE,
                                new OperatorId[] {
                                    new OperatorId( // Matches none
                                            new byte[] {0x21, (byte) 0xF3, 0x54},
                                            new byte[] {4},
                                            null),
                                    new OperatorId( // Matches opA
                                            new byte[] {0x21, 0x63, 0x54}, null, new byte[] {4})
                                },
                                EuiccRat.PolicyRuleFlag.CONSENT_REQUIRED)
                        .add(
                                EuiccProfile.PolicyRule.DO_NOT_DISABLE,
                                new OperatorId[] {
                                    new OperatorId( // Matches none
                                            new byte[] {0x21, 0x63, 0x54}, null, new byte[] {5}),
                                    new OperatorId( // Matches opA and opB
                                            new byte[] {0x21, 0x6E, 0x54}, null, null)
                                },
                                0)
                        .add(
                                EuiccProfile.PolicyRule.DO_NOT_DELETE
                                        | EuiccProfile.PolicyRule.DO_NOT_DISABLE,
                                new OperatorId[] {
                                    new OperatorId( // Matches none
                                            new byte[] {0x21, 0x69, 0x54}, new byte[] {5}, null),
                                    new OperatorId( // Matches opB
                                            new byte[] {0x21, 0x6E, 0x54}, new byte[] {4}, null),
                                    new OperatorId( // Matches opA
                                            new byte[] {0x21, 0x6E, 0x54}, null, new byte[] {4})
                                },
                                EuiccRat.PolicyRuleFlag.CONSENT_REQUIRED)
                        .build();

        assertEquals(1, rat.findIndex(EuiccProfile.PolicyRule.DO_NOT_DELETE, opA));
        assertEquals(3, rat.findIndex(EuiccProfile.PolicyRule.DO_NOT_DELETE, opB));
        assertEquals(2, rat.findIndex(EuiccProfile.PolicyRule.DO_NOT_DISABLE, opA));
        assertEquals(2, rat.findIndex(EuiccProfile.PolicyRule.DO_NOT_DISABLE, opB));
        assertTrue(rat.hasPolicyRuleFlag(1, EuiccRat.PolicyRuleFlag.CONSENT_REQUIRED));
        assertFalse(rat.hasPolicyRuleFlag(2, EuiccRat.PolicyRuleFlag.CONSENT_REQUIRED));
        assertTrue(rat.hasPolicyRuleFlag(3, EuiccRat.PolicyRuleFlag.CONSENT_REQUIRED));
    }

    @Test
    public void testFindIndex_AllowAllWithUserConsent() {
        OperatorId opA = new OperatorId(new byte[] {0x21, 0x63, 0x54}, null, new byte[] {4});
        OperatorId opB = new OperatorId(new byte[] {0x78, (byte) 0xF4, 0x25}, new byte[] {4}, null);
        EuiccRat rat =
                new EuiccRat.Builder(1)
                        .add(
                                EuiccProfile.PolicyRule.DO_NOT_DELETE
                                        | EuiccProfile.PolicyRule.DO_NOT_DISABLE,
                                new OperatorId[] {
                                    new OperatorId( // Matches none
                                            new byte[] {(byte) 0xEE, (byte) 0xEE, (byte) 0xEE},
                                            null,
                                            null)
                                },
                                EuiccRat.PolicyRuleFlag.CONSENT_REQUIRED)
                        .build();
        assertEquals(0, rat.findIndex(EuiccProfile.PolicyRule.DO_NOT_DELETE, opA));
        assertEquals(0, rat.findIndex(EuiccProfile.PolicyRule.DO_NOT_DELETE, opB));
        assertEquals(0, rat.findIndex(EuiccProfile.PolicyRule.DO_NOT_DISABLE, opA));
        assertEquals(0, rat.findIndex(EuiccProfile.PolicyRule.DO_NOT_DISABLE, opB));
    }

    @Test
    public void testFindIndex_DisallowAll() {
        OperatorId opA = new OperatorId(new byte[] {0x21, 0x63, 0x54}, null, new byte[] {4});
        OperatorId opB = new OperatorId(new byte[] {0x78, (byte) 0xF4, 0x25}, new byte[] {4}, null);
        EuiccRat rat = new EuiccRat.Builder(1).add(0, new OperatorId[] {}, 0).build();
        assertEquals(-1, rat.findIndex(EuiccProfile.PolicyRule.DO_NOT_DELETE, opA));
        assertEquals(-1, rat.findIndex(EuiccProfile.PolicyRule.DO_NOT_DELETE, opB));
        assertEquals(-1, rat.findIndex(EuiccProfile.PolicyRule.DO_NOT_DISABLE, opA));
        assertEquals(-1, rat.findIndex(EuiccProfile.PolicyRule.DO_NOT_DISABLE, opB));
    }

    @Test
    public void testFindIndex_DisallowAllWithEmptyRules() {
        OperatorId opA = new OperatorId(new byte[] {0x21, 0x63, 0x54}, null, new byte[] {4});
        OperatorId opB = new OperatorId(new byte[] {0x78, 0x34, 0x25}, new byte[] {4}, null);
        EuiccRat rat = new EuiccRat.Builder(0).build();
        assertEquals(-1, rat.findIndex(EuiccProfile.PolicyRule.DO_NOT_DELETE, opA));
        assertEquals(-1, rat.findIndex(EuiccProfile.PolicyRule.DO_NOT_DELETE, opB));
        assertEquals(-1, rat.findIndex(EuiccProfile.PolicyRule.DO_NOT_DISABLE, opA));
        assertEquals(-1, rat.findIndex(EuiccProfile.PolicyRule.DO_NOT_DISABLE, opB));
    }

    @Test(expected = IllegalStateException.class)
    public void testBuild_NotEnoughRules() {
        new EuiccRat.Builder(1).build();
    }

    @Test(expected = ArrayIndexOutOfBoundsException.class)
    public void testBuild_TooManyRules() {
        new EuiccRat.Builder(0).add(0, new OperatorId[] {}, 0).build();
    }

    @Test(expected = ArrayIndexOutOfBoundsException.class)
    public void testHasPolicyRuleFlag_OutOfBounds() {
        EuiccRat rat = new EuiccRat.Builder(1).add(0, new OperatorId[] {}, 0).build();
        rat.hasPolicyRuleFlag(1, EuiccRat.PolicyRuleFlag.CONSENT_REQUIRED);
    }

    @Test
    public void testMatch() {
        assertTrue(EuiccRat.match("12", "12"));
        assertTrue(EuiccRat.match("1E", "12"));
        assertTrue(EuiccRat.match("12E", "12"));
        assertTrue(EuiccRat.match("EEE", "12"));
        assertTrue(EuiccRat.match("120", "120"));
        assertTrue(EuiccRat.match("12E", "120"));
        assertTrue(EuiccRat.match("EEE", "120"));

        assertFalse(EuiccRat.match("13", "12"));
        assertFalse(EuiccRat.match("2E", "12"));
        assertFalse(EuiccRat.match("123", "120"));
        assertFalse(EuiccRat.match("1E", "120"));
        assertFalse(EuiccRat.match("EE", "120"));
    }
}
