/*
 * Copyright (C) 2020 The Android Open Source Project
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
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import android.annotation.NonNull;
import android.os.test.TestLooper;

import org.junit.Before;
import org.junit.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class CarrierPrivilegesTrackerTest {
    private static final int ADMINISTRATOR_UID = 10;

    private Phone mPhone;
    private TestLooper mTestLooper;
    private CarrierPrivilegesTracker mTracker;
    private TestListener mListener;

    @Before
    public void setUp() {
        mPhone = mock(Phone.class);
        mTestLooper = new TestLooper();
        mTracker = new CarrierPrivilegesTracker(mPhone, mTestLooper.getLooper());
        mListener = new TestListener();
    }

    @Test
    public void testRegisterListener() {
        mTracker.mAdministratorUids.add(ADMINISTRATOR_UID);

        mTracker.registerCarrierPrivilegeListener(mListener);
        mTestLooper.dispatchAll();
        assertTrue(mTracker.mListeners.contains(mListener));
        assertTrue(mListener.mUids.contains(ADMINISTRATOR_UID));
        assertEquals(1, mListener.mTimesInvoked.get());
    }

    @Test
    public void testUnregisterListener() {
        mTracker.registerCarrierPrivilegeListener(mListener);
        mTestLooper.dispatchAll();

        mTracker.unregisterCallback(mListener);
        mTestLooper.dispatchAll();
        assertTrue(mTracker.mListeners.isEmpty());
    }

    private class TestListener extends CarrierPrivilegesTracker.CarrierPrivilegesListener {
        private Set<Integer> mUids;
        private final AtomicInteger mTimesInvoked = new AtomicInteger();

        @Override
        public void onCarrierPrivilegesChanged(@NonNull Set<Integer> uids) {
            mUids = uids;
            mTimesInvoked.getAndIncrement();
        }
    }
}
