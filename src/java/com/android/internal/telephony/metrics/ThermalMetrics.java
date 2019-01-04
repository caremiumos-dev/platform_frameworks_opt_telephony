/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.internal.telephony.metrics;

import static android.security.KeyStore.getApplicationContext;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryStats;
import android.os.HardwarePropertiesManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.connectivity.CellularBatteryStats;
import android.util.Log;

import com.android.internal.app.IBatteryStats;
import com.android.internal.telephony.nano.TelephonyProto.TemperatureHistogram;
import com.android.internal.telephony.nano.TelephonyProto.ThermalStats;

import java.util.Arrays;
/**
 *  ThermalMetrics class is used to receive temperature data. Data is stored as a Histogram.
 * */

public class ThermalMetrics extends BroadcastReceiver {

    private static final String TAG = "ThermalMetrics";

    /** Specifies the max temperature that will be bucketed. Any higher temperatures will be placed
     * in the last bin to represent greater than TEMPERATURE_MAX_CELSIUS in Celsius
     */
    private static final int TEMPERATURE_MAX_CELSIUS = 100;

    /** Minimum temperature in CELSIUS */
    private static final int TEMPERATURE_MIN_CELSIUS = -20;

    /** Specifies how many degrees each bucket contains */
    private static final int BUCKET_SPAN = 5;

    /** Needed directly for calling getCurrentTemperature */
    private static HardwarePropertiesManager sHardwarePropertiesManager;

    /** Contains all temperature sample data organized in their specific buckets */
    private long[] mBucketCounters;

    /* BatteryStats API */
    private final IBatteryStats mBatteryStats;

    public ThermalMetrics() {
        /* Set up 15 min alarm for temperature collection */
        final String thermalMetricsAction =
                "com.android.internal.telephony.metric.ThermalMetrics";
        /* Get context of class */
        Context mContext = getApplicationContext();
        IntentFilter intentFilter = new IntentFilter(thermalMetricsAction);
        /* Non-evasive Alarm used for collecting temperature */
        AlarmManager mAlarmMgr = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
        /* Create receiver for the temperature alarm broadcast */
        mContext.registerReceiver(this, intentFilter);
        Intent anotherIntent = new Intent(thermalMetricsAction);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(mContext,
                0,
                anotherIntent,
                0);
        /* Set alarm to repeat approximately every 15 minutes but do not wake up system if
         * alarm goes off during a doze/sleep mode. */
        mAlarmMgr.setInexactRepeating(AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime()
                        + (AlarmManager.INTERVAL_FIFTEEN_MINUTES),
                (AlarmManager.INTERVAL_FIFTEEN_MINUTES),
                pendingIntent);
        /* Finished setup for 15min alarm for temperature collection */
        /* Initialize buckets and Hardware Properties Manager interface */
        sHardwarePropertiesManager = (HardwarePropertiesManager)
                getApplicationContext().getSystemService(Context.HARDWARE_PROPERTIES_SERVICE);
        /* Counts how many buckets are available
         *  First bucket is < TEMPERATURE_MIN_CELSIUS are last bucket is > TEMPERATURE_MAX_CELSIUS
         *  For example: Buckets would be for (TEMPERATURE_MAX_CELSIUS = 60), (BUCKET_SPAN = 5),
         *  (TEMPERATURE_MIN_CELSIUS = 0)
         *  Each bucket includes the lower degree and not the upper degree
         *  < TEMPERATURE_MIN_CELSIUS, 0 - 5, 0 - 10, 10 - 15, 15 - 20, 20 - 25, 25 - 30, 30 - 35,
         *  ... , >= TEMPERATURE_MAX_CELSIUS */
        int mBucketCount = (TEMPERATURE_MAX_CELSIUS - TEMPERATURE_MIN_CELSIUS) / BUCKET_SPAN + 2;
        mBucketCounters = new long[mBucketCount];
        /* Set end points and initial bucket values */
        for (int i = 0; i < mBucketCount; i++) {
            mBucketCounters[i] = 0;
        }

        mBatteryStats = IBatteryStats.Stub.asInterface(ServiceManager.getService(
                BatteryStats.SERVICE_NAME));
    }

    /** Builds the proto for thermal stats */
    public ThermalStats buildProto() {
        ThermalStats t = new ThermalStats();
        TemperatureHistogram temperatureHistogram = getStats();
        CellularBatteryStats cellularBatteryStats = getCellularBatteryStats();
        if (temperatureHistogram != null) {
            t.skinTemperature = temperatureHistogram;
            long[] temp = cellularBatteryStats.getDeviceShutdownTemperatureC();
            t.deviceShutdownTemperatureC = new long[temp.length];
            System.arraycopy(temp, 0, t.deviceShutdownTemperatureC, 0, temp.length);
        }
        return t;
    }


    /** Reset temperature buckets */
    public void reset() {
        Arrays.fill(mBucketCounters, 0);
    }

    /** Method to receive the temperature alarm */
    @Override
    public void onReceive(Context context, Intent intent) {
        if (sHardwarePropertiesManager != null) {
            float[] skinTemperature = sHardwarePropertiesManager.getDeviceTemperatures(
                    HardwarePropertiesManager.DEVICE_TEMPERATURE_SKIN,
                    HardwarePropertiesManager.TEMPERATURE_CURRENT);
            for (float temperatureCelsius : skinTemperature) {
                mBucketCounters[bucketTemperature(temperatureCelsius)]++;
            }
        } else {
            Log.e(TAG, "Failed to get Hardware Properties Manager");
        }
    }

    private int bucketTemperature(float temperatureCelsius) {
        int val = (int) (temperatureCelsius - TEMPERATURE_MIN_CELSIUS) / BUCKET_SPAN + 1;
        if (val < 0) {
            return 0;
        }
        if (val > (TEMPERATURE_MAX_CELSIUS - TEMPERATURE_MIN_CELSIUS) / BUCKET_SPAN + 1) {
            return (TEMPERATURE_MAX_CELSIUS - TEMPERATURE_MIN_CELSIUS) / BUCKET_SPAN + 1;
        }
        return val;
    }

    /** Provide proto stats information to telephony metrics */
    public TemperatureHistogram getStats() {
        TemperatureHistogram temperatureHistogram = new TemperatureHistogram();
        temperatureHistogram.minTempC = TEMPERATURE_MIN_CELSIUS;
        temperatureHistogram.maxTempC = TEMPERATURE_MAX_CELSIUS;
        temperatureHistogram.bucketSpanC = BUCKET_SPAN;
        temperatureHistogram.bucketCounters = mBucketCounters;
        return temperatureHistogram;
    }

    /** Output histogram to string */
    public String toString() {
        String basic = " Histogram Temperature(C): min bucket temperature = "
                + TEMPERATURE_MIN_CELSIUS
                + " max bucket temperature = " + TEMPERATURE_MAX_CELSIUS
                + " temperature bucket span = " + BUCKET_SPAN;
        StringBuilder intervals = new StringBuilder(" Interval counters:");
        for (long mBucketCounter : mBucketCounters) intervals.append(" ").append(mBucketCounter);
        return basic + intervals;
    }

    /**
     * Get cellular stats from batterystats
     * @return CellularBatteryStats
     */
    private CellularBatteryStats getCellularBatteryStats() {
        try {
            return mBatteryStats.getCellularBatteryStats();
        } catch (RemoteException e) {
        }
        return null;
    }
}
