/*
 * Copyright 2018 The Android Open Source Project
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

import android.telephony.Rlog;
import android.util.Log;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;

/**
 * This class parses an Answer To Reset (ATR) message.
 * The ATR message structure is defined in standard ISO/IEC 7816-3. The eUICC related ATR message
 * is defined in standard ETSI TS 102 221 V14.0.0.
 */
public class AnswerToReset {
    private static final String TAG = "AnswerToReset";
    private static final boolean DBG = true;

    public static final String EUICC_SUPPORTED = "82";
    public static final String DIRECT_CONVENTION = "3B";
    public static final String INVERSE_CONVENTION = "3F";
    public static final int INTERFACE_BYTES_MASK = 0xF0;
    public static final int T_MASK = 0x0F;
    public static final int T_VALUE_FOR_GLOBAL_INTERFACE = 15;
    public static final int TA_MASK = 0x10;
    public static final int TB_MASK = 0x20;
    public static final int TC_MASK = 0x40;
    public static final int TD_MASK = 0x80;

    private boolean mIsDirectConvention = false;
    private boolean mOnlyTEqualsZero = true;
    private boolean mIsEuiccSupported = false;
    private String mFormatByte;
    private ArrayList<InterfaceByte> mInterfaceBytes = new ArrayList<>();
    private ArrayList<String> mHistoricalBytes = new ArrayList<>();
    private int mHistoricalBytesLength = 0;
    private String mCheckByte;

    /**
     * Returns an AnswerToReset by parsing the input atr string, return null if the parsing fails.
     */
    public static AnswerToReset make(String atr) {
        AnswerToReset answerToReset = new AnswerToReset();
        if (answerToReset.parseAtrString(atr)) {
            return answerToReset;
        } else {
            return null;
        }
    }

    private AnswerToReset() {}

    private String readNextByteOrNull(String str, int index) {
        try {
            String value = str.substring(index, index + 2);
            return value;
        } catch (StringIndexOutOfBoundsException e) {
            Log.e(TAG, "readNextByteOrFail: ", e);
            return null;
        }
    }

    private int parseConventionByte(String atr, int index) {
        String value = atr.substring(index, index + 2);
        if (value.equals(DIRECT_CONVENTION)) {
            mIsDirectConvention = true;
        } else if (value.equals(INVERSE_CONVENTION)) {
            mIsDirectConvention = false;
        } else {
            loge("Unrecognized convention byte " + value);
            return -1;
        }
        return index + 2;
    }

    private int parseFormatByte(String atr, int index) {
        mFormatByte = readNextByteOrNull(atr, index);
        if (mFormatByte == null) {
            loge("Failed to read the format byted.");
            return -1;
        }
        mHistoricalBytesLength = Integer.parseInt(mFormatByte, 16) & T_MASK;
        return index + 2;
    }

    private int parseInterfaceBytes(String atr, int index) {
        boolean hasFoundTheFirstTEquals15 = false;
        // The first lastTD is actually not any TD but instead the format byte.
        String lastTD = mFormatByte;
        while (lastTD != null) {
            // Parse the interface bytes.
            int lastTDValue = Integer.parseInt(lastTD, 16);
            if ((lastTDValue & INTERFACE_BYTES_MASK) != 0) {
                InterfaceByte interfaceByte = new InterfaceByte();
                if ((lastTDValue & TA_MASK) != 0) {
                    String b = readNextByteOrNull(atr, index);
                    if (b != null) {
                        interfaceByte.tA = b;
                        index = index + 2;
                    } else {
                        loge("Failed to read the byte for TA.");
                        return -1;
                    }
                }

                if ((lastTDValue & TB_MASK) != 0) {
                    String b = readNextByteOrNull(atr, index);
                    if (b != null) {
                        interfaceByte.tB = b;
                        // eUICC is supported only if the value of the first tB after T=15 is 82.
                        if (hasFoundTheFirstTEquals15 && b.equals(EUICC_SUPPORTED)) {
                            mIsEuiccSupported = true;
                            hasFoundTheFirstTEquals15 = false;
                        }
                        index = index + 2;
                    } else {
                        loge("Failed to read the byte for TB.");
                        return -1;
                    }
                }

                if ((lastTDValue & TC_MASK) != 0) {
                    String b = readNextByteOrNull(atr, index);
                    if (b != null) {
                        interfaceByte.tC = b;
                        index = index + 2;
                    } else {
                        loge("Failed to read the byte for TC.");
                        return -1;
                    }
                }

                if ((lastTDValue & TD_MASK) != 0) {
                    String b = readNextByteOrNull(atr, index);
                    if (b != null) {
                        interfaceByte.tD = b;
                        index = index + 2;
                    } else {
                        loge("Failed to read the byte for TD.");
                        return -1;
                    }
                }
                lastTD = interfaceByte.tD;
                mInterfaceBytes.add(interfaceByte);

                // Parse the T values from all the TD.
                if (lastTD != null) {
                    int TDValue = Integer.parseInt(lastTD, 16);
                    if ((TDValue & T_MASK) != 0) {
                        mOnlyTEqualsZero = false;
                        if ((TDValue & T_MASK) == T_VALUE_FOR_GLOBAL_INTERFACE) {
                            hasFoundTheFirstTEquals15 = true;
                        }
                    }
                }
            } else {
                lastTD = null;
            }
        }
        return index;
    }

