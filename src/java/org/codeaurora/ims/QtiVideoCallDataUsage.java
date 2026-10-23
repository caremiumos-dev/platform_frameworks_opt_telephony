/*
 * Copyright (c) 2017, The Linux Foundation. All rights reserved.
 *
 * Not a Contribution.
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

package org.codeaurora.ims;

import android.os.Parcel;
import android.os.Parcelable;

public class QtiVideoCallDataUsage implements Parcelable {
    public static final int DATA_USAGE_INVALID_VALUE = -1;
    public static final int DATA_USAGE_LTE = 0;
    public static final int DATA_USAGE_WLAN = 1;
    private long[] mDataUsage;
    private static final String[] TEXT = {"LteDataUsage = ", " WlanDataUsage = "};
    public static final Parcelable.Creator<QtiVideoCallDataUsage> CREATOR =
            new Parcelable.Creator<QtiVideoCallDataUsage>() {
        @Override
        public QtiVideoCallDataUsage createFromParcel(Parcel parcel) {
            return new QtiVideoCallDataUsage(parcel);
        }

        @Override
        public QtiVideoCallDataUsage[] newArray(int size) {
            return new QtiVideoCallDataUsage[size];
        }
    };

    public QtiVideoCallDataUsage(Parcel parcel) {
        readFromParcel(parcel);
    }

    public QtiVideoCallDataUsage(long[] dataUsage) {
        if (dataUsage == null || dataUsage.length == 0) {
            throw new RuntimeException();
        }
        mDataUsage = dataUsage;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public long getLteDataUsage() {
        if (mDataUsage.length > 0) {
            return mDataUsage[0];
        }
        return -1L;
    }

    public long getWlanDataUsage() {
        if (mDataUsage.length > 1) {
            return mDataUsage[1];
        }
        return -1L;
    }

    public void readFromParcel(Parcel parcel) {
        mDataUsage = parcel.createLongArray();
    }

    @Override
    public String toString() {
        if (mDataUsage == null) {
            return null;
        }
        String result = "";
        for (int i = 0; i < mDataUsage.length; i++) {
            result = result + TEXT[i] + mDataUsage[i];
        }
        return result;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeLongArray(mDataUsage);
    }
}
