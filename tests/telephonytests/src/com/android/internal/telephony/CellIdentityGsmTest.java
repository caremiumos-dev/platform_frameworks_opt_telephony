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
import android.telephony.CellIdentityGsm;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

/** Unit tests for {@link CellIdentityGsm}. */

public class CellIdentityGsmTest extends AndroidTestCase {

    private static final int sLac = 0xFFFF;
    private static final int sCid = 0xFFFF;
    private static final int sArfcn = 0xFFFF;
    private static final int sBsic = 0xFFFF;
    private static final int sMcc = 120;
    private static final int sMnc = 260;
    private static final String sMccStr = "120";
    private static final String sMncStr = "260";
    private static final String sAlphaLong = "long";
    private static final String sAlphaShort = "short";


    @SmallTest
    public void testDefaultConstructor() {
        CellIdentityGsm ci =
                new CellIdentityGsm(sLac, sCid, sArfcn, sBsic, sMccStr, sMncStr,
                        sAlphaLong, sAlphaShort);

        assertEquals(sLac, ci.getLac());
        assertEquals(sCid, ci.getCid());
        assertEquals(sArfcn, ci.getArfcn());
        assertEquals(sBsic, ci.getBsic());
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
        final String mncWithThreeDigit = "061";
        CellIdentityGsm ci =
                new CellIdentityGsm(sLac, sCid, sArfcn, sBsic, sMccStr, mncWithThreeDigit,
                        sAlphaLong, sAlphaShort);

        assertEquals(sMcc, ci.getMcc());
        assertEquals(61, ci.getMnc());
        assertEquals(sMccStr, ci.getMccStr());
        assertEquals(mncWithThreeDigit, ci.getMncStr());
        assertEquals(sMccStr + mncWithThreeDigit, ci.getMobileNetworkOperator());
    }

    @SmallTest
    public void testConstructorWithTwoDigitMnc() {
        final String mncWithTwoDigit = "60";
        CellIdentityGsm ci =
                new CellIdentityGsm(sLac, sCid, sArfcn, sBsic, sMccStr, mncWithTwoDigit,
                        sAlphaLong, sAlphaShort);

        assertEquals(sMcc, ci.getMcc());
        assertEquals(60, ci.getMnc());
        assertEquals(sMccStr, ci.getMccStr());
        assertEquals(mncWithTwoDigit, ci.getMncStr());
        assertEquals(sMccStr + mncWithTwoDigit, ci.getMobileNetworkOperator());
    }

    @SmallTest
    public void testConstructorWithEmptyMccMnc() {
        final String integerMaxValue = String.valueOf(Integer.MAX_VALUE);
        CellIdentityGsm ci =
                new CellIdentityGsm(sLac, sCid, sArfcn, sBsic, null, null, sAlphaLong, sAlphaShort);

        assertEquals(Integer.MAX_VALUE, ci.getMcc());
        assertEquals(Integer.MAX_VALUE, ci.getMnc());
        assertEquals(integerMaxValue, ci.getMccStr());
        assertEquals(integerMaxValue, ci.getMncStr());
        assertEquals(integerMaxValue + integerMaxValue, ci.getMobileNetworkOperator());
    }

    @SmallTest
    public void testFormerConstructor() {
        CellIdentityGsm ci =
                new CellIdentityGsm(sMcc, sMnc, sLac, sCid);

        assertEquals(sLac, ci.getLac());
        assertEquals(sCid, ci.getCid());
        assertEquals(Integer.MAX_VALUE, ci.getArfcn());
        assertEquals(Integer.MAX_VALUE, ci.getBsic());
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
        CellIdentityGsm ci =
                new CellIdentityGsm(sLac, sCid, sArfcn, sBsic, sMccStr, sMncStr,
                        sAlphaLong, sAlphaShort);

        Parcel p = Parcel.obtain();
        ci.writeToParcel(p, 0);
        p.setDataPosition(0);

        CellIdentityGsm newCi = CellIdentityGsm.CREATOR.createFromParcel(p);
        assertEquals(ci, newCi);
    }
}
