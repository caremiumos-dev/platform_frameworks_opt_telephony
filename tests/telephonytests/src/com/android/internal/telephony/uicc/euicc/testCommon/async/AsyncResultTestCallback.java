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

import java.util.concurrent.CountDownLatch;

/** Callback to capture returned result and the thrown exception in tests. */
public class AsyncResultTestCallback<T> extends AsyncResultCallback<T> {
    private final CountDownLatch mSignal;
    private T mResult;
    private Throwable mException;

    /** {@code} signal is used as a barrier to wait for this callback to be called. */
    public AsyncResultTestCallback() {
        mSignal = new CountDownLatch(1);
    }

    public void await() throws InterruptedException {
        mSignal.await();
    }

    public T getResult() {
        return mResult;
    }

    public Throwable getException() {
        return mException;
    }

    @Override
    public void onResult(T result) {
        mResult = result;
        mSignal.countDown();
    }

    @Override
    public void onException(Throwable throwable) {
        mException = throwable;
        mSignal.countDown();
    }
}
