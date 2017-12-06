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
 * limitations under the License
 */
package com.android.euicc.data;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.Nullable;
import android.telephony.UiccAccessRule;
import java.util.Arrays;
import java.util.Objects;

/** Represents a carrier privilege rule of a profile on an eUICC. */
public class CarrierPrivilegeRule implements Parcelable {
    private final byte[] mCertificateHash;
    @Nullable private final String mPackageName;
    private final long mAccessType;

    /**
     * Creates a {@link CarrierPrivilegeRule} instance.
     *
     * @param certificateHash The hash of the signing certificate used to whitelist apps under this
     *     rule. Should be length 20 (for SHA-1, deprecated) or 32 (for SHA-256).
     * @param packageName An optional package name identifying the privileged app. If null, all apps
     *     signed by a certificate matching the given certificate hash will be privileged.
     * @param accessType A bit-mask indicating the specific permissions being granted.
     */
    public CarrierPrivilegeRule(
            byte[] certificateHash, @Nullable String packageName, long accessType) {
        mCertificateHash = certificateHash;
        mPackageName = packageName;
        mAccessType = accessType;
    }

    /** @return a new {@link UiccAccessRule} from this rule. */
    public UiccAccessRule toUiccAccessRule() {
        return new UiccAccessRule(mCertificateHash, mPackageName, mAccessType);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        CarrierPrivilegeRule that = (CarrierPrivilegeRule) obj;
        return Arrays.equals(mCertificateHash, that.mCertificateHash)
                && Objects.equals(mPackageName, that.mPackageName)
                && mAccessType == that.mAccessType;
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = 31 * result + Arrays.hashCode(mCertificateHash);
        result = 31 * result + Objects.hashCode(mPackageName);
        result = 31 * result + Objects.hashCode(mAccessType);
        return result;
    }

    @Override
    public String toString() {
        return "CarrierPrivilegeRule (mCertificateHash="
                + Arrays.toString(mCertificateHash)
                + ", mPackageName="
                + mPackageName
                + ", mAccessRule="
                + mAccessType
                + ")";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeByteArray(mCertificateHash);
        dest.writeString(mPackageName);
        dest.writeLong(mAccessType);
    }

    private CarrierPrivilegeRule(Parcel in) {
        mCertificateHash = in.createByteArray();
        mPackageName = in.readString();
        mAccessType = in.readLong();
    }

    public static final Creator<CarrierPrivilegeRule> CREATOR =
            new Creator<CarrierPrivilegeRule>() {
                @Override
                public CarrierPrivilegeRule createFromParcel(Parcel source) {
                    return new CarrierPrivilegeRule(source);
                }

                @Override
                public CarrierPrivilegeRule[] newArray(int size) {
                    return new CarrierPrivilegeRule[size];
                }
            };
}
