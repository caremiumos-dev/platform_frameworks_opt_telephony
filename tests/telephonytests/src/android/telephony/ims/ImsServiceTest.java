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

package android.telephony.ims;

import android.os.RemoteException;
import android.support.test.runner.AndroidJUnit4;
import android.telephony.ims.feature.ImsFeature;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.SparseArray;

import com.android.ims.internal.IImsServiceController;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNull;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for ImsService
 */
@RunWith(AndroidJUnit4.class)
public class ImsServiceTest {

    private static final int TEST_SLOT_0 = 0;
    private static final int TEST_SLOT_1 = 1;

    private TestImsService mTestImsService;
    private IImsServiceController mTestImsServiceBinder;

    @Before
    public void setUp() throws Exception {
        mTestImsService = new TestImsService();
        mTestImsServiceBinder = (IImsServiceController) mTestImsService.onBind(null);
    }

    @After
    public void tearDown() throws Exception {
        mTestImsService = null;
        mTestImsServiceBinder = null;
    }

    @Test
    @SmallTest
    public void testCreateMMTelFeature() throws RemoteException {
        mTestImsServiceBinder.createImsFeature(TEST_SLOT_0, ImsFeature.MMTEL);

        SparseArray<ImsFeature> features = mTestImsService.getImsFeatureMap(TEST_SLOT_0);
        assertEquals(mTestImsService.mMockMMTelFeature,
                mTestImsService.getImsFeatureFromType(features, ImsFeature.MMTEL));
    }

    @Test
    @SmallTest
    public void testRemoveMMTelFeature() throws RemoteException {
        mTestImsServiceBinder.createImsFeature(TEST_SLOT_0, ImsFeature.MMTEL);

        mTestImsServiceBinder.removeImsFeature(TEST_SLOT_0, ImsFeature.MMTEL);

        verify(mTestImsService.mMockMMTelFeature).onFeatureRemoved();
        SparseArray<ImsFeature> features = mTestImsService.getImsFeatureMap(TEST_SLOT_0);
        assertNull(mTestImsService.getImsFeatureFromType(features, ImsFeature.MMTEL));
    }

    @Test
    @SmallTest
    public void testCallMethodOnCreatedFeature() throws RemoteException {
        mTestImsServiceBinder.createImsFeature(TEST_SLOT_0, ImsFeature.MMTEL);

        mTestImsServiceBinder.isConnected(TEST_SLOT_0, ImsFeature.MMTEL, 0 /*session id*/,
                0 /*callSessionType*/, 0 /*callType*/);

        verify(mTestImsService.mMockMMTelFeature).isConnected(anyInt(), anyInt(), anyInt());
    }

    @Test
    @SmallTest
    public void testCallMethodWithNoCreatedFeature() throws RemoteException {
        mTestImsServiceBinder.createImsFeature(TEST_SLOT_0, ImsFeature.MMTEL);

        mTestImsServiceBinder.isConnected(TEST_SLOT_1, ImsFeature.MMTEL, 0 /*session id*/,
                0 /*callSessionType*/, 0 /*callType*/);

        verify(mTestImsService.mMockMMTelFeature, never()).isConnected(anyInt(), anyInt(),
                anyInt());
    }
}
