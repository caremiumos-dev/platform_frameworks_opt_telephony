package com.android.internal.telephony.ratswitch;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * XTC attenuation-time engine backing {@link NetworkRatPolicyManager}. Holds
 * the adaptive list of network-switch durations.
 *
 * @hide
 */
public class AttenuationEngine {
    private static final String TAG = "AttenuationEngine";
    private static final long CORRECTION_VALUE = 360000;
    private static final long TIME_BASE_TEN_MIN = 600000;
    private static final int VERY_LONG_TIME = 4;

    private List<Long> babseTimeList;
    private int decay;
    private List<Long> timeList;

    private static class InstanceHolder {
        private static final AttenuationEngine INSTANCE = new AttenuationEngine();
    }

    private AttenuationEngine() {
        decay = 0;
        initTimeList();
    }

    private List<Long> getBabseTimeList() {
        return babseTimeList;
    }

    private int getDecay() {
        return decay;
    }

    public static AttenuationEngine getInstance() {
        return InstanceHolder.INSTANCE;
    }

    private void initTimeList() {
        Log.e(TAG, "initTimeList ");
        ArrayList<Long> list = new ArrayList<>();
        babseTimeList = new ArrayList<>();
        for (int i = 0; i < VERY_LONG_TIME; i++) {
            long value = (((long) ((i * i) + 1)) * TIME_BASE_TEN_MIN) + CORRECTION_VALUE;
            list.add(i, value);
            babseTimeList.add(i, value);
        }
        setTimeList(list);
    }

    private void modifyTimeList(int index, long value) {
        getTimeList().set(index, value);
    }

    private void setDecay(int decay) {
        this.decay = decay;
    }

    private void setTimeList(List<Long> timeList) {
        this.timeList = timeList;
    }

    public long getTimeFormList(int index) {
        return getTimeList().get(index).longValue();
    }

    public List<Long> getTimeList() {
        return timeList;
    }

    public void shouldModifyTimeList(long duration) {
        double factor = 1.0d;
        List<Long> base = babseTimeList;
        if (duration <= base.get(0).longValue()) {
            setDecay(1);
        } else if (duration <= base.get(1).longValue()) {
            setDecay(2);
        } else if (duration <= base.get(2).longValue()) {
            setDecay(3);
        } else {
            setDecay(4);
        }
        Log.e(TAG, "dccayTime = " + duration + ".... decay = " + getDecay()
                + " babseTimeList 2 = " + babseTimeList.get(2));
        switch (getDecay()) {
            case 1:
                factor = 3.0d;
                break;
            case 2:
                for (int i = 0; i < VERY_LONG_TIME; i++) {
                    modifyTimeList(i, ((2 * duration) - CORRECTION_VALUE)
                            + getTimeList().get(i).longValue());
                }
                factor = 0.0d;
                break;
            case 4:
                factor = 0.5d;
                break;
            default:
                break;
        }
        if (factor != 0.0d) {
            for (int i = 0; i < VERY_LONG_TIME; i++) {
                modifyTimeList(i, (long) (getBabseTimeList().get(i).longValue() * factor));
            }
        }
    }
}
