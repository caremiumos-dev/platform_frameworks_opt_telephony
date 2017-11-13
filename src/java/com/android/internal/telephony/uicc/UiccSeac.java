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
 * limitations under the License.
 */
package com.android.internal.telephony.uicc;

import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.telephony.Rlog;

import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.uicc.IccUtils;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import android.util.ArrayMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@hide}
 */
public class UiccSeac extends Handler {
    private static final String LOG_TAG = "UiccSeac";
    private static final boolean DBG = true;

    private static final String AID = "A00000015141434C00";
    private static final String PKCS15_AID = "A000000063504B43532D3135";
    private static final int CLA = 0x80;
    private static final int COMMAND = 0xCA;
    private static final int P1 = 0xFF;
    private static final int P2 = 0x40;
    private static final int P1_RF_TAG = 0xDF;
    private static final int P2_RF_TAG = 0x20;
    private static final int P2_EXTENDED_DATA = 0x60;
    private static final int P3 = 0x00;
    private static final String DATA = "";

    /*
     * Rules format:
     *   ALL_REF_AR_DO = TAG_ALL_REF_AR_DO + len + [REF_AR_DO]*n
     *   REF_AR_DO = TAG_REF_AR_DO + len + REF-DO + AR-DO
     *
     *   REF_DO = TAG_REF_DO + len + AID_REF_DO + DEVICE_APP_ID_REF_DO
     *   AR_DO = TAG_AR_DO + len + PERM_AR_DO
     *
     *   DEVICE_APP_ID_REF_DO = TAG_DEVICE_APP_ID_REF_DO + len + sha1/sha256 hexstring of cert
     *   AID_REF_DO = TAG_AID_REF_DO + len + target aid
     *   PERM_AR_DO = TAG_PERM_AR_DO + len + detailed permission (8 bytes)
     *
     * Data objects hierarchy by TAG:
     * FF40
     *   E2
     *     E1
     *       4F
     *       C1
     *     E3
     *       D0
     */
    // Values from the data standard.
    private static final String TAG_ALL_REF_AR_DO = "FF40";
    private static final String TAG_REFTAG_DO = "DF20";
    private static final String TAG_REF_AR_DO = "E2";
    private static final String TAG_REF_DO = "E1";
    private static final String TAG_AID_REF_DO = "4F";
    private static final String TAG_DEVICE_APP_ID_REF_DO = "C1";
    private static final String TAG_AR_DO = "E3";
    private static final String TAG_APDU_AR_DO = "D0";

    private static final String ID_ACMF = "4200";
    private static final String ID_ACRF = "4300";
    private static final String TAG_ASN_SEQUENCE = "30";
    private static final String TAG_ASN_OCTET_STRING = "04";
    private static final String TAG_TARGET_AID = "A0";
    private static final String TAG_ALL_AID = "82";

    private static final int EVENT_OPEN_LOGICAL_CHANNEL_DONE = 1;
    private static final int EVENT_OPEN_LOGICAL_PKCS15_CHANNEL_DONE = 2;
    private static final int EVENT_TRANSMIT_LOGICAL_CHANNEL_DONE = 3;
    private static final int EVENT_TRANSMIT_LOGICAL_CHANNEL_REFRESH_TAG_DONE = 4;
    private static final int EVENT_LOAD_ACMF_DONE = 5;
    private static final int EVENT_LOAD_ACRF_DONE = 6;
    private static final int EVENT_LOAD_ACCF_DONE = 7;
    private static final int EVENT_CLOSE_LOGICAL_CHANNEL_DONE = 8;

    // Max number of retries for open logical channel, interval is 10s.
    private static final int MAX_RETRY = 1;
    private static final int RETRY_INTERVAL_MS = 10000;

