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

package com.android.internal.telephony.ims;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.ims.feature.RcsFeature.RcsImsCapabilities;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.ims.RcsFeatureManager;
import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public class RcsFeatureManagerTest extends TelephonyTest {

    private class TestRcsFeatureManager extends RcsFeatureManager {
        private boolean mIsCapabilityEnabled = false;
        private int mCapabilityType = -1;

        TestRcsFeatureManager(Context context, int slotId) {
            super(context, slotId);
        }

        public void reset() {
            mIsCapabilityEnabled = false;
            mCapabilityType = -1;
        }

        public boolean isCapabilityEnabled() {
            return mIsCapabilityEnabled;
        }

        public int getCapabilityType() {
            return mCapabilityType;
        }

        public void triggerFeatureCreatedCallback() {
            mConnectionCallback.RcsFeatureCreated();
        }

        @Override
        public void enableRcsUceCapability(
                @RcsImsCapabilities.RcsImsCapabilityFlag int capabilityType) {
            if (capabilityType == RcsImsCapabilities.CAPABILITY_TYPE_OPTIONS_UCE
                    || capabilityType == RcsImsCapabilities.CAPABILITY_TYPE_PRESENCE_UCE) {
                mIsCapabilityEnabled = true;
                mCapabilityType = capabilityType;
            }
        }

        @Override
        public void disableRcsUceCapabilities() {
            mIsCapabilityEnabled = false;
        }
    }

    private int mPhoneId;
    private PersistableBundle mCarrierConfigBundle;
    private TestRcsFeatureManager mTestRcsFeatureManager;
    @Mock
    RcsFeatureManager.SubscriptionManagerProxy mSubscriptionManagerProxy;

    @Before
    public void setUp() throws Exception {
        super.setUp("RcsFeatureManagerTest");
        mPhoneId = mPhone.getPhoneId();
        mCarrierConfigBundle = mContextFixture.getCarrierConfigBundle();

        doReturn(null).when(mContext).getMainLooper();
        doReturn(true).when(mPackageManager).hasSystemFeature(PackageManager.FEATURE_TELEPHONY_IMS);

        when(mSubscriptionManagerProxy.getSubId(0)).thenReturn(1);
        RcsFeatureManager.setSubscriptionManager(mSubscriptionManagerProxy);

        mTestRcsFeatureManager = new TestRcsFeatureManager(mContext, mPhoneId);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * Test RCS UCE feature is supported by carrier
     */
    @Test
    @SmallTest
    public void testRcsUceFeatureIsSupportedByCarrier() {
        int phoneId = mPhone.getPhoneId();

        // RCS UCE is supported by carrier
        setIsSupportedByCarrier(true, true);
        assertTrue(RcsFeatureManager.isRcsUceSupportedByCarrier(mContext, phoneId));

        // RCS UCE is not supported by carrier
        setIsSupportedByCarrier(false, false);
        assertFalse(RcsFeatureManager.isRcsUceSupportedByCarrier(mContext, phoneId));
    }

    /**
     * Test enableRcsUceCapability and disableRcsUceCapabilities can be called correctly.
     */
    @Test
    @SmallTest
    public void testUpdateCapabilityByCarrierConfig() {
        // Sip Options is supported by carrier
        setIsSupportedByCarrier(true, false);
        mTestRcsFeatureManager.triggerFeatureCreatedCallback();

        assertTrue(mTestRcsFeatureManager.isCapabilityEnabled());
        assertEquals(mTestRcsFeatureManager.getCapabilityType(),
                RcsImsCapabilities.CAPABILITY_TYPE_OPTIONS_UCE);

        // Presence is supported by carrier
        setIsSupportedByCarrier(false, true);
        mTestRcsFeatureManager.reset();
        mTestRcsFeatureManager.triggerFeatureCreatedCallback();

        assertTrue(mTestRcsFeatureManager.isCapabilityEnabled());
        assertEquals(mTestRcsFeatureManager.getCapabilityType(),
                RcsImsCapabilities.CAPABILITY_TYPE_PRESENCE_UCE);

        // UCE is not supported by carrier
        setIsSupportedByCarrier(false, false);
        mTestRcsFeatureManager.reset();
        mTestRcsFeatureManager.triggerFeatureCreatedCallback();

        assertFalse(mTestRcsFeatureManager.isCapabilityEnabled());
    }

    private void setIsSupportedByCarrier(boolean isOptionsSupported, boolean isPresenceSupported) {
        mCarrierConfigBundle.putBoolean(
                CarrierConfigManager.KEY_USE_RCS_SIP_OPTIONS_BOOL, isOptionsSupported);
        mCarrierConfigBundle.putBoolean(
                CarrierConfigManager.KEY_USE_RCS_PRESENCE_BOOL, isPresenceSupported);
    }
}
