/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.Parcel;
import android.telephony.SignalThresholdInfo;

import androidx.test.filters.SmallTest;

import junit.framework.TestCase;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RunWith(JUnit4.class)
public class SignalThresholdInfoTest extends TestCase {
    private static final int HYSTERESIS_DB = 2;
    private static final int HYSTERESIS_MS = 30;
    private static final int[] SSRSRP_THRESHOLDS = new int[] {-30, 10, 45, 130};

    // Deliberately picking up the max/min value in each range to test the edge cases
    private final int[] mRssiThresholds = new int[] {-113, -103, -97, -51};
    private final int[] mRscpThresholds = new int[] {-120, -105, -95, -25};
    private final int[] mRsrpThresholds = new int[] {-140, -118, -108, -44};
    private final int[] mRsrqThresholds = new int[] {-34, -17, -14, 3};
    private final int[] mRssnrThresholds = new int[] {-20, 10, 20, 30};
    private final int[] mSsrsrpThresholds = new int[] {-140, -118, -98, -44};
    private final int[] mSsrsrqThresholds = new int[] {-43, -17, -14, 20};
    private final int[] mSssinrThresholds = new int[] {-23, -16, -10, 40};
    private final int[][] mThresholds = {mRssiThresholds, mRscpThresholds, mRsrpThresholds,
            mRsrqThresholds, mRssnrThresholds, mSsrsrpThresholds, mSsrsrqThresholds,
            mSssinrThresholds};

    // Map of SignalMeasurementType to invalid thresholds edge values.
    // Each invalid value will be constructed with a thresholds array to test separately.
    private static final Map<Integer, List<Integer>> INVALID_THRESHOLDS_MAP = Map.of(
            SignalThresholdInfo.SIGNAL_RSSI,
            List.of(SignalThresholdInfo.SIGNAL_RSSI_MIN_VALUE - 1,
                    SignalThresholdInfo.SIGNAL_RSSI_MAX_VALUE + 1),
            SignalThresholdInfo.SIGNAL_RSCP,
            List.of(SignalThresholdInfo.SIGNAL_RSCP_MIN_VALUE - 1,
                    SignalThresholdInfo.SIGNAL_RSCP_MAX_VALUE + 1),
            SignalThresholdInfo.SIGNAL_RSRP,
            List.of(SignalThresholdInfo.SIGNAL_RSRP_MIN_VALUE - 1,
                    SignalThresholdInfo.SIGNAL_RSRP_MAX_VALUE + 1),
            SignalThresholdInfo.SIGNAL_RSRQ,
            List.of(SignalThresholdInfo.SIGNAL_RSRQ_MIN_VALUE - 1,
                    SignalThresholdInfo.SIGNAL_RSRQ_MAX_VALUE + 1),
            SignalThresholdInfo.SIGNAL_RSSNR,
            List.of(SignalThresholdInfo.SIGNAL_RSSNR_MIN_VALUE - 1,
                    SignalThresholdInfo.SIGNAL_RSSNR_MAX_VALUE + 1),
            SignalThresholdInfo.SIGNAL_SSRSRP,
            List.of(SignalThresholdInfo.SIGNAL_SSRSRP_MIN_VALUE - 1,
                    SignalThresholdInfo.SIGNAL_SSRSRP_MAX_VALUE + 1),
            SignalThresholdInfo.SIGNAL_SSRSRQ,
            List.of(SignalThresholdInfo.SIGNAL_SSRSRQ_MIN_VALUE - 1,
                    SignalThresholdInfo.SIGNAL_SSRSRQ_MAX_VALUE + 1),
            SignalThresholdInfo.SIGNAL_SSSINR,
            List.of(SignalThresholdInfo.SIGNAL_SSSINR_MIN_VALUE - 1,
                    SignalThresholdInfo.SIGNAL_SSSINR_MAX_VALUE + 1)
    );

