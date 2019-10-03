/*
 * Copyright 2019 The Android Open Source Project
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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.spy;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.telephony.ims.feature.ImsFeature;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.ims.FeatureConnector;
import com.android.ims.ImsException;
import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.concurrent.Executor;

public class FeatureConnectorTest extends TelephonyTest {

    private Executor mExecutor = new Executor() {
        @Override
        public void execute(Runnable r) {
            r.run();
        }
    };

    public class TestFeatureConnector extends FeatureConnector {
        public boolean isSupported = true;
        public boolean isGetImsServiceCalled = false;
        public boolean isRetryGetImsServiceCalled = false;
        public boolean isNotifyReadyCalled = false;
        public int imsFeatureStatus = ImsFeature.STATE_UNAVAILABLE;

        public TestFeatureConnector(Context context, int phoneId, Listener listener,
                Executor executor, Looper looper) {
            super(context, phoneId, listener, executor, looper);
        }

        public void reset() {
            isSupported = true;
            isGetImsServiceCalled = false;
            isRetryGetImsServiceCalled = false;
            isNotifyReadyCalled = false;
            imsFeatureStatus = ImsFeature.STATE_UNAVAILABLE;
        }

        @Override
        public boolean isSupported() {
            return isSupported;
        }

        public boolean setIsSupported(boolean isSupported) {
            return this.isSupported = isSupported;
        }

        @Override
        public void getImsService() throws ImsException {
            isGetImsServiceCalled = true;
        }

        @Override
        public void retryGetImsService() {
            isRetryGetImsServiceCalled = true;
        }

        public void triggerNotifyStatusChanged() {
            mNotifyStatusChangedCallback.notifyStateChanged();
        }

        @Override
        public int getImsFeatureStatus() throws ImsException {
            return imsFeatureStatus;
        }

        public void setImsFeatureStatus(int status) {
            imsFeatureStatus = status;
        }

        @Override
        public void notifyReady() throws ImsException {
            isNotifyReadyCalled = true;
        }
    }

    @Mock
    FeatureConnector.Listener mListener;
    private TestFeatureConnector mFeatureConnector;
    private TestFeatureConnector mSpyFeatureConnector;
    private HandlerThread mHandlerThread;
    private Handler mTestHandler;

    @Before
    public void setUp() throws Exception {
        super.setUp("FeatureConnectorTest");
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }

        int phoneId = mPhone.getPhoneId();
        mHandlerThread = new HandlerThread("ConnectorHandlerThread");
        mHandlerThread.start();
        mFeatureConnector = new TestFeatureConnector(mContext, phoneId, mListener, mExecutor,
                mHandlerThread.getLooper());
        mSpyFeatureConnector = spy(mFeatureConnector);
    }

    @After
    public void tearDown() throws Exception {
        mHandlerThread.quit();
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testGetImsServiceIsCalled() {
        // ImsFeature is supported
        mFeatureConnector.setIsSupported(true);

        mFeatureConnector.connect();
        waitForHandlerAction(mFeatureConnector, 1000);

        assertTrue(mFeatureConnector.isGetImsServiceCalled);

        // ImsFeature is not supported
        mFeatureConnector.reset();
        mFeatureConnector.setIsSupported(false);

        mFeatureConnector.connect();
        waitForHandlerAction(mFeatureConnector, 1000);

        assertFalse(mFeatureConnector.isGetImsServiceCalled);
    }

    @Test
    @SmallTest
    public void testDisconnect() {
        // Verify disconnect will remove the GetServiceRunnable
        mFeatureConnector.disconnect();
        assertFalse(mFeatureConnector.hasCallbacks(mFeatureConnector.mGetServiceRunnable));
    }

    @Test
    @SmallTest
    public void testNotifyStateChanged() {
        // ImsFeature is ready
        mFeatureConnector.setImsFeatureStatus(ImsFeature.STATE_READY);
        // Trigger status changed
        mFeatureConnector.triggerNotifyStatusChanged();
        // Verify NotifyReady is called
        assertTrue(mFeatureConnector.isNotifyReadyCalled);

        // ImsFeature is unavailable
        mFeatureConnector.reset();
        mFeatureConnector.setImsFeatureStatus(ImsFeature.STATE_UNAVAILABLE);
        // Trigger status changed
        mFeatureConnector.triggerNotifyStatusChanged();
        // Verify NotifyReady won't be called.
        assertFalse(mFeatureConnector.isNotifyReadyCalled);
    }
}
