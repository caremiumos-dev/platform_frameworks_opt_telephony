/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.annotation.Nullable;
import android.os.Parcel;
import android.os.Parcelable;
import android.telephony.CellIdentity;
import android.telephony.NetworkRegistrationState;

import java.util.Objects;

/**
 * Subclass of NetworkRegistrationState. It maps to DataRegStateResult in types.hal.
 */
public class DataNetworkRegistrationState extends NetworkRegistrationState {
    // The maximum number of simultaneous Data Calls that
    // must be established using setupDataCall().
    private final int mMaxDataCalls;

    public DataNetworkRegistrationState(int transportType, int domain, int regState,
            int accessNetworkTechnology, int reasonForDenial, boolean emergencyOnly,
            int[] availableServices, @Nullable CellIdentity cellIdentity, int maxDataCalls) {
        super(transportType, domain, regState, accessNetworkTechnology, reasonForDenial,
                emergencyOnly, availableServices, cellIdentity);

        mMaxDataCalls = maxDataCalls;
    }

    private DataNetworkRegistrationState(Parcel source) {
        super(source);
        mMaxDataCalls = source.readInt();
    }

    public int getMaxDataCalls() {
        return mMaxDataCalls;
    }

    @Override
    public String toString() {
        return "DataNetworkRegistrationState {" + super.toString() + " mMaxDataCalls="
                + mMaxDataCalls + "}";
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mMaxDataCalls);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || !(o instanceof DataNetworkRegistrationState)) {
            return false;
        }

        DataNetworkRegistrationState other = (DataNetworkRegistrationState) o;
        return super.equals(other) && mMaxDataCalls == other.mMaxDataCalls;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeInt(mMaxDataCalls);
    }

    public static final Parcelable.Creator<DataNetworkRegistrationState> CREATOR =
            new Parcelable.Creator<DataNetworkRegistrationState>() {
                @Override
                public DataNetworkRegistrationState createFromParcel(Parcel source) {
                    return new DataNetworkRegistrationState(source);
                }

                @Override
                public DataNetworkRegistrationState[] newArray(int size) {
                    return new DataNetworkRegistrationState[size];
                }
            };
}
