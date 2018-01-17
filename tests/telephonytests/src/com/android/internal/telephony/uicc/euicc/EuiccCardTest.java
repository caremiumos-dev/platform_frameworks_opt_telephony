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

package com.android.euicc.card;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.support.test.filters.SmallTest;
import android.telephony.IccOpenLogicalChannelResponse;
import android.telephony.TelephonyManager;
import com.android.euicc.asn1.Asn1Converter;
import com.android.euicc.asn1.Asn1Node;
import com.android.euicc.data.CarrierPrivilegeRule;
import com.android.euicc.data.EuiccNotification;
import com.android.euicc.data.EuiccProfile;
import com.android.euicc.data.EuiccProfile.PolicyRule;
import com.android.euicc.data.EuiccProfile.ProfileClass;
import com.android.euicc.data.EuiccProfile.ProfileState;
import com.android.euicc.data.EuiccRat;
import com.android.euicc.data.EuiccSpecVersion;
import com.android.euicc.data.OperatorId;
import com.android.euicc.telephony.UiccChannelException;
import com.android.euicc.telephony.UiccChannelMocker;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@SmallTest
@RunWith(MockitoJUnitRunner.class)
public class EuiccCardImplV2Test {
    @Mock
    private TelephonyManager mMockTm;

    private EuiccCardImplV2 mEuiccCard;

    @Before
    public void setUp() {
        mEuiccCard = new EuiccCardImplV2(new EuiccSpecVersion(2, 0, 0), mMockTm);
    }

    @Test
    public void testGetAllProfiles() throws UiccChannelException, EuiccCardException {
        int channel =
                mockLogicalChannelResponses("BF2D14A012E3105A0A896700000000004523019F7001019000");

        EuiccProfile[] profiles = mEuiccCard.getAllProfiles();

        assertEquals(1, profiles.length);
        assertEquals("98760000000000543210", profiles[0].getIccid());
        assertEquals(ProfileState.ENABLED, profiles[0].getState());
        verifyStoreData(channel, "BF2D0D5C0B5A909192B79F709599BF76");
    }

    @Test
    public void testFSuffix() throws UiccChannelException, EuiccCardException {
        // iccID is 987600000000005432FF.
        int channel =
                mockLogicalChannelResponses("BF2D14A012E3105A0A896700000000004523FF9F7001019000");

        EuiccProfile[] profiles = mEuiccCard.getAllProfiles();

        assertEquals(1, profiles.length);
        assertEquals("987600000000005432", profiles[0].getIccid());
        assertEquals(ProfileState.ENABLED, profiles[0].getState());
        verifyStoreData(channel, "BF2D0D5C0B5A909192B79F709599BF76");
    }
}
