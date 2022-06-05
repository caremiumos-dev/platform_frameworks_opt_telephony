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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.os.Process;
import android.os.UserHandle;
import android.service.carrier.CarrierService;
import android.telephony.TelephonyManager;
import android.telephony.TelephonyManager.CarrierPrivilegesCallback;
import android.test.suitebuilder.annotation.SmallTest;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.util.SparseArray;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

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

    private static final int BIND_FLAGS = Context.BIND_AUTO_CREATE | Context.BIND_FOREGROUND_SERVICE
            | Context.BIND_INCLUDE_CAPABILITIES;

    // Mocks Context since TelephonyTest#mContext doesn't support [un]bindService very well
    private Context mContext;

    private CarrierPrivilegesCallback mCarrierPrivilegesCallback;
    private BroadcastReceiver mUserUnlockedReceiver;
    private BroadcastReceiver mPackageChangeReceiver;

    private CarrierServiceBindHelper mCarrierServiceBindHelper;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mContext = mock(Context.class);
        when(mContext.createContextAsUser(notNull(), eq(0))).thenReturn(mContext);
        when(mContext.getSystemServiceName(TelephonyManager.class)).thenReturn(
                Context.TELEPHONY_SERVICE);
        when(mContext.getSystemService(TelephonyManager.class)).thenReturn(mTelephonyManager);
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        when(mContext.createPackageContextAsUser(any(), eq(0), eq(UserHandle.SYSTEM))).thenReturn(
                mContext);
        // By default, Single SIM
        when(mTelephonyManager.getActiveModemCount()).thenReturn(1);
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

        // Verify that CarrierPrivilegesCallbacks are registered on both phones.
        // Capture the callbacks for further verification
        ArgumentCaptor<CarrierPrivilegesCallback> phone0CallbackCaptor = ArgumentCaptor.forClass(
                CarrierPrivilegesCallback.class);
        verify(mTelephonyManager).registerCarrierPrivilegesCallback(eq(PHONE_ID_0), any(),
                phone0CallbackCaptor.capture());
        CarrierPrivilegesCallback phone0Callback = phone0CallbackCaptor.getAllValues().get(0);
        assertNotNull(phone0Callback);

        ArgumentCaptor<CarrierPrivilegesCallback> phone1CallbackCaptor = ArgumentCaptor.forClass(
                CarrierPrivilegesCallback.class);
        verify(mTelephonyManager).registerCarrierPrivilegesCallback(eq(PHONE_ID_1), any(),
                phone1CallbackCaptor.capture());
        CarrierPrivilegesCallback phone1Callback = phone1CallbackCaptor.getAllValues().get(0);
        assertNotNull(phone1Callback);

        // Switch back to single SIM.
        doReturn(1).when(mTelephonyManager).getActiveModemCount();
        PhoneConfigurationManager.notifyMultiSimConfigChange(1);
        processAllMessages();

        // Verify the callback for phone1 had been unregistered while phone0 didn't.
        verify(mTelephonyManager).unregisterCarrierPrivilegesCallback(eq(phone1Callback));
        verify(mTelephonyManager, never()).unregisterCarrierPrivilegesCallback(eq(phone0Callback));
    }

    @Test
    public void testCarrierPackageChanged_carrierPackageIsBack_shouldBind() throws Exception {
        setupTestCarrierServicePackageForPhone(PHONE_ID_0);

        sendCarrierPackageChanged(TEST_CARRIER_PACKAGE, TEST_CARRIER_UID);
        processAllMessages();

        verifyBindService();
    }

    @Test
    public void testCarrierPackageChanged_carrierPackageIsGone_shouldNotBind() throws Exception {
        setupTestCarrierServicePackageForPhone(PHONE_ID_0);

        sendCarrierPackageChanged(null, Process.INVALID_UID);
        processAllMessages();

        verifyNeverBindService();
    }

    @Test
    public void testUserUnlockedBroadcastIntentReceived() throws Exception {
        setupTestCarrierServicePackageForPhone(PHONE_ID_0);

        sendUserUnlockedBroadcastIntent();
        processAllMessages();

        verifyBindService();
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

        mCarrierServiceBindHelper.updateForPhoneId(PHONE_ID_0, SIM_STATE_STRING_LOADED);
        processAllMessages();

        verifyBindService();
    }

    @Test
    public void testSimStateChange_duplicatedStates_shouldNotRebind() {
        setupTestCarrierServicePackageForPhone(PHONE_ID_0);
        setupInitialSimState(PHONE_ID_0, SIM_STATE_STRING_LOADED);

        mCarrierServiceBindHelper.updateForPhoneId(PHONE_ID_0, SIM_STATE_STRING_LOADED);
        processAllMessages();

        verifyNoRebind(PHONE_ID_0);
    }

    @Test
    public void testPackageChanged_carrierPackageAdded_shouldBind() {
        setupTestCarrierServicePackageForPhone(PHONE_ID_0);

        sendTestCarrierPackageAddedIntent();
        processAllMessages();

        verifyBindService();
    }

    @Test
    public void testPackageChanged_carrierPackageRemoved_shouldNotBind() {
        setupTestCarrierServicePackageForPhone(PHONE_ID_0);

        sendTestCarrierPackageRemovedIntent();
        processAllMessages();

        verifyNeverBindService();
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

    private void sendCarrierPackageChanged(String packageName, int uid) {
        mCarrierPrivilegesCallback.onCarrierServiceChanged(packageName, uid);
        when(mTelephonyManager.getCarrierServicePackageNameForLogicalSlot(PHONE_ID_0)).thenReturn(
                packageName);
    }

    private void sendUserUnlockedBroadcastIntent() {
        mUserUnlockedReceiver.onReceive(mContext, new Intent(Intent.ACTION_USER_UNLOCKED));
    }

    private void sendPackageChangedIntent(String action, String packageName) {
        Intent packageIntent = new Intent(action,
                Uri.fromParts("package", packageName, null));
        mPackageChangeReceiver.onReceive(mContext, packageIntent);
    }

    private void sendTestCarrierPackageAddedIntent() {
        sendPackageChangedIntent(Intent.ACTION_PACKAGE_ADDED, TEST_CARRIER_PACKAGE);
    }

    private void sendTestCarrierPackageRemovedIntent() {
        when(mTelephonyManager.getCarrierServicePackageNameForLogicalSlot(PHONE_ID_0)).thenReturn(
                null);
        sendPackageChangedIntent(Intent.ACTION_PACKAGE_REMOVED, TEST_CARRIER_PACKAGE);
    }

    private void setupInitialSimState(int phoneId, String state) {
        SparseArray<String> lastSimState = mCarrierServiceBindHelper.mLastSimState;
        if (phoneId >= lastSimState.size()) return;

        lastSimState.put(phoneId, state);
    }

    private void setupTestCarrierServicePackageForPhone(int phoneId) {
        ResolveInfo resolveInfo = createResolveInfo();
        doReturn(TEST_CARRIER_PACKAGE).when(
                mTelephonyManager).getCarrierServicePackageNameForLogicalSlot(eq(phoneId));
        doReturn(resolveInfo).when(mPackageManager).resolveService(
                any(), eq(PackageManager.GET_META_DATA));

        mCarrierServiceBindHelper = new CarrierServiceBindHelper(mContext);

        ArgumentCaptor<BroadcastReceiver> userUnlockedArgumentCaptor = ArgumentCaptor.forClass(
                BroadcastReceiver.class);
        verify(mContext).registerReceiver(userUnlockedArgumentCaptor.capture(), argThat(argument ->
                argument.hasAction(Intent.ACTION_USER_UNLOCKED)), eq(null), any());
        mUserUnlockedReceiver = userUnlockedArgumentCaptor.getAllValues().get(0);

        ArgumentCaptor<BroadcastReceiver> packageChangedArgumentCaptor = ArgumentCaptor.forClass(
                BroadcastReceiver.class);
        verify(mContext).registerReceiver(packageChangedArgumentCaptor.capture(),
                argThat(argument ->
                        argument.hasAction(Intent.ACTION_PACKAGE_ADDED)), eq(null), any());
        mPackageChangeReceiver = packageChangedArgumentCaptor.getAllValues().get(0);

        ArgumentCaptor<CarrierPrivilegesCallback> callbackCaptor = ArgumentCaptor.forClass(
                CarrierPrivilegesCallback.class);
        verify(mTelephonyManager).registerCarrierPrivilegesCallback(eq(phoneId), any(),
                callbackCaptor.capture());
        mCarrierPrivilegesCallback = callbackCaptor.getAllValues().get(0);
    }

    /**
     * Verify {@link Context#bindService(Intent, ServiceConnection, int)} is called with expected
     * intent and binding flags.
     */
    private void verifyBindService() {
        ArgumentCaptor<Intent> intentArgumentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext).bindService(intentArgumentCaptor.capture(), eq(BIND_FLAGS), any(), any());
        Intent capturedIntent = intentArgumentCaptor.getAllValues().get(0);

        assertNotNull(capturedIntent);
        assertEquals(CarrierService.CARRIER_SERVICE_INTERFACE, capturedIntent.getAction());
        assertEquals(TEST_CARRIER_PACKAGE, capturedIntent.getPackage());
    }

    /**
     * Verify {@link Context#bindService(Intent, ServiceConnection, int)}  is never called.
     */
    private void verifyNeverBindService() {
        verify(mContext, never()).bindService(any(), eq(BIND_FLAGS), any(), any());
    }

    /**
     * Verify CarrierServiceBinderHelper#rebind is never called by checking
     * TM#getCarrierServicePackageNameForLogicalSlot is never invoked.
     */
    private void verifyNoRebind(int phoneId) {
        verify(mTelephonyManager, never()).getCarrierServicePackageNameForLogicalSlot(eq(phoneId));
    }
}
