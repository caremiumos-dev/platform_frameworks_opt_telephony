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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.Calendar;


public class SlidingWindowEventCounterTest extends TelephonyTest {
    @Mock
    Calendar mCalendar;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
    }

    @Test
    public void test_returnsTrue_ifInWindow() {
        SlidingWindowEventCounter counter = new SlidingWindowEventCounter(100, 3);
        for (int i = 0; i < 3; i++) {
            counter.addOccurrence();
        }
        assertTrue(counter.isInWindow());
    }

    @Test
    public void test_returnsFalse_ifNotInWindow() {
        SlidingWindowEventCounter counter = new SlidingWindowEventCounter(100, 3);
        for (int i = 0; i < 2; i++) {
            counter.addOccurrence();
        }
        assertFalse(counter.isInWindow());
    }
}
