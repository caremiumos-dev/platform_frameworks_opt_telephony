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
import android.telephony.CellIdentityWcdma;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

/** Unit tests for {@link CellIdentityWcdma}. */

public class CellIdentityWcdmaTest extends AndroidTestCase {

    private static final int sLac = 0;
    private static final int sCid = 0;
    private static final int sPsc = 0;
    private static final int sUarfcn = 0;
    private static final int sMcc = 120;
    private static final int sMnc = 260;
    private static final String sMccStr = "120";
    private static final String sMncStr = "260";
    private static final String sAlphaLong = "long";
    private static final String sAlphaShort = "short";

    @SmallTest
    public void testDefaultConstructor() {
        CellIdentityWcdma ci =
                new CellIdentityWcdma(sLac, sCid, sPsc, sUarfcn, sMccStr, sMncStr,
                        sAlphaLong, sAlphaShort);

        assertEquals(sLac, ci.getLac());
        assertEquals(sCid, ci.getCid());
        assertEquals(sPsc, ci.getPsc());
        assertEquals(sMcc, ci.getMcc());
        assertEquals(sMnc, ci.getMnc());
        assertEquals(sMccStr, ci.getMccStr());
        assertEquals(sMncStr, ci.getMncStr());
        assertEquals(sMccStr + sMncStr, ci.getMobileNetworkOperator());
        assertEquals(sAlphaLong, ci.getOperatorAlphaLong());
        assertEquals(sAlphaShort, ci.getOperatorAlphaShort());
    }

    @SmallTest
    public void testConstructorWithThreeDigitMnc() {
        final String mncWithThreeDigit = "060";
        CellIdentityWcdma ci =
                new CellIdentityWcdma(sLac, sCid, sPsc, sUarfcn, sMccStr, mncWithThreeDigit,
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
        CellIdentityWcdma ci =
                new CellIdentityWcdma(sLac, sCid, sPsc, sUarfcn, sMccStr, mncWithTwoDigit,
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
        CellIdentityWcdma ci =
                new CellIdentityWcdma(sLac, sCid, sPsc, sUarfcn, null, null,
                        sAlphaLong, sAlphaShort);

        assertEquals(Integer.MAX_VALUE, ci.getMcc());
        assertEquals(Integer.MAX_VALUE, ci.getMnc());
        assertEquals(integerMaxValue, ci.getMccStr());
        assertEquals(integerMaxValue, ci.getMncStr());
        assertEquals(integerMaxValue + integerMaxValue, ci.getMobileNetworkOperator());
    }

    @SmallTest
    public void testFormerConstructor() {
        CellIdentityWcdma ci =
                new CellIdentityWcdma(sMcc, sMnc, sLac, sCid, sPsc);

        assertEquals(sLac, ci.getLac());
        assertEquals(sCid, ci.getCid());
        assertEquals(sPsc, ci.getPsc());
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
        CellIdentityWcdma ci =
                new CellIdentityWcdma(sLac, sCid, sPsc, sUarfcn, sMccStr, sMncStr,
                        sAlphaLong, sAlphaShort);

        Parcel p = Parcel.obtain();
        ci.writeToParcel(p, 0);
        p.setDataPosition(0);

        CellIdentityWcdma newCi = CellIdentityWcdma.CREATOR.createFromParcel(p);
        assertEquals(ci, newCi);
    }
}
