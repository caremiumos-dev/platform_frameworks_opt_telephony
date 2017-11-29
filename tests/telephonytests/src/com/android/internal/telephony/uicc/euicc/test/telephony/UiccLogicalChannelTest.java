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

package com.android.internal.telephony.uicc.euicc.telephony;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.telephony.IccOpenLogicalChannelResponse;
import android.telephony.TelephonyManager;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class UiccLogicalChannelTest {
    @Mock private TelephonyManager mMockTm;

    @Test
    public void testOpen() throws UiccChannelException {
        int channel =
                UiccChannelMocker.mockOpenLogicalChannelResponse(
                        mMockTm, IccOpenLogicalChannelResponse.STATUS_NO_ERROR, "A1A1A19000");
        UiccChannelMocker.mockCloseLogicalChannel(mMockTm, channel);

        String aid = "B2C3D4";
        try (UiccLogicalChannel ch = UiccLogicalChannel.open(mMockTm, aid)) {
            assertEquals(channel, ch.getChannel());
            assertEquals("A1A1A1", ch.getSelectResponse());
        }

        verify(mMockTm).iccOpenLogicalChannel(aid);
        verify(mMockTm).iccCloseLogicalChannel(channel);
    }

    @Test(expected = UiccChannelException.class)
    public void testOpen_OpenChannelErrorStatus() throws UiccChannelException {
        UiccChannelMocker.mockOpenLogicalChannelResponse(
                mMockTm, IccOpenLogicalChannelResponse.STATUS_NO_SUCH_ELEMENT, "");

        String aid = "B2C3D4";
        try (UiccLogicalChannel ch = UiccLogicalChannel.open(mMockTm, aid)) {
            // Do nothing.
        } catch (UiccChannelException e) {
            assertEquals(0, e.getApduStatus());

            verify(mMockTm).iccOpenLogicalChannel(aid);
            throw e;
        }
    }

    @Test(expected = UiccChannelException.class)
    public void testOpen_OpenChannelBadResponse() throws UiccChannelException {
        int channel =
                UiccChannelMocker.mockOpenLogicalChannelResponse(
                        mMockTm, IccOpenLogicalChannelResponse.STATUS_NO_ERROR, "6985");
        UiccChannelMocker.mockCloseLogicalChannel(mMockTm, channel);

        String aid = "B2C3D4";
        try (UiccLogicalChannel ch = UiccLogicalChannel.open(mMockTm, aid)) {
            // Do nothing.
        } catch (UiccChannelException e) {
            assertEquals("6985", e.getStatusHex());

            verify(mMockTm).iccOpenLogicalChannel(aid);
            verify(mMockTm).iccCloseLogicalChannel(channel);
            throw e;
        }
    }

    @Test
    public void testOpenIsdR() throws UiccChannelException {
        int channel =
                UiccChannelMocker.mockOpenLogicalChannelResponse(
                        mMockTm, IccOpenLogicalChannelResponse.STATUS_NO_ERROR, "A1A1A19000");
        UiccChannelMocker.mockCloseLogicalChannel(mMockTm, channel);

        try (UiccLogicalChannel ch = UiccLogicalChannel.openIsdR(mMockTm)) {
            assertEquals(channel, ch.getChannel());
            assertEquals("A1A1A1", ch.getSelectResponse());
        }

        verify(mMockTm).iccOpenLogicalChannel(UiccLogicalChannel.ISD_R_AID);
        verify(mMockTm).iccCloseLogicalChannel(channel);
    }

    @Test
    public void testExecApdu() throws UiccChannelException {
        int channel =
                UiccChannelMocker.mockOpenLogicalChannelResponse(
                        mMockTm, IccOpenLogicalChannelResponse.STATUS_NO_ERROR, "9000");
        UiccChannelMocker.mockSendToLogicalChannel(mMockTm, channel, "A1A1A19000");
        UiccChannelMocker.mockCloseLogicalChannel(mMockTm, channel);

        try (UiccLogicalChannel ch = UiccLogicalChannel.openIsdR(mMockTm)) {
            assertEquals("A1A1A1", ch.execApdu(11, 22, 33, 44));
        }

        verify(mMockTm).iccOpenLogicalChannel(UiccLogicalChannel.ISD_R_AID);
        verify(mMockTm).iccTransmitApduLogicalChannel(channel, 11, 22, 33, 44, 0, "");
        verify(mMockTm).iccCloseLogicalChannel(channel);
    }

    @Test
    public void testExecApdu_WithCmd() throws UiccChannelException {
        int channel =
                UiccChannelMocker.mockOpenLogicalChannelResponse(
                        mMockTm, IccOpenLogicalChannelResponse.STATUS_NO_ERROR, "9000");
        UiccChannelMocker.mockSendToLogicalChannel(mMockTm, channel, "A1A1A19000");
        UiccChannelMocker.mockCloseLogicalChannel(mMockTm, channel);

        try (UiccLogicalChannel ch = UiccLogicalChannel.openIsdR(mMockTm)) {
            assertEquals("A1A1A1", ch.execApdu(11, 22, 33, 44, "A1B2"));
        }

        verify(mMockTm).iccOpenLogicalChannel(UiccLogicalChannel.ISD_R_AID);
        verify(mMockTm).iccTransmitApduLogicalChannel(channel, 11, 22, 33, 44, 2, "A1B2");
        verify(mMockTm).iccCloseLogicalChannel(channel);
    }

    @Test
    public void testExecApdu_LongResponse() throws UiccChannelException {
        int channel =
                UiccChannelMocker.mockOpenLogicalChannelResponse(
                        mMockTm, IccOpenLogicalChannelResponse.STATUS_NO_ERROR, "9000");
        UiccChannelMocker.mockSendToLogicalChannel(
                mMockTm, channel, "A1A1A16104", "B2B2B2B26102", "C3C39000");
        UiccChannelMocker.mockCloseLogicalChannel(mMockTm, channel);

        try (UiccLogicalChannel ch = UiccLogicalChannel.openIsdR(mMockTm)) {
            assertEquals("A1A1A1B2B2B2B2C3C3", ch.execApdu(11, 22, 33, 44, "A1B2"));
        }

        verify(mMockTm).iccOpenLogicalChannel(UiccLogicalChannel.ISD_R_AID);
        verify(mMockTm).iccTransmitApduLogicalChannel(channel, 11, 22, 33, 44, 2, "A1B2");
        verify(mMockTm).iccTransmitApduLogicalChannel(channel, channel, 0xC0, 0, 0, 4, "");
        verify(mMockTm).iccTransmitApduLogicalChannel(channel, channel, 0xC0, 0, 0, 2, "");
        verify(mMockTm).iccCloseLogicalChannel(channel);
    }

    @Test(expected = UiccChannelException.class)
    public void testExecApdu_OpenChannelErrorStatus() throws UiccChannelException {
        UiccChannelMocker.mockOpenLogicalChannelResponse(
                mMockTm, IccOpenLogicalChannelResponse.STATUS_NO_SUCH_ELEMENT, "");

        try (UiccLogicalChannel ch = UiccLogicalChannel.openIsdR(mMockTm)) {
            ch.execApdu(11, 22, 33, 44, "A1B2");
        } catch (UiccChannelException e) {
            assertEquals(0, e.getApduStatus());

            verify(mMockTm).iccOpenLogicalChannel(UiccLogicalChannel.ISD_R_AID);
            throw e;
        }
    }

    @Test(expected = UiccChannelException.class)
    public void testExecApdu_OpenChannelBadResponse() throws UiccChannelException {
        int channel =
                UiccChannelMocker.mockOpenLogicalChannelResponse(
                        mMockTm, IccOpenLogicalChannelResponse.STATUS_NO_ERROR, "6985");
        UiccChannelMocker.mockCloseLogicalChannel(mMockTm, channel);

        try (UiccLogicalChannel ch = UiccLogicalChannel.openIsdR(mMockTm)) {
            ch.execApdu(11, 22, 33, 44, "A1B2");
        } catch (UiccChannelException e) {
            assertEquals("6985", e.getStatusHex());

            verify(mMockTm).iccOpenLogicalChannel(UiccLogicalChannel.ISD_R_AID);
            verify(mMockTm).iccCloseLogicalChannel(channel);
            throw e;
        }
    }

    @Test
    public void testExecStoreData() throws UiccChannelException {
        int channel =
                UiccChannelMocker.mockOpenLogicalChannelResponse(
                        mMockTm, IccOpenLogicalChannelResponse.STATUS_NO_ERROR, "9000");
        UiccChannelMocker.mockSendToLogicalChannel(mMockTm, channel, "A1A1A19000");
        UiccChannelMocker.mockCloseLogicalChannel(mMockTm, channel);

        try (UiccLogicalChannel ch = UiccLogicalChannel.openIsdR(mMockTm)) {
            assertEquals("A1A1A1", ch.execStoreData("A1B2"));
        }

        verify(mMockTm).iccOpenLogicalChannel(UiccLogicalChannel.ISD_R_AID);
        verify(mMockTm).iccTransmitApduLogicalChannel(channel, 0x81, 0xE2, 0x91, 0, 2, "A1B2");
        verify(mMockTm).iccCloseLogicalChannel(channel);
    }

    @Test
    public void testExecStoreData_LongDataLongResponse() throws UiccChannelException {
        int channel =
                UiccChannelMocker.mockOpenLogicalChannelResponse(
                        mMockTm, IccOpenLogicalChannelResponse.STATUS_NO_ERROR, "9000");
        UiccChannelMocker.mockSendToLogicalChannel(
                mMockTm, channel, "A19000", "9000", "9000", "B22B6103", "B2222B9000", "C39000");
        UiccChannelMocker.mockCloseLogicalChannel(mMockTm, channel);

        // Each segment has 0xF0 (the limit of a single command) bytes.
        String segment1 = new String(new char[0xF0]).replace("\0", "AA");
        String segment2 = new String(new char[0xF0]).replace("\0", "BB");
        String segment3 = new String(new char[16]).replace("\0", "CC");
        String longData = segment1 + segment2 + segment3;
        try (UiccLogicalChannel ch = UiccLogicalChannel.openIsdR(mMockTm)) {
            assertEquals("A1", ch.execStoreData("AA11"));
            assertEquals("B22BB2222B", ch.execStoreData(longData));
            assertEquals("C3", ch.execStoreData("CC33"));
        }

        verify(mMockTm).iccOpenLogicalChannel(UiccLogicalChannel.ISD_R_AID);
        verify(mMockTm).iccTransmitApduLogicalChannel(channel, 0x81, 0xE2, 0x91, 0, 2, "AA11");
        verify(mMockTm).iccTransmitApduLogicalChannel(channel, 0x81, 0xE2, 0x11, 0, 0xF0, segment1);
        verify(mMockTm).iccTransmitApduLogicalChannel(channel, 0x81, 0xE2, 0x11, 1, 0xF0, segment2);
        verify(mMockTm).iccTransmitApduLogicalChannel(channel, 0x81, 0xE2, 0x91, 2, 16, segment3);
        verify(mMockTm).iccTransmitApduLogicalChannel(channel, 0x81, 0xE2, 0x91, 0, 2, "CC33");
        verify(mMockTm).iccCloseLogicalChannel(channel);
    }

    @Test(expected = UiccChannelException.class)
    public void testExecStoreData_ErrorResponse() throws UiccChannelException {
        int channel =
                UiccChannelMocker.mockOpenLogicalChannelResponse(
                        mMockTm, IccOpenLogicalChannelResponse.STATUS_NO_ERROR, "9000");
        UiccChannelMocker.mockSendToLogicalChannel(mMockTm, channel, "A19000", "9000", "6985");
        UiccChannelMocker.mockCloseLogicalChannel(mMockTm, channel);

        // Each segment has 0xF0 (the limit of a single command) bytes.
        String segment1 = new String(new char[0xF0]).replace("\0", "AA");
        String segment2 = new String(new char[0xF0]).replace("\0", "BB");
        String segment3 = new String(new char[16]).replace("\0", "CC");
        String longData = segment1 + segment2 + segment3;
        try (UiccLogicalChannel ch = UiccLogicalChannel.openIsdR(mMockTm)) {
            assertEquals("A1", ch.execStoreData("AA11"));
            ch.execStoreData(longData);
        } catch (UiccChannelException e) {
            assertEquals("6985", e.getStatusHex());

            verify(mMockTm).iccOpenLogicalChannel(UiccLogicalChannel.ISD_R_AID);
            verify(mMockTm).iccTransmitApduLogicalChannel(channel, 0x81, 0xE2, 0x91, 0, 2, "AA11");
            verify(mMockTm)
                    .iccTransmitApduLogicalChannel(channel, 0x81, 0xE2, 0x11, 0, 0xF0, segment1);
            verify(mMockTm)
                    .iccTransmitApduLogicalChannel(channel, 0x81, 0xE2, 0x11, 1, 0xF0, segment2);
            verify(mMockTm).iccCloseLogicalChannel(channel);
            throw e;
        }
    }

    @Test
    public void testOpen_AnotherSameChannelOpened() throws UiccChannelException {
        int channel =
                UiccChannelMocker.mockOpenLogicalChannelResponse(
                        mMockTm, IccOpenLogicalChannelResponse.STATUS_NO_ERROR, "A1A1A19000");
        UiccChannelMocker.mockCloseLogicalChannel(mMockTm, channel);

        try (UiccLogicalChannel ch = UiccLogicalChannel.open(mMockTm, "B2C3D4")) {
            assertEquals(channel, ch.getChannel());
            assertEquals("A1A1A1", ch.getSelectResponse());

            try (UiccLogicalChannel anotherCh = UiccLogicalChannel.open(mMockTm, "B2C3D4")) {
                fail("New channel should not be opened while another channel is opened.");
            } catch (UiccChannelException e) {
                assertEquals(
                        "Cannot open channel because another one has already been opened: B2C3D4 "
                                + "(apduStatus=0)",
                        e.getMessage());
            }
        }

        verify(mMockTm).iccOpenLogicalChannel("B2C3D4");
        verify(mMockTm).iccCloseLogicalChannel(channel);
    }

    @Test
    public void testOpen_SameChannelOpenedTwice() throws UiccChannelException {
        int channel =
                UiccChannelMocker.mockOpenLogicalChannelResponse(
                        mMockTm, IccOpenLogicalChannelResponse.STATUS_NO_ERROR, "A1A1A19000");
        UiccChannelMocker.mockCloseLogicalChannel(mMockTm, channel);

        try (UiccLogicalChannel ch = UiccLogicalChannel.open(mMockTm, "B2C3D4")) {
            assertEquals(channel, ch.getChannel());
            assertEquals("A1A1A1", ch.getSelectResponse());
        }

        try (UiccLogicalChannel ch = UiccLogicalChannel.open(mMockTm, "B2C3D4")) {
            assertEquals(channel, ch.getChannel());
            assertEquals("A1A1A1", ch.getSelectResponse());
        }

        verify(mMockTm, times(2)).iccOpenLogicalChannel("B2C3D4");
        verify(mMockTm, times(2)).iccCloseLogicalChannel(channel);
    }

    @Test
    public void testOpen_AnotherDifferentChannelOpened() throws UiccChannelException {
        int channel =
                UiccChannelMocker.mockOpenLogicalChannelResponse(
                        mMockTm, IccOpenLogicalChannelResponse.STATUS_NO_ERROR, "A1A1A19000");
        UiccChannelMocker.mockCloseLogicalChannel(mMockTm, channel);

        try (UiccLogicalChannel ch = UiccLogicalChannel.open(mMockTm, "B2C3D4")) {
            assertEquals(channel, ch.getChannel());
            assertEquals("A1A1A1", ch.getSelectResponse());

            try (UiccLogicalChannel anotherCh = UiccLogicalChannel.open(mMockTm, "E5F6")) {
                // Do nothing
            } catch (UiccChannelException e) {
                fail("New channel should be opened while a channel is opened on a different aid.");
            }
        }

        verify(mMockTm).iccOpenLogicalChannel("B2C3D4");
        verify(mMockTm).iccOpenLogicalChannel("E5F6");
        verify(mMockTm, times(2)).iccCloseLogicalChannel(channel);
    }
}
