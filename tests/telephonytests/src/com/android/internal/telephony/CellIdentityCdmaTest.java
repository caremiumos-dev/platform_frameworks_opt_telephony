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

package com.android.internal.telephony;

import static org.junit.Assert.assertEquals;

import android.os.Parcel;
import android.telephony.CellIdentityCdma;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

/** Unit tests for {@link CellIdentityCdma}. */

public class CellIdentityCdmaTest extends AndroidTestCase {

    @SmallTest
    public void testConstructor() {
        CellIdentityCdma  ci =
                new CellIdentityCdma(65535, 32767, 65535, 2592000, 1296000, "long", "short");

        assertEquals(65535, ci.getNetworkId());
        assertEquals(1296000, ci.getLatitude());
        assertEquals("long", ci.getOperatorAlphaLong());
        assertEquals("short", ci.getOperatorAlphaShort());
    }

    @SmallTest
    public void testParcel() {
        CellIdentityCdma  ci =
                new CellIdentityCdma(65535, 32767, 65535, 2592000, 1296000, "long", "short");

        Parcel p = Parcel.obtain();
        ci.writeToParcel(p, 0);
        p.setDataPosition(0);

        CellIdentityCdma newCi = CellIdentityCdma.CREATOR.createFromParcel(p);
        assertEquals(ci, newCi);
    }
}
