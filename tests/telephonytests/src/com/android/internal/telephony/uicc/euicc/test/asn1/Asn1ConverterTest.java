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

package com.android.euicc.asn1;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class Asn1ConverterTest {
    @Test
    public void testBytesToInt() {
        assertEquals(0, Asn1Converter.bytesToInt(new byte[] {}, 0, 0));
        assertEquals(0, Asn1Converter.bytesToInt(new byte[] {0}, 0, 0));
        assertEquals(0x1234, Asn1Converter.bytesToInt(new byte[] {0x12, 0x34}, 0, 2));
        assertEquals(0x34, Asn1Converter.bytesToInt(new byte[] {0x12, 0x34}, 1, 1));
        assertEquals(0xFF, Asn1Converter.bytesToInt(new byte[] {-2, 0, -1, -3, -4}, 1, 2));
        assertEquals(0x7FFFFFFF, Asn1Converter.bytesToInt(new byte[] {0x7F, -1, -1, -1}, 0, 4));
        assertEquals(0x80, Asn1Converter.bytesToInt(new byte[] {0x00, -128}, 0, 2));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBytesToInt_IllegalLength() {
        Asn1Converter.bytesToInt(new byte[] {1, 2, 3, 4, 4}, 0, 5);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testBytesToInt_IndexOutOfBounds() {
        Asn1Converter.bytesToInt(new byte[] {1, 2}, 0, 3);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBytesToInt_NegativeResult() {
        Asn1Converter.bytesToInt(new byte[] {-1, 1, 2, 3}, 0, 4);
    }

    @Test
    public void testBytesToLong() {
        assertEquals(0, Asn1Converter.bytesToRawLong(new byte[] {}, 0, 0));
        assertEquals(0, Asn1Converter.bytesToRawLong(new byte[] {0}, 0, 0));
        assertEquals(
                0x12345678,
                Asn1Converter.bytesToRawLong(new byte[] {0x12, 0x34, 0x56, 0x78}, 0, 4));
        assertEquals(0x34, Asn1Converter.bytesToRawLong(new byte[] {0x12, 0x34, 0x56, 0x78}, 1, 1));
        assertEquals(0xFF, Asn1Converter.bytesToRawLong(new byte[] {-2, 0, -1, -3, -4}, 1, 2));
        assertEquals(
                -1,
                Asn1Converter.bytesToRawLong(new byte[] {-1, -1, -1, -1, -1, -1, -1, -1}, 0, 8));
        assertEquals(0x80, Asn1Converter.bytesToRawLong(new byte[] {0x00, -128}, 0, 2));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBytesToLong_IllegalLength() {
        Asn1Converter.bytesToRawLong(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9}, 0, 9);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testBytesToLong_IndexOutOfBounds() {
        Asn1Converter.bytesToRawLong(new byte[] {1, 2}, 0, 3);
    }

    @Test
    public void testUnsignedToBytes() {
        assertArrayEquals(new byte[] {0}, Asn1Converter.unsignedIntToBytes(0));
        assertArrayEquals(new byte[] {0x12, 0x34}, Asn1Converter.unsignedIntToBytes(0x1234));
        assertArrayEquals(
                new byte[] {0x7F, 0x12, -2, 0x34}, Asn1Converter.unsignedIntToBytes(0x7F12FE34));
        assertArrayEquals(new byte[] {-128}, Asn1Converter.unsignedIntToBytes(0x80));
        assertArrayEquals(new byte[] {-128, 0x05}, Asn1Converter.unsignedIntToBytes(0x8005));

        byte[] bs = new byte[] {0, 0, 0, 0, 0, 0};
        assertEquals(4, Asn1Converter.unsignedIntToBytes(0x7F12FE34, bs, 2));
        assertArrayEquals(new byte[] {0, 0, 0x7F, 0x12, -2, 0x34}, bs);
    }

    @Test
    public void testSignedToBytes() {
        assertArrayEquals(new byte[] {0}, Asn1Converter.signedIntToBytes(0));
        assertArrayEquals(new byte[] {0x12, 0x34}, Asn1Converter.signedIntToBytes(0x1234));
        assertArrayEquals(
                new byte[] {0x7F, 0x12, -2, 0x34}, Asn1Converter.signedIntToBytes(0x7F12FE34));
        assertArrayEquals(new byte[] {0, -128}, Asn1Converter.signedIntToBytes(0x80));
        assertArrayEquals(new byte[] {0, -128, 0x05}, Asn1Converter.signedIntToBytes(0x8005));

        byte[] bs = new byte[] {0, 0, 0, 0, 0, 0};
        assertEquals(4, Asn1Converter.signedIntToBytes(0x801234, bs, 2));
        assertArrayEquals(new byte[] {0, 0, 0, -128, 0x12, 0x34}, bs);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testUnsignedIntToBytes_NoEnoughSpace() {
        byte[] bs = new byte[] {0, 0, 0, 0, 0, 0};
        Asn1Converter.unsignedIntToBytes(0x7F12FE34, bs, 3);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testSignedIntToBytes_NoEnoughSpace() {
        byte[] bs = new byte[] {0, 0, 0, 0, 0, 0};
        Asn1Converter.signedIntToBytes(0x801234, bs, 3);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUnsignedIntToBytes_NegativeNumber() {
        Asn1Converter.unsignedIntToBytes(-1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSignedIntToBytes_NegativeNumber() {
        Asn1Converter.signedIntToBytes(-1);
    }

    @Test
    public void testByteNumForUnsignedInt() {
        assertEquals(1, Asn1Converter.byteNumForUnsignedInt(0));
        assertEquals(1, Asn1Converter.byteNumForUnsignedInt(0x12));
        assertEquals(1, Asn1Converter.byteNumForUnsignedInt(0x7F));
        assertEquals(1, Asn1Converter.byteNumForUnsignedInt(0x80));
        assertEquals(2, Asn1Converter.byteNumForUnsignedInt(0x7FFF));
        assertEquals(2, Asn1Converter.byteNumForUnsignedInt(0x8000));
        assertEquals(3, Asn1Converter.byteNumForUnsignedInt(0x7FFFFF));
        assertEquals(3, Asn1Converter.byteNumForUnsignedInt(0x800000));
        assertEquals(4, Asn1Converter.byteNumForUnsignedInt(0x12345678));
        assertEquals(4, Asn1Converter.byteNumForUnsignedInt(0x7FFFFFFF));
    }

    @Test
    public void testByteNumForSignedInt() {
        assertEquals(1, Asn1Converter.byteNumForSignedInt(0));
        assertEquals(1, Asn1Converter.byteNumForSignedInt(0x12));
        assertEquals(1, Asn1Converter.byteNumForSignedInt(0x7F));
        assertEquals(2, Asn1Converter.byteNumForSignedInt(0x80));
        assertEquals(2, Asn1Converter.byteNumForSignedInt(0x7FFF));
        assertEquals(3, Asn1Converter.byteNumForSignedInt(0x8000));
        assertEquals(3, Asn1Converter.byteNumForSignedInt(0x7FFFFF));
        assertEquals(4, Asn1Converter.byteNumForSignedInt(0x800000));
        assertEquals(4, Asn1Converter.byteNumForSignedInt(0x12345678));
        assertEquals(4, Asn1Converter.byteNumForSignedInt(0x7FFFFFFF));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testByteNumForUnsignedInt_NegativeNumber() {
        Asn1Converter.byteNumForUnsignedInt(0xFFFFFFFF);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testByteNumForSignedInt_NegativeNumber() {
        Asn1Converter.byteNumForSignedInt(0xFFFFFFFF);
    }

    @Test
    public void testCountTrailingZeros() {
        assertEquals(8, Asn1Converter.countTrailingZeros((byte) 0));
        assertEquals(7, Asn1Converter.countTrailingZeros((byte) 0x80));
        assertEquals(5, Asn1Converter.countTrailingZeros((byte) 0xA0));
        assertEquals(0, Asn1Converter.countTrailingZeros((byte) 1));
    }

    @Test
    public void testBytesToHex() {
        assertEquals("", Asn1Converter.bytesToHex(new byte[] {}));
        assertEquals("00", Asn1Converter.bytesToHex(new byte[] {0}));
        assertEquals(
                "FF12FE34FD56FC78",
                Asn1Converter.bytesToHex(new byte[] {-1, 0x12, -2, 0x34, -3, 0x56, -4, 0x78}));
    }

    @Test
    public void testHexToBytes() {
        assertArrayEquals(new byte[] {}, Asn1Converter.hexToBytes(""));
        assertArrayEquals(new byte[] {0}, Asn1Converter.hexToBytes("00"));
        assertArrayEquals(
                new byte[] {-1, 0x12, -2, 0x34, -3, 0x56, -4, 0x78},
                Asn1Converter.hexToBytes("FF12FE34FD56FC78"));
        // The last character is ignored when the length is uneven.
        assertArrayEquals(
                new byte[] {-1, 0x12, -2, 0x34, -3, 0x56, -4, 0x78},
                Asn1Converter.hexToBytes("FF12FE34FD56FC780"));
    }

    @Test
    public void testBcdBytesToString() {
        assertEquals("", Asn1Converter.bcdBytesToString(new byte[] {}));
        assertEquals("00", Asn1Converter.bcdBytesToString(new byte[] {0}));
        assertEquals("214365", Asn1Converter.bcdBytesToString(new byte[] {0x12, 0x34, 0x56}));
    }

    @Test
    public void testBcdStringToBytes() {
        assertArrayEquals(new byte[] {}, Asn1Converter.bcdStringToBytes(""));
        assertArrayEquals(new byte[] {0}, Asn1Converter.bcdStringToBytes("00"));
        assertArrayEquals(new byte[] {0x21, 0x43, 0x65}, Asn1Converter.bcdStringToBytes("123456"));
        assertArrayEquals(new byte[] {0x21, 0x43, 0x05}, Asn1Converter.bcdStringToBytes("12345"));

        byte[] output;
        output = new byte[3];
        Asn1Converter.bcdStringToBytes("123456", output);
        assertArrayEquals(new byte[] {0x21, 0x43, 0x65}, output);

        output = new byte[2];
        Asn1Converter.bcdStringToBytes("123456", output);
        assertArrayEquals(new byte[] {0x21, 0x43}, output);

        output = new byte[4];
        Asn1Converter.bcdStringToBytes("12345", output);
        assertArrayEquals(new byte[] {0x21, 0x43, 0x05, 0}, output);
    }
}
