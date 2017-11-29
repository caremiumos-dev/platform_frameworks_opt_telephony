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

package com.android.euicc.card;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import com.android.euicc.asn1.Asn1Node;
import com.android.euicc.asn1.InvalidAsn1DataException;
import com.android.euicc.asn1.TagNotFoundException;
import com.android.euicc.async.AsyncResultTestCallback;
import com.android.euicc.card.EuiccCard.CancelReason;
import com.android.euicc.card.EuiccCard.ResetOption;
import com.android.euicc.data.EuiccNotification;
import com.android.euicc.data.EuiccNotification.Event;
import com.android.euicc.data.EuiccProfile;
import com.android.euicc.data.EuiccRat;
import com.android.euicc.data.EuiccSpecVersion;
import com.android.euicc.telephony.UiccChannelException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class EuiccCardTest {
    private class EuiccCardForTest extends EuiccCard {
        EuiccCardForTest(Looper handlerThreadLooper, EuiccSpecVersion cardVersion) {
            super(handlerThreadLooper, cardVersion);
        }

        @Override
        public void buildProfile(Asn1Node profileNode, EuiccProfile.Builder profileBuilder)
                throws InvalidAsn1DataException, TagNotFoundException {
            mDelegate.buildProfile(profileNode, profileBuilder);
        }

        @Override
        protected EuiccProfile[] getAllProfiles() throws UiccChannelException, EuiccCardException {
            return mDelegate.getAllProfiles();
        }

        protected EuiccProfile getProfile(String iccid)
                throws UiccChannelException, EuiccCardException {
            return mDelegate.getProfile(iccid);
        }

        @Override
        protected void disableProfile(String iccid, boolean refresh)
                throws UiccChannelException, EuiccCardException {
            mDelegate.disableProfile(iccid, refresh);
        }

        @Override
        protected EuiccProfile switchToProfile(String iccid, boolean refresh)
                throws UiccChannelException, EuiccCardException {
            return mDelegate.switchToProfile(iccid, refresh);
        }

        @Override
        protected String getEid() throws UiccChannelException, EuiccCardException {
            return mDelegate.getEid();
        }

        @Override
        protected void setNickname(String iccid, String nickname)
                throws UiccChannelException, EuiccCardException {
            mDelegate.setNickname(iccid, nickname);
        }

        @Override
        protected void deleteProfile(String iccid) throws UiccChannelException, EuiccCardException {
            mDelegate.deleteProfile(iccid);
        }

        @Override
        protected void resetMemory(@ResetOption int options)
                throws UiccChannelException, EuiccCardException {
            mDelegate.resetMemory(options);
        }

        @Override
        protected String getDefaultSmdpAddress() throws UiccChannelException, EuiccCardException {
            return mDelegate.getDefaultSmdpAddress();
        }

        @Override
        protected String getSmdsAddress() throws UiccChannelException, EuiccCardException {
            return mDelegate.getSmdsAddress();
        }

        @Override
        protected void setDefaultSmdpAddress(String defaultSmdpAddress)
                throws UiccChannelException, EuiccCardException {
            mDelegate.setDefaultSmdpAddress(defaultSmdpAddress);
        }

        @Override
        protected EuiccRat getRat() throws UiccChannelException, EuiccCardException {
            return mDelegate.getRat();
        }

        @Override
        protected byte[] getEuiccChallenge() throws UiccChannelException, EuiccCardException {
            return mDelegate.getEuiccChallenge();
        }

        @Override
        protected Asn1Node getEuiccInfo1() throws UiccChannelException, EuiccCardException {
            return mDelegate.getEuiccInfo1();
        }

        @Override
        protected Asn1Node getEuiccInfo2() throws UiccChannelException, EuiccCardException {
            return mDelegate.getEuiccInfo2();
        }

        @Override
        protected Asn1Node authenticateServer(
                String matchingId,
                Asn1Node serverSigned1,
                Asn1Node serverSignature1,
                Asn1Node euiccCiPkIdToBeUsed,
                Asn1Node serverCertificate)
                throws UiccChannelException, EuiccCardException {
            return mDelegate.authenticateServer(
                    matchingId,
                    serverSigned1,
                    serverSignature1,
                    euiccCiPkIdToBeUsed,
                    serverCertificate);
        }

        @Override
        protected Asn1Node prepareDownload(
                @Nullable byte[] hashCc,
                Asn1Node smdpSigned2,
                Asn1Node smdpSignature2,
                Asn1Node smdpCertificate)
                throws UiccChannelException, EuiccCardException {
            return mDelegate.prepareDownload(hashCc, smdpSigned2, smdpSignature2, smdpCertificate);
        }

        @Override
        protected Asn1Node loadBoundProfilePackage(Asn1Node boundProfilePackage)
                throws UiccChannelException, EuiccCardException {
            return mDelegate.loadBoundProfilePackage(boundProfilePackage);
        }

        @Override
        protected Asn1Node cancelSession(byte[] transactionId, @CancelReason int reason)
                throws UiccChannelException, EuiccCardException {
            return mDelegate.cancelSession(transactionId, reason);
        }

        @Override
        protected EuiccNotification[] listNotifications(@Event int events)
                throws UiccChannelException, EuiccCardException {
            return mDelegate.listNotifications(events);
        }

        @Override
        protected EuiccNotification[] retrieveNotificationList(@Event int events)
                throws UiccChannelException, EuiccCardException {
            return mDelegate.retrieveNotificationList(events);
        }

        @Nullable
        @Override
        protected EuiccNotification retrieveNotification(int seqNumber)
                throws UiccChannelException, EuiccCardException {
            return mDelegate.retrieveNotification(seqNumber);
        }

        @Override
        protected void removeNotificationFromList(int seqNumber)
                throws UiccChannelException, EuiccCardException {
            mDelegate.removeNotificationFromList(seqNumber);
        }

        @Override
        protected boolean isInTestMode() {
            return mIsInTestMode;
        }
    }

    private HandlerThread mThread;
    private Handler mHandler;
    private EuiccCard mEuiccCard;
    @Mock private EuiccCard mDelegate;
    private boolean mIsInTestMode;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mThread = new HandlerThread("EuiccCardTest");
        mThread.start();
        mHandler = new Handler(mThread.getLooper());
        mEuiccCard = new EuiccCardForTest(mThread.getLooper(), new EuiccSpecVersion(2, 0, 0));
    }

    @After
    public void tearDown() {
        mThread.quit();
    }

    @Test
    public void testGetProfiles()
            throws UiccChannelException, EuiccCardException, InterruptedException {
        EuiccProfile profile =
                new EuiccProfile.Builder()
                        .setIccid("123456")
                        .setProfileClass(EuiccProfile.ProfileClass.OPERATIONAL)
                        .build();
        EuiccProfile testProfile =
                new EuiccProfile.Builder()
                        .setIccid("123456")
                        .setProfileClass(EuiccProfile.ProfileClass.TESTING)
                        .build();
        when(mDelegate.getAllProfiles()).thenReturn(new EuiccProfile[] {profile, testProfile});

        AsyncResultTestCallback<EuiccProfile[]> callback = new AsyncResultTestCallback<>();
        mEuiccCard.getAllProfiles(callback, mHandler);
        callback.await();

        assertEquals(1, callback.getResult().length);
        assertEquals("123456", callback.getResult()[0].getIccid());
        verify(mDelegate).getAllProfiles();
    }

    @Test
    public void testGetProfiles_testMode()
            throws UiccChannelException, EuiccCardException, InterruptedException {
        mIsInTestMode = true;

        EuiccProfile profile =
                new EuiccProfile.Builder()
                        .setIccid("123456")
                        .setProfileClass(EuiccProfile.ProfileClass.OPERATIONAL)
                        .build();
        EuiccProfile testProfile =
                new EuiccProfile.Builder()
                        .setIccid("123456")
                        .setProfileClass(EuiccProfile.ProfileClass.TESTING)
                        .build();
        when(mDelegate.getAllProfiles()).thenReturn(new EuiccProfile[] {profile, testProfile});

        AsyncResultTestCallback<EuiccProfile[]> callback = new AsyncResultTestCallback<>();
        mEuiccCard.getAllProfiles(callback, mHandler);
        callback.await();

        assertEquals(2, callback.getResult().length);
        verify(mDelegate).getAllProfiles();
    }

    @Test
    public void testGetProfile()
            throws UiccChannelException, EuiccCardException, InterruptedException {
        EuiccProfile profile = new EuiccProfile.Builder().setIccid("123456").build();
        when(mDelegate.getProfile("123456")).thenReturn(profile);

        AsyncResultTestCallback<EuiccProfile> callback = new AsyncResultTestCallback<>();
        mEuiccCard.getProfile("123456", callback, mHandler);
        callback.await();

        assertEquals(profile, callback.getResult());
        verify(mDelegate).getProfile("123456");
    }

    @Test
    public void testDisableProfile()
            throws UiccChannelException, EuiccCardException, InterruptedException {
        AsyncResultTestCallback<Void> callback = new AsyncResultTestCallback<>();
        mEuiccCard.disableProfile("12345679801234567890", true, callback, mHandler);
        callback.await();

        verify(mDelegate).disableProfile("12345679801234567890", true);
    }

    @Test
    public void testSwitchToProfile()
            throws UiccChannelException, EuiccCardException, InterruptedException {
        AsyncResultTestCallback<EuiccProfile> callback = new AsyncResultTestCallback<>();
        mEuiccCard.switchToProfile("12345679801234567890", true, callback, mHandler);
        callback.await();

        verify(mDelegate).switchToProfile("12345679801234567890", true);
    }

    @Test
    public void testGetEid() throws UiccChannelException, EuiccCardException, InterruptedException {
        when(mDelegate.getEid()).thenReturn("12345679801234567890");

        AsyncResultTestCallback<String> callback = new AsyncResultTestCallback<>();
        mEuiccCard.getEid(callback, mHandler);
        callback.await();

        assertEquals("12345679801234567890", callback.getResult());
        verify(mDelegate).getEid();
    }

    @Test
    public void testSetNickname()
            throws UiccChannelException, EuiccCardException, InterruptedException {
        AsyncResultTestCallback<Void> callback = new AsyncResultTestCallback<>();
        mEuiccCard.setNickname("12345679801234567890", "nickname", callback, mHandler);
        callback.await();

        verify(mDelegate).setNickname("12345679801234567890", "nickname");
    }

    @Test
    public void testDeleteProfile()
            throws UiccChannelException, EuiccCardException, InterruptedException {
        AsyncResultTestCallback<Void> callback = new AsyncResultTestCallback<>();
        mEuiccCard.deleteProfile("12345679801234567890", callback, mHandler);
        callback.await();

        verify(mDelegate).deleteProfile("12345679801234567890");
    }

    @Test
    public void testResetMemory()
            throws UiccChannelException, EuiccCardException, InterruptedException {
        AsyncResultTestCallback<Void> callback = new AsyncResultTestCallback<>();
        mEuiccCard.resetMemory(ResetOption.DELETE_OPERATIONAL_PROFILES, callback, mHandler);
        callback.await();

        verify(mDelegate).resetMemory(ResetOption.DELETE_OPERATIONAL_PROFILES);
    }

    @Test
    public void testGetDefaultSmdpAddress()
            throws UiccChannelException, EuiccCardException, InterruptedException {
        when(mDelegate.getDefaultSmdpAddress()).thenReturn("smdp.google.com");

        AsyncResultTestCallback<String> callback = new AsyncResultTestCallback<>();
        mEuiccCard.getDefaultSmdpAddress(callback, mHandler);
        callback.await();

        assertEquals("smdp.google.com", callback.getResult());
        verify(mDelegate).getDefaultSmdpAddress();
    }

    @Test
    public void testGetSmdsAddress()
            throws UiccChannelException, EuiccCardException, InterruptedException {
        when(mDelegate.getSmdsAddress()).thenReturn("smds.google.com");

        AsyncResultTestCallback<String> callback = new AsyncResultTestCallback<>();
        mEuiccCard.getSmdsAddress(callback, mHandler);
        callback.await();

        assertEquals("smds.google.com", callback.getResult());
        verify(mDelegate).getSmdsAddress();
    }

    @Test
    public void testSetDefaultSmdpAddress()
            throws UiccChannelException, EuiccCardException, InterruptedException {
        AsyncResultTestCallback<Void> callback = new AsyncResultTestCallback<>();
        mEuiccCard.setDefaultSmdpAddress("smdp.google.com", callback, mHandler);
        callback.await();

        verify(mDelegate).setDefaultSmdpAddress("smdp.google.com");
    }

    @Test
    public void testGetRat() throws UiccChannelException, EuiccCardException, InterruptedException {
        EuiccRat rat = new EuiccRat.Builder(0).build();
        when(mDelegate.getRat()).thenReturn(rat);

        AsyncResultTestCallback<EuiccRat> callback = new AsyncResultTestCallback<>();
        mEuiccCard.getRat(callback, mHandler);
        callback.await();

        assertEquals(rat, callback.getResult());
        verify(mDelegate).getRat();
    }

    @Test
    public void testGetEuiccChallenge()
            throws UiccChannelException, EuiccCardException, InterruptedException {
        byte[] challenge = new byte[] {1, 2, 3};
        when(mDelegate.getEuiccChallenge()).thenReturn(challenge);

        AsyncResultTestCallback<byte[]> callback = new AsyncResultTestCallback<>();
        mEuiccCard.getEuiccChallenge(callback, mHandler);
        callback.await();

        assertArrayEquals(challenge, callback.getResult());
        verify(mDelegate).getEuiccChallenge();
    }

    @Test
    public void testGetEuiccInfo1()
            throws UiccChannelException, EuiccCardException, InterruptedException {
        Asn1Node node = Asn1Node.newBuilder(0xA0).build();
        when(mDelegate.getEuiccInfo1()).thenReturn(node);

        AsyncResultTestCallback<Asn1Node> callback = new AsyncResultTestCallback<>();
        mEuiccCard.getEuiccInfo1(callback, mHandler);
        callback.await();

        assertEquals(node, callback.getResult());
        verify(mDelegate).getEuiccInfo1();
    }

    @Test
    public void testGetEuiccInfo2()
            throws UiccChannelException, EuiccCardException, InterruptedException {
        Asn1Node node = Asn1Node.newBuilder(0xA0).build();
        when(mDelegate.getEuiccInfo2()).thenReturn(node);

        AsyncResultTestCallback<Asn1Node> callback = new AsyncResultTestCallback<>();
        mEuiccCard.getEuiccInfo2(callback, mHandler);
        callback.await();

        assertEquals(node, callback.getResult());
        verify(mDelegate).getEuiccInfo2();
    }

    @Test
    public void testAuthenticateServer()
            throws UiccChannelException, EuiccCardException, InterruptedException {
        Asn1Node result = Asn1Node.newBuilder(0xA0).build();
        Asn1Node serverSigned1 = Asn1Node.newBuilder(0xA1).build();
        Asn1Node serverSignature1 = Asn1Node.newBuilder(0xA2).build();
        Asn1Node euiccCiPkIdToBeUsed = Asn1Node.newBuilder(0xA3).build();
        Asn1Node serverCertificate = Asn1Node.newBuilder(0xA4).build();
        when(mDelegate.authenticateServer(
                        "ABC",
                        serverSigned1,
                        serverSignature1,
                        euiccCiPkIdToBeUsed,
                        serverCertificate))
                .thenReturn(result);

        AsyncResultTestCallback<Asn1Node> callback = new AsyncResultTestCallback<>();
        mEuiccCard.authenticateServer(
                "ABC",
                serverSigned1,
                serverSignature1,
                euiccCiPkIdToBeUsed,
                serverCertificate,
                callback,
                mHandler);
        callback.await();

        assertEquals(result, callback.getResult());
        verify(mDelegate)
                .authenticateServer(
                        "ABC",
                        serverSigned1,
                        serverSignature1,
                        euiccCiPkIdToBeUsed,
                        serverCertificate);
    }

    @Test
    public void testPrepareDownload()
            throws UiccChannelException, EuiccCardException, InterruptedException {
        Asn1Node result = Asn1Node.newBuilder(0xA0).build();
        byte[] hashCc = new byte[] {1, 2, 3};
        Asn1Node smdpSigned2 = Asn1Node.newBuilder(0xA1).build();
        Asn1Node smdpSignature2 = Asn1Node.newBuilder(0xA2).build();
        Asn1Node smdpCertificate = Asn1Node.newBuilder(0xA3).build();
        when(mDelegate.prepareDownload(hashCc, smdpSigned2, smdpSignature2, smdpCertificate))
                .thenReturn(result);

        AsyncResultTestCallback<Asn1Node> callback = new AsyncResultTestCallback<>();
        mEuiccCard.prepareDownload(
                hashCc, smdpSigned2, smdpSignature2, smdpCertificate, callback, mHandler);
        callback.await();

        assertEquals(result, callback.getResult());
        verify(mDelegate).prepareDownload(hashCc, smdpSigned2, smdpSignature2, smdpCertificate);
    }

    @Test
    public void testLoadBoundProfilePackage()
            throws UiccChannelException, EuiccCardException, InterruptedException {
        Asn1Node result = Asn1Node.newBuilder(0xA0).build();
        Asn1Node profilePackage = Asn1Node.newBuilder(0xA1).build();
        when(mDelegate.loadBoundProfilePackage(profilePackage)).thenReturn(result);

        AsyncResultTestCallback<Asn1Node> callback = new AsyncResultTestCallback<>();
        mEuiccCard.loadBoundProfilePackage(profilePackage, callback, mHandler);
        callback.await();

        assertEquals(result, callback.getResult());
        verify(mDelegate).loadBoundProfilePackage(profilePackage);
    }

    @Test
    public void testCancelSession()
            throws UiccChannelException, EuiccCardException, InterruptedException {
        byte[] transId = new byte[] {1, 2, 3};
        Asn1Node result = Asn1Node.newBuilder(0xA0).build();
        when(mDelegate.cancelSession(transId, CancelReason.POSTPONED)).thenReturn(result);

        AsyncResultTestCallback<Asn1Node> callback = new AsyncResultTestCallback<>();
        mEuiccCard.cancelSession(transId, CancelReason.POSTPONED, callback, mHandler);
        callback.await();

        assertEquals(result, callback.getResult());
        verify(mDelegate).cancelSession(transId, CancelReason.POSTPONED);
    }

    @Test
    public void testListNotifications()
            throws UiccChannelException, EuiccCardException, InterruptedException {
        EuiccNotification[] result = new EuiccNotification[1];
        when(mDelegate.listNotifications(Event.DELETE | Event.DISABLE)).thenReturn(result);

        AsyncResultTestCallback<EuiccNotification[]> callback = new AsyncResultTestCallback<>();
        mEuiccCard.listNotifications(Event.DELETE | Event.DISABLE, callback, mHandler);
        callback.await();

        assertEquals(1, callback.getResult().length);
        verify(mDelegate).listNotifications(Event.DELETE | Event.DISABLE);
    }

    @Test
    public void testRetrieveNotificationList()
            throws UiccChannelException, EuiccCardException, InterruptedException {
        EuiccNotification[] result = new EuiccNotification[1];
        when(mDelegate.retrieveNotificationList(Event.DELETE | Event.DISABLE)).thenReturn(result);

        AsyncResultTestCallback<EuiccNotification[]> callback = new AsyncResultTestCallback<>();
        mEuiccCard.retrieveNotificationList(Event.DELETE | Event.DISABLE, callback, mHandler);
        callback.await();

        assertEquals(1, callback.getResult().length);
        verify(mDelegate).retrieveNotificationList(Event.DELETE | Event.DISABLE);
    }

    @Test
    public void testRetrieveNotification()
            throws UiccChannelException, EuiccCardException, InterruptedException {
        EuiccNotification result =
                new EuiccNotification(12, "smdp.google.com", Event.DISABLE, null);
        when(mDelegate.retrieveNotification(12)).thenReturn(result);

        AsyncResultTestCallback<EuiccNotification> callback = new AsyncResultTestCallback<>();
        mEuiccCard.retrieveNotification(12, callback, mHandler);
        callback.await();

        assertEquals(result, callback.getResult());
        verify(mDelegate).retrieveNotification(12);
    }

    @Test
    public void getRemoveNotificationFromList()
            throws UiccChannelException, EuiccCardException, InterruptedException {
        AsyncResultTestCallback<Void> callback = new AsyncResultTestCallback<>();
        mEuiccCard.removeNotificationFromList(12, callback, mHandler);
        callback.await();

        verify(mDelegate).removeNotificationFromList(12);
    }
}
