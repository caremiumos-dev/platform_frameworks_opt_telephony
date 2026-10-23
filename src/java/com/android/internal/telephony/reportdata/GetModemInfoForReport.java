package com.android.internal.telephony.reportdata;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Parcelable;
import android.os.ResultReceiver;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.SynchronousResultReceiver;
import android.telephony.ModemActivityInfo;
import android.telephony.Rlog;
import android.telephony.TelephonyManager;

import java.util.concurrent.TimeoutException;

/**
 * XTC collector that periodically samples {@link ModemActivityInfo} and reports
 * modem activity / sleep exceptions through {@link ReportDataResource}.
 *
 * @hide
 */
public class GetModemInfoForReport {
    private static final String LOG_TAG = "GetModemInfoForReport";
    private static final long EXTERNAL_STATS_SYNC_TIMEOUT_MILLIS = 2000;
    private static final int MAX_TIMES = 3;
    private static final int MODEM_TYPE = 13;
    private static final long REPORT_MODEM_INFO_TINME = 600000;

    private Context mContext;
    private ReportDataResource mReportDataResource;
    private int count;
    private int mBatteryLevel = 0;
    private long reportTimeStamp = 99;

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_BATTERY_CHANGED)) {
                new requestModemInfoAsyncTask().execute(
                        Integer.valueOf(intent.getIntExtra("level", 0)));
            }
        }
    };

    class requestModemInfoAsyncTask extends AsyncTask<Integer, Integer, Integer> {
        @Override
        protected Integer doInBackground(Integer... values) {
            willReportModemInfo(mContext, values[0].intValue());
            return null;
        }
    }

    public GetModemInfoForReport(Context context) {
        mContext = context;
        init();
    }

    private static <T extends Parcelable> T awaitControllerInfo(
            SynchronousResultReceiver receiver) throws TimeoutException {
        if (receiver == null) {
            return null;
        }
        SynchronousResultReceiver.Result result =
                receiver.awaitResult(EXTERNAL_STATS_SYNC_TIMEOUT_MILLIS);
        if (result.bundle != null) {
            result.bundle.setDefusable(true);
            T t = (T) result.bundle.getParcelable("controller_activity");
            if (t != null) {
                return t;
            }
        }
        return null;
    }

    private void init() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_BATTERY_CHANGED);
        mContext.registerReceiver(mIntentReceiver, intentFilter);
        mReportDataResource = new ReportDataResource();
    }

    private boolean shouldSendLog() {
        return SystemProperties.getBoolean("log.tag.network.server", false);
    }

    private void willReportModemInfo(Context context, int level) {
        long now = SystemClock.elapsedRealtime();
        long duration = now - reportTimeStamp;
        log("mBatteryLevel :" + mBatteryLevel + ",level = " + level + ",currentTimeStamp = "
                + now + ",time_duration = " + duration);
        int delta = mBatteryLevel - level;
        if ((delta > 0 && delta < 3 && duration >= 1800000)
                || ((delta > 2 && delta < 6 && duration >= 1200000)
                        || ((delta > 5 && duration >= REPORT_MODEM_INFO_TINME)
                                || duration >= 7200000 || reportTimeStamp == 99))) {
            SynchronousResultReceiver receiver = new SynchronousResultReceiver();
            ((TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE))
                    .requestModemActivityInfo(receiver);
            try {
                ModemActivityInfo info = awaitControllerInfo(receiver);
                int[] txTimeMillis = info.getTxTimeMillis();
                int txTime = 0;
                for (int i = 0; i < 5; i++) {
                    txTime += txTimeMillis[i];
                }
                int sleepTimeMillis = info.getSleepTimeMillis();
                mReportDataResource.putOrginDataForInt(context, 21, sleepTimeMillis,
                        info.getIdleTimeMillis(), txTime, info.getRxTimeMillis());
                if (sleepTimeMillis == 0) {
                    count++;
                } else {
                    count = 0;
                }
                if (count > 1) {
                    mReportDataResource.putOrginDataForInt(context, 22, 1);
                    if (shouldSendLog() && SystemProperties.getInt("log.tag.network.server.type",
                            com.android.internal.telephony.ServiceStateTracker.CS_NOTIFICATION)
                            == MODEM_TYPE) {
                        context.sendBroadcast(new Intent(
                                "com.qualcomm.qcrilmsgtunnel.ACTION_MODEM_SLEEP_EXCEPTION"));
                        log("send Broadcas to get modem log");
                    }
                    count = 0;
                }
            } catch (TimeoutException e) {
                log("Timeout reading modem stats");
            }
            reportTimeStamp = now;
            mBatteryLevel = level;
        }
        if (mBatteryLevel - level < 0) {
            mBatteryLevel = level;
        }
    }

    protected void log(String msg) {
        Rlog.d(LOG_TAG, msg);
    }
}
