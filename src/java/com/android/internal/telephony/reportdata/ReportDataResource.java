package com.android.internal.telephony.reportdata;

import android.content.Context;
import android.util.Log;

/**
 * XTC dispatcher for the modem data reports.
 *
 * @hide
 */
public class ReportDataResource implements ReportDataType {
    private static final String TAG = "ReportDataResource";

    private Context context;
    private ReportData mCollectDataForNoLTE;
    private ReportData mReportDataForModemInfo;
    private ReportData mReportDataForModemSleepException;

    public ReportDataResource() {
    }

    public ReportDataResource(Context context) {
        this.context = context;
    }

    public void putOrginDataForInt(Context context, int type, int... values) {
        Log.e(TAG, "putOrginDataForInt type = " + type + ",msg[0]" + values[0]);
        this.context = context;
        switch (type) {
            case REPORT_TYPE_MODEM_INFO:
                if (mReportDataForModemInfo == null) {
                    mReportDataForModemInfo = new ReportDataForModemInfo(context);
                }
                mReportDataForModemInfo.reportDataForInt(values);
                break;
            case REPORT_TYPE_MODEM_SLEEP_EXCEPTION_INFO:
                if (mReportDataForModemSleepException == null) {
                    mReportDataForModemSleepException = new ReportDataForModemSleepException(context);
                }
                mReportDataForModemSleepException.reportDataForInt(values);
                break;
            case COLLECT_FOR_NO_LTE:
                if (mCollectDataForNoLTE == null) {
                    mCollectDataForNoLTE = new CollectDataForNoLTE(context);
                }
                mCollectDataForNoLTE.reportDataForInt(values);
                break;
            default:
                break;
        }
    }

    public void putOrginDataForString(Context context, int type, String... values) {
        Log.e(TAG, "putOrginDataForString type = " + type + ",msg[0]" + values[0]);
        this.context = context;
        switch (type) {
            case REPORT_TYPE_MODEM_INFO:
                if (mReportDataForModemInfo == null) {
                    mReportDataForModemInfo = new ReportDataForModemInfo(context);
                }
                mReportDataForModemInfo.reportDataForString(values);
                break;
            case REPORT_TYPE_MODEM_SLEEP_EXCEPTION_INFO:
                if (mReportDataForModemSleepException == null) {
                    mReportDataForModemSleepException = new ReportDataForModemSleepException(context);
                }
                mReportDataForModemSleepException.reportDataForString(values);
                break;
            default:
                break;
        }
    }
}
