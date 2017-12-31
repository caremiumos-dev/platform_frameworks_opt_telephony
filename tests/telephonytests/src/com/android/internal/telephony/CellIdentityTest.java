/*
 * Copyright 2017 The Android Open Source Project
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

import android.os.Parcel;
import android.telephony.CellIdentity;
import android.telephony.CellIdentityLte;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

public class CellIdentityTest extends AndroidTestCase {

    // Cell identity ranges from 0 to 268435456.
    private static final int CI = 268435456;
    // Physical cell id ranges from 0 to 503.
    private static final int PCI = 503;
    // Tracking area code ranges from 0 to 65535.
    private static final int TAC = 65535;
    // Absolute RF Channel Number ranges from 0 to 262140.
    private static final int EARFCN = 262140;
    private static final int MCC = 120;
    private static final int MNC = 260;
    private static final String MCC_STR = "120";
    private static final String MNC_STR = "260";
    private static final String ALPHA_LONG = "long";
    private static final String ALPHA_SHORT = "short";

    @SmallTest
    public void testEquals() {
        CellIdentity ciA = new CellIdentityLte(
                CI, PCI, TAC, EARFCN, MCC_STR, MNC_STR, ALPHA_LONG, ALPHA_SHORT);
        CellIdentity ciB = new CellIdentityLte(
                CI, PCI, TAC, EARFCN, MCC_STR, MNC_STR, ALPHA_LONG, ALPHA_SHORT);

        assertTrue(ciA.equals(ciB));

        ciA = new CellIdentityLte(CI, PCI, TAC, EARFCN, null, null, ALPHA_LONG, ALPHA_SHORT);
        ciB = new CellIdentityLte(CI, PCI, TAC, EARFCN, null, null, ALPHA_LONG, ALPHA_SHORT);

        assertTrue(ciA.equals(ciB));

        ciA = new CellIdentityLte(CI, PCI, TAC, EARFCN, MCC_STR, null, ALPHA_LONG, ALPHA_SHORT);
        ciB = new CellIdentityLte(CI, PCI, TAC, EARFCN, null, null, ALPHA_LONG, ALPHA_SHORT);

        assertFalse(ciA.equals(ciB));
    }

    @SmallTest
    public void testParcel() {
        CellIdentity ci =
                new CellIdentityLte(CI, PCI, TAC, EARFCN, MCC_STR, MNC_STR,
                        ALPHA_LONG, ALPHA_SHORT);

        Parcel p = Parcel.obtain();
        ci.writeToParcel(p, 0);
        p.setDataPosition(0);

        CellIdentity newCi = CellIdentity.CREATOR.createFromParcel(p);
        assertEquals(ci, newCi);
    }
}
