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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.os.Parcel;
import android.os.RemoteException;
import android.support.test.runner.AndroidJUnit4;
import android.telephony.ServiceState;
import android.telephony.ims.internal.aidl.IImsRegistration;
import android.telephony.ims.internal.feature.ImsFeature;
import android.telephony.ims.internal.stub.ImsRegistrationConfiguration;
import android.telephony.ims.internal.stub.ImsRegistrationImplBase;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.ims.ImsReasonInfo;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class ImsRegistrationTests {

    @Spy private ImsRegistrationImplBase.Callback mCallback;
    private TestImsRegistration mRegistration;
    private IImsRegistration mRegBinder;

    @Before
    public void setup() throws RemoteException {
        MockitoAnnotations.initMocks(this);
        mRegistration = new TestImsRegistration();
        mRegBinder = mRegistration.getBinder();
        mRegBinder.addRegistrationCallback(mCallback);
    }

    @After
    public void tearDown() {
        mRegistration = null;
        mRegBinder = null;
    }

    @SmallTest
    @Test
    public void testQuerySupportedImsFeatures() throws Exception {
        ImsRegistrationConfiguration testConfig = new ImsRegistrationConfiguration(
                new int[] {ImsFeature.MMTEL});
        mRegistration.setConfig(testConfig);

        ImsRegistrationConfiguration c = mRegBinder.querySupportedImsFeatures();

        assertEquals(testConfig, c);
    }

    @SmallTest
    @Test
    public void testRegistrationFeaturesChanged() throws Exception {
        ImsRegistrationConfiguration testConfig = new ImsRegistrationConfiguration(
                new int[] {ImsFeature.MMTEL});

        mRegBinder.registrationFeaturesChanged(testConfig);
        assertEquals(testConfig, mRegistration.getRegistrationConfiguration());
    }

    @SmallTest
    @Test
    public void testRegistrationFeaturesChangedTwiceSquelch() throws Exception {
        ImsRegistrationConfiguration testConfig = new ImsRegistrationConfiguration(
                new int[] {ImsFeature.MMTEL});
        // Make sure we test contents equality
        ImsRegistrationConfiguration testConfig2 = new ImsRegistrationConfiguration(
                new int[] {ImsFeature.MMTEL});

        mRegBinder.registrationFeaturesChanged(testConfig);
        mRegBinder.registrationFeaturesChanged(testConfig2);
        // The registration features hasn't changed, so it should be one.
        assertEquals(1, mRegistration.getRegistrationFeaturesChangedCount());
    }

    @SmallTest
    @Test
    public void testRegistrationFeaturesChangedTwice() throws Exception {
        ImsRegistrationConfiguration testConfig = new ImsRegistrationConfiguration(
                new int[] {ImsFeature.MMTEL});
        // Make sure we test contents equality
        ImsRegistrationConfiguration testConfig2 = new ImsRegistrationConfiguration(
                new int[] {ImsFeature.RCS});

        mRegBinder.registrationFeaturesChanged(testConfig);
        mRegBinder.registrationFeaturesChanged(testConfig2);
        assertEquals(2, mRegistration.getRegistrationFeaturesChangedCount());
        assertEquals(testConfig2, mRegistration.getRegistrationConfiguration());
    }

    @SmallTest
    @Test
    public void testRegistrationConfigParcel() {
        ImsRegistrationConfiguration testConfig = new ImsRegistrationConfiguration.Builder()
                .addFeature(ImsFeature.MMTEL)
                .addFeature(ImsFeature.RCS)
                .addExternalFeature(3)
                .addExternalFeature(4)
                .addCarrierSpecificFeature("Test1")
                .addCarrierSpecificFeature("Test2")
                .build();
        Parcel p = Parcel.obtain();
        testConfig.writeToParcel(p, 0);
        p.setDataPosition(0);
        ImsRegistrationConfiguration result =
                ImsRegistrationConfiguration.CREATOR.createFromParcel(p);
        p.recycle();

        assertEquals(testConfig, result);
    }

    @SmallTest
    @Test
    public void testRegistrationConfigPermutationEqual() {
        ImsRegistrationConfiguration testConfig = new ImsRegistrationConfiguration.Builder()
                .addFeature(ImsFeature.MMTEL)
                .addFeature(ImsFeature.RCS)
                .addExternalFeature(3)
                .addExternalFeature(4)
                .addCarrierSpecificFeature("Test1")
                .addCarrierSpecificFeature("Test2")
                .build();

        // Permute field insertion ordering to ensure order doesn't matter.
        ImsRegistrationConfiguration testConfig2 = new ImsRegistrationConfiguration.Builder()
                .addFeature(ImsFeature.RCS)
                .addFeature(ImsFeature.MMTEL)
                .addExternalFeature(4)
                .addExternalFeature(3)
                .addCarrierSpecificFeature("Test2")
                .addCarrierSpecificFeature("Test1")
                .build();

        assertEquals(testConfig, testConfig2);
    }

    @SmallTest
    @Test
    public void testRegistrationConfigConstructorsEqual() {
        ImsRegistrationConfiguration testConfig = new ImsRegistrationConfiguration(
                new int[] {ImsFeature.MMTEL, ImsFeature.RCS});

        // Permute field insertion ordering to ensure order doesn't matter.
        ImsRegistrationConfiguration testConfig2 = new ImsRegistrationConfiguration.Builder()
                .addFeature(ImsFeature.RCS)
                .addFeature(ImsFeature.MMTEL)
                .build();

        assertEquals(testConfig, testConfig2);
    }

    @SmallTest
    @Test
    public void testRegistrationConfigCarrierFeatures() {
        List<String> testFeatures = new ArrayList<>();
        testFeatures.add("Test1");
        testFeatures.add("Test2");

        ImsRegistrationConfiguration testConfig = new ImsRegistrationConfiguration.Builder()
                .addFeature(ImsFeature.MMTEL)
                .addCarrierSpecificFeature("Test1")
                .addCarrierSpecificFeature("Test2")
                .build();

        String[] resultFeatures = testConfig.getCarrierSpecificRegistrationFeatures();
        List<String> resultFeatureList = new ArrayList<>(Arrays.asList(resultFeatures));
        assertEquals(testFeatures, resultFeatureList);
    }

    @SmallTest
    @Test
    public void testRegistrationConfigCombinedFeatures() {
        List<Integer> testFeatures = new ArrayList<>();
        testFeatures.add(ImsFeature.MMTEL);
        testFeatures.add(ImsFeature.RCS);
        testFeatures.add(1000);
        testFeatures.add(1001);

        ImsRegistrationConfiguration testConfig = new ImsRegistrationConfiguration.Builder()
                .addFeature(ImsFeature.MMTEL)
                .addFeature(ImsFeature.RCS)
                .addExternalFeature(1000)
                .addExternalFeature(1001)
                .build();

        int[] resultFeatures = testConfig.getRegistrationFeatures();
        List<Integer> resultFeatureList = new ArrayList<>(resultFeatures.length);
        for (int i : resultFeatures) {
            resultFeatureList.add(i);
        }

        assertEquals(testFeatures, resultFeatureList);
    }

    @SmallTest
    @Test
    public void testRegistrationCallbackOnRegistrationConnected() throws RemoteException {
        mRegistration.registrationConnected(ServiceState.RIL_RADIO_TECHNOLOGY_LTE);

        verify(mCallback).onRegistrationConnected(ServiceState.RIL_RADIO_TECHNOLOGY_LTE);
    }

    @SmallTest
    @Test
    public void testRegistrationCallbackOnRegistrationProcessing() throws RemoteException {
        mRegistration.registrationProcessing(ServiceState.RIL_RADIO_TECHNOLOGY_LTE);

        verify(mCallback).onRegistrationProcessing(ServiceState.RIL_RADIO_TECHNOLOGY_LTE);
    }

    @SmallTest
    @Test
    public void testRegistrationCallbackOnRegistrationDisconnected() throws RemoteException {
        ImsReasonInfo info = new ImsReasonInfo();
        mRegistration.registrationDisconnected(ServiceState.RIL_RADIO_TECHNOLOGY_LTE, info);

        verify(mCallback).onRegistrationDisconnected(eq(ServiceState.RIL_RADIO_TECHNOLOGY_LTE),
                eq(info));
    }

    @SmallTest
    @Test
    public void testRegistrationCallbackOnRegistrationResumed() throws RemoteException {
        mRegistration.registrationResumed(ServiceState.RIL_RADIO_TECHNOLOGY_LTE);

        verify(mCallback).onRegistrationResumed(ServiceState.RIL_RADIO_TECHNOLOGY_LTE);
    }

    @SmallTest
    @Test
    public void testRegistrationCallbackOnRegistrationSuspended() throws RemoteException {
        mRegistration.registrationSuspended(ServiceState.RIL_RADIO_TECHNOLOGY_LTE);

        verify(mCallback).onRegistrationSuspended(ServiceState.RIL_RADIO_TECHNOLOGY_LTE);
    }

    @SmallTest
    @Test
    public void testRegistrationCallbackOnRegistrationChangeFailed() throws RemoteException {
        ImsReasonInfo info = new ImsReasonInfo();
        mRegistration.changingSupportedImsFeaturesFailed(info);

        verify(mCallback).onChangeImsFeaturesFailed(eq(info));
    }

    @SmallTest
    @Test
    public void testRegistrationCallbackOnRegistrationChange() throws RemoteException {
        ImsRegistrationConfiguration c = new ImsRegistrationConfiguration(
                new int[] {ImsFeature.MMTEL});
        mRegistration.supportedImsFeaturesChanged(c);

        verify(mCallback).changeSupportedImsFeatures(eq(c));
    }

    @SmallTest
    @Test
    public void testRegistrationCallbackAfterUnregistered() throws RemoteException {
        mRegBinder.removeRegistrationCallback(mCallback);

        mRegistration.registrationConnected(ServiceState.RIL_RADIO_TECHNOLOGY_LTE);

        verify(mCallback, never()).onRegistrationConnected(ServiceState.RIL_RADIO_TECHNOLOGY_LTE);
    }

    public void testRegistrationCallbackSendCurrentState() throws RemoteException {
        ImsRegistrationImplBase.Callback mCallback2 = spy(new ImsRegistrationImplBase.Callback());
        mRegistration.registrationConnected(ImsRegistrationImplBase.REGISTRATION_TECH_LTE);

        mRegBinder.addRegistrationCallback(mCallback2);

        verify(mCallback2).onRegistrationConnected(
                eq(ImsRegistrationImplBase.REGISTRATION_TECH_LTE));
    }

    public void testRegistrationCallbackGetRegistrationTech() throws RemoteException {
        mRegistration.registrationConnected(ImsRegistrationImplBase.REGISTRATION_TECH_LTE);

        assertEquals(ImsRegistrationImplBase.REGISTRATION_TECH_LTE,
                mRegBinder.getRegistrationTechnology());
    }

    public void testRegistrationCallbackSendCurrentStateDisconnected() throws RemoteException {
        ImsRegistrationImplBase.Callback mCallback2 = spy(new ImsRegistrationImplBase.Callback());
        ImsReasonInfo info = new ImsReasonInfo(ImsReasonInfo.CODE_LOCAL_NETWORK_NO_LTE_COVERAGE, 0);
        mRegistration.registrationDisconnected(ImsRegistrationImplBase.REGISTRATION_TECH_LTE, info);

        mRegBinder.addRegistrationCallback(mCallback2);

        verify(mCallback2).onRegistrationDisconnected(
                eq(ImsRegistrationImplBase.REGISTRATION_TECH_LTE), eq(info));
    }

    public void testRegistrationCallbackGetRegistrationTechDisconnected() throws RemoteException {
        ImsReasonInfo info = new ImsReasonInfo(ImsReasonInfo.CODE_LOCAL_NETWORK_NO_LTE_COVERAGE, 0);

        mRegistration.registrationDisconnected(ImsRegistrationImplBase.REGISTRATION_TECH_LTE, info);

        verify(mCallback).onRegistrationDisconnected(
                eq(ImsRegistrationImplBase.REGISTRATION_TECH_LTE), eq(info));
        assertEquals(ImsRegistrationImplBase.REGISTRATION_TECH_NONE,
                mRegBinder.getRegistrationTechnology());
    }
}
