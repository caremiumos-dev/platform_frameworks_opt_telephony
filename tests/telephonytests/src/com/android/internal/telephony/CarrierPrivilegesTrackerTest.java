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
import static org.mockito.Mockito.when;

import android.annotation.NonNull;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.test.TestLooper;
import android.telephony.CarrierConfigManager;
import android.telephony.TelephonyManager;

import org.junit.Before;
import org.junit.Test;

import java.util.Set;

public class CarrierPrivilegesTrackerTest {
    private static final int ADMINISTRATOR_UID = 10;
    private static final int SUB_ID = 1;

    private Phone mPhone;
    private Context mContext;
    private CarrierConfigManager mCarrierConfigManager;
    private PackageManager mPackageManager;
    private TelephonyManager mTelephonyManager;

    private TestLooper mTestLooper;
    private CarrierPrivilegesTracker mTracker;
    private TestListener mListener;

    @Before
    public void setUp() {
        mPhone = mock(Phone.class);
        mContext = mock(Context.class);
        mCarrierConfigManager = mock(CarrierConfigManager.class);
        mPackageManager = mock(PackageManager.class);
        mTelephonyManager = mock(TelephonyManager.class);

        when(mPhone.getContext()).thenReturn(mContext);
        when(mPhone.getSubId()).thenReturn(SUB_ID);

        TelephonyManager mockInitialTelephonyManager = mock(TelephonyManager.class);
        when(mockInitialTelephonyManager.createForSubscriptionId(SUB_ID))
                .thenReturn(mTelephonyManager);

        mTestLooper = new TestLooper();
        mTracker =
                new CarrierPrivilegesTracker(
                        mTestLooper.getLooper(),
                        mPhone,
                        mCarrierConfigManager,
                        mPackageManager,
                        mockInitialTelephonyManager);
        mListener = new TestListener();
    }

    @Test
    public void testRegisterListener() {
        mTracker.mPrivilegedUids.add(ADMINISTRATOR_UID);

        mTracker.registerCarrierPrivilegeListener(mListener);
        mTestLooper.dispatchAll();
        assertTrue(mTracker.mListeners.contains(mListener));
        assertTrue(mListener.mUids.contains(ADMINISTRATOR_UID));
        assertEquals(1, mListener.mTimesInvoked);
    }

    @Test
    public void testUnregisterListener() {
        mTracker.registerCarrierPrivilegeListener(mListener);
        mTestLooper.dispatchAll();

        mTracker.unregisterCallback(mListener);
        mTestLooper.dispatchAll();
        assertTrue(mTracker.mListeners.isEmpty());
    }

    private class TestListener implements CarrierPrivilegesTracker.CarrierPrivilegesListener {
        private Set<Integer> mUids;
        private int mTimesInvoked;

        @Override
        public void onCarrierPrivilegesChanged(@NonNull Set<Integer> uids) {
            mUids = uids;
            mTimesInvoked++;
        }
    }
}
