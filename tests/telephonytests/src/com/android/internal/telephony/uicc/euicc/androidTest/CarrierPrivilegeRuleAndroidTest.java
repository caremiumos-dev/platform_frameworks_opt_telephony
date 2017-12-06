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
 * limitations under the License
 */

package com.android.euicc.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.os.Parcel;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class CarrierPrivilegeRuleAndroidTest {
    @Test
    public void testWriteToParcel() {
        CarrierPrivilegeRule rule =
                new CarrierPrivilegeRule(new byte[] {0x23, 0x45, 0x67}, "test.package", 12345L);

        Parcel parcel = Parcel.obtain();
        assertTrue(parcel != null);
        rule.writeToParcel(parcel, 0);

        parcel.setDataPosition(0);
        CarrierPrivilegeRule fromParcel = CarrierPrivilegeRule.CREATOR.createFromParcel(parcel);

        assertEquals(rule, fromParcel);
    }
}
