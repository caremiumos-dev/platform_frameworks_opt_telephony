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

package com.android.internal.telephony.uicc.euicc.async;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;

public class AsyncResultCallbackTest {
    @Test
    public void testNormalOperation() throws ExecutionException, InterruptedException {
        final List<String> called = new ArrayList<>();
        final AsyncResultCallback<Integer> cb1 = new AsyncResultCallback<>();
        final AsyncResultCallback<String> cb2 = new AsyncResultCallback<>();
        final AsyncResultCallback<Void> cb3 = new AsyncResultCallback<>();

        cb1.onResult(123);

        final CountDownLatch countDownLatch = new CountDownLatch(1);
        new Thread(
                new Runnable() {
                    @Override
                    public void run() {
                        called.add("first");

                        int result1;
                        try {
                            result1 = cb1.asFuture().get();
                        } catch (InterruptedException | ExecutionException e) {
                            fail("Unexpected exception: " + e.getMessage());
                            return;
                        }

                        called.add("then1");
                        assertEquals(123, result1);

                        String result2;
                        try {
                            result2 = cb2.asFuture().get();
                        } catch (InterruptedException | ExecutionException e) {
                            fail("Unexpected exception: " + e.getMessage());
                            return;
                        }

                        called.add("then2");
                        assertEquals("result", result2);

                        try {
                            cb3.asFuture().get();
                            fail("Should have an exception thrown.");
                        } catch (InterruptedException e) {
                            fail("Should have an InterruptedException exception thrown.");
                        } catch (ExecutionException e) {
                            called.add("then3");
                            assertTrue(e.getCause() instanceof Exception);
                        }
                        countDownLatch.countDown();
                    }
                }).start();

        cb2.onResult("result");
        cb3.onException(new Exception(""));
        countDownLatch.await();

        assertEquals(4, called.size());
        assertEquals("first", called.get(0));
        assertEquals("then1", called.get(1));
        assertEquals("then2", called.get(2));
        assertEquals("then3", called.get(3));
    }
}
