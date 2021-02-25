/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.annotation.IntRange;
import android.os.SystemClock;
import android.util.LongArrayQueue;

import com.android.internal.annotations.VisibleForTesting;

/**
 * Tracks whether an event occurs mNumOccurrences times within the time span mWindowSizeMillis.
 * <p/>
 * Stores all events in memory, use a small number of required occurrences when using.
 */
public class SlidingWindowEventCounter {
    private final long mWindowSizeMillis;
    private final int mNumOccurrences;
    private final LongArrayQueue mTimestampQueueMs;

    public SlidingWindowEventCounter(@IntRange(from = 1) final long windowSizeMillis,
            @IntRange(from = 2) final int numOccurrences) {
        if (windowSizeMillis <= 0) {
            throw new IllegalArgumentException("windowSizeMillis must be greater than 0");
        }
        if (numOccurrences <= 1) {
            throw new IllegalArgumentException("numOccurrences must be greater than 1");
        }

        mWindowSizeMillis = windowSizeMillis;
        mNumOccurrences = numOccurrences;
        mTimestampQueueMs = new LongArrayQueue(numOccurrences);
    }

    /**
     * Increments the occurrence counter.
     * <p/>
     * Returns true if the condition is satisfied
     */
    public synchronized boolean addOccurrence() {
        mTimestampQueueMs.addLast(SystemClock.elapsedRealtime());

        if (mTimestampQueueMs.size() > mNumOccurrences) {
            // If the window was satisfied the last time #addOccurrence was called, then
            // there will now be more occurrences than are required, which means the pop must
            // be popped.
            mTimestampQueueMs.removeFirst();
        }

        long lastTimeMs = mTimestampQueueMs.peekLast();
        while (mTimestampQueueMs.size() > 0) {
            final long firstTimeMs = mTimestampQueueMs.peekFirst();
            if (firstTimeMs + mWindowSizeMillis >= lastTimeMs) {
                // In the sliding time window
                return mTimestampQueueMs.size() == mNumOccurrences;
            }

            // Not in the time window means pop the top
            mTimestampQueueMs.removeFirst();
        }
        return false;
    }

    /**
     * Returns true if the conditions is satisfied.
     */
    public synchronized boolean isInWindow() {
        return mTimestampQueueMs.size() == mNumOccurrences;
    }

    @VisibleForTesting
    int getNumOccurrences() {
        return mTimestampQueueMs.size();
    }
}
