/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.internal.telephony.uicc.euicc.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.os.Parcel;
import android.support.test.runner.AndroidJUnit4;
import android.test.suitebuilder.annotation.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class OperatorIdAndroidTest {
    @Test
    public void testWriteToParcel() {
        OperatorId oid =
                new OperatorId(
                        new byte[] {0x23, 0x45, 0x67}, new byte[] {1, 2, 3}, new byte[] {4, 5});

        Parcel parcel = Parcel.obtain();
        assertTrue(parcel != null);
        oid.writeToParcel(parcel, 0);

        parcel.setDataPosition(0);
        OperatorId fromParcel = OperatorId.CREATOR.createFromParcel(parcel);

        assertEquals(oid, fromParcel);
    }
}
