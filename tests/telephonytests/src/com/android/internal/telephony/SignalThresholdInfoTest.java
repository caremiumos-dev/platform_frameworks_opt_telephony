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

    private final int[] mRssiThresholds = new int[] {-109, -103, -97, -89};
    private final int[] mRscpThresholds = new int[] {-115, -105, -95, -85};
    private final int[] mRsrpThresholds = new int[] {-128, -118, -108, -98};
    private final int[] mRsrqThresholds = new int[] {-19, -17, -14, -12};
    private final int[] mRssnrThresholds = new int[] {-20, 10, 20, 30};
    private final int[][] mThresholds = new int[5][];

    private static final int[] INVALID_SIGNAL_MEASUREMENT_TYPES = new int[]{-1, 0, 9};

    private static final List<int[]> EMPTY_THRESHOLDS = new ArrayList<>();
    static {
        EMPTY_THRESHOLDS.add(null);
        EMPTY_THRESHOLDS.add(new int[]{});
    }

    private static final Map<Integer, int[]> INVALID_THRESHOLDS_MAP = Map.of(
            SignalThresholdInfo.SIGNAL_RSSI, new int[]{
                    SignalThresholdInfo.SIGNAL_RSSI_MIN_VALUE - 1,
                    SignalThresholdInfo.SIGNAL_RSSI_MAX_VALUE + 1},
            SignalThresholdInfo.SIGNAL_RSCP, new int[]{
                    SignalThresholdInfo.SIGNAL_RSCP_MIN_VALUE - 1,
                    SignalThresholdInfo.SIGNAL_RSCP_MAX_VALUE + 1},
            SignalThresholdInfo.SIGNAL_RSRQ, new int[]{
                    SignalThresholdInfo.SIGNAL_RSRQ_MIN_VALUE - 1,
                    SignalThresholdInfo.SIGNAL_RSRQ_MAX_VALUE + 1},
            SignalThresholdInfo.SIGNAL_RSSNR, new int[]{
                    SignalThresholdInfo.SIGNAL_RSSNR_MIN_VALUE - 1,
                    SignalThresholdInfo.SIGNAL_RSSNR_MAX_VALUE + 1},
            SignalThresholdInfo.SIGNAL_SSRSRP, new int[]{
                    SignalThresholdInfo.SIGNAL_SSRSRP_MIN_VALUE - 1,
                    SignalThresholdInfo.SIGNAL_SSRSRP_MAX_VALUE + 1},
            SignalThresholdInfo.SIGNAL_SSRSRQ, new int[]{
                    SignalThresholdInfo.SIGNAL_SSRSRQ_MIN_VALUE - 1,
                    SignalThresholdInfo.SIGNAL_SSRSRQ_MAX_VALUE + 1}
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
        setThresholds();
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
        setThresholds();
        ArrayList<SignalThresholdInfo> stList = setSignalThresholdInfoFromPublicConstructor();

        int count = 0;
        for (SignalThresholdInfo st : stList) {
            assertThat(st.getThresholds()).isEqualTo(mThresholds[count]);
            assertThat(st.getHysteresisMs()).isEqualTo(SignalThresholdInfo.HYSTERESIS_MS_UNUSED);
            assertThat(st.getHysteresisDb()).isEqualTo(SignalThresholdInfo.HYSTERESIS_DB_UNUSED);
            assertThat(st.isEnabled()).isEqualTo(SignalThresholdInfo.IS_ENABLED_UNUSED);
            count++;
        }
    }

    @Test
    @SmallTest
    public void testPublicConstructorWithInvalidParameter() {
        for (int signalMeasurementType : INVALID_SIGNAL_MEASUREMENT_TYPES) {
            constructWithInvalidParameterThrowException(signalMeasurementType, new int[]{-1});
        }

        for (int[] thresholds : EMPTY_THRESHOLDS) {
            constructWithInvalidParameterThrowException(SignalThresholdInfo.SIGNAL_RSSI,
                    thresholds);
        }

        for (int signalMeasurementType : INVALID_THRESHOLDS_MAP.keySet()) {
            int[] thresholds = INVALID_THRESHOLDS_MAP.get(signalMeasurementType);
            constructWithInvalidParameterThrowException(signalMeasurementType, thresholds);
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

    private void setThresholds() {
        mThresholds[0] = mRssiThresholds;
        mThresholds[1] = mRscpThresholds;
        mThresholds[2] = mRsrpThresholds;
        mThresholds[3] = mRsrqThresholds;
        mThresholds[4] = mRssnrThresholds;
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

        return stList;
    }

    private ArrayList<SignalThresholdInfo> setSignalThresholdInfoFromPublicConstructor() {
        ArrayList<SignalThresholdInfo> stList = new ArrayList<>();

        stList.add(new SignalThresholdInfo(SignalThresholdInfo.SIGNAL_RSSI, mRssiThresholds));
        stList.add(new SignalThresholdInfo(SignalThresholdInfo.SIGNAL_RSCP, mRscpThresholds));
        stList.add(new SignalThresholdInfo(SignalThresholdInfo.SIGNAL_RSRP, mRsrpThresholds));
        stList.add(new SignalThresholdInfo(SignalThresholdInfo.SIGNAL_RSRQ, mRsrqThresholds));
        stList.add(new SignalThresholdInfo(SignalThresholdInfo.SIGNAL_RSSNR, mRssnrThresholds));

        return stList;
    }
}
