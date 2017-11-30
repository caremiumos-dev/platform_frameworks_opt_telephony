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

package android.telephony.ims.internal;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.support.test.runner.AndroidJUnit4;
import android.telephony.ims.internal.feature.ImsFeature;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.ims.internal.IImsFeatureStatusCallback;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class ImsFeatureTest {

    private TestImsFeature mTestImsFeature;
    private ImsFeature.CapabilityCallback mCapabilityCallback;

    @Mock
    private IImsFeatureStatusCallback mTestStatusCallback;
    @Mock
    private IImsFeatureStatusCallback mTestStatusCallback2;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mTestImsFeature = new TestImsFeature();
        mCapabilityCallback = Mockito.spy(new ImsFeature.CapabilityCallback());
        mTestImsFeature.addCapabilityCallback(mCapabilityCallback);
    }

    @After
    public void tearDown() {
        mTestImsFeature = null;
        mCapabilityCallback = null;
    }

    @Test
    @SmallTest
    public void testSetCallbackAndNotify() throws Exception {
        mTestImsFeature.addImsFeatureStatusCallback(mTestStatusCallback);
        mTestImsFeature.addImsFeatureStatusCallback(mTestStatusCallback2);

        verify(mTestStatusCallback).notifyImsFeatureStatus(eq(ImsFeature.STATE_NOT_AVAILABLE));
        verify(mTestStatusCallback2).notifyImsFeatureStatus(eq(ImsFeature.STATE_NOT_AVAILABLE));
    }

    @Test
    @SmallTest
    public void testSetFeatureAndCheckCallback() throws Exception {
        mTestImsFeature.addImsFeatureStatusCallback(mTestStatusCallback);
        mTestImsFeature.addImsFeatureStatusCallback(mTestStatusCallback2);

        mTestImsFeature.testSetFeatureState(ImsFeature.STATE_READY);

        verify(mTestStatusCallback).notifyImsFeatureStatus(eq(ImsFeature.STATE_READY));
        verify(mTestStatusCallback2).notifyImsFeatureStatus(eq(ImsFeature.STATE_READY));
        assertEquals(ImsFeature.STATE_READY, mTestImsFeature.getFeatureState());
    }

    @SmallTest
    @Test
    public void testCapabilityConfigAdd() throws Exception {
        ImsFeature.CapabilityConfiguration c = new ImsFeature.CapabilityConfiguration();
        c.addCapabilities(TestImsFeature.CAPABILITY_TEST_1);

        assertTrue(c.isCapable(TestImsFeature.CAPABILITY_TEST_1));
    }

    @SmallTest
    @Test
    public void testCapabilityConfigAddMultiple() throws Exception {
        ImsFeature.CapabilityConfiguration c = new ImsFeature.CapabilityConfiguration();
        c.addCapabilities(TestImsFeature.CAPABILITY_TEST_1);
        c.addCapabilities(TestImsFeature.CAPABILITY_TEST_2);

        assertTrue(c.isCapable(TestImsFeature.CAPABILITY_TEST_2));
    }

    @SmallTest
    @Test
    public void testCapabilityConfigHasMultiple() throws Exception {
        ImsFeature.CapabilityConfiguration c = new ImsFeature.CapabilityConfiguration();
        c.addCapabilities(TestImsFeature.CAPABILITY_TEST_1);
        c.addCapabilities(TestImsFeature.CAPABILITY_TEST_2);

        assertTrue(c.isCapable(
                TestImsFeature.CAPABILITY_TEST_1 | TestImsFeature.CAPABILITY_TEST_2));
    }

    @SmallTest
    @Test
    public void testCapabilityConfigRemove() throws Exception {
        ImsFeature.CapabilityConfiguration c = new ImsFeature.CapabilityConfiguration();
        c.addCapabilities(TestImsFeature.CAPABILITY_TEST_1);
        c.addCapabilities(TestImsFeature.CAPABILITY_TEST_2);
        c.removeCapabilities(TestImsFeature.CAPABILITY_TEST_1);

        assertTrue(c.isCapable(TestImsFeature.CAPABILITY_TEST_2));
    }

    @SmallTest
    @Test
    public void testSetCapabilityConfig() throws Exception {
        ImsFeature.CapabilityConfiguration c = new ImsFeature.CapabilityConfiguration();
        c.addCapabilities(TestImsFeature.CAPABILITY_TEST_1);

        mTestImsFeature.setCapabilities(c, null);

        assertEquals(c.getMask(), mTestImsFeature.queryCapabilities().getMask());
        assertEquals(c.getMask(), mTestImsFeature.lastConfig.getMask());
    }

    @SmallTest
    @Test
    public void testSetCapabilityConfigNotify() throws Exception {
        ImsFeature.CapabilityConfiguration c = new ImsFeature.CapabilityConfiguration();
        c.addCapabilities(TestImsFeature.CAPABILITY_TEST_1);

        mTestImsFeature.setCapabilities(c, null);

        assertEquals(c.getMask(), mTestImsFeature.queryCapabilities().getMask());
        assertEquals(c.getMask(), mTestImsFeature.lastConfig.getMask());
        verify(mCapabilityCallback).onCapabilitiesChanged(eq(c));
    }

    @SmallTest
    @Test
    public void testSetCapabilityConfigNoNotify() throws Exception {
        ImsFeature.CapabilityConfiguration c = new ImsFeature.CapabilityConfiguration();
        c.addCapabilities(TestImsFeature.CAPABILITY_TEST_1);

        mTestImsFeature.removeCapabilityCallback(mCapabilityCallback);
        mTestImsFeature.setCapabilities(c, null);

        assertEquals(c.getMask(), mTestImsFeature.queryCapabilities().getMask());
        assertEquals(c.getMask(), mTestImsFeature.lastConfig.getMask());
        verify(mCapabilityCallback, never()).onCapabilitiesChanged(eq(c));
    }


    @SmallTest
    @Test
    public void testSetCapabilityConfigError() throws Exception {
        ImsFeature.CapabilityConfiguration c = new ImsFeature.CapabilityConfiguration();
        c.addCapabilities(TestImsFeature.CAPABILITY_TEST_1);

        mTestImsFeature.setCapabilitiesResult = ImsFeature.CAPABILITY_ERROR_GENERIC;
        mTestImsFeature.setCapabilities(c, mCapabilityCallback);

        verify(mCapabilityCallback).onSetCapabilitiesError(eq(ImsFeature.CAPABILITY_ERROR_GENERIC));
        assertFalse(c.equals(mTestImsFeature.queryCapabilities()));
        assertEquals(c.getMask(), mTestImsFeature.lastConfig.getMask());
    }

    @SmallTest
    @Test
    public void testSetCapabilityConfigErrorNoCallback() throws Exception {
        ImsFeature.CapabilityConfiguration c = new ImsFeature.CapabilityConfiguration();
        c.addCapabilities(TestImsFeature.CAPABILITY_TEST_1);

        mTestImsFeature.setCapabilitiesResult = ImsFeature.CAPABILITY_ERROR_GENERIC;
        mTestImsFeature.setCapabilities(c, mCapabilityCallback);

        verify(mCapabilityCallback).onSetCapabilitiesError(eq(ImsFeature.CAPABILITY_ERROR_GENERIC));
        verify(mCapabilityCallback, never()).onCapabilitiesChanged(any());
        assertFalse(c.equals(mTestImsFeature.queryCapabilities()));
        assertEquals(c.getMask(), mTestImsFeature.lastConfig.getMask());
    }

    @SmallTest
    @Test
    public void testNotifyCapabilityStatusChanged() throws Exception {
        ImsFeature.CapabilityConfiguration status =
                new ImsFeature.CapabilityConfiguration();
        status.addCapabilities(TestImsFeature.CAPABILITY_TEST_1);
        status.addCapabilities(TestImsFeature.CAPABILITY_TEST_2);

        mTestImsFeature.capabilitiesStatusChanged(status);

        assertEquals(status.getMask(), mTestImsFeature.queryCapabilityStatus().getMask());
    }

    @SmallTest
    @Test
    public void testNotifyCapabilityStatusChangedCallback() throws Exception {
        ImsFeature.CapabilityConfiguration status =
                new ImsFeature.CapabilityConfiguration();
        status.addCapabilities(TestImsFeature.CAPABILITY_TEST_1);
        status.addCapabilities(TestImsFeature.CAPABILITY_TEST_2);

        mTestImsFeature.capabilitiesStatusChanged(status);

        assertEquals(status.getMask(), mTestImsFeature.queryCapabilityStatus().getMask());
        verify(mCapabilityCallback).onCapabilitiesStatusChanged(eq(status));
    }
}
