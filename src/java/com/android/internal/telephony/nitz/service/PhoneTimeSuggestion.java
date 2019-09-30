/*
 * Copyright 2019 The Android Open Source Project
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

package com.android.internal.telephony.nitz.service;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.TimestampedValue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A suggested time from a Phone-based signal, e.g. from NITZ.
 */
public final class PhoneTimeSuggestion implements Parcelable {

    public static final Creator<PhoneTimeSuggestion> CREATOR =
            new Creator<PhoneTimeSuggestion>() {
                public PhoneTimeSuggestion createFromParcel(Parcel in) {
                    return PhoneTimeSuggestion.createFromParcel(in);
                }

                public PhoneTimeSuggestion[] newArray(int size) {
                    return new PhoneTimeSuggestion[size];
                }
            };

    /**
     * The ID of the phone this suggestion is associated with. For multiple-sim devices this
     * helps to establish origin so filtering / stickiness can be implemented.
     */
    private final int mPhoneId;

    /**
     * The suggestion. {@code null} means there is no current suggestion and any previous suggestion
     * should be forgotten.
     */
    @Nullable
    private TimestampedValue<Long> mUtcTime;

    /**
     * Free-form debug information about how the signal was derived. Used for debug only,
     * intentionally not used in equals(), etc.
     */
    private List<String> mDebugInfo;

    public PhoneTimeSuggestion(int phoneId) {
        this.mPhoneId = phoneId;
    }

    @SuppressWarnings("unchecked")
    private static PhoneTimeSuggestion createFromParcel(Parcel in) {
        ClassLoader classLoader = PhoneTimeSuggestion.class.getClassLoader();
        int phoneId = in.readInt();
        PhoneTimeSuggestion phoneTimeSuggestion = new PhoneTimeSuggestion(phoneId);
        phoneTimeSuggestion.mUtcTime = in.readParcelable(null /* classLoader */);
        phoneTimeSuggestion.mDebugInfo = (List<String>) in.readArrayList(classLoader);
        return phoneTimeSuggestion;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(mPhoneId);
        dest.writeParcelable(mUtcTime, 0);
        dest.writeList(mDebugInfo);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public int getPhoneId() {
        return mPhoneId;
    }

    @Nullable
    public TimestampedValue<Long> getUtcTime() {
        return mUtcTime;
    }

    public void setUtcTime(@Nullable TimestampedValue<Long> utcTime) {
        mUtcTime = utcTime;
    }

    public List<String> getDebugInfo() {
        return Collections.unmodifiableList(mDebugInfo);
    }

    /**
     * Associates information with the instance that can be useful for debugging / logging. The
     * information is present in {@link #toString()} but is not considered for
     * {@link #equals(Object)} and {@link #hashCode()}.
     */
    public void addDebugInfo(String... debugInfos) {
        if (mDebugInfo == null) {
            mDebugInfo = new ArrayList<>();
        }
        mDebugInfo.addAll(Arrays.asList(debugInfos));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PhoneTimeSuggestion that = (PhoneTimeSuggestion) o;
        return mPhoneId == that.mPhoneId
                && Objects.equals(mUtcTime, that.mUtcTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mPhoneId, mUtcTime);
    }

    @Override
    public String toString() {
        return "PhoneTimeSuggestion{"
                + "mPhoneId=" + mPhoneId
                + ", mUtcTime='" + mUtcTime
                + ", mDebugInfo=" + mDebugInfo
                + '}';
    }
}
