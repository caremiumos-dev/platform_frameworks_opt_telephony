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

package com.android.euicc.telephony;

import android.support.annotation.VisibleForTesting;
import android.support.v4.util.ArrayMap;
import android.telephony.IccOpenLogicalChannelResponse;
import android.telephony.TelephonyManager;
import com.android.euicc.asn1.Asn1Converter;
import com.android.euicc.logging.EuiccLog;

/**
 * This class manages a logical channel as a closable resource. Logical channels are very limited on
 * a modem (usually 4). You should keep a logical channel open for a short time (seconds), and use
 * try-with-resources statement with this class to make sure the logical channel is closed after
 * use. This class guarantees there is only one logical channel opened at any time. This class is
 * thread-safe, but you should send APDU commands in a deterministic order.
 */
public class UiccLogicalChannel implements AutoCloseable {
    // The maximum bytes of the data of an APDU command. If more bytes need to be sent than this
    // limit, we need to separate them into multiple commands.
    private static final int MAX_APDU_DATA_LEN = 0xF0;

    // Parameter and response used by the command to get extra responses of an APDU command.
    private static final int INS_GET_MORE_RESPONSE = 0xC0;
    private static final String SW1_MORE_RESPONSE = "61";

    // Status code of APDU response
    private static final int STATUS_NO_ERROR = 0x9000;

    // AID of ISD-R of an eUICC, defined by SGP.22.
    @VisibleForTesting static final String ISD_R_AID = "A0000005591010FFFFFFFF8900000100";

    // Parameters used by STORE DATA command sent to ISD-R, defined by SGP.22 v2.0.
    private static final int CLA_STORE_DATA = 0x80;
    private static final int INS_STORE_DATA = 0xE2;
    private static final int P1_STORE_DATA_INTERM = 0x11;
    private static final int P1_STORE_DATA_END = 0x91;

    // Parameters used by TERMINAL CAPABILITIES command sent to eUICC, defined by SGP.22 v2.0.
    private static final int TAG_TERM_CAPS = 0xA9;
    private static final int TAG_CAPS = 0x83;
    private static final int CLA_TERM_CAPS = 0x80;
    private static final int INS_TERM_CAPS = 0xAA;
    private static final int CAPS_LUI_LPD_LDS = 0x7;

    /**
     * The lock for opening and closing a logical channel. We only allow one logical channel to be
     * opened at any time.
     */
    private static final Object sLock = new Object();
    // Access to sOpenedChannel should be synchronized with sLock.
    private static final ArrayMap<String, UiccLogicalChannel> sOpenedChannels = new ArrayMap<>();

    protected final TelephonyManager mTm;
    protected int mChannel;
    private String mSelectResponse;
    private boolean mOpened;
    private String mAid;

    /**
     * Opens a logical channel on the given {@code aid}.
     *
     * @throws UiccChannelException If a logical channel cannot be opened, or the channel is opened
     *                              but returns an error status in the select response. The channel
     *                              will be closed before this exception is thrown.
     */
    public static UiccLogicalChannel open(TelephonyManager tm, String aid)
            throws UiccChannelException {
        UiccLogicalChannel ch = new UiccLogicalChannel(tm);
        ch.open(aid);
        return ch;
    }

    /**
     * Opens a logical channel on the ISD-R of eUICC. It is assumed that the Terminal Capability
     * command has been sent to the eUICC before this is called.
     *
     * @throws UiccChannelException If a logical channel cannot be opened, or the channel is opened
     *     but returns an error status in the select response. The channel will be closed before
     *     this exception is thrown.
     */
    public static UiccLogicalChannel openIsdR(TelephonyManager tm) throws UiccChannelException {
        UiccLogicalChannel ch = new UiccLogicalChannel(tm);
        ch.open(ISD_R_AID);
        return ch;
    }

    protected UiccLogicalChannel(TelephonyManager tm) {
        mTm = tm;
    }

    /**
     * @return The logical channel number.
     */
    public int getChannel() {
        return mChannel;
    }

    /**
     * @return The response of selecting the AID when the channel is opened.
     */
    public String getSelectResponse() {
        return mSelectResponse;
    }

    /**
     * Executes an APDU command without command data and returns all the responses.
     *
     * @throws UiccChannelException If an error status is returned in the response.
     */
    public String execApdu(int cla, int ins, int p1, int p2) throws UiccChannelException {
        return execApdu(cla, ins, p1, p2, "");
    }

    /**
     * Executes an APDU command and returns all the responses.
     *
     * @throws UiccChannelException If an error status is returned in the response.
     */
    public String execApdu(int cla, int ins, int p1, int p2, String cmdHex)
            throws UiccChannelException {
        return execApdu(cla | mChannel, ins, p1, p2, cmdHex.length() / 2, cmdHex);
    }

    /**
     * Executes the STORE DATA command and returns all the responses.  Long command length of which
     * is larger than {@link #MAX_APDU_DATA_LEN} will be automatically split into multiple ones.
     *
     * @throws UiccChannelException If an error status is returned in the response or any response
     *                              of a long command.
     */
    public String execStoreData(String cmd) throws UiccChannelException {
        final int cmdLen = MAX_APDU_DATA_LEN * 2;
        int startPos = 0;
        int totalLen = cmd.length() / 2;
        int totalSubCmds =
                totalLen == 0 ? 1 : (totalLen + MAX_APDU_DATA_LEN - 1) / MAX_APDU_DATA_LEN;
        for (int i = 1; i < totalSubCmds; ++i) {
            String data = cmd.substring(startPos, startPos + cmdLen);
            EuiccLog.get().v("STORE DATA: transmit %d of %d: %s", i, totalSubCmds, data);
            execApdu(
                    CLA_STORE_DATA | mChannel,
                    INS_STORE_DATA,
                    P1_STORE_DATA_INTERM,
                    i - 1,
                    MAX_APDU_DATA_LEN,
                    data);
            startPos += cmdLen;
        }
        String data = cmd.substring(startPos);
        EuiccLog.get().v("STORE DATA: transmit %1$d of %1$d: %2$s", totalSubCmds, data);
        return execApdu(
                CLA_STORE_DATA | mChannel,
                INS_STORE_DATA,
                P1_STORE_DATA_END,
                totalSubCmds - 1,
                totalLen % MAX_APDU_DATA_LEN,
                data);
    }

