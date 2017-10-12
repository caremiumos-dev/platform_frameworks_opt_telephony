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

    private static final int sCi = 0;
    private static final int sPci = 0;
    private static final int sTac = 0;
    private static final int sEarfcn = 0;
    private static final int sMcc = 120;
    private static final int sMnc = 260;
    private static final String sMccStr = "120";
    private static final String sMncStr = "260";
    private static final String sAlphaLong = "long";
    private static final String sAlphaShort = "short";

    @SmallTest
    public void testDefaultConstructor() {
        CellIdentityLte ci =
                new CellIdentityLte(sCi, sPci, sTac, sEarfcn, sMccStr, sMncStr,
                        sAlphaLong, sAlphaShort);

        assertEquals(sCi, ci.getCi());
        assertEquals(sPci, ci.getPci());
        assertEquals(sTac, ci.getTac());
        assertEquals(sEarfcn, ci.getEarfcn());
        assertEquals(sMcc, ci.getMcc());
        assertEquals(sMnc, ci.getMnc());
        assertEquals(sMccStr, ci.getMccStr());
        assertEquals(sMncStr, ci.getMncStr());
        assertEquals(sMccStr + mMncStr, ci.getMobileNetworkOperator());
        assertEquals(sAlphaLong, ci.getOperatorAlphaLong());
        assertEquals(sAlphaShort, ci.getOperatorAlphaShort());
    }

    @SmallTest
    public void testConstructorWithThreeDigitMnc() {
        final String mncWithThreeDigit = "060";
        CellIdentityLte ci =
                new CellIdentityLte(sCi, sPci, sTac, sEarfcn, sMccStr, mncWithThreeDigit,
                        sAlphaLong, sAlphaShort);

        assertEquals(sMcc, ci.getMcc());
        assertEquals(60, ci.getMnc());
        assertEquals(sMccStr, ci.getMccStr());
        assertEquals(mncWithThreeDigit, ci.getMncStr());
        assertEquals(sMccStr + mncWithThreeDigit, ci.getMobileNetworkOperator());
    }

    @SmallTest
    public void testConstructorWithTwoDigitMnc() {
        final String mncWithTwoDigit = "60";
        CellIdentityLte ci =
                new CellIdentityLte(sCi, sPci, sTac, sEarfcn, sMccStr, mncWithThreeDigit,
                        sAlphaLong, sAlphaShort);

        assertEquals(sMcc, ci.getMcc());
        assertEquals(60, ci.getMnc());
        assertEquals(sMccStr, ci.getMccStr());
        assertEquals(mncWithTwoDigit, ci.getMncStr());
        assertEquals(sMccStr + mncWithTwoDigit, ci.getMobileNetworkOperator());
    }

    @SmallTest
    public void testConstructorWithEmptyMccMnc() {
        final String integerMaxValue = "2147483647";
        CellIdentityLte ci =
                new CellIdentityLte(sCi, sPci, sTac, sEarfcn, null, null,
                        sAlphaLong, sAlphaShort);

        assertEquals(Integer.MAX_VALUE, ci.getMcc());
        assertEquals(Integer.MAX_VALUE, ci.getMnc());
        assertEquals(integerMaxValue, ci.getMccStr());
        assertEquals(integerMaxValue, ci.getMncStr());
        assertEquals(integerMaxValue + integerMaxValue, ci.getMobileNetworkOperator());
    }

    @SmallTest
    public void testFormerConstructor() {
        CellIdentityLte ci =
                new CellIdentityLte(sMcc, sMnc, sCi, sPci, sTac);

        assertEquals(sCi, ci.getCi());
        assertEquals(sPci, ci.getPci());
        assertEquals(sTac, ci.getTac());
        assertEquals(Integer.MAX_VALUE, ci.getEarfcn());
        assertEquals(sMcc, ci.getMcc());
        assertEquals(sMnc, ci.getMnc());
        assertEquals(sMccStr, ci.getMccStr());
        assertEquals(sMncStr, ci.getMncStr());
        assertEquals(sMccStr + sMncStr, ci.getMobileNetworkOperator());
        assertNull(ci.getOperatorAlphaLong());
        assertNull(ci.getOperatorAlphaShort());
    }

    @SmallTest
    public void testParcel() {
        CellIdentityLte ci =
                new CellIdentityLte(sCi, sPci, sTac, sEarfcn, sMccStr, sMncStr,
                        sAlphaLong, sAlphaShort);

        Parcel p = Parcel.obtain();
        ci.writeToParcel(p, 0);
        p.setDataPosition(0);

        CellIdentityLte newCi = CellIdentityLte.CREATOR.createFromParcel(p);
        assertEquals(ci, newCi);
    }
}