    @Test
    @SmallTest
    public void testSignalThresholdInfo() throws Exception {
        SignalThresholdInfo signalThresholdInfo = new SignalThresholdInfo(
                SignalThresholdInfo.SIGNAL_SSRSRP,
                HYSTERESIS_MS,
                HYSTERESIS_DB,
                SSRSRP_THRESHOLDS,
                false);

        assertEquals(SignalThresholdInfo.SIGNAL_SSRSRP,
                signalThresholdInfo.getSignalMeasurement());
        assertEquals(HYSTERESIS_MS, signalThresholdInfo.getHysteresisMs());
        assertEquals(HYSTERESIS_DB, signalThresholdInfo.getHysteresisDb());
        assertEquals(Arrays.toString(SSRSRP_THRESHOLDS), Arrays.toString(
                signalThresholdInfo.getThresholds()));
        assertFalse(signalThresholdInfo.isEnabled());
    }

    @Test
    @SmallTest
    public void testDefaultThresholdsConstruction() {
        ArrayList<SignalThresholdInfo> stList = setSignalThresholdInfoConstructor();

        int count = 0;
        for (SignalThresholdInfo st : stList) {
            assertThat(st.getThresholds()).isEqualTo(mThresholds[count]);
            count++;
        }
    }

    @Test
    @SmallTest
    public void testDefaultThresholdsParcel() {
        ArrayList<SignalThresholdInfo> stList = setSignalThresholdInfoConstructor();

        for (SignalThresholdInfo st : stList) {
            Parcel p = Parcel.obtain();
            st.writeToParcel(p, 0);
            p.setDataPosition(0);

            SignalThresholdInfo newSt = SignalThresholdInfo.CREATOR.createFromParcel(p);
            assertThat(newSt).isEqualTo(st);
        }
    }

    @Test
    @SmallTest
    public void testGetSignalThresholdInfo() {
        ArrayList<SignalThresholdInfo> stList = new ArrayList<>();
        stList.add(new SignalThresholdInfo(0, 0, 0, null, false));
        stList.add(new SignalThresholdInfo(SignalThresholdInfo.SIGNAL_RSSI, HYSTERESIS_MS,
                HYSTERESIS_DB, mRssiThresholds, false));

        assertThat(stList.get(0).getThresholds()).isEqualTo(null);
        assertThat(stList.get(1).getSignalMeasurement()).isEqualTo(SignalThresholdInfo.SIGNAL_RSSI);
        assertThat(stList.get(1).getThresholds()).isEqualTo(mRssiThresholds);
    }

    @Test
    @SmallTest
    public void testEqualsSignalThresholdInfo() {
        final int[] dummyThresholds = new int[] {-100, -1, 1, 100};
        SignalThresholdInfo st1 = new SignalThresholdInfo(1, HYSTERESIS_MS, HYSTERESIS_DB,
                mRssiThresholds, false);
        SignalThresholdInfo st2 = new SignalThresholdInfo(2, HYSTERESIS_MS, HYSTERESIS_DB,
                mRssiThresholds, false);
        SignalThresholdInfo st3 = new SignalThresholdInfo(1, HYSTERESIS_MS, HYSTERESIS_DB,
                dummyThresholds, false);
        SignalThresholdInfo st4 = new SignalThresholdInfo(1, HYSTERESIS_MS, HYSTERESIS_DB,
                mRssiThresholds, false);

        //Return true if all SignalThresholdInfo values match.
        assertTrue(st1.equals(st1));
        assertFalse(st1.equals(st2));
        assertFalse(st1.equals(st3));
        assertTrue(st1.equals(st4));
        //Return false if the object of argument is other than SignalThresholdInfo.
        assertFalse(st1.equals(new String("test")));
    }

    @Test
    @SmallTest
    public void testPublicConstructorWithValidParameters() {
        ArrayList<SignalThresholdInfo> stList = setSignalThresholdInfoFromPublicConstructor();

        for (int i = 0; i < stList.size(); i++) {
            SignalThresholdInfo st = stList.get(i);
            assertThat(st.getThresholds()).isEqualTo(mThresholds[i]);
            assertThat(st.getHysteresisMs()).isEqualTo(SignalThresholdInfo.HYSTERESIS_MS_UNUSED);
            assertThat(st.getHysteresisDb()).isEqualTo(SignalThresholdInfo.HYSTERESIS_DB_UNUSED);
            assertThat(st.isEnabled()).isEqualTo(SignalThresholdInfo.IS_ENABLED_UNUSED);
        }
    }

