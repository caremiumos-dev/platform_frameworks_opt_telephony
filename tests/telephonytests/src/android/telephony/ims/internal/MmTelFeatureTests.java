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

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.os.RemoteException;
import android.support.test.runner.AndroidJUnit4;
import android.telephony.ims.internal.aidl.IImsMmTelFeature;
import android.telephony.ims.internal.feature.ImsFeature;
import android.telephony.ims.internal.feature.MmTelFeature;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.ims.internal.IImsCallSession;
import com.android.ims.internal.ImsCallSession;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

@RunWith(AndroidJUnit4.class)
public class MmTelFeatureTests {

    private TestMmTelFeature mFeature;
    private IImsMmTelFeature mFeatureBinder;
    private ImsFeature.CapabilityCallback mCapabilityCallback;
    private MmTelFeature.Listener mListener;

    @Before
    public void setup() throws RemoteException {
        mFeature = new TestMmTelFeature();
        mFeatureBinder = mFeature.getBinder();
        mCapabilityCallback = spy(new ImsFeature.CapabilityCallback());
        mFeatureBinder.addCapabilityCallback(mCapabilityCallback);
        mListener = spy(new MmTelFeature.Listener());
        mFeatureBinder.setListener(mListener);
    }

    @After
    public void tearDown() {
        mFeature = null;
        mFeatureBinder = null;
        mCapabilityCallback = null;
    }

    @SmallTest
    @Test
    public void testAddCapabilityConfig() throws Exception {
        MmTelFeature.MmTelCapabilityConfiguration c =
                new MmTelFeature.MmTelCapabilityConfiguration();
        c.addCapabilities(MmTelFeature.MmTelCapabilityConfiguration.CAPABILITY_TYPE_VOICE);

        mFeatureBinder.setCapabilities(c.getMask(), null);
        // keep this config in sync with the MmTelFeature
        c.addCapabilities(
                MmTelFeature.MmTelCapabilityConfiguration.CAPABILITY_TYPE_VIDEO);
        mFeatureBinder.addCapability(
                MmTelFeature.MmTelCapabilityConfiguration.CAPABILITY_TYPE_VIDEO,
                mCapabilityCallback);

        assertEquals(c.getMask(), mFeatureBinder.queryCapabilities());
        assertEquals(c.getMask(), mFeature.lastConfig.getMask());
        verify(mCapabilityCallback, never()).onSetCapabilitiesError(anyInt());
    }

    @SmallTest
    @Test
    public void testAddCapabilityConfigNotifyError() throws Exception {
        MmTelFeature.MmTelCapabilityConfiguration config =
                new MmTelFeature.MmTelCapabilityConfiguration();
        config.addCapabilities(
                MmTelFeature.MmTelCapabilityConfiguration.CAPABILITY_TYPE_VOICE);
        MmTelFeature.MmTelCapabilityConfiguration configError =
                new MmTelFeature.MmTelCapabilityConfiguration(config);
        configError.addCapabilities(
                MmTelFeature.MmTelCapabilityConfiguration.CAPABILITY_TYPE_VIDEO);

        mFeatureBinder.setCapabilities(config.getMask(), null);
        mFeature.setCapabilitiesResult = ImsFeature.CAPABILITY_ERROR_GENERIC;
        mFeatureBinder.addCapability(
                MmTelFeature.MmTelCapabilityConfiguration.CAPABILITY_TYPE_VIDEO,
                mCapabilityCallback);

        assertEquals(config.getMask(), mFeatureBinder.queryCapabilities());
        assertEquals(configError.getMask(), mFeature.lastConfig.getMask());
        verify(mCapabilityCallback).onSetCapabilitiesError(ImsFeature.CAPABILITY_ERROR_GENERIC);
    }

    @SmallTest
    @Test
    public void testRemoveCapabilityConfig() throws Exception {
        MmTelFeature.MmTelCapabilityConfiguration c =
                new MmTelFeature.MmTelCapabilityConfiguration();
        c.addCapabilities(MmTelFeature.MmTelCapabilityConfiguration.CAPABILITY_TYPE_VOICE);
        c.addCapabilities(MmTelFeature.MmTelCapabilityConfiguration.CAPABILITY_TYPE_VIDEO);

        mFeatureBinder.setCapabilities(c.getMask(), null);
        // keep this config in sync with the MmTelFeature
        c.removeCapabilities(
                MmTelFeature.MmTelCapabilityConfiguration.CAPABILITY_TYPE_VOICE);
        mFeatureBinder.removeCapability(
                MmTelFeature.MmTelCapabilityConfiguration.CAPABILITY_TYPE_VOICE,
                mCapabilityCallback);

        assertEquals(c.getMask(), mFeatureBinder.queryCapabilities());
        assertEquals(c.getMask(), mFeature.lastConfig.getMask());
        verify(mCapabilityCallback, never()).onSetCapabilitiesError(anyInt());
    }

    @SmallTest
    @Test
    public void testRemoveCapabilityConfigNotifyError() throws Exception {
        MmTelFeature.MmTelCapabilityConfiguration config =
                new MmTelFeature.MmTelCapabilityConfiguration();
        config.addCapabilities(
                MmTelFeature.MmTelCapabilityConfiguration.CAPABILITY_TYPE_VOICE);
        config.addCapabilities(
                MmTelFeature.MmTelCapabilityConfiguration.CAPABILITY_TYPE_VIDEO);
        MmTelFeature.MmTelCapabilityConfiguration configError =
                new MmTelFeature.MmTelCapabilityConfiguration(config);
        configError.removeCapabilities(
                MmTelFeature.MmTelCapabilityConfiguration.CAPABILITY_TYPE_VOICE);

        mFeatureBinder.setCapabilities(config.getMask(), null);
        mFeature.setCapabilitiesResult = ImsFeature.CAPABILITY_ERROR_GENERIC;
        mFeatureBinder.removeCapability(
                MmTelFeature.MmTelCapabilityConfiguration.CAPABILITY_TYPE_VOICE,
                mCapabilityCallback);

        assertEquals(config.getMask(), mFeatureBinder.queryCapabilities());
        assertEquals(configError.getMask(), mFeature.lastConfig.getMask());
        verify(mCapabilityCallback).onSetCapabilitiesError(ImsFeature.CAPABILITY_ERROR_GENERIC);
    }

    @SmallTest
    @Test
    public void testNewIncomingCall() throws Exception {
        IImsCallSession sessionBinder = Mockito.mock(IImsCallSession.class);
        ImsCallSession session = new ImsCallSession(sessionBinder);

        mFeature.incomingCall(session);
        ArgumentCaptor<ImsCallSession> captor = ArgumentCaptor.forClass(ImsCallSession.class);
        verify(mListener).onIncomingCall(captor.capture());

        assertEquals(sessionBinder, captor.getValue().getSession());
    }
}