    private int parseHistoricalBytes(String atr, int index) {
        for (int i = 0; i < mHistoricalBytesLength; i++) {
            String b = readNextByteOrNull(atr, index);
            if (b != null) {
                mHistoricalBytes.add(b);
                index = index + 2;
            } else {
                loge("Failed to read the historical bytes when i = " + i);
                return -1;
            }
        }
        return index;
    }

    private int parseCheckBytes(String atr, int index) {
        mCheckByte = readNextByteOrNull(atr, index);
        if (mCheckByte == null) {
            if (!mOnlyTEqualsZero) {
                loge("Check byte must be present because T equals to values other than 0.");
                return -1;
            } else {
                log("Check byte can be absent because T=0.");
            }
        } else {
            index = index + 2;
        }
        return index;
    }

    private boolean parseAtrString(String atr) {
        if (atr.length() % 2 != 0) {
            loge("The length of input ATR string " + atr.length() + " is not even.");
            return false;
        }

        if (atr.length() < 4) {
            loge("Valid ATR string must at least contains TS and T0.");
            return false;
        }

        int index = parseConventionByte(atr, 0);
        if (index == -1) {
            return false;
        }

        index = parseFormatByte(atr, index);
        if (index == -1) {
            return false;
        }

        index = parseInterfaceBytes(atr, index);
        if (index == -1) {
            return false;
        }

        index = parseHistoricalBytes(atr, index);
        if (index == -1) {
            return false;
        }

        index = parseCheckBytes(atr, index);
        if (index == -1) {
            return false;
        }

        if (index != atr.length()) {
            loge("Unexpected bytes after the check byte.");
            return false;
        }
        log("Successfully parsed the ATR string " + atr + " into " + toString());
        return true;
    }

    /**
     * This class holds the interface bytes.
     */
    public static class InterfaceByte {
        public String tA;
        public String tB;
        public String tC;
        public String tD;

        public InterfaceByte() {
            tA = null;
            tB = null;
            tC = null;
            tD = null;
        }

        public InterfaceByte(String tA, String tB, String tC, String tD) {
            this.tA = tA;
            this.tB = tB;
            this.tC = tC;
            this.tD = tD;
        }

        @Override
        public boolean equals(Object o) {
            InterfaceByte ib;
            try {
                ib = (InterfaceByte) o;
            } catch (ClassCastException ex) {
                return false;
            }

            if (o == null) {
                return false;
            }
            return ((tA == null ? ib.tA == null : tA.equals(ib.tA))
                    && (tB == null ? ib.tB == null : tB.equals(ib.tB))
                    && (tC == null ? ib.tC == null : tC.equals(ib.tC))
                    && (tD == null ? ib.tD == null : tD.equals(ib.tD)));
        }
    };

    private void log(String msg) {
        Rlog.d(TAG, msg);
    }

    private void loge(String msg) {
        Rlog.e(TAG, msg);
    }

    public String getConventionByte() {
        return mIsDirectConvention ? DIRECT_CONVENTION : INVERSE_CONVENTION;
    }

    public String getFormatByte() {
        return mFormatByte;
    }

    public ArrayList<InterfaceByte> getInterfaceBytes() {
        return mInterfaceBytes;
    }

    public ArrayList<String> getHistoricalBytes() {
        return mHistoricalBytes;
    }

    public String getCheckByte() {
        return mCheckByte;
    }

    public boolean isEuiccSupported() {
        return mIsEuiccSupported;
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();

        sb.append("AnswerToReset:{");
        sb.append("mConventionByte=")
                .append(mIsDirectConvention ? DIRECT_CONVENTION : INVERSE_CONVENTION)
                .append(",");
        sb.append("mFormatByte=").append(mFormatByte).append(",");
        sb.append("mInterfaceBytes={");
        for (InterfaceByte ib : mInterfaceBytes) {
            sb.append("{");
            sb.append("TA=").append(ib.tA).append(",");
            sb.append("TB=").append(ib.tB).append(",");
            sb.append("TC=").append(ib.tC).append(",");
            sb.append("TD=").append(ib.tD);
            sb.append("}");
        }
        sb.append("},");
        sb.append("mHistoricalBytes={");
        for (String b : mHistoricalBytes) {
            sb.append((b)).append(",");
        }
        sb.append("},");
        sb.append("mCheckByte=").append(mCheckByte);
        sb.append("}");
        return sb.toString();
    }

    /**
     * Dump
     */
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("AnswerToReset:");
        pw.println(toString());
        pw.flush();
    }
}
