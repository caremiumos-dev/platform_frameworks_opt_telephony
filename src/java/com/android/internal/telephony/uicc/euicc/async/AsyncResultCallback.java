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

package com.android.euicc.async;

import android.support.annotation.NonNull;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Class to deliver the returned value from an asynchronous call. Either {@link #onResult(Result)}
 * or {@link #onException(Throwable)} will be called. You can create an anonymous subclass and
 * override these methods to handle the result or the throwable from an asynchronous call, for
 * example:
 *
 * <pre>
 *     doSomethingAsync(
 *             new AsyncResultCallback&lt;Result&gt;() {
 *                 void onResult(Result r) {
 *                     Log.i("Got the result: %s", r.toString());
 *                 }
 *
 *                 void onException(Throwable e) {...}
 *             });
 * <pre>
 *
 * <p>You can also use {@link #asFuture()} to convert this callback as a {@link Future} object. This
 * adapts the asynchronous call into a synchronous context. For example,
 *
 * <pre>
 *     new AsyncResultCallback&lt;Result&gt; callback = new new AsyncResultCallback&lt;&gt;();
 *     doSomethingAsync(callback);
 *
 *     Result r = callback.asFuture().get(); // Waits for the callback to be called.
 *     Log.i("Got the result: %s", r.toString());
 * <pre>
 *
 * @param <Result> The returned value of the asynchronous call.
 */
public class AsyncResultCallback<Result> {
    private final CountDownLatch mLatch = new CountDownLatch(1);
    private volatile Result mResult;
    private volatile Throwable mError;

    /** This will be called when the result is returned. */
    public void onResult(Result result) {
        mResult = result;
        mLatch.countDown();
    }

    /** This will be called when any exception is thrown. */
    public void onException(Throwable e) {
        mError = e;
        mLatch.countDown();
    }

    /**
     * @return A {@link Future} object to be used in a synchronous context. Every call returns a new
     *     future. All returned futures will be notified when the callback is called.
     */
    public final Future<Result> asFuture() {
        return new Future<Result>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                // We currently don't support cancel.
                // TODO: implement cancelling and throwing an InterruptedException to the callback.
                return false;
            }

            @Override
            public boolean isCancelled() {
                return false;
            }

            @Override
            public boolean isDone() {
                return mLatch.getCount() == 0;
            }

            @Override
            public Result get() throws InterruptedException, ExecutionException {
                mLatch.await();
                if (mError != null) {
                    throw new ExecutionException(mError);
                }
                return mResult;
            }

            @Override
            public Result get(long timeout, @NonNull TimeUnit unit)
                    throws InterruptedException, ExecutionException, TimeoutException {
                if (!mLatch.await(timeout, unit)) {
                    throw new TimeoutException(
                            "Callback isn't called in "
                                    + TimeUnit.MILLISECONDS.convert(timeout, unit)
                                    + " milliseconds.");
                }
                if (mError != null) {
                    throw new ExecutionException(mError);
                }
                return mResult;
            }
        };
    }
}
