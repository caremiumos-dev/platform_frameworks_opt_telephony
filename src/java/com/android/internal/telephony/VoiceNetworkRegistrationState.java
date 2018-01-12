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
 * Subclass of NetworkRegistrationState. It maps to VoiceRegStateResult in types.hal.
 */
public class VoiceNetworkRegistrationState extends NetworkRegistrationState {

    // concurrent services support indicator. if
    // registered on a CDMA system.
    // false - Concurrent services not supported,
    // true - Concurrent services supported
    private final boolean mCssSupported;

    // TSB-58 Roaming Indicator if registered
    // on a CDMA or EVDO system or -1 if not.
    // Valid values are 0-255.
    private final int mRoamingIndicator;

    // indicates whether the current system is in the
    // PRL if registered on a CDMA or EVDO system or -1 if
    // not. 0=not in the PRL, 1=in the PRL
    private final int mSystemIsInPrl;

    // default Roaming Indicator from the PRL,
    // if registered on a CDMA or EVDO system or -1 if not.
    // Valid values are 0-255.
    private final int mDefaultRoamingIndicator;

    public VoiceNetworkRegistrationState(int transportType, int domain, int regState,
            int accessNetworkTechnology, int reasonForDenial, boolean emergencyOnly,
            int[] availableServices, @Nullable CellIdentity cellIdentity, boolean cssSupported,
            int roamingIndicator, int systemIsInPrl, int defaultRoamingIndicator) {
        super(transportType, domain, regState, accessNetworkTechnology, reasonForDenial,
                emergencyOnly, availableServices, cellIdentity);

        mCssSupported = cssSupported;
        mRoamingIndicator = roamingIndicator;
        mSystemIsInPrl = systemIsInPrl;
        mDefaultRoamingIndicator = defaultRoamingIndicator;
    }

    private VoiceNetworkRegistrationState(Parcel source) {
        super(source);
        mCssSupported = source.readBoolean();
        mRoamingIndicator = source.readInt();
        mSystemIsInPrl = source.readInt();
        mDefaultRoamingIndicator = source.readInt();
    }

    public boolean getCssSupported() {
        return mCssSupported;
    }

    public int getRoamingIndicator() {
        return mRoamingIndicator;
    }

    public int getDefaultRoamingIndicator() {
        return mDefaultRoamingIndicator;
    }

    public int getSystemIsInPrl() {
        return mSystemIsInPrl;
    }

    @Override
    public String toString() {
        return "DataNetworkRegistrationState {" + super.toString()
                + " mCssSupported=" + mCssSupported
                + " mRoamingIndicator=" + mRoamingIndicator
                + " mSystemIsInPrl=" + mSystemIsInPrl
                + " mDefaultRoamingIndicator=" + mDefaultRoamingIndicator + "}";
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mCssSupported, mRoamingIndicator, mSystemIsInPrl,
                mDefaultRoamingIndicator);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || !(o instanceof VoiceNetworkRegistrationState)) {
            return false;
        }

        VoiceNetworkRegistrationState other = (VoiceNetworkRegistrationState) o;
        return super.equals(other) && mCssSupported == other.mCssSupported
                && mRoamingIndicator == other.mRoamingIndicator
                && mSystemIsInPrl == other.mSystemIsInPrl
                && mDefaultRoamingIndicator == other.mDefaultRoamingIndicator;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeBoolean(mCssSupported);
        dest.writeInt(mRoamingIndicator);
        dest.writeInt(mSystemIsInPrl);
        dest.writeInt(mDefaultRoamingIndicator);
    }

    public static final Parcelable.Creator<VoiceNetworkRegistrationState> CREATOR =
            new Parcelable.Creator<VoiceNetworkRegistrationState>() {
                @Override
                public VoiceNetworkRegistrationState createFromParcel(Parcel source) {
                    return new VoiceNetworkRegistrationState(source);
                }

                @Override
                public VoiceNetworkRegistrationState[] newArray(int size) {
                    return new VoiceNetworkRegistrationState[size];
                }
            };
}
