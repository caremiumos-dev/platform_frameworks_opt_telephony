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

package android.telephony.ims;

import android.support.test.runner.AndroidJUnit4;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.ims.ImsServiceProxy;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import static org.junit.Assert.fail;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests the {@link ImsServiceProxy.CallbackManager} class.
 */
@RunWith(AndroidJUnit4.class)
public class CallbackManagerTest {

    private TestCallbackManager mTestCallbackManager;

    private static class TestCallback {
        boolean mDidNotify = false;

        void notifyEvent() {
            mDidNotify = true;
        }
    }

    private class TestCallbackManager extends ImsServiceProxy.CallbackManager<TestCallback> {

        int mNumBinderCreations = 0;
        boolean mBinderUnavailable = false;
        boolean mFailCreateBinder = false;

        @Override
        protected boolean createCallbackBinder() {
            if (mFailCreateBinder) {
                return false;
            }

            mNumBinderCreations++;
            return true;
        }

        @Override
        protected void onBinderNotAvailable() {
            mBinderUnavailable = true;
        }

        public void setFailCreateBinder(boolean willFail) {
            mFailCreateBinder = willFail;
        }
    }
    @Before
    public void setUp() {
        mTestCallbackManager = new TestCallbackManager();
    }

    @After
    public void tearDown() {
        mTestCallbackManager = null;
    }

    /**
     * Tests that when a callback is added,
     * {@link ImsServiceProxy.CallbackManager#createCallbackBinder} is called once and
     * {@link ImsServiceProxy.CallbackManager#notifyCallback} notifies the callback method
     * specified.
     */
    @Test
    @SmallTest
    public void testAddCallbackAndNotify() throws Exception {
        TestCallback testCallback = new TestCallback();
        mTestCallbackManager.addCallback(testCallback);

        mTestCallbackManager.notifyCallback(TestCallback::notifyEvent);

        assertEquals(1, mTestCallbackManager.mNumBinderCreations);
        assertTrue(testCallback.mDidNotify);
    }

    /**
     * Tests that when multiple callbacks are added,
     * {@link ImsServiceProxy.CallbackManager#createCallbackBinder} is called once and
     * {@link ImsServiceProxy.CallbackManager#notifyCallback} notifies all registered callbacks.
     * @throws Exception
     */
    @Test
    @SmallTest
    public void testAddCallbacksAndNotify() throws Exception {
        TestCallback testCallback = new TestCallback();
        TestCallback testCallback2 = new TestCallback();
        mTestCallbackManager.addCallback(testCallback);
        mTestCallbackManager.addCallback(testCallback2);

        mTestCallbackManager.notifyCallback(TestCallback::notifyEvent);

        // There should still only be one callback binder call.
        assertEquals(1, mTestCallbackManager.mNumBinderCreations);
        assertTrue(testCallback.mDidNotify);
        assertTrue(testCallback2.mDidNotify);
    }


    /**
     * Tests that when {@link ImsServiceProxy.CallbackManager#notifyBinderNotAvailable()} is called,
     * previously registered callbacks are removed and the
     * {@link ImsServiceProxy.CallbackManager#onBinderNotAvailable()} method is called.
     */
    @Test
    @SmallTest
    public void testServiceUnavailableNoNotify() throws Exception {
        TestCallback testCallback = new TestCallback();
        mTestCallbackManager.addCallback(testCallback);

        mTestCallbackManager.notifyBinderNotAvailable();
        mTestCallbackManager.notifyCallback(TestCallback::notifyEvent);

        assertEquals(1, mTestCallbackManager.mNumBinderCreations);
        assertTrue(mTestCallbackManager.mBinderUnavailable);
        assertFalse(testCallback.mDidNotify);
    }

    /**
     * Test that {@link ImsServiceProxy.CallbackManager#addCallback} throws an Exception when it is
     * called and binder creation fails.
     */
    @Test
    @SmallTest
    public void testBinderUnavailable() {
        TestCallback testCallback = new TestCallback();
        mTestCallbackManager.setFailCreateBinder(true);
        try {
            // this should throw an exception.
            mTestCallbackManager.addCallback(testCallback);
            fail();
        } catch (Exception e) {
            // expected
        }

        assertEquals(0, mTestCallbackManager.mNumBinderCreations);
        assertFalse(testCallback.mDidNotify);
    }
}
