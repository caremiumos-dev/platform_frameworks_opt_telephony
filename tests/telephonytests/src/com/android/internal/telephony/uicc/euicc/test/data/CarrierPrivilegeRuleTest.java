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
 * limitations under the License
 */
package com.android.euicc.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CarrierPrivilegeRuleTest {
    @Test
    public void testToUiccAccessRule() {
        byte[] signature = new byte[] {0x01, 0x02, 0x03, 0x04};
        String packageName = "test.package";
        long accessType = 12345L;
        CarrierPrivilegeRule rule = new CarrierPrivilegeRule(signature, packageName, accessType);
        assertNotNull(rule.toUiccAccessRule());
    }

    @Test
    public void testEqualsHashCode() {
        CarrierPrivilegeRule rule =
                new CarrierPrivilegeRule(new byte[] {0x01}, "test.package", 12345L);
        assertTrue(rule.equals(rule));
        assertFalse(rule.equals(new Object()));

        CarrierPrivilegeRule r = null;
        assertFalse(rule.equals(r));

        r = new CarrierPrivilegeRule(new byte[] {0x01}, "test.package", 12345L);
        assertTrue(rule.equals(r));
        assertEquals(rule.hashCode(), r.hashCode());

        r = new CarrierPrivilegeRule(new byte[] {0x02}, "test.package", 12345L);
        assertFalse(rule.equals(r));
        assertNotEquals(rule.hashCode(), r.hashCode());

        r = new CarrierPrivilegeRule(new byte[] {0x01}, "other.package", 12345L);
        assertFalse(rule.equals(r));
        assertNotEquals(rule.hashCode(), r.hashCode());

        r = new CarrierPrivilegeRule(new byte[] {0x01}, "test.package", 54321L);
        assertFalse(rule.equals(r));
        assertNotEquals(rule.hashCode(), r.hashCode());
    }
}
