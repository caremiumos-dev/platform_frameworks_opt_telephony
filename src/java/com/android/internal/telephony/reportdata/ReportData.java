package com.android.internal.telephony.reportdata;

import android.content.Context;
import android.util.Log;

import com.xtc.behavior.XtcBehavior;

import java.util.LinkedHashMap;

/**
 * XTC base class for modem data reports sent through {@code XtcBehavior}.
 *
 * @hide
 */
public abstract class ReportData {
    private static final String TAG_LOG = "ReportData";
    private boolean DBG = true;
    private Context context;

    public ReportData(Context context) {
        this.context = context;
    }

    abstract void clearData();

    abstract void dataEngine(int... values);

    public void dataEngineForString(String... values) {
    }

    abstract String getFunsionName();

    abstract LinkedHashMap getReprotValue();

    abstract boolean isReportData();

    void log(String tag, String msg) {
        if (DBG) {
            Log.d(TAG_LOG, tag + " : " + msg);
        }
    }

    void logForce(String tag, String msg) {
        Log.d(TAG_LOG, tag + " : " + msg);
    }

    void reportData(String functionName, LinkedHashMap value) {
        log(TAG_LOG, "reportData funsionName " + functionName);
        clearData();
        new XtcBehavior.Builder(context, functionName, value).build().sendData();
    }

    void reportDataForInt(int... values) {
        dataEngine(values);
        if (isReportData()) {
            reportData(getFunsionName(), getReprotValue());
        }
    }

    void reportDataForString(String... values) {
        dataEngineForString(values);
        if (isReportData()) {
            reportData(getFunsionName(), getReprotValue());
        }
    }
}
