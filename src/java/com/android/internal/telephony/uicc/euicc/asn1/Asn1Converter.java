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

/**
 * {@code Asn1Converter} provides a few convenient static methods to convert data among hex string,
 * bytes, and integer in ASN.1 DER encoding.
 */
public final class Asn1Converter {
    // A table mapping from a number to a hex character for fast encoding hex strings.
    private static final char[] HEX_CHARS = {
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
    };

    /**
     * Converts an array of bytes to a hex string.
     */
    public static String bytesToHex(byte[] bytes) {
        StringBuilder output = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            output.append(HEX_CHARS[(b & 0xFF) >>> 4]);
            output.append(HEX_CHARS[b & 0xF]);
        }
        return output.toString();
    }

    /**
     * Converts a hex string to an array of bytes. The {@code hex} should have an even length. If
     * not, the last character will be ignored.
     */
    public static byte[] hexToBytes(String hex) {
        byte[] output = new byte[hex.length() / 2];
        for (int i = 0, j = 0; i + 1 < hex.length(); i += 2, j++) {
            output[j] = (byte) (charToByte(hex.charAt(i)) << 4 | charToByte(hex.charAt(i + 1)));
        }
        return output;
    }

    /**
     * Converts BCD encoded bytes to a string. BCD bytes has reversed order of high and low digits
     * of a hex string but are supposed to be formed by decimal digits. We don't enforce decimal
     * digits for simplicity. It's possible to see hex characters in the result if the input is not
     * strictly BCD encoded.
     */
    public static String bcdBytesToString(byte[] bytes) {
        StringBuilder output = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            output.append(HEX_CHARS[b & 0xF]);
            output.append(HEX_CHARS[(b & 0xFF) >>> 4]);
        }
        return output.toString();
    }

    /**
     * Converts BCD string to bytes. See {@link #bcdBytesToString(byte[])} for the details about BCD
     * encoding.
     *
     * @param bcd This should have an even length. If not, an "0" will be appended to the string.
     */
    public static byte[] bcdStringToBytes(String bcd) {
        byte[] output = new byte[(bcd.length() + 1) / 2];
        bcdStringToBytes(bcd, output);
        return output;
    }

    /**
     * Converts BCD string to bytes and put it into the given byte array. See {@link
     * #bcdBytesToString(byte[])}.
     *
     * @param bcd This should have an even length. If not, an "0" will be appended to the string.
     * @param bytes If the array size is less than needed, the rest of the BCD string isn't be
     *     converted. If the array size is more than needed, the rest of array remains unchanged.
     */
    public static void bcdStringToBytes(String bcd, byte[] bytes) {
        if (bcd.length() % 2 != 0) {
            bcd += "0";
        }
        int size = Math.min(bytes.length * 2, bcd.length());
        for (int i = 0, j = 0; i + 1 < size; i += 2, j++) {
            bytes[j] = (byte) (charToByte(bcd.charAt(i + 1)) << 4 | charToByte(bcd.charAt(i)));
        }
    }

    /**
     * Converts a series of bytes to an integer. This method currently only supports positive 32-bit
     * integers.
     *
     * @param src The source bytes.
     * @param offset The position of the first byte of the data to be converted. The data is base
     *     256 with the most significant digit first.
     * @param length The length of the data to be converted. It must be <= 4.
     * @throws IllegalArgumentException If {@code length} is bigger than 4 or {@code src} cannot be
     *     parsed as a positive integer.
     * @throws IndexOutOfBoundsException If the range defined by {@code offset} and {@code length}
     *     exceeds the bounds of {@code src}.
     */
    static int bytesToInt(byte[] src, int offset, int length) {
        if (length > 4) {
            throw new IllegalArgumentException(
                    "length must be <= 4 (only 32-bit integer supported): " + length);
        }
        if (offset < 0 || length < 0 || offset + length > src.length) {
            throw new IndexOutOfBoundsException(
                    "Out of the bounds: src=["
                            + src.length
                            + "], offset="
                            + offset
                            + ", length="
                            + length);
        }
        int result = 0;
        for (int i = 0; i < length; i++) {
            result = (result << 8) | (src[offset + i] & 0xFF);
        }
        if (result < 0) {
            throw new IllegalArgumentException(
                    "src cannot be parsed as a positive integer: " + result);
        }
        return result;
    }

    /**
     * Converts a series of bytes to a raw long variable which can be both positive and negative.
     * This method currently only supports 64-bit long variable.
     *
     * @param src The source bytes.
     * @param offset The position of the first byte of the data to be converted. The data is base
     *     256 with the most significant digit first.
     * @param length The length of the data to be converted. It must be <= 8.
     * @throws IllegalArgumentException If {@code length} is bigger than 8.
     * @throws IndexOutOfBoundsException If the range defined by {@code offset} and {@code length}
     *     exceeds the bounds of {@code src}.
     */
    static long bytesToRawLong(byte[] src, int offset, int length) {
        if (length > 8) {
            throw new IllegalArgumentException(
                    "length must be <= 8 (only 64-bit long supported): " + length);
        }
        if (offset < 0 || length < 0 || offset + length > src.length) {
            throw new IndexOutOfBoundsException(
                    "Out of the bounds: src=["
                            + src.length
                            + "], offset="
                            + offset
                            + ", length="
                            + length);
        }
        long result = 0;
        for (int i = 0; i < length; i++) {
            result = (result << 8) | (src[offset + i] & 0xFF);
        }
        return result;
    }

    /**
     * Converts an integer to a new byte array with base 256 and the most significant digit first.
     *
     * @throws IllegalArgumentException If {@code value} is negative.
     */
    static byte[] unsignedIntToBytes(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("value must be 0 or positive: " + value);
        }
        byte[] bytes = new byte[byteNumForUnsignedInt(value)];
        unsignedIntToBytes(value, bytes, 0);
        return bytes;
    }

    /**
     * Converts an integer to a new byte array with base 256 and the most significant digit first.
     * The first byte's highest bit is used for sign. If the most significant digit is larger than
     * 127, an extra byte (0) will be prepended before it. This method currently doesn't support
     * negative values.
     *
     * @throws IllegalArgumentException If {@code value} is negative.
     */
    static byte[] signedIntToBytes(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("value must be 0 or positive: " + value);
        }
        byte[] bytes = new byte[byteNumForSignedInt(value)];
        signedIntToBytes(value, bytes, 0);
        return bytes;
    }

    /**
     * Converts an integer to a series of bytes with base 256 and the most significant digit first.
     *
     * @throws IllegalArgumentException If {@code value} is negative.
     * @throws IndexOutOfBoundsException If {@code offset} exceeds the bounds of {@code dest}.
     */
    static int unsignedIntToBytes(int value, byte[] dest, int offset) {
        return intToBytes(value, dest, offset, false);
    }

    /**
     * Converts an integer to a series of bytes with base 256 and the most significant digit first.
     * The first byte's highest bit is used for sign. If the most significant digit is larger than
     * 127, an extra byte (0) will be prepended before it. This method currently doesn't support
     * negative values.
     *
     * @throws IllegalArgumentException If {@code value} is negative.
     * @throws IndexOutOfBoundsException If {@code offset} exceeds the bounds of {@code dest}.
     */
    static int signedIntToBytes(int value, byte[] dest, int offset) {
        return intToBytes(value, dest, offset, true);
    }

    /**
     * Calculates the number of required bytes to represent {@code value}. The bytes will be base
     * 256 with the most significant digit first.
     *
     * @throws IllegalArgumentException If {@code value} is negative.
     */
    static int byteNumForUnsignedInt(int value) {
        return byteNumForInt(value, false);
    }

    /**
     * Calculates the number of required bytes to represent {@code value}. The bytes will be base
     * 256 with the most significant digit first. If the most significant digit is larger than 127,
     * an extra byte (0) will be prepended before it. This method currently only supports positive
     * integers.
     *
     * @throws IllegalArgumentException If {@code value} is negative.
     */
    static int byteNumForSignedInt(int value) {
        return byteNumForInt(value, true);
    }

    private static int intToBytes(int value, byte[] dest, int offset, boolean signed) {
        int l = byteNumForInt(value, signed);
        if (offset < 0 || offset + l > dest.length) {
            throw new IndexOutOfBoundsException("Not enough space to write. Required bytes: " + l);
        }
        for (int i = l - 1, v = value; i >= 0; i--, v >>>= 8) {
            byte b = (byte) (v & 0xFF);
            dest[offset + i] = b;
        }
        return l;
    }

    private static int byteNumForInt(int value, boolean signed) {
        if (value < 0) {
            throw new IllegalArgumentException("value must be 0 or positive: " + value);
        }
        if (signed) {
            if (value <= 0x7F) {
                return 1;
            }
            if (value <= 0x7FFF) {
                return 2;
            }
            if (value <= 0x7FFFFF) {
                return 3;
            }
        } else {
            if (value <= 0xFF) {
                return 1;
            }
            if (value <= 0xFFFF) {
                return 2;
            }
            if (value <= 0xFFFFFF) {
                return 3;
            }
        }
        return 4;
    }


    /**
     * Counts the number of trailing zero bits of a byte.
     */
    static byte countTrailingZeros(byte b) {
        if (b == 0) {
            return 8;
        }
        int v = b & 0xFF;
        byte c = 7;
        if ((v & 0x0F) != 0) {
            c -= 4;
        }
        if ((v & 0x33) != 0) {
            c -= 2;
        }
        if ((v & 0x55) != 0) {
            c -= 1;
        }
        return c;
    }

    /**
     * Converts a byte to a hex string.
     */
    static String byteToHex(byte b) {
        return new String(new char[] {HEX_CHARS[(b & 0xFF) >>> 4], HEX_CHARS[b & 0xF]});
    }

    /**
     * Converts a character of [0-9a-aA-F] to its hex value in a byte. If the character is not a
     * hex number, 0 will be returned.
     */
    private static byte charToByte(char c) {
        if (c >= 0x30 && c <= 0x39) {
            return (byte) (c - 0x30);
        } else if (c >= 0x41 && c <= 0x46) {
            return (byte) (c - 0x37);
        } else if (c >= 0x61 && c <= 0x66) {
            return (byte) (c - 0x57);
        }
        return 0;
    }

    private Asn1Converter() {}
}
