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

    private static final int CI = Short.MAX_VALUE;
    private static final int PCI = Short.MAX_VALUE;
    private static final int TAC = Short.MAX_VALUE;
    private static final int EARFCN = Short.MAX_VALUE;
    private static final int MCC = 120;
    private static final int MNC = 260;
    private static final String MCC_STR = "120";
    private static final String MNC_STR = "260";
    private static final String ALPHA_LONG = "long";
    private static final String ALPHA_SHORT = "short";

    @SmallTest
    public void testDefaultConstructor() {
        CellIdentityLte ci =
                new CellIdentityLte(CI, PCI, TAC, EARFCN, MCC_STR, MNC_STR,
                        ALPHA_LONG, ALPHA_SHORT);

        assertEquals(CI, ci.getCi());
        assertEquals(PCI, ci.getPci());
        assertEquals(TAC, ci.getTac());
        assertEquals(EARFCN, ci.getEarfcn());
        assertEquals(MCC, ci.getMcc());
        assertEquals(MNC, ci.getMnc());
        assertEquals(MCC_STR, ci.getMccStr());
        assertEquals(MNC_STR, ci.getMncStr());
        assertEquals(MCC_STR + MNC_STR, ci.getMobileNetworkOperator());
        assertEquals(ALPHA_LONG, ci.getOperatorAlphaLong());
        assertEquals(ALPHA_SHORT, ci.getOperatorAlphaShort());
    }

    @SmallTest
    public void testConstructorWithThreeDigitMnc() {
        final String mncWithThreeDigit = "061";
        CellIdentityLte ci =
                new CellIdentityLte(CI, PCI, TAC, EARFCN, MCC_STR, mncWithThreeDigit,
                        ALPHA_LONG, ALPHA_SHORT);

        assertEquals(MCC, ci.getMcc());
        assertEquals(61, ci.getMnc());
        assertEquals(MCC_STR, ci.getMccStr());
        assertEquals(mncWithThreeDigit, ci.getMncStr());
        assertEquals(MCC_STR + mncWithThreeDigit, ci.getMobileNetworkOperator());
    }

    @SmallTest
    public void testConstructorWithTwoDigitMnc() {
        final String mncWithTwoDigit = "61";
        CellIdentityLte ci =
                new CellIdentityLte(CI, PCI, TAC, EARFCN, MCC_STR, mncWithTwoDigit,
                        ALPHA_LONG, ALPHA_SHORT);

        assertEquals(MCC, ci.getMcc());
        assertEquals(61, ci.getMnc());
        assertEquals(MCC_STR, ci.getMccStr());
        assertEquals(mncWithTwoDigit, ci.getMncStr());
        assertEquals(MCC_STR + mncWithTwoDigit, ci.getMobileNetworkOperator());
    }

    @SmallTest
    public void testConstructorWithEmptyMccMnc() {
        final String integerMaxValue = String.valueOf(Integer.MAX_VALUE);
        CellIdentityLte ci =
                new CellIdentityLte(CI, PCI, TAC, EARFCN, null, null, ALPHA_LONG, ALPHA_SHORT);

        assertEquals(Integer.MAX_VALUE, ci.getMcc());
        assertEquals(Integer.MAX_VALUE, ci.getMnc());
        assertEquals(integerMaxValue, ci.getMccStr());
        assertEquals(integerMaxValue, ci.getMncStr());
        assertEquals(integerMaxValue + integerMaxValue, ci.getMobileNetworkOperator());
    }

    @SmallTest
    public void testFormerConstructor() {
        CellIdentityLte ci =
                new CellIdentityLte(MCC, MNC, CI, PCI, TAC);

        assertEquals(CI, ci.getCi());
        assertEquals(PCI, ci.getPci());
        assertEquals(TAC, ci.getTac());
        assertEquals(Integer.MAX_VALUE, ci.getEarfcn());
        assertEquals(MCC, ci.getMcc());
        assertEquals(MNC, ci.getMnc());
        assertEquals(MCC_STR, ci.getMccStr());
        assertEquals(MNC_STR, ci.getMncStr());
        assertEquals(MCC_STR + MNC_STR, ci.getMobileNetworkOperator());
        assertNull(ci.getOperatorAlphaLong());
        assertNull(ci.getOperatorAlphaShort());
    }

    @SmallTest
    public void testParcel() {
        CellIdentityLte ci =
                new CellIdentityLte(CI, PCI, TAC, EARFCN, MCC_STR, MNC_STR,
                        ALPHA_LONG, ALPHA_SHORT);

        Parcel p = Parcel.obtain();
        ci.writeToParcel(p, 0);
        p.setDataPosition(0);

        CellIdentityLte newCi = CellIdentityLte.CREATOR.createFromParcel(p);
        assertEquals(ci, newCi);
    }
}
