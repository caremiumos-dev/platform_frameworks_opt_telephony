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
import android.telephony.CellIdentityLte;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

/** Unit tests for {@link CellIdentityLte}. */

public class CellIdentityLteTest extends AndroidTestCase {

    @SmallTest
    public void testDefaultConstructor() {
        CellIdentityLte ci =
                new CellIdentityLte(0, 0, 0, 0, "120", "260", "long", "short");

        assertEquals(0, ci.getCi());
        assertEquals(0, ci.getPci());
        assertEquals(0, ci.getTac());
        assertEquals(0, ci.getEarfcn());
        assertEquals(120, ci.getMcc());
        assertEquals(260, ci.getMnc());
        assertEquals("120", ci.getMccStr());
        assertEquals("260", ci.getMncStr());
        assertEquals("120260", ci.getMobileNetworkOperator());
        assertEquals("long", ci.getOperatorAlphaLong());
        assertEquals("short", ci.getOperatorAlphaShort());
    }

    @SmallTest
    public void testConstructorWithThreeDigitMnc() {
        CellIdentityLte ci =
                new CellIdentityLte(0, 0, 0, 0, "120", "060", "long", "short");

        assertEquals(120, ci.getMcc());
        assertEquals(60, ci.getMnc());
        assertEquals("120", ci.getMccStr());
        assertEquals("060", ci.getMncStr());
        assertEquals("120060", ci.getMobileNetworkOperator());
    }

    @SmallTest
    public void testConstructorWithTwoDigitMnc() {
        CellIdentityLte ci =
                new CellIdentityLte(0, 0, 0, 0, "120", "60", "long", "short");

        assertEquals(120, ci.getMcc());
        assertEquals(60, ci.getMnc());
        assertEquals("120", ci.getMccStr());
        assertEquals("60", ci.getMncStr());
        assertEquals("12060", ci.getMobileNetworkOperator());
    }

    @SmallTest
    public void testConstructorWithEmptyMccMnc() {
        CellIdentityLte ci =
                new CellIdentityLte(0, 0, 0, 0, null, null, "long", "short");

        assertEquals(Integer.MAX_VALUE, ci.getMcc());
        assertEquals(Integer.MAX_VALUE, ci.getMnc());
        assertEquals("2147483647", ci.getMccStr());
        assertEquals("2147483647", ci.getMncStr());
        assertEquals("21474836472147483647", ci.getMobileNetworkOperator());
    }

    @SmallTest
    public void testFormerConstructor() {
        CellIdentityLte ci =
                new CellIdentityLte(120, 260, 0, 0, 0);

        assertEquals(0, ci.getCi());
        assertEquals(0, ci.getPci());
        assertEquals(0, ci.getTac());
        assertEquals(Integer.MAX_VALUE, ci.getEarfcn());
        assertEquals(120, ci.getMcc());
        assertEquals(260, ci.getMnc());
        assertEquals("120", ci.getMccStr());
        assertEquals("260", ci.getMncStr());
        assertEquals("120260", ci.getMobileNetworkOperator());
        assertEquals(null, ci.getOperatorAlphaLong());
        assertEquals(null, ci.getOperatorAlphaShort());
    }

    @SmallTest
    public void testParcel() {
        CellIdentityLte ci =
                new CellIdentityLte(0, 0, 0, 0, "120", "260", "long", "short");

        Parcel p = Parcel.obtain();
        ci.writeToParcel(p, 0);
        p.setDataPosition(0);

        CellIdentityLte newCi = CellIdentityLte.CREATOR.createFromParcel(p);
        assertEquals(ci, newCi);
    }
}
