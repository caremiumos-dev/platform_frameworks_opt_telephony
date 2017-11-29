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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class OperatorIdTest {
    @Test
    public void testConstructorAndGetters() {
        OperatorId oid =
                new OperatorId(
                        new byte[] {0x23, 0x45, 0x67}, new byte[] {1, 2, 3}, new byte[] {4, 5});

        assertEquals("325", oid.getMcc());
        assertEquals("764", oid.getMnc());
        assertArrayEquals(new byte[] {1, 2, 3}, oid.getGid1());
        assertArrayEquals(new byte[] {4, 5}, oid.getGid2());

        oid = new OperatorId(new byte[] {0x23, (byte) 0xF5, 0x67}, null, null);

        assertEquals("325", oid.getMcc());
        assertEquals("76", oid.getMnc());
        assertNull(oid.getGid1());
        assertNull(oid.getGid2());
    }

    @Test
    public void testEqualsHashCode() {
        OperatorId oid = new OperatorId(new byte[] {1, 2, 3}, new byte[] {1}, new byte[] {2});
        assertTrue(oid.equals(oid));
        assertFalse(oid.equals(new Object()));

        OperatorId t = null;
        assertFalse(oid.equals(t));

        t = new OperatorId(new byte[] {1, 2, 3}, new byte[] {1}, new byte[] {2});
        assertTrue(oid.equals(t));
        assertEquals(oid.hashCode(), t.hashCode());

        t = new OperatorId(new byte[] {2, 2, 3}, new byte[] {1}, new byte[] {2});
        assertFalse(oid.equals(t));
        assertNotEquals(oid.hashCode(), t.hashCode());

        t = new OperatorId(new byte[] {1, 2, 3}, null, new byte[] {2});
        assertFalse(oid.equals(t));
        assertNotEquals(oid.hashCode(), t.hashCode());

        t = new OperatorId(new byte[] {1, 2, 3}, new byte[] {1}, null);
        assertFalse(oid.equals(t));
        assertNotEquals(oid.hashCode(), t.hashCode());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_InvalidMccMnc() {
        new OperatorId(new byte[] {1, 2, 3, 4}, new byte[] {1, 2, 3}, new byte[] {4, 5});
    }

    @Test
    public void testCreatorNewArray() {
        OperatorId[] oids = OperatorId.CREATOR.newArray(123);
        assertEquals(123, oids.length);
    }
}