    @Override
    public void close() {
        if (!closeChannel()) {
            // We don't throw exception when failing to close a channel. This usually happens when
            // some eUICC implementations have closed the channel on some APDU command failures.
            EuiccLog.get().e("Cannot close logical channel: %d, already closed?", mChannel);
        }
        synchronized (sLock) {
            mOpened = false;
            if (mAid != null) {
                sOpenedChannels.remove(mAid);
            }
        }
    }

    protected boolean closeChannel() {
        return mTm.iccCloseLogicalChannel(mChannel);
    }

    @Override
    protected void finalize() throws Throwable {
        if (mOpened) {
            close();
            EuiccLog.get().wtf("UiccLogicalChannel is finalized but still opened.");
        }
        super.finalize();
    }

    /** This method should be synchronized with sLock. */
    protected void open(String aid) throws UiccChannelException {
        String selectResponse;
        synchronized (sLock) {
            // We only allow one channel to be opened to an particular AID at any time.
            if (sOpenedChannels.containsKey(aid)) {
                throw new UiccChannelException(
                        "Cannot open channel because another one has already been opened: " + aid);
            }
            selectResponse = openChannel(aid);

            mAid = aid;
            mOpened = true;
            sOpenedChannels.put(aid, this);
        }

        // Makes sure the select AID response is good, otherwise we just close the channel.
        try {
            mSelectResponse = parseResponse(selectResponse);
        } catch (UiccChannelException e) {
            close();
            throw e;
        }
    }

    /**
     * Opens a logical channel to the ICC card and returns the response for SELECT command. If it's
     * successful, mChannel will be assigned to the channel number.
     */
    protected String openChannel(String aid) throws UiccChannelException {
        IccOpenLogicalChannelResponse openChannelResponse;

        // Tries to open the channel.
        openChannelResponse = mTm.iccOpenLogicalChannel(aid);
        if (openChannelResponse == null) {
            throw new UiccChannelException("Cannot open logical channel for AID: " + aid);
        }
        mChannel = openChannelResponse.getChannel();
        int openStatus = openChannelResponse.getStatus();
        if (mChannel == IccOpenLogicalChannelResponse.INVALID_CHANNEL
                || openStatus != IccOpenLogicalChannelResponse.STATUS_NO_ERROR) {
            throw new UiccChannelException(
                    "Failed to open logical channel opened for AID: "
                            + aid
                            + ", with status: "
                            + openStatus);
        }

        return Asn1Converter.bytesToHex(openChannelResponse.getSelectResponse());
    }

    private String parseResponse(String response) throws UiccChannelException {
        // Parses the response.
        if (response == null) {
            // This should never happen.
            throw new UiccChannelException("Null response");
        }
        if (response.length() < 4) {
            throw new UiccChannelException("Response with wrong length: " + response);
        }
        int responseStatus = extractResponseStatus(response);
        if (responseStatus != STATUS_NO_ERROR) {
            throw new UiccChannelException(responseStatus);
        }
        return extractResponseData(response);
    }

    private String execApdu(int cla, int ins, int p1, int p2, int p3, String cmdHex)
            throws UiccChannelException {
        String response = transmitApdu(cla, ins, p1, p2, p3, cmdHex);
        // There can be more response data needed to be got by GET RESPONSE command.
        if (hasMoreResponse(response)) {
            response = execAllGetResponses(response);
        }
        EuiccLog.get().v("APDU response: %s", response);
        return parseResponse(response);
    }

    private String execAllGetResponses(String response) throws UiccChannelException {
        EuiccLog.get().v("Partial response: %s", response);
        StringBuilder allResponse = new StringBuilder();
        while (hasMoreResponse(response)) {
            allResponse.append(extractResponseData(response));
            response =
                    transmitApdu(
                            mChannel,
                            INS_GET_MORE_RESPONSE,
                            0,
                            0,
                            getMoreResponseLength(response),
                            "");
            EuiccLog.get().v("Partial response: %s", response);
        }
        allResponse.append(response);
        return allResponse.toString();
    }

    protected String transmitApdu(int cla, int ins, int p1, int p2, int p3, String cmdHex) {
        return mTm.iccTransmitApduLogicalChannel(mChannel, cla, ins, p1, p2, p3, cmdHex);
    }

    private boolean hasMoreResponse(String response) {
        int len = response.length();
        return len >= 4 && response.substring(len - 4, len - 2).equals(SW1_MORE_RESPONSE);
    }

    private int getMoreResponseLength(String response) throws UiccChannelException {
        int len = response.length();
        try {
            return Integer.parseInt(response.substring(len - 2), 16);
        } catch (NumberFormatException e) {
            throw new UiccChannelException("Malformed response", e);
        }
    }

    private String extractResponseData(String response) {
        return response.substring(0, response.length() - 4);
    }

    private int extractResponseStatus(String response) throws UiccChannelException {
        try {
            return Integer.parseInt(response.substring(response.length() - 4), 16);
        } catch (NumberFormatException e) {
            throw new UiccChannelException("Malformed response", e);
        }
    }
}
