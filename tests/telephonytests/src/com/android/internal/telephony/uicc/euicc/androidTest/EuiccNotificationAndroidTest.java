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
import com.android.euicc.asn1.Asn1Converter;
import com.android.euicc.data.EuiccNotification.Event;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class EuiccNotificationAndroidTest {
    @Test
    public void testWriteToParcel() {
        byte[] data = Asn1Converter.hexToBytes("BF372401");
        EuiccNotification notification = new EuiccNotification(1, "smdp.com", Event.DELETE, data);
        Parcel parcel = Parcel.obtain();
        assertTrue(parcel != null);
        notification.writeToParcel(parcel, 0);

        parcel.setDataPosition(0);
        EuiccNotification fromParcel = EuiccNotification.CREATOR.createFromParcel(parcel);
        assertEquals(notification, fromParcel);
    }
}
