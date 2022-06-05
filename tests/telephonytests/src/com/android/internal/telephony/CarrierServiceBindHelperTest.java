/*
 * Copyright (C) 2019 The Android Open Source Project
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Bundle;
import android.os.Message;
import android.os.Process;
import android.telephony.TelephonyManager.CarrierPrivilegesCallback;
import android.test.suitebuilder.annotation.SmallTest;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.util.SparseArray;

import androidx.annotation.NonNull;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.Executor;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class CarrierServiceBindHelperTest extends TelephonyTest {
    private static final int PHONE_ID_0 = 0;
    private static final int PHONE_ID_1 = 1;
    private static final int INVALID_PHONE_ID = 99;

    private static final String TEST_CARRIER_PACKAGE = "com.android.carrier";
    private static final String TEST_CARRIER_SERVICE_CLASS = "carrier_service";
    private static final int TEST_CARRIER_UID = 10086;

    private static final String SIM_STATE_STRING_ABSENT = IccCardConstants.INTENT_VALUE_ICC_ABSENT;
    private static final String SIM_STATE_STRING_LOADED = IccCardConstants.INTENT_VALUE_ICC_LOADED;

    private CarrierServiceBindHelper mCarrierServiceBindHelper;
    private LocalServiceBinding mLocalServiceBinding;

    // A ServiceBinding impl that always succeeds on bind/unbindService attempt
    private class LocalServiceBinding implements CarrierServiceBindHelper.ServiceBinding {
        private boolean mServiceConnected = false;

        @Override
        public boolean bindService(Intent service, int flags, Executor executor,
                @NonNull ServiceConnection conn) {
            mServiceConnected = true;
            return true;
        }

        @Override
        public void unbindService(@NonNull ServiceConnection conn) {
            mServiceConnected = false;
        }

        public boolean isServiceConnected() {
            return mServiceConnected;
        }
    }

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mLocalServiceBinding = new LocalServiceBinding();
        doReturn(1).when(mTelephonyManager).getActiveModemCount();
    }

    @After
    public void tearDown() throws Exception {
        mCarrierServiceBindHelper = null;
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testMultiSimConfigChanged() throws Exception {
        clearInvocations(mPhoneConfigurationManager);
        mCarrierServiceBindHelper = new CarrierServiceBindHelper(mContext);
        assertEquals(1, mCarrierServiceBindHelper.mBindings.size());
        assertEquals(1, mCarrierServiceBindHelper.mLastSimState.size());
        assertNotNull(mCarrierServiceBindHelper.mBindings.get(0));
        assertNotNull(mCarrierServiceBindHelper.mLastSimState.get(0));

        // Verify registration of EVENT_MULTI_SIM_CONFIG_CHANGED.
        doReturn(2).when(mTelephonyManager).getActiveModemCount();
        PhoneConfigurationManager.notifyMultiSimConfigChange(2);
        processAllMessages();

        assertEquals(2, mCarrierServiceBindHelper.mBindings.size());
        assertEquals(2, mCarrierServiceBindHelper.mLastSimState.size());
        assertNotNull(mCarrierServiceBindHelper.mBindings.get(0));
        assertNotNull(mCarrierServiceBindHelper.mBindings.get(1));
        assertNotNull(mCarrierServiceBindHelper.mLastSimState.get(0));
        assertNotNull(mCarrierServiceBindHelper.mLastSimState.get(1));

        // Switch back to single SIM.
        doReturn(1).when(mTelephonyManager).getActiveModemCount();
        PhoneConfigurationManager.notifyMultiSimConfigChange(1);
        processAllMessages();

        assertEquals(1, mCarrierServiceBindHelper.mBindings.size());
        assertEquals(1, mCarrierServiceBindHelper.mLastSimState.size());
        assertNotNull(mCarrierServiceBindHelper.mBindings.get(0));
        assertNotNull(mCarrierServiceBindHelper.mLastSimState.get(0));
    }

    @Test
    public void testUnbindWhenNotBound() throws Exception {
        mCarrierServiceBindHelper = new CarrierServiceBindHelper(mContext);

        // Try unbinding without binding and make sure we don't throw an Exception
        mCarrierServiceBindHelper.mHandler.handleMessage(
                Message.obtain(mCarrierServiceBindHelper.mHandler,
                        CarrierServiceBindHelper.EVENT_PERFORM_IMMEDIATE_UNBIND,
                        new Integer(0)));
    }

    @Test
    public void testCarrierPrivilegesCallbackRegistration() {
        // Device starts with DSDS mode
        doReturn(2).when(mTelephonyManager).getActiveModemCount();
        mCarrierServiceBindHelper = new CarrierServiceBindHelper(mContext);
        processAllMessages();

        // Verify that CarrierPrivilegesCallbacks are registered on both phones
        // Capture the callback for phone1 for further verification
        verify(mTelephonyManager).registerCarrierPrivilegesCallback(eq(PHONE_ID_0), any(), any());
        ArgumentCaptor<CarrierPrivilegesCallback> phone1CallbackCaptor = ArgumentCaptor.forClass(
                CarrierPrivilegesCallback.class);
        verify(mTelephonyManager).registerCarrierPrivilegesCallback(eq(PHONE_ID_1), any(),
                phone1CallbackCaptor.capture());
        CarrierPrivilegesCallback callback = phone1CallbackCaptor.getAllValues().get(0);
        assertNotNull(callback);

        // Switch back to single SIM.
        doReturn(1).when(mTelephonyManager).getActiveModemCount();
        PhoneConfigurationManager.notifyMultiSimConfigChange(1);
        processAllMessages();

        // Verify the callback for phone1 has been unregistered.
        verify(mTelephonyManager).unregisterCarrierPrivilegesCallback(eq(callback));
    }

    @Test
    public void testCarrierPackageChanged_carrierPackageIsBack_shouldConnected() throws Exception {
        setupTestCarrierServicePackageForPhone(PHONE_ID_0);
        CarrierPrivilegesCallback carrierPrivilegesCallback = getCarrierPrivilegesCallbackForPhone(
                PHONE_ID_0);
        assertFalse(mLocalServiceBinding.mServiceConnected);

        carrierPrivilegesCallback.onCarrierServiceChanged(TEST_CARRIER_PACKAGE, TEST_CARRIER_UID);
        processAllMessages();

        assertTrue(mLocalServiceBinding.mServiceConnected);
    }

    @Test
    public void testCarrierPackageChanged_carrierPackageIsGone_shouldNotConnected()
            throws Exception {
        setupTestCarrierServicePackageForPhone(PHONE_ID_0);
        CarrierPrivilegesCallback carrierPrivilegesCallback = getCarrierPrivilegesCallbackForPhone(
                PHONE_ID_0);
        assertFalse(mLocalServiceBinding.mServiceConnected);

        carrierPrivilegesCallback.onCarrierServiceChanged(null, Process.INVALID_UID);
        doReturn(null).when(
                mTelephonyManager).getCarrierServicePackageNameForLogicalSlot(eq(PHONE_ID_0));
        processAllMessages();

        assertFalse(mLocalServiceBinding.mServiceConnected);
    }

    @Test
    public void testUserUnlockedBroadcastIntentReceived() throws Exception {
        setupTestCarrierServicePackageForPhone(PHONE_ID_0);
        assertFalse(mLocalServiceBinding.mServiceConnected);

        sendUserUnlockedBroadcastIntent();
        processAllMessages();

        assertTrue(mLocalServiceBinding.mServiceConnected);
    }

    @Test
    public void testUpdateSimState_withInvalidPhoneId_shouldNotRebind() {
        setupTestCarrierServicePackageForPhone(PHONE_ID_0);
        setupInitialSimState(PHONE_ID_0, SIM_STATE_STRING_ABSENT);

        mCarrierServiceBindHelper.updateForPhoneId(INVALID_PHONE_ID, SIM_STATE_STRING_LOADED);
        processAllMessages();

        verifyNoRebind(PHONE_ID_0);
    }

    @Test
    public void testUpdateSimState_simLoaded_shouldRebind() {
        setupTestCarrierServicePackageForPhone(PHONE_ID_0);
        setupInitialSimState(PHONE_ID_0, SIM_STATE_STRING_ABSENT);
        assertFalse(mLocalServiceBinding.mServiceConnected);

        mCarrierServiceBindHelper.updateForPhoneId(PHONE_ID_0, SIM_STATE_STRING_LOADED);
        processAllMessages();

        assertTrue(mLocalServiceBinding.mServiceConnected);
    }

    @Test
    public void testSimStateChange_duplicatedStates_shouldNotRebind() {
        setupTestCarrierServicePackageForPhone(PHONE_ID_0);
        setupInitialSimState(PHONE_ID_0, SIM_STATE_STRING_LOADED);

        mCarrierServiceBindHelper.updateForPhoneId(PHONE_ID_0, SIM_STATE_STRING_LOADED);
        processAllMessages();

        verifyNoRebind(PHONE_ID_0);
    }

    private ResolveInfo createResolveInfo() {
        Bundle metaData = new Bundle();
        metaData.putBoolean("android.service.carrier.LONG_LIVED_BINDING", true);

        ResolveInfo r = new ResolveInfo();
        r.serviceInfo = new ServiceInfo();
        r.serviceInfo.metaData = metaData;
        r.serviceInfo.packageName = TEST_CARRIER_PACKAGE;
        r.serviceInfo.name = TEST_CARRIER_SERVICE_CLASS;
        return r;
    }

    private void sendUserUnlockedBroadcastIntent() {
        mContext.sendBroadcast(new Intent(Intent.ACTION_USER_UNLOCKED));
    }

    private void setupInitialSimState(int phoneId, String state) {
        SparseArray<String> lastSimState = mCarrierServiceBindHelper.mLastSimState;
        if (phoneId >= lastSimState.size()) return;

        lastSimState.put(phoneId, state);
    }

    /**
     * Verify rebind() is not called by checking TM#getCarrierServicePackageNameForLogicalSlot is
     * never invoked.
     */
    private void verifyNoRebind(int phoneId) {
        verify(mTelephonyManager, never()).getCarrierServicePackageNameForLogicalSlot(eq(phoneId));
    }

    private void setupTestCarrierServicePackageForPhone(int phoneId) {
        ResolveInfo resolveInfo = createResolveInfo();
        doReturn(TEST_CARRIER_PACKAGE).when(
                mTelephonyManager).getCarrierServicePackageNameForLogicalSlot(eq(phoneId));
        doReturn(resolveInfo).when(mPackageManager).resolveService(
                any(), eq(PackageManager.GET_META_DATA));

        mCarrierServiceBindHelper = new CarrierServiceBindHelper(mContext);
        mCarrierServiceBindHelper.setServiceBinding(mLocalServiceBinding);

    }

    private CarrierPrivilegesCallback getCarrierPrivilegesCallbackForPhone(int phoneId) {
        ArgumentCaptor<CarrierPrivilegesCallback> callbackCaptor
                = ArgumentCaptor.forClass(CarrierPrivilegesCallback.class);
        verify(mTelephonyManager).registerCarrierPrivilegesCallback(eq(phoneId), any(),
                callbackCaptor.capture());
        CarrierPrivilegesCallback carrierPrivilegesCallback = callbackCaptor.getAllValues().get(0);
        assertNotNull(carrierPrivilegesCallback);
        return carrierPrivilegesCallback;
    }
}
