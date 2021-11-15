/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import android.os.Parcel;
import android.telephony.SubscriptionManager;
import android.test.suitebuilder.annotation.SmallTest;

import junit.framework.TestCase;

import org.junit.Test;

public class IccOpenLogicalChannelRequestTest extends TestCase {

    private static final int SUB_ID = 0;
    private static final String CALLING_PKG = "test_app";
    private static final String AID = "aid";
    private static final int P2 = 0;

    @Test
    @SmallTest
    public void testConstructor_withValidParmas_shouldReturnValidValues() {
        IccOpenLogicalChannelRequest request =
                new IccOpenLogicalChannelRequest(SUB_ID,
                        SubscriptionManager.INVALID_SIM_SLOT_INDEX, CALLING_PKG, AID, P2);

        assertThat(request.subId).isEqualTo(SUB_ID);
        assertThat(request.slotIndex).isEqualTo(SubscriptionManager.INVALID_SIM_SLOT_INDEX);
        assertThat(request.callingPackage).isEqualTo(CALLING_PKG);
        assertThat(request.aid).isEqualTo(AID);
        assertThat(request.p2).isEqualTo(P2);
    }

    @Test
    @SmallTest
    public void testEquals_sameInput_shouldReturnTrue() {
        IccOpenLogicalChannelRequest request1 =
                new IccOpenLogicalChannelRequest(SUB_ID,
                        SubscriptionManager.INVALID_SIM_SLOT_INDEX, CALLING_PKG, AID, P2);
        IccOpenLogicalChannelRequest request2 =
                new IccOpenLogicalChannelRequest(SUB_ID,
                        SubscriptionManager.INVALID_SIM_SLOT_INDEX, CALLING_PKG, AID, P2);

        assertThat(request1).isEqualTo(request2);
    }

    @Test
    @SmallTest
    public void testEquals_differentInput_shouldReturnFalse() {
        IccOpenLogicalChannelRequest request1 =
                new IccOpenLogicalChannelRequest(SUB_ID,
                        SubscriptionManager.INVALID_SIM_SLOT_INDEX, CALLING_PKG, AID, P2);
        // request2 is diff from request2 with AID
        IccOpenLogicalChannelRequest request2 =
                new IccOpenLogicalChannelRequest(SUB_ID,
                        SubscriptionManager.INVALID_SIM_SLOT_INDEX, CALLING_PKG, "aid2", P2);

        assertThat(request1.equals(request2)).isFalse();
    }

    @Test
    @SmallTest
    public void testParcel() {
        IccOpenLogicalChannelRequest oldrequest =
                new IccOpenLogicalChannelRequest(SUB_ID,
                        SubscriptionManager.INVALID_SIM_SLOT_INDEX, CALLING_PKG, AID, P2);

        Parcel p = Parcel.obtain();
        oldrequest.writeToParcel(p, /*flags=*/0);
        p.setDataPosition(0);
        IccOpenLogicalChannelRequest newRequest =
                IccOpenLogicalChannelRequest.CREATOR.createFromParcel(p);

        assertThat(newRequest).isEqualTo(oldrequest);
    }
}
