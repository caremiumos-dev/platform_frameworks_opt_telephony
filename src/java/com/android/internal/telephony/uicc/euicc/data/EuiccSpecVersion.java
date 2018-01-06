/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.internal.telephony.uicc.euicc.data;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Arrays;

/**
 * This represents the version of GSMA SGP.22 spec in the form of 3 numbers: major, minor, and
 * revision.
 *
 * @hide
 *
 * TODO(b/35851809): Make this a @SystemApi.
 */
public final class EuiccSpecVersion implements Comparable<EuiccSpecVersion>, Parcelable {
    /** This represents a range of version. */
    public static final class Range {
        private final EuiccSpecVersion mMinVersion;
        private final EuiccSpecVersion mMaxVersion;

        /** Creates a range between {@code minVersion} and {@code maxVersion} (both inclusive). */
        public Range(EuiccSpecVersion minVersion, EuiccSpecVersion maxVersion) {
            mMinVersion = minVersion;
            mMaxVersion = maxVersion;
        }

        /** Checks whether given range is included. */
        public boolean includes(EuiccSpecVersion version) {
            return mMinVersion.compareTo(version) <= 0 && version.compareTo(mMaxVersion) <= 0;
        }

        @Override
        public String toString() {
            return "EuiccSpecVersion.Range (minVersion="
                    + mMinVersion.toString()
                    + ", maxVersion="
                    + mMaxVersion.toString()
                    + ")";
        }
    }

    private final int[] mVersionValues = new int[3];

    public EuiccSpecVersion(int major, int minor, int revision) {
        mVersionValues[0] = major;
        mVersionValues[1] = minor;
        mVersionValues[2] = revision;
    }

    public int getMajor() {
        return mVersionValues[0];
    }

    public int getMinor() {
        return mVersionValues[1];
    }

    public int getRevision() {
        return mVersionValues[2];
    }

    @Override
    public int compareTo(EuiccSpecVersion that) {
        if (getMajor() > that.getMajor()) {
            return 1;
        } else if (getMajor() < that.getMajor()) {
            return -1;
        }
        if (getMinor() > that.getMinor()) {
            return 1;
        } else if (getMinor() < that.getMinor()) {
            return -1;
        }
        if (getRevision() > that.getRevision()) {
            return 1;
        } else if (getRevision() < that.getRevision()) {
            return -1;
        }
        return 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        return Arrays.equals(mVersionValues, ((EuiccSpecVersion) obj).mVersionValues);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(mVersionValues);
    }

    @Override
    public String toString() {
        return mVersionValues[0] + "." + mVersionValues[1] + "." + mVersionValues[2];
    }


    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mVersionValues[0]);
        dest.writeInt(mVersionValues[1]);
        dest.writeInt(mVersionValues[2]);
    }

    private EuiccSpecVersion(Parcel source) {
        mVersionValues[0] = source.readInt();
        mVersionValues[1] = source.readInt();
        mVersionValues[2] = source.readInt();
    }

    public static final Creator<EuiccSpecVersion> CREATOR =
            new Creator<EuiccSpecVersion>() {
                @Override
                public EuiccSpecVersion createFromParcel(Parcel source) {
                    return new EuiccSpecVersion(source);
                }

                @Override
                public EuiccSpecVersion[] newArray(int size) {
                    return new EuiccSpecVersion[size];
                }
            };

}