    @Test
    @SmallTest
    public void testPublicConstructorWithInvalidParameter() {
        int[] invalidSignalMeasurementTypes = new int[]{-1, 0, 9};
        for (int signalMeasurementType : invalidSignalMeasurementTypes) {
            constructWithInvalidParameterThrowException(signalMeasurementType, new int[]{-1});
        }

        List<int[]> emptyThresholds = Arrays.asList(null, new int[]{});
        for (int[] thresholds : emptyThresholds) {
            constructWithInvalidParameterThrowException(SignalThresholdInfo.SIGNAL_RSSI,
                    thresholds);
        }

        for (int signalMeasurementType : INVALID_THRESHOLDS_MAP.keySet()) {
            List<Integer> invalidThresholds = INVALID_THRESHOLDS_MAP.get(signalMeasurementType);
            for (int threshold : invalidThresholds) {
                constructWithInvalidParameterThrowException(signalMeasurementType,
                        new int[]{threshold});
            }
        }
    }

    private void constructWithInvalidParameterThrowException(int signalMeasurementType,
            int[] thresholds) {
        try {
            new SignalThresholdInfo(signalMeasurementType, thresholds);
            fail("expected exception");
        } catch (IllegalArgumentException expected) {
        }
    }

    private ArrayList<SignalThresholdInfo> setSignalThresholdInfoConstructor() {
        ArrayList<SignalThresholdInfo> stList = new ArrayList<>();
        stList.add(new SignalThresholdInfo(SignalThresholdInfo.SIGNAL_RSSI, HYSTERESIS_MS,
                HYSTERESIS_DB, mRssiThresholds, false));
        stList.add(new SignalThresholdInfo(SignalThresholdInfo.SIGNAL_RSCP, HYSTERESIS_MS,
                HYSTERESIS_DB, mRscpThresholds, false));
        stList.add(new SignalThresholdInfo(SignalThresholdInfo.SIGNAL_RSRP, HYSTERESIS_MS,
                HYSTERESIS_DB, mRsrpThresholds, false));
        stList.add(new SignalThresholdInfo(SignalThresholdInfo.SIGNAL_RSRQ, HYSTERESIS_MS,
                HYSTERESIS_DB, mRsrqThresholds, false));
        stList.add(new SignalThresholdInfo(SignalThresholdInfo.SIGNAL_RSSNR, HYSTERESIS_MS,
                HYSTERESIS_DB, mRssnrThresholds, false));
        stList.add(new SignalThresholdInfo(SignalThresholdInfo.SIGNAL_SSRSRP, HYSTERESIS_MS,
                HYSTERESIS_DB, mSsrsrpThresholds, false));
        stList.add(new SignalThresholdInfo(SignalThresholdInfo.SIGNAL_SSRSRQ, HYSTERESIS_MS,
                HYSTERESIS_DB, mSsrsrqThresholds, false));
        stList.add(new SignalThresholdInfo(SignalThresholdInfo.SIGNAL_SSSINR, HYSTERESIS_MS,
                HYSTERESIS_DB, mSssinrThresholds, false));

        return stList;
    }

    private ArrayList<SignalThresholdInfo> setSignalThresholdInfoFromPublicConstructor() {
        ArrayList<SignalThresholdInfo> stList = new ArrayList<>();

        stList.add(new SignalThresholdInfo(SignalThresholdInfo.SIGNAL_RSSI, mRssiThresholds));
        stList.add(new SignalThresholdInfo(SignalThresholdInfo.SIGNAL_RSCP, mRscpThresholds));
        stList.add(new SignalThresholdInfo(SignalThresholdInfo.SIGNAL_RSRP, mRsrpThresholds));
        stList.add(new SignalThresholdInfo(SignalThresholdInfo.SIGNAL_RSRQ, mRsrqThresholds));
        stList.add(new SignalThresholdInfo(SignalThresholdInfo.SIGNAL_RSSNR, mRssnrThresholds));
        stList.add(new SignalThresholdInfo(SignalThresholdInfo.SIGNAL_SSRSRP, mSsrsrpThresholds));
        stList.add(new SignalThresholdInfo(SignalThresholdInfo.SIGNAL_SSRSRQ, mSsrsrqThresholds));
        stList.add(new SignalThresholdInfo(SignalThresholdInfo.SIGNAL_SSSINR, mSssinrThresholds));

        return stList;
    }
}
