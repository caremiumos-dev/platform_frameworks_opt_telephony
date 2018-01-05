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

package com.android.internal.telephony.uicc.euicc.apdu;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.os.Handler;
import android.os.HandlerThread;
import android.telephony.IccOpenLogicalChannelResponse;

import com.android.internal.telephony.uicc.IccUtils;
import com.android.internal.telephony.uicc.UiccProfile;
import com.android.internal.telephony.uicc.euicc.async.AsyncResultCallback;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ApduSenderTest {
    private static final long WAIT_TIMEOUT_MLLIS = 5000;

    @Mock
    private UiccProfile mMockProfile;

    private HandlerThread mThread = new HandlerThread("ApduSenderTest");
    private Handler mHandler = mThread.getThreadHandler();

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @After
    public void tearDown() {
        mThread.quit();
    }

    @Test
    public void testSendEmptyCommands() throws InterruptedException {
        String aid = "B2C3D4";
        ApduSender sender = new ApduSender(mMockProfile, aid);

        int channel =
                LogicalChannelMocker.mockOpenLogicalChannelResponse(
                        mMockProfile, IccOpenLogicalChannelResponse.STATUS_NO_ERROR, "A1A1A19000");
        LogicalChannelMocker.mockCloseLogicalChannel(mMockProfile, channel);

        CountDownLatch latch = new CountDownLatch(1);
        sender.send(
                (selectResponse, requestBuilder) -> {
                    assertEquals("A1A1A1", IccUtils.bytesToHexString(selectResponse));
                },
                new AsyncResultCallback<byte[]>() {
                    @Override
                    public void onResult(byte[] bytes) {
                        assertNull(bytes);
                        latch.countDown();
                    }
                },
                mHandler);

        latch.await(WAIT_TIMEOUT_MLLIS, TimeUnit.MILLISECONDS);

        verify(mMockProfile).iccOpenLogicalChannel(eq(aid), anyInt(), any());
        verify(mMockProfile).iccCloseLogicalChannel(eq(channel), any());
    }

    public void testOpenChannelErrorStatus() throws InterruptedException {
        String aid = "B2C3D4";
        ApduSender sender = new ApduSender(mMockProfile, aid);

        LogicalChannelMocker.mockOpenLogicalChannelResponse(
                mMockProfile, IccOpenLogicalChannelResponse.STATUS_NO_SUCH_ELEMENT, "");

        CountDownLatch latch = new CountDownLatch(1);
        sender.send(
                (selectResponse, requestBuilder) -> {
                    fail("Request provider should not be called when failed to open channel.");
                },
                new AsyncResultCallback<byte[]>() {
                    @Override
                    public void onResult(byte[] bytes) {
                        fail("onResult should not be called when failed to open channel.");
                        latch.countDown();
                    }

                    @Override
                    public void onException(Throwable e) {
                        assertTrue(e instanceof ApduException);
                        latch.countDown();
                    }
                },
                mHandler);

        latch.await(WAIT_TIMEOUT_MLLIS, TimeUnit.MILLISECONDS);

        verify(mMockProfile).iccOpenLogicalChannel(eq(aid), anyInt(), any());
    }

    @Test
    public void testSend() throws InterruptedException {
        String aid = "B2C3D4";
        ApduSender sender = new ApduSender(mMockProfile, aid);

        int channel =
                LogicalChannelMocker.mockOpenLogicalChannelResponse(
                        mMockProfile, IccOpenLogicalChannelResponse.STATUS_NO_ERROR, "9000");
        LogicalChannelMocker.mockSendToLogicalChannel(mMockProfile, channel, "A1A1A19000");
        LogicalChannelMocker.mockCloseLogicalChannel(mMockProfile, channel);

        CountDownLatch latch = new CountDownLatch(1);
        sender.send(
                (selectResponse, requestBuilder) -> {
                    requestBuilder.addApdu(10, 1, 2, 3, 0, "a");
                },
                new AsyncResultCallback<byte[]>() {
                    @Override
                    public void onResult(byte[] bytes) {
                        assertEquals("A1A1A1", IccUtils.bytesToHexString(bytes));
                        latch.countDown();
                    }
                },
                mHandler);

        latch.await(WAIT_TIMEOUT_MLLIS, TimeUnit.MILLISECONDS);

        verify(mMockProfile)
                .iccTransmitApduLogicalChannel(
                        eq(channel), eq(channel | 10), eq(1), eq(2), eq(3), eq(0), eq("a"), any());
    }

    @Test
    public void testSendMultiApdus() throws InterruptedException {
        String aid = "B2C3D4";
        ApduSender sender = new ApduSender(mMockProfile, aid);

        int channel =
                LogicalChannelMocker.mockOpenLogicalChannelResponse(
                        mMockProfile, IccOpenLogicalChannelResponse.STATUS_NO_ERROR, "9000");
        LogicalChannelMocker
                .mockSendToLogicalChannel(
                        mMockProfile, channel, "A19000", "A29000", "A39000", "A49000");
        LogicalChannelMocker.mockCloseLogicalChannel(mMockProfile, channel);

        CountDownLatch latch = new CountDownLatch(1);
        sender.send(
                (selectResponse, requestBuilder) -> {
                    requestBuilder.addApdu(10, 1, 2, 3, 0, "a");
                    requestBuilder.addApdu(10, 1, 2, 3, "ab");
                    requestBuilder.addApdu(10, 1, 2, 3);
                    requestBuilder.addStoreData("abc");
                },
                new AsyncResultCallback<byte[]>() {
                    @Override
                    public void onResult(byte[] bytes) {
                        assertEquals("A4", IccUtils.bytesToHexString(bytes));
                        latch.countDown();
                    }
                },
                mHandler);

        latch.await(WAIT_TIMEOUT_MLLIS, TimeUnit.MILLISECONDS);

        verify(mMockProfile)
                .iccTransmitApduLogicalChannel(
                        eq(channel), eq(channel | 10), eq(1), eq(2), eq(3), eq(0), eq("a"), any());
        verify(mMockProfile)
                .iccTransmitApduLogicalChannel(
                        eq(channel), eq(channel | 10), eq(1), eq(2), eq(3), eq(2), eq("ab"), any());
        verify(mMockProfile)
                .iccTransmitApduLogicalChannel(
                        eq(channel), eq(channel | 10), eq(1), eq(2), eq(3), eq(0), eq(""), any());
        verify(mMockProfile)
                .iccTransmitApduLogicalChannel(
                        eq(channel), eq(0x81), eq(0xE2), eq(0x91), eq(0), eq(3), eq("abc"), any());
    }

    @Test
    public void testSendLongResponse() throws InterruptedException {
        String aid = "B2C3D4";
        ApduSender sender = new ApduSender(mMockProfile, aid);

        int channel =
                LogicalChannelMocker.mockOpenLogicalChannelResponse(
                        mMockProfile, IccOpenLogicalChannelResponse.STATUS_NO_ERROR, "9000");
        LogicalChannelMocker
                .mockSendToLogicalChannel(
                        mMockProfile, channel, "A1A1A16104", "B2B2B2B26102", "C3C39000");
        LogicalChannelMocker.mockCloseLogicalChannel(mMockProfile, channel);

        CountDownLatch latch = new CountDownLatch(1);
        sender.send(
                (selectResponse, requestBuilder) -> {
                    requestBuilder.addApdu(10, 1, 2, 3, 0, "a");
                },
                new AsyncResultCallback<byte[]>() {
                    @Override
                    public void onResult(byte[] bytes) {
                        assertEquals("A1A1A1B2B2B2B2C3C3", IccUtils.bytesToHexString(bytes));
                        latch.countDown();
                    }
                },
                mHandler);

        latch.await(WAIT_TIMEOUT_MLLIS, TimeUnit.MILLISECONDS);

        verify(mMockProfile)
                .iccTransmitApduLogicalChannel(
                        eq(channel), eq(channel | 10), eq(1), eq(2), eq(3), eq(0), eq("a"), any());
        verify(mMockProfile)
                .iccTransmitApduLogicalChannel(
                        eq(channel), eq(channel), eq(0xC0), eq(0), eq(0), eq(4), eq(""), any());
        verify(mMockProfile)
                .iccTransmitApduLogicalChannel(
                        eq(channel), eq(channel), eq(0xC0), eq(0), eq(0), eq(2), eq(""), any());
    }

    @Test
    public void testExecStoreData_LongDataLongResponse() throws InterruptedException {
        String aid = "B2C3D4";
        ApduSender sender = new ApduSender(mMockProfile, aid);

        int channel =
                LogicalChannelMocker.mockOpenLogicalChannelResponse(
                        mMockProfile, IccOpenLogicalChannelResponse.STATUS_NO_ERROR, "9000");
        LogicalChannelMocker
                .mockSendToLogicalChannel(
                        mMockProfile,
                        channel,
                        "A19000",
                        "9000",
                        "9000",
                        "B22B6103",
                        "B2222B9000",
                        "C39000");
        LogicalChannelMocker.mockCloseLogicalChannel(mMockProfile, channel);

        CountDownLatch latch = new CountDownLatch(1);
        // Each segment has 0xFF (the limit of a single command) bytes.
        String s1 = new String(new char[0xFF]).replace("\0", "AA");
        String s2 = new String(new char[0xFF]).replace("\0", "BB");
        String s3 = new String(new char[16]).replace("\0", "CC");
        String longData = s1 + s2 + s3;
        sender.send(
                (selectResponse, requestBuilder) -> {
                    requestBuilder.addApdu(10, 1, 2, 3, 0, "a");
                    requestBuilder.addApdu(10, 1, 2, 3, 0, "b");
                    requestBuilder.addStoreData(longData);
                },
                new AsyncResultCallback<byte[]>() {
                    @Override
                    public void onResult(byte[] bytes) {
                        assertEquals("C3", IccUtils.bytesToHexString(bytes));
                        latch.countDown();
                    }
                },
                mHandler);

        latch.await(WAIT_TIMEOUT_MLLIS, TimeUnit.MILLISECONDS);

        verify(mMockProfile)
                .iccTransmitApduLogicalChannel(
                        eq(channel), eq(channel | 10), eq(1), eq(2), eq(3), eq(0), eq("a"), any());
        verify(mMockProfile)
                .iccTransmitApduLogicalChannel(
                        eq(channel), eq(channel | 10), eq(1), eq(2), eq(3), eq(0), eq("c"), any());
        verify(mMockProfile)
                .iccTransmitApduLogicalChannel(
                        eq(channel), eq(0x81), eq(0xE2), eq(0x91), eq(0), eq(0xFF), eq(s1), any());
        verify(mMockProfile)
                .iccTransmitApduLogicalChannel(
                        eq(channel), eq(0x81), eq(0xE2), eq(0x91), eq(0), eq(0xFF), eq(s2), any());
        verify(mMockProfile)
                .iccTransmitApduLogicalChannel(
                        eq(channel), eq(0x81), eq(0xE2), eq(0x91), eq(0), eq(16), eq(s3), any());
    }

    @Test
    public void testSendErrorResponse() throws InterruptedException {
        String aid = "B2C3D4";
        ApduSender sender = new ApduSender(mMockProfile, aid);

        int channel =
                LogicalChannelMocker.mockOpenLogicalChannelResponse(
                        mMockProfile, IccOpenLogicalChannelResponse.STATUS_NO_ERROR, "9000");
        LogicalChannelMocker
                .mockSendToLogicalChannel(
                        mMockProfile, channel, "A19000", "B22B6103", "6985");
        LogicalChannelMocker.mockCloseLogicalChannel(mMockProfile, channel);

        CountDownLatch latch = new CountDownLatch(1);
        // Each segment has 0xFF (the limit of a single command) bytes.
        String s1 = new String(new char[0xFF]).replace("\0", "AA");
        String s2 = new String(new char[0xFF]).replace("\0", "BB");
        String s3 = new String(new char[16]).replace("\0", "CC");
        String longData = s1 + s2 + s3;
        sender.send(
                (selectResponse, requestBuilder) -> {
                    requestBuilder.addApdu(10, 1, 2, 3, 0, "a");
                    requestBuilder.addStoreData(longData);
                },
                new AsyncResultCallback<byte[]>() {
                    @Override
                    public void onResult(byte[] bytes) {
                        fail("onResult should not be called when a command failed.");
                        latch.countDown();
                    }

                    @Override
                    public void onException(Throwable e) {
                        assertEquals(0x6985, ((ApduException) e).getApduStatus());
                        latch.countDown();
                    }
                },
                mHandler);

        latch.await(WAIT_TIMEOUT_MLLIS, TimeUnit.MILLISECONDS);

        verify(mMockProfile)
                .iccTransmitApduLogicalChannel(
                        eq(channel), eq(channel | 10), eq(1), eq(2), eq(3), eq(0), eq("a"), any());
        verify(mMockProfile)
                .iccTransmitApduLogicalChannel(
                        eq(channel), eq(channel | 10), eq(1), eq(2), eq(3), eq(0), eq("c"), any());
        verify(mMockProfile)
                .iccTransmitApduLogicalChannel(
                        eq(channel), eq(0x81), eq(0xE2), eq(0x91), eq(0), eq(0xFF), eq(s1), any());
        verify(mMockProfile)
                .iccTransmitApduLogicalChannel(
                        eq(channel), eq(0x81), eq(0xE2), eq(0x91), eq(0), eq(0xFF), eq(s2), any());
        verify(mMockProfile, never())
                .iccTransmitApduLogicalChannel(
                        eq(channel), eq(0x81), eq(0xE2), eq(0x91), eq(0), eq(16), eq(s3), any());
    }

    @Test
    public void testChannelAlreadyOpened() throws InterruptedException {
        String aid = "B2C3D4";
        ApduSender sender = new ApduSender(mMockProfile, aid);

        LogicalChannelMocker.mockOpenLogicalChannelResponse(
                mMockProfile, IccOpenLogicalChannelResponse.STATUS_NO_ERROR, "9000");

        CountDownLatch latch = new CountDownLatch(1);
        sender.send(
                (selectResponse, requestBuilder) -> {
                    sender.send(
                            (selectResponseOther, requestBuilderOther) -> {
                                fail("Should not open channel when another one is already opened.");
                            },
                            new AsyncResultCallback<byte[]>() {
                                @Override
                                public void onResult(byte[] bytes) {
                                    fail(
                                            "onResult should not be called when another channel "
                                                    + "is already opened.");
                                    latch.countDown();
                                }

                                @Override
                                public void onException(Throwable e) {
                                    assertTrue(e instanceof ApduException);
                                    latch.countDown();
                                }
                            },
                            mHandler);
                },
                new AsyncResultCallback<byte[]>() {
                    @Override
                    public void onResult(byte[] bytes) {
                        latch.countDown();
                    }
                },
                mHandler);

        latch.await(WAIT_TIMEOUT_MLLIS, TimeUnit.MILLISECONDS);

        verify(mMockProfile, times(1)).iccOpenLogicalChannel(eq(aid), anyInt(), any());
    }
}
