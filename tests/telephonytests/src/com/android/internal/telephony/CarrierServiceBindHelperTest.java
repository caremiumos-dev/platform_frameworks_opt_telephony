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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
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

import java.util.Set;
import java.util.concurrent.Executor;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class CarrierServiceBindHelperTest extends TelephonyTest {
    private static final int PHONE_ID_0 = 0;
    private static final int PHONE_ID_1 = 1;
    private static final int INVALID_PHONE_ID = 99;

    private static final String TEST_CARRIER_PACKAGE = "com.android.carrier";
    private static final String TEST_CARRIER_SERVICE_CLASS = "carrier_service";
    private static final ComponentName TEST_CARRIER_COMPONENT_NAME = new ComponentName(
            TEST_CARRIER_PACKAGE, TEST_CARRIER_SERVICE_CLASS);
    private static final int TEST_CARRIER_UID = 10086;

    private static final String SIM_STATE_STRING_ABSENT = IccCardConstants.INTENT_VALUE_ICC_ABSENT;
    private static final String SIM_STATE_STRING_LOADED = IccCardConstants.INTENT_VALUE_ICC_LOADED;

    private static final int BIND_FLAGS =
            Context.BIND_AUTO_CREATE
                    | Context.BIND_FOREGROUND_SERVICE
                    | Context.BIND_INCLUDE_CAPABILITIES;

    // Mocks Context to better support [un]bindService
    private Context mMockedContext;

    private CarrierPrivilegesCallback mCarrierPrivilegesCallback;
    private BroadcastReceiver mUserUnlockedReceiver;
    private BroadcastReceiver mPackageChangeReceiver;
    private ServiceConnection mServiceConnection;

    private CarrierServiceBindHelper mCarrierServiceBindHelper;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mMockedContext = mock(Context.class);

        when(mMockedContext.createContextAsUser(notNull(), eq(0))).thenReturn(mMockedContext);
        when(mMockedContext.getSystemServiceName(TelephonyManager.class)).thenReturn(
                Context.TELEPHONY_SERVICE);
        when(mMockedContext.getSystemService(TelephonyManager.class)).thenReturn(mTelephonyManager);
        when(mMockedContext.getPackageManager()).thenReturn(mPackageManager);
        when(mMockedContext.createPackageContextAsUser(any(), eq(0),
                eq(UserHandle.SYSTEM))).thenReturn(mMockedContext);
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
        mCarrierServiceBindHelper = new CarrierServiceBindHelper(mMockedContext);
        assertEquals(1, mCarrierServiceBindHelper.mBindings.size());
        assertEquals(1, mCarrierServiceBindHelper.mLastSimState.size());
        assertNotNull(mCarrierServiceBindHelper.mBindings.get(0));
        assertNotNull(mCarrierServiceBindHelper.mLastSimState.get(0));

        // Verify registration of EVENT_MULTI_SIM_CONFIG_CHANGED.
        when(mTelephonyManager.getActiveModemCount()).thenReturn(2);
        PhoneConfigurationManager.notifyMultiSimConfigChange(2);
        processAllMessages();

        assertEquals(2, mCarrierServiceBindHelper.mBindings.size());
        assertEquals(2, mCarrierServiceBindHelper.mLastSimState.size());
        assertNotNull(mCarrierServiceBindHelper.mBindings.get(0));
        assertNotNull(mCarrierServiceBindHelper.mBindings.get(1));
        assertNotNull(mCarrierServiceBindHelper.mLastSimState.get(0));
        assertNotNull(mCarrierServiceBindHelper.mLastSimState.get(1));

        // Switch back to single SIM.
        when(mTelephonyManager.getActiveModemCount()).thenReturn(1);
        PhoneConfigurationManager.notifyMultiSimConfigChange(1);
        processAllMessages();

        assertEquals(1, mCarrierServiceBindHelper.mBindings.size());
        assertEquals(1, mCarrierServiceBindHelper.mLastSimState.size());
        assertNotNull(mCarrierServiceBindHelper.mBindings.get(0));
        assertNotNull(mCarrierServiceBindHelper.mLastSimState.get(0));
    }

    @Test
    public void testUnbindWhenNotBound() throws Exception {
        mCarrierServiceBindHelper = new CarrierServiceBindHelper(mMockedContext);

        // Try unbinding without binding and make sure we don't throw an Exception
        mCarrierServiceBindHelper.mHandler.handleMessage(
                Message.obtain(mCarrierServiceBindHelper.mHandler,
                        CarrierServiceBindHelper.EVENT_PERFORM_IMMEDIATE_UNBIND,
                        new Integer(0)));
    }

    @Test
    public void testCarrierPrivilegesCallbackRegistration() {
        // Device starts with DSDS mode
        when(mTelephonyManager.getActiveModemCount()).thenReturn(2);
        mCarrierServiceBindHelper = new CarrierServiceBindHelper(mMockedContext);
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
        when(mTelephonyManager.getActiveModemCount()).thenReturn(1);
        PhoneConfigurationManager.notifyMultiSimConfigChange(1);
        processAllMessages();

        // Verify the callback for phone1 had been unregistered while phone0 didn't.
        verify(mTelephonyManager).unregisterCarrierPrivilegesCallback(eq(phone1Callback));
        verify(mTelephonyManager, never()).unregisterCarrierPrivilegesCallback(eq(phone0Callback));
    }

    @Test
    public void testCarrierPackageChanged_carrierPackageIsGone_shouldNotBind() throws Exception {
        setupTestCarrierServicePackageForPhone(PHONE_ID_0);

        sendCarrierPackageChanged(null, Process.INVALID_UID);
        processAllMessages();

        verifyNoBindingConnection(PHONE_ID_0);
    }

    @Test
    public void testCarrierPackageChanged_carrierPackageIsBack_shouldBeConnected()
            throws Exception {
        setupTestCarrierServicePackageForPhone(PHONE_ID_0);

        sendCarrierPackageChanged(TEST_CARRIER_PACKAGE, TEST_CARRIER_UID);
        processAllMessages();

        verifyBindServiceForNumberOfTimes(1 /* numOfTimes */);

        triggerCarrierServiceConnectionConnected();
        processAllMessages();

        verifyBindingConnectionConnected(PHONE_ID_0);
    }

    @Test
    public void testCarrierServiceChange_carrierPrivilegesChanged_shouldNotBind() throws Exception {
        setupTestCarrierServicePackageForPhone(PHONE_ID_0);

        sendCarrierPrivilegesChanged(Set.of(TEST_CARRIER_PACKAGE), Set.of(TEST_CARRIER_UID));
        processAllMessages();

        verifyNoBindingConnection(PHONE_ID_0);
    }

    @Test
    public void testBroadcastReceiver_userUnlockedReceived_shouldBeConnected() throws Exception {
        setupTestCarrierServicePackageForPhone(PHONE_ID_0);

        sendUserUnlockedBroadcastIntent();
        processAllMessages();

        verifyBindServiceForNumberOfTimes(1 /* numOfTimes */);

        triggerCarrierServiceConnectionConnected();
        processAllMessages();

        verifyBindingConnectionConnected(PHONE_ID_0);
    }

    @Test
    public void testBroadcastReceiver_unrelatedIntentReceived_shouldNotBind() throws Exception {
        setupTestCarrierServicePackageForPhone(PHONE_ID_0);

        sendUserInfoChangedBroadcastIntent();
        processAllMessages();

        verifyNoBindingConnection(PHONE_ID_0);
    }

    @Test
    public void testUpdateSimState_withInvalidPhoneId_shouldNotRebind() {
        setupTestCarrierServicePackageForPhone(PHONE_ID_0);
        setupInitialSimState(PHONE_ID_0, SIM_STATE_STRING_ABSENT);

        mCarrierServiceBindHelper.updateForPhoneId(INVALID_PHONE_ID, SIM_STATE_STRING_LOADED);
        processAllMessages();

        verifyNoBindingConnection(PHONE_ID_0);
    }

    @Test
    public void testUpdateSimState_simLoaded_shouldRebind() {
        setupTestCarrierServicePackageForPhone(PHONE_ID_0);
        setupInitialSimState(PHONE_ID_0, SIM_STATE_STRING_ABSENT);

        mCarrierServiceBindHelper.updateForPhoneId(PHONE_ID_0, SIM_STATE_STRING_LOADED);
        processAllMessages();

        verifyBindServiceForNumberOfTimes(1 /* numOfTimes */);

        triggerCarrierServiceConnectionConnected();
        processAllMessages();

        verifyBindingConnectionConnected(PHONE_ID_0);
    }

    @Test
    public void testSimStateChange_duplicatedStates_shouldNotRebind() {
        setupTestCarrierServicePackageForPhone(PHONE_ID_0);
        setupInitialSimState(PHONE_ID_0, SIM_STATE_STRING_LOADED);

        mCarrierServiceBindHelper.updateForPhoneId(PHONE_ID_0, SIM_STATE_STRING_LOADED);
        processAllMessages();

        verifyNoBindingConnection(PHONE_ID_0);
    }

    // TODO(b/201423849): Test other invalid SIM states when implementation is ready
    @Test
    public void testSimStateChange_invalidSimStateReceived_shouldNotRebind() {
        setupTestCarrierServicePackageForPhone(PHONE_ID_0);
        setupInitialSimState(PHONE_ID_0, SIM_STATE_STRING_LOADED);

        mCarrierServiceBindHelper.updateForPhoneId(PHONE_ID_0, "");
        processAllMessages();

        verifyNoBindingConnection(PHONE_ID_0);
    }

    @Test
    public void testPackageChanged_carrierPackageAdded_shouldBeConnected() {
        setupTestCarrierServicePackageForPhone(PHONE_ID_0);

        sendTestCarrierPackageAddedIntent();
        processAllMessages();

        verifyBindServiceForNumberOfTimes(1 /* numOfTimes */);

        triggerCarrierServiceConnectionConnected();
        processAllMessages();

        verifyBindingConnectionConnected(PHONE_ID_0);
    }

    @Test
    public void testPackageChanged_carrierPackageRemoved_shouldNotBind() {
        setupTestCarrierServicePackageForPhone(PHONE_ID_0);

        sendTestCarrierPackageRemovedIntent();
        processAllMessages();

        verifyNoBindingConnection(PHONE_ID_0);
    }

    /**
     * Verify the race condition scenario reported in b/232461097 when carrier package updating
     * was firstly received in CarrierServiceBindHelper but not in CarrierPrivilegesTracker yet.
     */
    @Test
    public void testCarrierPackageUpgradingRaceCondition() {
        // At first, binding connection was connected
        setupTestCarrierServicePackageForPhone(PHONE_ID_0);
        sendUserUnlockedBroadcastIntent();
        verifyBindServiceForNumberOfTimes(1 /* numOfTimes */);
        triggerCarrierServiceConnectionConnected();
        processAllMessages();
        verifyBindingConnectionConnected(PHONE_ID_0);

        // Then carrier package is upgrading
        triggerCarrierServiceConnectionDisconnected();
        triggerCarrierServiceConnectionBindingDied();
        // But the carrier package hasn't updated in CarrierPrivilegesTracker yet
        when(mTelephonyManager.getCarrierServicePackageNameForLogicalSlot(PHONE_ID_0)).thenReturn(
                null);
        sendTestCarrierPackageUpdateFinishedIntent();
        processAllMessages();
        // The binding lost the connection and got into issue status
        verifyNoBindingConnection(PHONE_ID_0);

        // The CarrierPackageChange introduced additional safty checkpoint
        sendCarrierPackageChanged(TEST_CARRIER_PACKAGE, TEST_CARRIER_UID);
        processAllMessages();
        verifyBindServiceForNumberOfTimes(2 /* numOfTimes */);
        triggerCarrierServiceConnectionConnected();
        processAllMessages();

        // The final state of binding connection should be connected
        verifyBindingConnectionConnected(PHONE_ID_0);
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
        when(mTelephonyManager.getCarrierServicePackageNameForLogicalSlot(PHONE_ID_0)).thenReturn(
                packageName);
        mCarrierPrivilegesCallback.onCarrierServiceChanged(packageName, uid);
    }

    private void sendCarrierPrivilegesChanged(Set<String> privilegedPackageNames,
            Set<Integer> privilegedUids) {
        mCarrierPrivilegesCallback.onCarrierPrivilegesChanged(privilegedPackageNames,
                privilegedUids);
    }

    private void sendUserUnlockedBroadcastIntent() {
        mUserUnlockedReceiver.onReceive(mMockedContext, new Intent(Intent.ACTION_USER_UNLOCKED));
    }

    private void sendUserInfoChangedBroadcastIntent() {
        mUserUnlockedReceiver.onReceive(mMockedContext,
                new Intent(Intent.ACTION_USER_INFO_CHANGED));
    }

    private void sendPackageChangedIntent(String action, String packageName) {
        Intent packageIntent = new Intent(action,
                Uri.fromParts("package", packageName, null));
        mPackageChangeReceiver.onReceive(mMockedContext, packageIntent);
    }

    private void sendTestCarrierPackageAddedIntent() {
        sendPackageChangedIntent(Intent.ACTION_PACKAGE_ADDED, TEST_CARRIER_PACKAGE);
    }

    private void sendTestCarrierPackageRemovedIntent() {
        when(mTelephonyManager.getCarrierServicePackageNameForLogicalSlot(PHONE_ID_0)).thenReturn(
                null);
        sendPackageChangedIntent(Intent.ACTION_PACKAGE_REMOVED, TEST_CARRIER_PACKAGE);
    }

    private void sendTestCarrierPackageUpdateFinishedIntent() {
        Intent packageIntent = new Intent(Intent.ACTION_PACKAGE_ADDED,
                Uri.fromParts("package", TEST_CARRIER_PACKAGE, null));
        packageIntent.putExtra(Intent.EXTRA_REPLACING, true);
        mPackageChangeReceiver.onReceive(mMockedContext, packageIntent);
    }

    private void triggerCarrierServiceConnectionConnected() {
        mServiceConnection.onServiceConnected(TEST_CARRIER_COMPONENT_NAME, mock(IBinder.class));
    }

    private void triggerCarrierServiceConnectionDisconnected() {
        mServiceConnection.onServiceDisconnected(TEST_CARRIER_COMPONENT_NAME);
    }

    private void triggerCarrierServiceConnectionBindingDied() {
        mServiceConnection.onBindingDied(TEST_CARRIER_COMPONENT_NAME);
    }

    private void triggerCarrierServiceConnectionOnNullBinding() {
        mServiceConnection.onNullBinding(TEST_CARRIER_COMPONENT_NAME);
    }

    private void setupInitialSimState(int phoneId, String state) {
        SparseArray<String> lastSimState = mCarrierServiceBindHelper.mLastSimState;
        if (phoneId >= lastSimState.size()) return;

        lastSimState.put(phoneId, state);
    }

    private void setupTestCarrierServicePackageForPhone(int phoneId) {
        ResolveInfo resolveInfo = createResolveInfo();
        when(mTelephonyManager.getCarrierServicePackageNameForLogicalSlot(eq(phoneId))).thenReturn(
                TEST_CARRIER_PACKAGE);
        when(mPackageManager.resolveService(any(), eq(PackageManager.GET_META_DATA))).thenReturn(
                resolveInfo);
        when(mMockedContext.bindService(any(), eq(BIND_FLAGS), any(), any())).thenReturn(true);

        mCarrierServiceBindHelper = new CarrierServiceBindHelper(mMockedContext);

        ArgumentCaptor<BroadcastReceiver> userUnlockedArgumentCaptor = ArgumentCaptor.forClass(
                BroadcastReceiver.class);
        verify(mMockedContext).registerReceiver(userUnlockedArgumentCaptor.capture(),
                argThat(argument ->
                        argument.hasAction(Intent.ACTION_USER_UNLOCKED)), eq(null), any());
        mUserUnlockedReceiver = userUnlockedArgumentCaptor.getAllValues().get(0);

        ArgumentCaptor<BroadcastReceiver> packageChangedArgumentCaptor = ArgumentCaptor.forClass(
                BroadcastReceiver.class);
        verify(mMockedContext).registerReceiver(packageChangedArgumentCaptor.capture(),
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
     * Verify {@link Context#bindService(Intent, int, Executor, ServiceConnection)} is called for
     * the specified number of times with expected intent and binding flags. The last
     * {@link ServiceConnection} is also captured for further usage.
     */
    private void verifyBindServiceForNumberOfTimes(int numOfTimes) {
        ArgumentCaptor<Intent> intentArgumentCaptor = ArgumentCaptor.forClass(Intent.class);
        ArgumentCaptor<ServiceConnection> connectionArgumentCaptor = ArgumentCaptor.forClass(
                ServiceConnection.class);
        verify(mMockedContext, times(numOfTimes)).bindService(intentArgumentCaptor.capture(),
                eq(BIND_FLAGS), any(),
                connectionArgumentCaptor.capture());
        // Capture intent and service connection for the LAST time
        Intent capturedIntent = intentArgumentCaptor.getAllValues().get(numOfTimes - 1);
        mServiceConnection = connectionArgumentCaptor.getAllValues().get(numOfTimes - 1);
        assertNotNull(mServiceConnection);

        assertNotNull(capturedIntent);
        assertEquals(CarrierService.CARRIER_SERVICE_INTERFACE, capturedIntent.getAction());
        assertEquals(TEST_CARRIER_PACKAGE, capturedIntent.getPackage());
    }

    private void verifyNoBindingConnection(int phoneId) {
        assertFalse(mCarrierServiceBindHelper.isBindingConnectionEstablished(phoneId));
    }

    private void verifyBindingConnectionDisconnected(int phoneId) {
        assertTrue(mCarrierServiceBindHelper.isBindingConnectionEstablished(phoneId));
        assertFalse(mCarrierServiceBindHelper.isBindingConnectionConnected(PHONE_ID_0));
    }

    private void verifyBindingConnectionConnected(int phoneId) {
        assertTrue(mCarrierServiceBindHelper.isBindingConnectionEstablished(phoneId));
        assertTrue(mCarrierServiceBindHelper.isBindingConnectionConnected(PHONE_ID_0));
    }
}
