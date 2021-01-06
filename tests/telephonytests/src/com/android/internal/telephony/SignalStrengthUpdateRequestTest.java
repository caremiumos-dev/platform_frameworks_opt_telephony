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
import android.telephony.SignalStrengthUpdateRequest;
import android.telephony.SignalThresholdInfo;

import androidx.test.filters.SmallTest;

import junit.framework.TestCase;

import org.junit.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class SignalStrengthUpdateRequestTest extends TestCase {

    private SignalThresholdInfo mRssiInfo = new SignalThresholdInfo(SignalThresholdInfo.SIGNAL_RSSI,
            new int[]{-109, -103, -97, -89});
    private SignalThresholdInfo mRscpInfo = new SignalThresholdInfo(SignalThresholdInfo.SIGNAL_RSCP,
            new int[]{-115, -105, -95, -85});
    private SignalThresholdInfo mRssiInfoWithLongThresholds = new SignalThresholdInfo(
            SignalThresholdInfo.SIGNAL_RSSI, new int[]{-109, -103, -97, -89, -88});

    @Test
    @SmallTest
    public void testPublicConstructorWithInvalidParam() {
        // null Collection
        validateConstructorWithInvalidParam(null);

        // empty Collection
        validateConstructorWithInvalidParam(Collections.EMPTY_LIST);

        // duplication of SignalMeasurementType in Collection
        validateConstructorWithInvalidParam(List.of(mRssiInfo, mRssiInfo));

        // long thresholds in SignalThresholdInfo
        validateConstructorWithInvalidParam(List.of(mRssiInfoWithLongThresholds));
    }

    @Test
    @SmallTest
    public void testPublicConstructorWithValidParam() {
        Collection<SignalThresholdInfo> infos = List.of(mRssiInfo, mRscpInfo);
        SignalStrengthUpdateRequest request = new SignalStrengthUpdateRequest(infos);
        assertEquals(false, request.getHonorSystemThresholdWhenScreenOff());
        assertEquals(infos, request.getSignalThresholdInfos());
    }

    @Test
    @SmallTest
    public void testParcel() {
        Collection<SignalThresholdInfo> infos = List.of(mRssiInfo, mRscpInfo);
        SignalStrengthUpdateRequest request = new SignalStrengthUpdateRequest(infos);

        Parcel p = Parcel.obtain();
        request.writeToParcel(p, 0);
        p.setDataPosition(0);

        SignalStrengthUpdateRequest newRequest =
                SignalStrengthUpdateRequest.CREATOR.createFromParcel(p);
        assertThat(newRequest).isEqualTo(request);
    }

    @Test
    @SmallTest
    public void testEquals() {
        Collection<SignalThresholdInfo> infos1 = List.of(mRssiInfo, mRscpInfo);
        SignalStrengthUpdateRequest request1 = new SignalStrengthUpdateRequest(infos1);

        assertTrue(request1.equals(request1));

        // Order matter
        Collection<SignalThresholdInfo> infos2 = List.of(mRscpInfo, mRssiInfo);
        SignalStrengthUpdateRequest request2 = new SignalStrengthUpdateRequest(infos2);
        assertFalse(request1.equals(request2));

        SignalStrengthUpdateRequest request3 = new SignalStrengthUpdateRequest(infos1, true);
        assertFalse(request1.equals(request3));

        // return false if the object is not SignalStrengthUpdateRequest
        assertFalse(request1.equals("test"));
    }

    private void validateConstructorWithInvalidParam(Collection<SignalThresholdInfo> infos) {
        try {
            new SignalStrengthUpdateRequest(infos);
            fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException expected) {
        }
    }
}
