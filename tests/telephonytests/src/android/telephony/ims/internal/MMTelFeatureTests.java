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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.os.RemoteException;
import android.support.test.runner.AndroidJUnit4;
import android.telephony.ims.internal.aidl.IImsMMTelFeature;
import android.telephony.ims.internal.feature.ImsFeature;
import android.telephony.ims.internal.feature.MMTelFeature;
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
public class MMTelFeatureTests {

    private TestMMTelFeature mFeature;
    private IImsMMTelFeature mFeatureBinder;
    private ImsFeature.CapabilityCallback mCapabilityCallback;
    private MMTelFeature.Listener mListener;

    @Before
    public void setup() throws RemoteException {
        mFeature = new TestMMTelFeature();
        mFeatureBinder = mFeature.getBinder();
        mCapabilityCallback = Mockito.spy(new ImsFeature.CapabilityCallback());
        mFeatureBinder.addCapabilityCallback(mCapabilityCallback);
        mListener = Mockito.spy(new MMTelFeature.Listener());
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
    public void testSetCapabilityConfig() throws Exception {
        MMTelFeature.MMTelCapabilityConfiguration c =
                new MMTelFeature.MMTelCapabilityConfiguration();
        c.addCapabilities(MMTelFeature.MMTelCapabilityConfiguration.CAPABILITY_TYPE_VOICE_OVER_LTE);

        mFeatureBinder.setCapabilities(c.getMask(), null);

        assertEquals(c.getMask(), mFeatureBinder.queryCapabilities());
        assertEquals(c.getMask(), mFeature.lastConfig.getMask());
    }

    @SmallTest
    @Test
    public void testSetCapabilityConfigNotify() throws Exception {
        MMTelFeature.MMTelCapabilityConfiguration c =
                new MMTelFeature.MMTelCapabilityConfiguration();
        c.addCapabilities(MMTelFeature.MMTelCapabilityConfiguration.CAPABILITY_TYPE_VOICE_OVER_LTE);

        mFeatureBinder.setCapabilities(c.getMask(), null);

        assertEquals(c.getMask(), mFeatureBinder.queryCapabilities());
        assertEquals(c.getMask(), mFeature.lastConfig.getMask());
        verify(mCapabilityCallback).onCapabilitiesChanged(eq(c));
    }

    @SmallTest
    @Test
    public void testSetCapabilityConfigNoNotify() throws Exception {
        MMTelFeature.MMTelCapabilityConfiguration c =
                new MMTelFeature.MMTelCapabilityConfiguration();
        c.addCapabilities(MMTelFeature.MMTelCapabilityConfiguration.CAPABILITY_TYPE_VOICE_OVER_LTE);

        mFeatureBinder.removeCapabilityCallback(mCapabilityCallback);
        mFeatureBinder.setCapabilities(c.getMask(), null);

        assertEquals(c.getMask(), mFeatureBinder.queryCapabilities());
        assertEquals(c.getMask(), mFeature.lastConfig.getMask());
        verify(mCapabilityCallback, never()).onCapabilitiesChanged(eq(c));
    }


    @SmallTest
    @Test
    public void testSetCapabilityConfigError() throws Exception {
        MMTelFeature.MMTelCapabilityConfiguration c =
                new MMTelFeature.MMTelCapabilityConfiguration();
        c.addCapabilities(MMTelFeature.MMTelCapabilityConfiguration.CAPABILITY_TYPE_VOICE_OVER_LTE);

        mFeature.setCapabilitiesResult = ImsFeature.CAPABILITY_ERROR_GENERIC;
        mFeatureBinder.setCapabilities(c.getMask(), mCapabilityCallback);

        verify(mCapabilityCallback).onSetCapabilitiesError(eq(ImsFeature.CAPABILITY_ERROR_GENERIC));
        assertFalse(c.getMask() == mFeatureBinder.queryCapabilities());
        assertEquals(c.getMask(), mFeature.lastConfig.getMask());
    }

    @SmallTest
    @Test
    public void testSetCapabilityConfigErrorNoCallback() throws Exception {
        MMTelFeature.MMTelCapabilityConfiguration c =
                new MMTelFeature.MMTelCapabilityConfiguration();
        c.addCapabilities(MMTelFeature.MMTelCapabilityConfiguration.CAPABILITY_TYPE_VOICE_OVER_LTE);

        mFeature.setCapabilitiesResult = ImsFeature.CAPABILITY_ERROR_GENERIC;
        mFeatureBinder.setCapabilities(c.getMask(), mCapabilityCallback);

        verify(mCapabilityCallback).onSetCapabilitiesError(eq(ImsFeature.CAPABILITY_ERROR_GENERIC));
        verify(mCapabilityCallback, never()).onCapabilitiesChanged(any());
        assertFalse(c.getMask() == mFeatureBinder.queryCapabilities());
        assertEquals(c.getMask(), mFeature.lastConfig.getMask());
    }

    @SmallTest
    @Test
    public void testAddCapabilityConfig() throws Exception {
        MMTelFeature.MMTelCapabilityConfiguration c =
                new MMTelFeature.MMTelCapabilityConfiguration();
        c.addCapabilities(MMTelFeature.MMTelCapabilityConfiguration.CAPABILITY_TYPE_VOICE_OVER_LTE);

        mFeatureBinder.setCapabilities(c.getMask(), null);
        // keep this config in sync with the MMTelFeature
        c.addCapabilities(
                MMTelFeature.MMTelCapabilityConfiguration.CAPABILITY_TYPE_VOICE_OVER_WIFI);
        mFeatureBinder.addCapability(
                MMTelFeature.MMTelCapabilityConfiguration.CAPABILITY_TYPE_VOICE_OVER_WIFI,
                mCapabilityCallback);

        assertEquals(c.getMask(), mFeatureBinder.queryCapabilities());
        assertEquals(c.getMask(), mFeature.lastConfig.getMask());
        verify(mCapabilityCallback, never()).onSetCapabilitiesError(anyInt());
    }

    @SmallTest
    @Test
    public void testAddCapabilityConfigNotifyError() throws Exception {
        MMTelFeature.MMTelCapabilityConfiguration config =
                new MMTelFeature.MMTelCapabilityConfiguration();
        config.addCapabilities(
                MMTelFeature.MMTelCapabilityConfiguration.CAPABILITY_TYPE_VOICE_OVER_LTE);
        MMTelFeature.MMTelCapabilityConfiguration configError =
                new MMTelFeature.MMTelCapabilityConfiguration(config);
        configError.addCapabilities(
                MMTelFeature.MMTelCapabilityConfiguration.CAPABILITY_TYPE_VOICE_OVER_WIFI);

        mFeatureBinder.setCapabilities(config.getMask(), null);
        mFeature.setCapabilitiesResult = ImsFeature.CAPABILITY_ERROR_GENERIC;
        mFeatureBinder.addCapability(
                MMTelFeature.MMTelCapabilityConfiguration.CAPABILITY_TYPE_VOICE_OVER_WIFI,
                mCapabilityCallback);

        assertEquals(config.getMask(), mFeatureBinder.queryCapabilities());
        assertEquals(configError.getMask(), mFeature.lastConfig.getMask());
        verify(mCapabilityCallback).onSetCapabilitiesError(ImsFeature.CAPABILITY_ERROR_GENERIC);
    }

    @SmallTest
    @Test
    public void testRemoveCapabilityConfig() throws Exception {
        MMTelFeature.MMTelCapabilityConfiguration c =
                new MMTelFeature.MMTelCapabilityConfiguration();
        c.addCapabilities(MMTelFeature.MMTelCapabilityConfiguration.CAPABILITY_TYPE_VOICE_OVER_LTE);
        c.addCapabilities(
                MMTelFeature.MMTelCapabilityConfiguration.CAPABILITY_TYPE_VOICE_OVER_WIFI);

        mFeatureBinder.setCapabilities(c.getMask(), null);
        // keep this config in sync with the MMTelFeature
        c.removeCapabilities(
                MMTelFeature.MMTelCapabilityConfiguration.CAPABILITY_TYPE_VOICE_OVER_LTE);
        mFeatureBinder.removeCapability(
                MMTelFeature.MMTelCapabilityConfiguration.CAPABILITY_TYPE_VOICE_OVER_LTE,
                mCapabilityCallback);

        assertEquals(c.getMask(), mFeatureBinder.queryCapabilities());
        assertEquals(c.getMask(), mFeature.lastConfig.getMask());
        verify(mCapabilityCallback, never()).onSetCapabilitiesError(anyInt());
    }

    @SmallTest
    @Test
    public void testRemoveCapabilityConfigNotifyError() throws Exception {
        MMTelFeature.MMTelCapabilityConfiguration config =
                new MMTelFeature.MMTelCapabilityConfiguration();
        config.addCapabilities(
                MMTelFeature.MMTelCapabilityConfiguration.CAPABILITY_TYPE_VOICE_OVER_LTE);
        config.addCapabilities(
                MMTelFeature.MMTelCapabilityConfiguration.CAPABILITY_TYPE_VOICE_OVER_WIFI);
        MMTelFeature.MMTelCapabilityConfiguration configError =
                new MMTelFeature.MMTelCapabilityConfiguration(config);
        configError.removeCapabilities(
                MMTelFeature.MMTelCapabilityConfiguration.CAPABILITY_TYPE_VOICE_OVER_LTE);

        mFeatureBinder.setCapabilities(config.getMask(), null);
        mFeature.setCapabilitiesResult = ImsFeature.CAPABILITY_ERROR_GENERIC;
        mFeatureBinder.removeCapability(
                MMTelFeature.MMTelCapabilityConfiguration.CAPABILITY_TYPE_VOICE_OVER_LTE,
                mCapabilityCallback);

        assertEquals(config.getMask(), mFeatureBinder.queryCapabilities());
        assertEquals(configError.getMask(), mFeature.lastConfig.getMask());
        verify(mCapabilityCallback).onSetCapabilitiesError(ImsFeature.CAPABILITY_ERROR_GENERIC);
    }

    @SmallTest
    @Test
    public void testNotifyCapabilityStatusChanged() throws Exception {
        MMTelFeature.MMTelCapabilityConfiguration status =
                new MMTelFeature.MMTelCapabilityConfiguration();
        status.addCapabilities(
                MMTelFeature.MMTelCapabilityConfiguration.CAPABILITY_TYPE_VOICE_OVER_LTE);
        status.addCapabilities(
                        MMTelFeature.MMTelCapabilityConfiguration.CAPABILITY_TYPE_VOICE_OVER_WIFI);

        mFeature.capabilitiesStatusChanged(status);

        assertEquals(status.getMask(), mFeatureBinder.queryCapabilityStatus());
    }

    @SmallTest
    @Test
    public void testNotifyCapabilityStatusChangedCallback() throws Exception {
        MMTelFeature.MMTelCapabilityConfiguration status =
                new MMTelFeature.MMTelCapabilityConfiguration();
        status.addCapabilities(
                MMTelFeature.MMTelCapabilityConfiguration.CAPABILITY_TYPE_VOICE_OVER_LTE);
        status.addCapabilities(
                MMTelFeature.MMTelCapabilityConfiguration.CAPABILITY_TYPE_VOICE_OVER_WIFI);

        mFeature.capabilitiesStatusChanged(status);

        assertEquals(status.getMask(), mFeatureBinder.queryCapabilityStatus());
        verify(mCapabilityCallback).onCapabilitiesStatusChanged(eq(status));
    }

    public void testNewIncomingCall() throws Exception {
        IImsCallSession sessionBinder = Mockito.mock(IImsCallSession.class);
        ImsCallSession session = new ImsCallSession(sessionBinder);

        mFeature.incomingCall(session);
        ArgumentCaptor<ImsCallSession> captor = ArgumentCaptor.forClass(ImsCallSession.class);
        verify(mListener).onIncomingCall(captor.capture());

        assertEquals(sessionBinder, captor.getValue().getSession());
    }
}