    private static final byte[] ACCESS_NEVER = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
                                                 (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
    private static final byte[] ACCESS_ALWAYS = {(byte) 0x11, (byte) 0x11, (byte) 0x11, (byte) 0x11, (byte) 0x11,
                                                 (byte) 0x11, (byte) 0x11, (byte) 0x11, (byte) 0x11, (byte) 0x11};

    private static final String APDU_ALWAYS = "01";
    private static final String APDU_NEVER = "00";

    private static final int STATE_LOADING  = 0;
    private static final int STATE_LOADED   = 1;
    private static final int STATE_ERROR    = 2;

    private static final int SHA1_LEN = 40;    //20bytes
    private static final int SHA256_LEN = 64;  //32bytes
    private static final int REFRESH_TAG_LEN = 16; //8bytes
    private static final int APDU_FILTER_BASE_LEN = 16; //8bytes
    private static final int APDU_ACCESS_LEN = 2; //1bytes


    // Used for parsing the data from the UICC.
    public static class TLV {
        private static final int SINGLE_BYTE_MAX_LENGTH = 0x80;
        private String tag;
        // Length encoding is in GPC_Specification_2.2.1: 11.1.5 APDU Message and Data Length.
        // Length field could be either 1 byte if length < 128, or multiple bytes with first byte
        // specifying how many bytes are used for length, followed by length bytes.
        // Bytes for the length field, in ASCII HEX string form.
        private String lengthBytes;
        // Decoded length as integer.
        private Integer length;
        private String value;

        public TLV(String tag) {
            this.tag = tag;
        }

        public String getValue() {
            if (value == null) {
                return "";
            }
            return value;
        }

        public String parseLength(String data) {
            int offset = tag.length();
            int firstByte = Integer.parseInt(data.substring(offset, offset + 2), 16);
            if (firstByte < SINGLE_BYTE_MAX_LENGTH) {
                length = firstByte * 2;
                lengthBytes = data.substring(offset, offset + 2);
            } else {
                int numBytes = firstByte - SINGLE_BYTE_MAX_LENGTH;
                length = Integer.parseInt(data.substring(offset + 2, offset + 2 + numBytes * 2), 16) * 2;
                lengthBytes = data.substring(offset, offset + 2 + numBytes * 2);
            }
            log("TLV parseLength length=" + length + "lenghtBytes: " + lengthBytes);
            return lengthBytes;
        }

        public String parse(String data, boolean shouldConsumeAll) {
            log("Parse TLV: " + tag);
            if (!data.startsWith(tag)) {
                throw new IllegalArgumentException("Tags don't match, Invalid Rules Format");
            }
            int index = tag.length();
            if (index + 2 > data.length()) {
                throw new IllegalArgumentException("No length.");
            }

            parseLength(data);
            index += lengthBytes.length();

            log("index=" + index + " length=" + length + "data.length=" + data.length());
            int remainingLength = data.length() - (index + length);
            if (remainingLength < 0) {
                throw new IllegalArgumentException("Not enough data.");
            }
            if (shouldConsumeAll && (remainingLength != 0)) {
                throw new IllegalArgumentException("Did not consume all.");
            }
            value = data.substring(index, index + length);

            log("Got TLV: " + tag + "," + length + "," + value);

            return data.substring(index + length);
        }
    }

    // Describes a single rule.
    private static class UiccAccessRule {
        private String targetAid;
        private byte[] certificateHash;

        public UiccAccessRule(String targetAid, byte[] certificateHash) {
            this.targetAid = targetAid;
            this.certificateHash = certificateHash;
        }

        @Override
        public String toString() {
            return "aid: " + targetAid + " cert: " + IccUtils.bytesToHexString(certificateHash);
        }

        public String getAid() {
            return this.targetAid;
        }

        public byte[] getCertHash() {
            return this.certificateHash;
        }
    }

    private UiccCard mUiccCard;
    private TreeMap<String, ArrayList<byte[]>> mUiccAccessRules;
    private String mARARefTagData;
    private String mARFRefTagData;
    private String mRules;
    private int mChannelId;
    private int mRetryCount;
    private Message mCallbackMessage = null;
    private List<String> mACCFList;
    private Map<String, List<String>> mAidMap;
    private FileHandler mFh;
    private String mSelectedFile;
    private int mIndex = 0;
    private APDUCommand mCommand;
    private AtomicInteger mState;

    private final Runnable mRetryRunnable = new Runnable() {
        @Override
        public void run() {
            openChannel();
        }
    };

    private class FileHandler extends Handler {
        private Message mCallback;
        private byte[] mData;

        static protected final int EVENT_SELECT_FILE_DONE = 101;
        static protected final int EVENT_READ_BINARY_DONE = 102;

        public FileHandler() {
            log("Creating FileHandler");
        }

        public void loadFile(String fileId, Message callBack) {
            log("loadFile: " + fileId);
            mCallback = callBack;
            selectFile(fileId);
        }

        private void selectFile(String fileId) {
            log("selectFile: fileId" + fileId);
            mData = null;
            mSelectedFile = fileId;
            mCommand = new APDUCommand(0x00, 0xA4, 0x00, 0x04, 0x02, fileId);
            transmit(mCommand, obtainMessage(EVENT_SELECT_FILE_DONE));
        }

        private void readBinary() {
            log("readBinary");
            mData = null;
            mCommand = new APDUCommand(0x00, 0xB0, 0x00, 0x00, 0x00, "");
            transmit(mCommand, obtainMessage(EVENT_READ_BINARY_DONE));
        }

        private byte[] appendResponse(byte[] r1, byte[] r2, int length) {
            if (r1 == null) {
                log("appendResponse: return: " + IccUtils.bytesToHexString(r2));
                return r2;
            } else {
                byte[] rsp = new byte[r1.length + length];
                System.arraycopy(r1, 0, rsp, 0, r1.length);
                System.arraycopy(r2, 0, rsp, r1.length, length);
                log("appendResponse: return: " + IccUtils.bytesToHexString(rsp));
                return rsp;
            }
        }

        @Override
        public void handleMessage(Message msg) {
            log("handleMessage: " + msg.what);
            AsyncResult ar = (AsyncResult) msg.obj;
            IccIoResult response = (IccIoResult) ar.result;
            if (ar.exception != null || ar.result == null) {
                log("Error: " + ar.exception);
                AsyncResult.forMessage(mCallback, null, ar.exception);
                mCallback.sendToTarget();
                return;
            }

            switch (msg.what) {
                case EVENT_SELECT_FILE_DONE:
                    response = (IccIoResult) ar.result;
                    log("EVENT_SELECT_FILE_DONE: response: " + response.toString());
                    if (response.sw1 != 0x6C && response.sw1 != 0x61 && !(response.sw1 == 0x90 && response.sw2 == 0x00)) {
                        AsyncResult.forMessage(mCallback, null, new IllegalArgumentException("Not supported SW"));
                        mCallback.sendToTarget();
                        break;
                    }
                    if (response.sw1 == 0x6C) {
                        log("wrong length, re-transmit with new length: " + response.sw2);
                        mCommand.p3 = response.sw2;
                        transmit(mCommand, obtainMessage(EVENT_SELECT_FILE_DONE));
                        break;
                    } else if (response.sw1 == 0x61) {
                        log("get response: " + response.sw2);
                        mCommand = new APDUCommand(0x00, 0xC0, 0x00, 0x00, response.sw2, "");
                        transmit(mCommand, obtainMessage(EVENT_SELECT_FILE_DONE));
                        break;
                    }
                    readBinary();
                    break;

                case EVENT_READ_BINARY_DONE:
                    response = (IccIoResult) ar.result;
                    log("EVENT_READ_BINARY_DONE: response: " + response.toString());
                    if (response.sw1 != 0x6C && response.sw1 != 0x61 && !(response.sw1 == 0x90 && response.sw2 == 0x00)) {
                        AsyncResult.forMessage(mCallback, null, new IllegalArgumentException("Not supported SW"));
                        mCallback.sendToTarget();
                        break;
                    }
                    if (response.sw1 == 0x6C) {
                        log("wrong length, re-transmit with new length: " + response.sw2);
                        mCommand.p3 = response.sw2;
                        transmit(mCommand, obtainMessage(EVENT_READ_BINARY_DONE));
                    } else if (response.sw1 == 0x61) {
                        mData = appendResponse(mData, response.payload, response.payload.length);
                        log("get response: " + response.sw2);
                        mCommand = new APDUCommand(0x00, 0xC0, 0x00, 0x00, response.sw2, "");
                        transmit(mCommand, obtainMessage(EVENT_READ_BINARY_DONE));
                    } else {
                        mData = appendResponse(mData, response.payload, response.payload.length);
                        String result = mData == null ? null : IccUtils.bytesToHexString(mData)
                                .toUpperCase(Locale.US);
                        log("IccIoResult: result: " + result);

                        AsyncResult.forMessage(mCallback, result, null);
                        mCallback.sendToTarget();
                    }
                    break;

                default:
                    log("Unknown event" + msg.what);
            }
        }
    }

    private static final class APDUCommand {
        private static int cla, ins, p1, p2, p3;
        private static String data;
        private APDUCommand(int cla, int ins, int p1, int p2, int p3, String data) {
            this.cla = cla;
            this.ins = ins;
            this.p1 = p1;
            this.p2 = p2;
            this.p3 = p3;
            this.data = data;
        }

        public String toString() {
            return "Command: cla = " + cla + " ins = " + ins + " p1 = " + p1 + " p2 = " + p2 + " p3 = " + p3
                    + " data = " + data;
        }
    }

    private void openChannel() {
        log("openChannel");
        mUiccCard.iccOpenLogicalChannel(AID,
                obtainMessage(EVENT_OPEN_LOGICAL_CHANNEL_DONE, null));
    }

    private void transmit(APDUCommand cmd, Message onCompleted) {
        log("transmit: cmd: " + cmd.toString());
        mUiccCard.iccTransmitApduLogicalChannel(mChannelId, cmd.cla, cmd.ins, cmd.p1, cmd.p2, cmd.p3, cmd.data,
                onCompleted);
    }

    private void cleanUp() {
        log("cleanUp");
        if (mChannelId >= 0) {
            mUiccCard.iccCloseLogicalChannel(mChannelId, obtainMessage(
                    EVENT_CLOSE_LOGICAL_CHANNEL_DONE));
            mChannelId = -1;
        }
    }

    public UiccSeac(UiccCard uiccCard) {
        log("Creating UiccSeac");
        mUiccCard = uiccCard;
        mARARefTagData = "";
        mARFRefTagData = "";
        mACCFList = new ArrayList();
        mAidMap = new ArrayMap();
        updateAccessRules(null);
    }

    @Override
    public void handleMessage(Message msg) {
        AsyncResult ar;
        mRetryCount = 0;
        log("handleMessage: " + msg.what);

        switch (msg.what) {

            case EVENT_OPEN_LOGICAL_CHANNEL_DONE:
                log("EVENT_OPEN_LOGICAL_CHANNEL_DONE");
                ar = (AsyncResult) msg.obj;
                log("EVENT_OPEN_LOGICAL_CHANNEL_DONE: ar.exception: " + ar.exception + " ar.result: " + ar.result);
                if (ar.exception == null && ar.result != null) {
                    mChannelId = ((int[]) ar.result)[0];
                    mRetryCount = 0;
                    if (mUiccAccessRules == null  && !mARARefTagData.equals("")) {
                        log("EVENT_OPEN_LOGICAL_CHANNEL_DONE: get rules from ARA");
                        mRules = "";
                        mCommand = new APDUCommand(CLA, COMMAND, P1, P2, P3, DATA);
                        transmit(mCommand, obtainMessage(EVENT_TRANSMIT_LOGICAL_CHANNEL_DONE, Integer.valueOf(mChannelId)));
                    } else {
                        log("EVENT_OPEN_LOGICAL_CHANNEL_DONE: get rf tag");
                        mCommand = new APDUCommand(CLA, COMMAND, P1_RF_TAG, P2_RF_TAG, P3, DATA);
                        transmit(mCommand, obtainMessage(EVENT_TRANSMIT_LOGICAL_CHANNEL_REFRESH_TAG_DONE, Integer.valueOf(mChannelId)));
                    }
                } else if (ar.exception instanceof CommandException && mRetryCount < MAX_RETRY
                        && ((CommandException) (ar.exception)).getCommandError()
                                == CommandException.Error.MISSING_RESOURCE) {
                    // MISSING_RESOURCE could be due to logical channels temporarily unavailable,
                    // so we retry up to MAX_RETRY times, with an interval of RETRY_INTERVAL_MS.
                    log("EVENT_OPEN_LOGICAL_CHANNEL_DONE: missing resource, try again");
                    mRetryCount++;
                    removeCallbacks(mRetryRunnable);
                    postDelayed(mRetryRunnable, RETRY_INTERVAL_MS);
                } else {
                    // if rules cannot be read from ARA applet,
                    // fallback to PKCS15-based ARF.
                    log("No ARA, try ARF next.");
                    mUiccCard.iccOpenLogicalChannel(PKCS15_AID,
                            obtainMessage(EVENT_OPEN_LOGICAL_PKCS15_CHANNEL_DONE, null));
                }
                break;

            case EVENT_TRANSMIT_LOGICAL_CHANNEL_DONE:
                log("EVENT_TRANSMIT_LOGICAL_CHANNEL_DONE");
                ar = (AsyncResult) msg.obj;
                log("EVENT_TRANSMIT_LOGICAL_CHANNEL_DONE: ar.exception: " + ar.exception + " ar.result: " + ar.result);
                if (ar.exception == null && ar.result != null) {
                    IccIoResult response = (IccIoResult) ar.result;
                    log("EVENT_TRANSMIT_LOGICAL_CHANNEL_DONE: SW1: " + response.sw1 + " SW2: " + response.sw2
                            + " payload: " + IccUtils.bytesToHexString(response.payload));
                    if (response.sw1 == 0x90 && response.sw2 == 0x00
                            && response.payload != null && response.payload.length > 0) {
                        try {
                            StringBuilder sb = new StringBuilder();
                            sb.append(mRules);
                            sb.append(IccUtils.bytesToHexString(response.payload).toUpperCase(Locale.US));
                            mRules = sb.toString();
                            log("EVENT_TRANSMIT_LOGICAL_CHANNEL_DONE: mRules: " + mRules);
                            if (isDataComplete()) {
                                mUiccAccessRules = new TreeMap<>();
                                parseRules(mRules);
                                log("EVENT_TRANSMIT_LOGICAL_CHANNEL_DONE: get rules done: mUiccAccessRules: " + mUiccAccessRules);
                                if (mCallbackMessage != null) {
                                    AsyncResult.forMessage(mCallbackMessage, mUiccAccessRules, null);
                                    mCallbackMessage.sendToTarget();
                                }
                                mState.set(STATE_LOADED);
                            } else {
                                log("EVENT_TRANSMIT_LOGICAL_CHANNEL_DONE: get next");
                                mCommand = new APDUCommand(CLA, COMMAND, P1, P2_EXTENDED_DATA, P3, DATA);
                                transmit(mCommand, obtainMessage(EVENT_TRANSMIT_LOGICAL_CHANNEL_DONE, Integer.valueOf(mChannelId)));
                                break;
                            }
                        } catch (IllegalArgumentException ex) {
                            mUiccAccessRules = null;
                            if (mCallbackMessage != null) {
                                AsyncResult.forMessage(mCallbackMessage, null, ex);
                                mCallbackMessage.sendToTarget();
                            }
                            mState.set(STATE_ERROR);
                        }
                    } else if (response.sw1 == 0x6C) {
                        log("wrong length, re-transmit with new length: " + response.sw2);
                        mCommand.p3 = response.sw2;
                        transmit(mCommand, obtainMessage(EVENT_TRANSMIT_LOGICAL_CHANNEL_DONE, Integer.valueOf(mChannelId)));
                        break;
                    } else if (response.sw1 == 0x61) {
                        if (response.payload != null && response.payload.length > 0) {
                            StringBuilder sb = new StringBuilder();
                            sb.append(mRules);
                            sb.append(IccUtils.bytesToHexString(response.payload).toUpperCase(Locale.US));
                            mRules = sb.toString();
                            log("EVENT_TRANSMIT_LOGICAL_CHANNEL_DONE: mRules: " + mRules);
                        }
                        log("get response: " + response.sw2);
                        mCommand = new APDUCommand(CLA, 0xC0, 0x00, 0x00, response.sw2, "");
                        transmit(mCommand, obtainMessage(EVENT_TRANSMIT_LOGICAL_CHANNEL_DONE, Integer.valueOf(mChannelId)));
                        break;
                    } else {
                        if (mCallbackMessage != null) {
                            AsyncResult.forMessage(mCallbackMessage, null, new IllegalArgumentException("Not supported SW"));
                            mCallbackMessage.sendToTarget();
                        }
                        mState.set(STATE_ERROR);
                    }
                } else {
                    if (mCallbackMessage != null) {
                        AsyncResult.forMessage(mCallbackMessage, null, new IllegalStateException("Can not transmit to uicc"));
                        mCallbackMessage.sendToTarget();
                    }
                    mState.set(STATE_ERROR);
                }

                cleanUp();
                break;

            case EVENT_TRANSMIT_LOGICAL_CHANNEL_REFRESH_TAG_DONE:
                log("EVENT_TRANSMIT_LOGICAL_CHANNEL_REFRESH_TAG_DONE");
                ar = (AsyncResult) msg.obj;
                log("EVENT_TRANSMIT_LOGICAL_CHANNEL_REFRESH_TAG_DONE: ar.exception: " + ar.exception + " ar.result: " + ar.result);
                String data = null;
                if (ar.exception == null && ar.result != null) {
                    IccIoResult response = (IccIoResult) ar.result;
                    log("EVENT_TRANSMIT_LOGICAL_CHANNEL_REFRESH_TAG_DONE: SW1: " + response.sw1 + " SW2: " + response.sw2
                            + " payload: " + IccUtils.bytesToHexString(response.payload));
                    if (response.sw1 == 0x90 && response.sw2 == 0x00
                            && response.payload != null && response.payload.length > 0) {
                        data = parseRefTag(IccUtils.bytesToHexString(response.payload).toUpperCase(Locale.US));
                        log("EVENT_TRANSMIT_LOGICAL_CHANNEL_REFRESH_TAG_DONE: refresh tag data: " + data);
                        if (data.length() != REFRESH_TAG_LEN) {
                            log("EVENT_TRANSMIT_LOGICAL_CHANNEL_REFRESH_TAG_DONE: invalid refresh tag data!!!");
                            if (mCallbackMessage != null) {
                                AsyncResult.forMessage(mCallbackMessage, null, new IllegalArgumentException("RefreshTag length not valid"));
                                mCallbackMessage.sendToTarget();
                            }
                            mState.set(STATE_ERROR);
                        } else {
                            if (!mARARefTagData.equals(data)) {
                                mARARefTagData = data;
                                log("EVENT_TRANSMIT_LOGICAL_CHANNEL_REFRESH_TAG_DONE: refresh tag changed, update access rules");
                                mUiccAccessRules = null;
                                mCommand = new APDUCommand(CLA, COMMAND, P1, P2, P3, DATA);
                                mRules = "";
                                transmit(mCommand, obtainMessage(EVENT_TRANSMIT_LOGICAL_CHANNEL_DONE, Integer.valueOf(mChannelId)));
                                break;
                            } else {
                                log("EVENT_TRANSMIT_LOGICAL_CHANNEL_REFRESH_TAG_DONE: return current access rules");
                                if (mCallbackMessage != null) {
                                    AsyncResult.forMessage(mCallbackMessage, mUiccAccessRules, null);
                                    mCallbackMessage.sendToTarget();
                                }
                                mState.set(STATE_LOADED);
                            }
                        }
                    } else if (response.sw1 == 0x6C) {
                        log("wrong length, re-transmit with new length: " + response.sw2);
                        mCommand.p3 = response.sw2;
                        transmit(mCommand, obtainMessage(EVENT_TRANSMIT_LOGICAL_CHANNEL_REFRESH_TAG_DONE, Integer.valueOf(mChannelId)));
                        break;
                    } else if (response.sw1 == 0x61) {
                        log("get response: " + response.sw2);
                        mCommand = new APDUCommand(CLA, 0xC0, 0x00, 0x00, response.sw2, "");
                        transmit(mCommand, obtainMessage(EVENT_TRANSMIT_LOGICAL_CHANNEL_REFRESH_TAG_DONE, Integer.valueOf(mChannelId)));
                        break;
                    } else {
                        if (mCallbackMessage != null) {
                            AsyncResult.forMessage(mCallbackMessage, null, new IllegalArgumentException("Not supported SW"));
                            mCallbackMessage.sendToTarget();
                        }
                        mState.set(STATE_ERROR);
                    }
                } else {
                    if (mCallbackMessage != null) {
                        AsyncResult.forMessage(mCallbackMessage, null, new IllegalStateException("Can not transmit to uicc"));
                        mCallbackMessage.sendToTarget();
                    }
                    mState.set(STATE_ERROR);
                }
                cleanUp();
                break;

            case EVENT_OPEN_LOGICAL_PKCS15_CHANNEL_DONE:
                log("EVENT_OPEN_LOGICAL_PKCS15_CHANNEL_DONE");
                ar = (AsyncResult) msg.obj;
                log("EVENT_OPEN_LOGICAL_PKCS15_CHANNEL_DONE: ar.exception: " + ar.exception + " ar.result: " + ar.result);
                if (ar.exception == null && ar.result != null) {
                    mChannelId = ((int[]) ar.result)[0];
                    log("mChannelId: " + mChannelId);
                    try {
                        if (mUiccAccessRules == null && (!"".equals(mARFRefTagData))) {
                            log("EVENT_OPEN_LOGICAL_PKCS15_CHANNEL_DONE: load ACRF");
                            mFh = new FileHandler();
                            mFh.loadFile(ID_ACRF, obtainMessage(EVENT_LOAD_ACRF_DONE));
                        } else {
                            log("EVENT_OPEN_LOGICAL_PKCS15_CHANNEL_DONE: load ACMF");
                            mFh = new FileHandler();
                            mFh.loadFile(ID_ACMF, obtainMessage(EVENT_LOAD_ACMF_DONE));
                        }
                    } catch (IllegalArgumentException ex) {
                        log("EVENT_OPEN_LOGICAL_PKCS15_CHANNEL_DONE: ex: " + ex);
                        if (mCallbackMessage != null) {
                            AsyncResult.forMessage(mCallbackMessage, null, ex);
                            mCallbackMessage.sendToTarget();
                        }
                    }
                } else {
                    log("error: " + ar.exception);
                    if (mCallbackMessage != null) {
                        AsyncResult.forMessage(mCallbackMessage, null, new IllegalStateException("Can not open Pkcs15 channel"));
                        mCallbackMessage.sendToTarget();
                    }
                    mState.set(STATE_ERROR);
                }
                break;

            case EVENT_LOAD_ACMF_DONE:
                log("EVENT_LOAD_ACMF_DONE");
                ar = (AsyncResult) msg.obj;

                log("EVENT_LOAD_ACMF_DONE: ar.exception: " + ar.exception + " ar.result: " + ar.result);
                if (ar.exception == null && ar.result != null) {
                    try {
                        String reftagdata = parseAcmf((String) ar.result);
                        log("EVENT_LOAD_ACMF_DONE: reftagdata: " + reftagdata + " mARFRefTagData: " + mARFRefTagData);
                        if (reftagdata.length() != REFRESH_TAG_LEN) {
                            log("EVENT_LOAD_ACMF_DONE: invalid refresh tag data!!!");
                            if (mCallbackMessage != null) {
                                AsyncResult.forMessage(mCallbackMessage, null, new IllegalArgumentException("RefreshTag length not valid"));
                                mCallbackMessage.sendToTarget();
                            }
                        } else {
                            if (mARFRefTagData.equals(reftagdata)) {
                                log("return current mUiccAccessRules: " + mUiccAccessRules);
                                if (mCallbackMessage != null) {
                                    AsyncResult.forMessage(mCallbackMessage, mUiccAccessRules, null);
                                    mCallbackMessage.sendToTarget();
                                }
                            } else {
                                mUiccAccessRules = null;
                                mARFRefTagData = reftagdata;
                                mFh = new FileHandler();
                                mFh.loadFile(ID_ACRF, obtainMessage(EVENT_LOAD_ACRF_DONE));
                                break;
                            }
                        }
                    } catch (IllegalArgumentException ex) {
                        log("EVENT_LOAD_ACMF_DONE: ex: " + ex);
                        if (mCallbackMessage != null) {
                            AsyncResult.forMessage(mCallbackMessage, null, ex);
                            mCallbackMessage.sendToTarget();
                        }
                    }
                } else {
                    if (mCallbackMessage != null) {
                        AsyncResult.forMessage(mCallbackMessage, null, new IllegalStateException("Can not load the Access Control Main File"));
                        mCallbackMessage.sendToTarget();
                    }
                }
                cleanUp();
                mState.set(STATE_ERROR);
                break;

            case EVENT_LOAD_ACRF_DONE:
                log("EVENT_LOAD_ACRF_DONE");
                ar = (AsyncResult) msg.obj;

                log("EVENT_LOAD_ACRF_DONE: ar.exception: " + ar.exception + " ar.result: " + ar.result);
                if (ar.exception == null && ar.result != null) {
                    mAidMap.clear();
                    try {
                        List<String> idAccf = parseAcrf((String) ar.result);
                        log("idAccf: " + idAccf);
                        mACCFList = idAccf;
                        if (!idAccf.isEmpty()) {
                            mFh.loadFile(idAccf.get(0), obtainMessage(EVENT_LOAD_ACCF_DONE));
                            break;
                        } else {
                            if (mCallbackMessage != null) {
                                AsyncResult.forMessage(mCallbackMessage, new TreeMap<>(), null);
                                mCallbackMessage.sendToTarget();
                            }
                        }
                    } catch (IllegalArgumentException ex) {
                        log("EVENT_LOAD_ACRF_DONE: Exception: " + ex);
                        if (mCallbackMessage != null) {
                            AsyncResult.forMessage(mCallbackMessage, null, ex);
                            mCallbackMessage.sendToTarget();
                        }
                    }
                } else {
                    if (mCallbackMessage != null) {
                        AsyncResult.forMessage(mCallbackMessage, null, new IllegalStateException("Can not load the Access Control Rules File"));
                        mCallbackMessage.sendToTarget();
                    }
                }
                cleanUp();
                mState.set(STATE_ERROR);
                break;

            case EVENT_LOAD_ACCF_DONE:
                log("EVENT_LOAD_ACCF_DONE");
                ar = (AsyncResult) msg.obj;

                log("EVENT_LOAD_ACCF_DONE: ar.exception: " + ar.exception + " ar.result: " + ar.result);
                log("EVENT_LOAD_ACCF_DONE: mACCFList: " + mACCFList);
                if (ar.exception == null && ar.result != null) {
                    try {
                        parseAccf((String) ar.result);
                        log("EVENT_LOAD_ACCF_DONE: mSelectedFile: " + mSelectedFile + " the last file of accf list: " + mACCFList.get(mACCFList.size() - 1));
                        if (mIndex < (mACCFList.size() - 1)) {
                            mIndex++;
                            log("EVENT_LOAD_ACCF_DONE: there are more rules, get next rule, index: " + mIndex);
                            mFh.loadFile(mACCFList.get(mIndex), obtainMessage(EVENT_LOAD_ACCF_DONE));
                            break;
                        } else {
                            mIndex = 0;
                            log("EVENT_LOAD_ACCF_DONE: get done: " + mUiccAccessRules);
                            if (mCallbackMessage != null) {
                                AsyncResult.forMessage(mCallbackMessage, mUiccAccessRules, null);
                                mCallbackMessage.sendToTarget();
                            }
                            mState.set(STATE_LOADED);
                        }
                    } catch (IllegalArgumentException ex) {
                        log("EVENT_LOAD_ACCF_DONE: Exception: " + ex);
                        mUiccAccessRules = null;
                        if (mCallbackMessage != null) {
                            AsyncResult.forMessage(mCallbackMessage, null, ex);
                            mCallbackMessage.sendToTarget();
                        }
                        mState.set(STATE_ERROR);
                    }
                } else {
                    if (mCallbackMessage != null) {
                        AsyncResult.forMessage(mCallbackMessage, null, new IllegalStateException("Can not load the Access Control Conditions File"));
                        mCallbackMessage.sendToTarget();
                    }
                    mState.set(STATE_ERROR);
                }
                cleanUp();
                break;

            case EVENT_CLOSE_LOGICAL_CHANNEL_DONE:
                log("EVENT_CLOSE_LOGICAL_CHANNEL_DONE");
                break;

            default:
                Rlog.e(LOG_TAG, "Unknown event " + msg.what);
        }
    }

    /*
     * Check if all rule bytes have been read from UICC.
     * For long payload, we need to fetch it repeatly before start parsing it.
     */
    private boolean isDataComplete() {
        log("isDataComplete mRules:" + mRules);
        if (mRules.startsWith(TAG_ALL_REF_AR_DO)) {
            TLV allRules = new TLV(TAG_ALL_REF_AR_DO);
            String lengthBytes = allRules.parseLength(mRules);
            log("isDataComplete lengthBytes: " + lengthBytes);
            if (mRules.length() == TAG_ALL_REF_AR_DO.length() + lengthBytes.length()
                    + allRules.length) {
                log("isDataComplete yes");
                return true;
            } else {
                log("isDataComplete no");
                return false;
            }
        } else {
            throw new IllegalArgumentException("Tags don't match.");
        }
    }

    /*
     * Parses the rules from the input string.
     */
    private void parseRules(String rules) {
        log("parseRules : Got rules: " + rules);

        TLV allRefArDo = new TLV(TAG_ALL_REF_AR_DO); //FF40
        allRefArDo.parse(rules, true);

        String arDos = allRefArDo.value;
        while (!arDos.isEmpty()) {
            TLV refArDo = new TLV(TAG_REF_AR_DO); //E2
            arDos = refArDo.parse(arDos, false);
            UiccAccessRule accessRule = parseRefArdo(refArDo.value);
            //Skip Unhandled Rules
            if (accessRule != null) {
                log("parseRules : accessRule: " + accessRule.toString());
                ArrayList<byte[]> certlist = new ArrayList<>();
                String aid = accessRule.getAid();
                if (mUiccAccessRules.containsKey(aid)) {
                    log("parseRules : there are more rules for aid: " + aid);
                    certlist = mUiccAccessRules.get(aid);
                }
                byte[] cert = accessRule.getCertHash();
                if (Arrays.equals(ACCESS_NEVER, cert)) {
                    certlist.clear();
                    certlist.add(ACCESS_NEVER);
                }
                if (!isDuplicatedValue(certlist, ACCESS_NEVER)) {
                    // Skip duplicated cert
                    if (!isDuplicatedValue(certlist, cert)) {
                        certlist.add(cert);
                    }
                    if (certlist.size() > 1 && isDuplicatedValue(certlist, ACCESS_ALWAYS)) {
                        certlist.remove(ACCESS_ALWAYS);
                    }
                }
                mUiccAccessRules.put(aid, certlist);
            }
        }
    }

    /*
     * Parses a single rule.
     */
    private UiccAccessRule parseRefArdo(String rule) {
        log("parseRefArdo: Got rule: " + rule);
        if (rule.isEmpty()) {
            return null;
        }
        String aid = "";
        String certificateHash = "";
        boolean apduAllowed = false;
        boolean isAPDUARDOPresent = false;

        // Parse Tag E1. If data does not start with tag E1, throw IllegalArgumentException.
        log("parseRefArdo: read tag E1");
        TLV refDo = new TLV(TAG_REF_DO); //E1
        rule = refDo.parse(rule, false);
        log("parseRefArdo: remaining rule: " + rule);
        TLV aidDo = new TLV(TAG_AID_REF_DO); //4F
        String tmp = aidDo.parse(refDo.value, false);
        log("parseRefArdo: tmp: " + tmp);
        aid = aidDo.value;
        log("parseRefArdo: aid: " + aid);
        if (aid.isEmpty()) {
            return null;
        }

        TLV deviceDo = new TLV(TAG_DEVICE_APP_ID_REF_DO); //C1
        deviceDo.parse(tmp, true);
        certificateHash = deviceDo.value;
        log("parseRefArdo: certificateHash: " + certificateHash);
        if (!"".equals(certificateHash)) {
            // Check certificate length
            if (certificateHash.length() != SHA1_LEN && certificateHash.length() != SHA256_LEN) {
                throw new IllegalArgumentException("Unknown Certificate Type");
            }
        }
        
        // Parse tag E3, if data does not start with tag E3, throw IllegalArgumentException
        log("parseRefArdo: read tag E3");
        TLV arDo = new TLV(TAG_AR_DO); //E3
        arDo.parse(rule, false);
        String apdurule = "";
        if (arDo.value.startsWith(TAG_APDU_AR_DO)) {
            TLV apduDo = new TLV(TAG_APDU_AR_DO); //D0
            apduDo.parse(arDo.value, true);
            apdurule = apduDo.value;
            log("parseRefArdo: apdurule: " + apdurule);
        }
        if (!apdurule.isEmpty()) {
            isAPDUARDOPresent = true;
            // D0 data length: 1 or n*8
            if (apdurule.length() == APDU_ACCESS_LEN) {
                if (APDU_NEVER.equals(apdurule)) {
                    apduAllowed = false;
                } else if (APDU_ALWAYS.equals(apdurule)) {
                    apduAllowed = true;
                } else {
                    throw new IllegalArgumentException("Invalid Rule Type");
                }
            } else {
                if (apdurule.length() % APDU_FILTER_BASE_LEN != 0) {
                    throw new IllegalArgumentException("Invalid Rule Type");
                } else {
                    apduAllowed = true;
                }
            }
        // Tag D0 is empty or not present
        } else {
            isAPDUARDOPresent = false;;
        }
        // Tag D0 not present
        log("parseRefArdo: APDU_AR_DO present: " + isAPDUARDOPresent + " apduAllowed: " + apduAllowed);

        if (!isAPDUARDOPresent) {
            if (certificateHash.isEmpty()) {
                return null;
            } else {
                return new UiccAccessRule(aid, IccUtils.hexStringToBytes(certificateHash));
            }
        } else {
            byte[] cert = apduAllowed ? ACCESS_ALWAYS : ACCESS_NEVER;
            return new UiccAccessRule(aid, cert);
        }
    }

    /*
     * Parses Refresh tag
     */
    private String parseRefTag(String data) {
        log("parseRefTag");
        TLV reftagDo = new TLV(TAG_REFTAG_DO);
        reftagDo.parse(data, true);
        return reftagDo.value;
    }

    /*
     * Parses a single rule.
     */
    private void parseAccf(String data) {
        log("parseAccf");
        if (mUiccAccessRules == null) {
            log("parseAccf: Create mUiccAccessRules");
            mUiccAccessRules = new TreeMap<>();
        }
        // Access denied for all apps.
        if (data == null || data.equals("")) {
            log("parseAccf: data is not supported");
            List<String> aidlist = (List<String>) mAidMap.get(mSelectedFile);
            for (String aid : aidlist) {
                log("parseAccf: empty file, aid: " + aid);
                ArrayList<byte[]> certlist = new ArrayList<>();
                certlist.add(ACCESS_NEVER);
                mUiccAccessRules.put(aid, certlist);
            }
        } else {
            // Access allowed for all apps.
            if (data.equals("3000")) {
                List<String> aidlist = (List<String>) mAidMap.get(mSelectedFile);
                for (String aid : aidlist) {
                    log("parseAccf: access allowed for all apps, aid: " + aid);
                    ArrayList<byte[]> certlist = new ArrayList<>();
                    if (!mUiccAccessRules.containsKey(aid)) {
                        certlist.add(ACCESS_ALWAYS);
                        mUiccAccessRules.put(aid, certlist);
                    }
                }
            } else {
                String acCondition = data;
                log("parseAccf: start decode: acCondition: " + acCondition);
                while (!acCondition.isEmpty()) {
                    TLV tlvCondition = new TLV(TAG_ASN_SEQUENCE);
                    TLV tlvCert = new TLV(TAG_ASN_OCTET_STRING);
                    acCondition = tlvCondition.parse(acCondition, false);
                    tlvCert.parse(tlvCondition.getValue(), true);
                    if (!tlvCert.getValue().isEmpty()) {
                        String cert = tlvCert.getValue();
                        log("parseAccf: cert: " + cert);
                        // Check certificate length
                        if (cert.length() != SHA1_LEN && cert.length() != SHA256_LEN) {
                            throw new IllegalArgumentException("Unknown Certificate Type");
                        }
                        List<String> aidlist = (List<String>) mAidMap.get(mSelectedFile);
                        for (String aid : aidlist) {
                            log("parseAccf: aid: " + aid);
                            ArrayList<byte[]> certlist = new ArrayList<>();
                            if (mUiccAccessRules.containsKey(aid)) {
                                certlist = mUiccAccessRules.get(aid);
                            }
                            // If AID's access rule is "NEVER", don't update the rule.
                            if (!certlist.contains(ACCESS_NEVER)) {
                                if (certlist.contains(ACCESS_ALWAYS)) {
                                    certlist.clear();
                                }
                                // Skip duplicated cert
                                if (!isDuplicatedValue(certlist, IccUtils.hexStringToBytes(cert))) {
                                    certlist.add(IccUtils.hexStringToBytes(cert));
                                }
                                mUiccAccessRules.put(aid, certlist);
                            }
                        }
                    }
                }
            }
        }
    }

    // parse ACRF file to get file id for ACCF file
    // data is hex string, return file id if parse success, null otherwise
    private List<String> parseAcrf(String data) {
        log("parseAcrf");
        List<String> ret = new ArrayList();

        String acRules = data;
        log("parseAcrf: start decode: acRules: " + acRules);
        while (!acRules.isEmpty()) {
            TLV tlvRule = new TLV(TAG_ASN_SEQUENCE);
            acRules = tlvRule.parse(acRules, false);
            String ruleString = tlvRule.getValue();
            log("parseAcrf: ruleString: " + ruleString);
            if (ruleString.startsWith(TAG_TARGET_AID)) {
                // [A0] length [04] length AID [30] 04 [04] 02 43 XX
                TLV tlvTarget = new TLV(TAG_TARGET_AID); // A0
                TLV tlvAid = new TLV(TAG_ASN_OCTET_STRING); // 04
                TLV tlvAsnPath = new TLV(TAG_ASN_SEQUENCE); // 30
                TLV tlvPath = new TLV(TAG_ASN_OCTET_STRING);  // 04

                ruleString = tlvTarget.parse(ruleString, false);
                tlvAid.parse(tlvTarget.getValue(), true);
                String aid = tlvAid.getValue();
                log("parseAcrf: aid: " + aid);

                tlvAsnPath.parse(ruleString, true);
                tlvPath.parse(tlvAsnPath.getValue(), true);
                String filepath = tlvPath.getValue();
                log("parseAcrf: filepath: " + filepath);
                if (!ret.contains(filepath)) {
                    ret.add(filepath);
                }
                List<String> aidlist = new ArrayList();
                if (mAidMap.containsKey(filepath)) {
                    aidlist = mAidMap.get(filepath);
                }
                aidlist.add(aid);
                mAidMap.put(filepath, aidlist);
            } else {
                if (!ruleString.startsWith(TAG_ALL_AID)) {
                    throw new IllegalArgumentException("Invalid Rule Type");
                }
                // Skip read tag 82
            }
            log("parseAcrf: while loop: acRules: " + acRules);
        }
        log("parseAcrf, mAidMap: " + mAidMap);
        log("parseAcrf, return list: " + ret);
        return ret;
    }

    // parse ACMF file to get refresh tag's data
    // data is hex string, return refresh tag's data if parse success, null otherwise
    private String parseAcmf(String data) {
        log("parseAcmf");
        String ret = null;
        String reftag = data;
        log("parseAcmf: start decode: reftag: " + reftag);
        while (!reftag.isEmpty()) {
            //30 10 04 08 01 02 03 04 05 06 07 08 30 04 04 02 43 00
            TLV tlvRule = new TLV(TAG_ASN_SEQUENCE); //30
            reftag = tlvRule.parse(reftag, false);
            TLV tlvRef = new TLV(TAG_ASN_OCTET_STRING); // 04
            tlvRef.parse(tlvRule.getValue(), false);
            ret = tlvRef.getValue();
        }
        log("parseAcmf: return: " + ret);
        return ret;
    }

    private static void log(String msg) {
        if (DBG) Rlog.d(LOG_TAG, msg);
    }

    private boolean isDuplicatedValue(ArrayList<byte[]> list, byte[] cert) {
        if (list == null || list.isEmpty()) {
            return false;
        }
        for (int i = 0; i < list.size(); i++) {
            if (Arrays.equals(list.get(i), cert)) {
                return true;
            }
        }
        return false;
    }


    public void updateAccessRules(Message onComplete) {
        log("updateAccessRules - IN");
        if (!mUiccCard.areCarrierPriviligeRulesLoaded() || (mState != null && mState.get() == STATE_LOADING)) {
            log("updateAccessRules: State Loading, return null");
            if (onComplete != null) {
                AsyncResult.forMessage(onComplete, null, new IllegalStateException("LOADING"));
                onComplete.sendToTarget();
            }
        } else {
            mCallbackMessage = onComplete;
            mState = new AtomicInteger(STATE_LOADING);
            openChannel();
        }
    }

}
