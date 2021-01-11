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

import static org.junit.Assert.assertTrue;

import android.test.suitebuilder.annotation.SmallTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class SignalStrengthControllerTest extends  TelephonyTest {

    private SignalStrengthController mSSC;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mSSC = new SignalStrengthController(mPhone);
    }

    @After
    public void tearDown() throws Exception {
        mSSC = null;
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testConstructor() {
        assertTrue(mSSC != null);
    }
}
