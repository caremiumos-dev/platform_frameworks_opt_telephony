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

import junit.framework.AssertionFailedError;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyObject;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.os.HandlerThread;
import android.os.Looper;
import android.telephony.ims.feature.ImsFeature;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.ims.FeatureConnector;
import com.android.ims.IFeatureConnector;
import com.android.ims.ImsException;
import com.android.ims.ImsManager;
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

    public class TestFeatureConnector<T extends IFeatureConnector> extends FeatureConnector<T> {
        public boolean isSupported = true;
        public boolean createImsServiceCalled = false;

        public TestFeatureConnector(Context context, int phoneId, int featureType,
                FeatureConnector.Listener listener, Executor executor, Looper looper) {
            super(context, phoneId, featureType, listener, executor, looper);
        }

        public void reset() {
            isSupported = true;
            createImsServiceCalled = false;
        }

        @Override
        public boolean isSupported() {
            return isSupported;
        }

        public boolean setIsSupported(boolean isSupported) {
            return this.isSupported = isSupported;
        }

        @Override
        public void createImsService() throws ImsException {
            createImsServiceCalled = true;
        }
    }

    private HandlerThread mHandlerThread;
    private TestFeatureConnector<ImsManager> mFeatureConnector;
    @Mock
    FeatureConnector.Listener mListener;
    @Mock
    ImsManager mImsManager;

    @Before
    public void setUp() throws Exception {
        super.setUp("FeatureConnectorTest");
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }

        int phoneId = mPhone.getPhoneId();
        mHandlerThread = new HandlerThread("ConnectorHandlerThread");
        mHandlerThread.start();
        mFeatureConnector = new TestFeatureConnector<ImsManager>(mContext, phoneId,
            ImsFeature.FEATURE_MMTEL, mListener, mExecutor, mHandlerThread.getLooper());
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

        assertTrue(mFeatureConnector.createImsServiceCalled);

        // ImsFeature is not supported
        mFeatureConnector.reset();
        mFeatureConnector.setIsSupported(false);

        mFeatureConnector.connect();
        waitForHandlerAction(mFeatureConnector, 1000);

        assertFalse(mFeatureConnector.createImsServiceCalled);
    }

    @Test
    @SmallTest
    public void testDisconnect() {
        // Verify disconnect will remove the GetServiceRunnable
        mFeatureConnector.disconnect();
        verify(mListener).connectionUnavailable();
    }

    @Test
    @SmallTest
    public void testNotifyStateChanged() {
        try {
            mFeatureConnector.mManager = mImsManager;
            when(mImsManager.getImsServiceState()).thenReturn(ImsFeature.STATE_READY);
            // Trigger status changed
            mFeatureConnector.mNotifyStatusChangedCallback.notifyStateChanged();
            // Verify NotifyReady is called
            //assertTrue(mFeatureConnector.isNotifyReadyCalled);
            verify(mListener).connectionReady(anyObject());
        } catch (ImsException e) {
            throw new AssertionFailedError("Exception in testNotifyStateChanged: " + e);
        }
    }
}
