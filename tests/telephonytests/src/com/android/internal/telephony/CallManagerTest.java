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
package com.android.internal.telephony;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isA;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.os.Handler;
import android.telephony.PhoneNumberUtils;
import android.telephony.ServiceState;
import android.test.suitebuilder.annotation.SmallTest;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.lang.reflect.Field;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class CallManagerTest extends TelephonyTest {

    @Mock
    GsmCdmaCall mFgCall;
    @Mock
    GsmCdmaCall mBgCall;
    @Mock
    GsmCdmaCall mRingingCall;
    @Mock
    Phone mSecondPhone;

    @Before
    public void setUp() throws Exception {
        super.setUp(this.getClass().getSimpleName());
        restoreInstance(CallManager.class, "INSTANCE", null);
        /* Mock Phone and Call, initially all calls are idle */
        doReturn(ServiceState.STATE_IN_SERVICE).when(mServiceState).getState();
        doReturn(mBgCall).when(mPhone).getBackgroundCall();
        doReturn(mFgCall).when(mPhone).getForegroundCall();
        doReturn(mRingingCall).when(mPhone).getRingingCall();
        doReturn(mPhone).when(mBgCall).getPhone();
        doReturn(mPhone).when(mFgCall).getPhone();
        doReturn(mPhone).when(mRingingCall).getPhone();
        doReturn(Call.State.IDLE).when(mBgCall).getState();
        doReturn(Call.State.IDLE).when(mFgCall).getState();
        doReturn(Call.State.IDLE).when(mRingingCall).getState();
        doReturn(true).when(mBgCall).isIdle();
        doReturn(true).when(mFgCall).isIdle();
        doReturn(true).when(mRingingCall).isIdle();

        /* CallManager is a static object with private constructor; no need to call constructor */
        CallManager.getInstance().registerPhone(mPhone);
        processAllMessages();
    }

    @After
    public void tearDown() throws Exception {
        CallManager.getInstance().unregisterPhone(mPhone);
        super.tearDown();
    }

    @SmallTest @Test
    public void testSanity() {
        assertEquals(mPhone, CallManager.getInstance().getDefaultPhone());
        assertFalse(CallManager.getInstance().hasActiveRingingCall());
        /* return the default phone if there is no any active call */
        assertEquals(mPhone, CallManager.getInstance().getRingingPhone());
        assertEquals(mPhone, CallManager.getInstance().getBgPhone());
        assertEquals(mPhone, CallManager.getInstance().getFgPhone());
    }

    @SmallTest @Test
    public void testBasicDial() throws Exception {
        //verify can dial and dial function of the phone is being triggered
        CallManager.getInstance().dial(mPhone,
                PhoneNumberUtils.stripSeparators("+17005554141"), 0);
        ArgumentCaptor<String> mCaptorString = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<PhoneInternalInterface.DialArgs> dialArgsCaptor =
                ArgumentCaptor.forClass(PhoneInternalInterface.DialArgs.class);
        verify(mPhone, times(1)).dial(mCaptorString.capture(), dialArgsCaptor.capture());
        assertEquals(PhoneNumberUtils.stripSeparators("+17005554141"),
                mCaptorString.getValue());
        assertEquals(0, dialArgsCaptor.getValue().videoState);
    }

    @SmallTest @Test
    public void testFgCallActiveDial() throws Exception {
        /* set Fg/Bg Call state to active, verify CallManager Logical */
        doReturn(false).when(mFgCall).isIdle();
        doReturn(false).when(mBgCall).isIdle();
        assertTrue(CallManager.getInstance().hasActiveFgCall(mPhone.getSubId()));
        assertFalse(CallManager.getInstance().hasDisconnectedFgCall());
        /* try dial with non-idle foreground call and background call */
        CallManager.getInstance().dial(mPhone,
                PhoneNumberUtils.stripSeparators("+17005554141"), 0);
        ArgumentCaptor<String> mCaptorString = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<PhoneInternalInterface.DialArgs> dialArgsCaptor =
                ArgumentCaptor.forClass(PhoneInternalInterface.DialArgs.class);

        verify(mPhone, times(1)).dial(mCaptorString.capture(), dialArgsCaptor.capture());
        assertEquals(PhoneNumberUtils.stripSeparators("+17005554141"),
                mCaptorString.getValue());
        assertEquals(0, dialArgsCaptor.getValue().videoState);
    }

    @Test @SmallTest
    public void testRegisterEvent() throws Exception {
        Field field = CallManager.class.getDeclaredField("EVENT_CALL_WAITING");
        field.setAccessible(true);
        int mEvent = (Integer) field.get(CallManager.getInstance());
        verify(mPhone, times(1)).registerForCallWaiting(isA(Handler.class),
                eq(mEvent), isNull());

        field = CallManager.class.getDeclaredField("EVENT_PRECISE_CALL_STATE_CHANGED");
        field.setAccessible(true);
        mEvent = (Integer) field.get(CallManager.getInstance());
        verify(mPhone, times(1)).registerForPreciseCallStateChanged(isA(Handler.class),
                eq(mEvent), isA(Object.class));

        field = CallManager.class.getDeclaredField("EVENT_RINGBACK_TONE");
        field.setAccessible(true);
        mEvent = (Integer) field.get(CallManager.getInstance());
        verify(mPhone, times(1)).registerForRingbackTone(isA(Handler.class),
                eq(mEvent), isA(Object.class));
    }
}
