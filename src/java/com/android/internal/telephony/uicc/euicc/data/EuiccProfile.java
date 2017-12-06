/*
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
 * limitations under the License
 */

package com.android.euicc.data;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;
import java.util.Objects;

/** This represents the metadata of a profile on eUICC or to be downloaded remotely. */
public final class EuiccProfile implements Parcelable {
    /** Profile policy rules (bit mask) */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
        value = {
            PolicyRule.DO_NOT_DISABLE,
            PolicyRule.DO_NOT_DELETE,
            PolicyRule.DELETE_AFTER_DISABLING
        },
        flag = true
    )
    public @interface PolicyRule {
        /** Once this profile is enabled, it cannot be disabled. */
        int DO_NOT_DISABLE = 1;
        /** This profile cannot be deleted. */
        int DO_NOT_DELETE = 1 << 1;
        /** This profile should be deleted after being disabled. */
        int DELETE_AFTER_DISABLING = 1 << 2;
    }

    /** Class of the profile */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({ProfileClass.TESTING, ProfileClass.PROVISIONING, ProfileClass.OPERATIONAL})
    public @interface ProfileClass {
        /** Testing profiles */
        int TESTING = 0;
        /** Provisioning profiles which are pre-loaded on eUICC */
        int PROVISIONING = 1;
        /** Operational profiles which can be pre-loaded or downloaded */
        int OPERATIONAL = 2;
    }

    /** State of the profile */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({ProfileState.DISABLED, ProfileState.ENABLED})
    public @interface ProfileState {
        /** Disabled profiles */
        int DISABLED = 0;
        /** Enabled profile */
        int ENABLED = 1;
    }

    private final String mIccid;
    private final String mNickname;
    private final String mServiceProviderName;
    private final String mProfileName;
    @ProfileClass private final int mProfileClass;
    @ProfileState private final int mState;
    private final OperatorId mOperatorId;
    @PolicyRule private final int mPolicyRules;
    @Nullable private final CarrierPrivilegeRule[] mCarrierPrivilegeRules;

    /**
     * The builder to build a new {@link EuiccProfile} instance. Values are only check for some
     * mutable entities defined in the GSMA spec SGP.22. Readonly values from eUICC are not checked.
     */
    public static final class Builder {
        private String mIccid;
        private String mNickname;
        private String mServiceProviderName;
        private String mProfileName;
        @ProfileClass private int mProfileClass;
        @ProfileState private int mState;
        private OperatorId mOperatorId;
        @PolicyRule private int mPolicyRules;
        @Nullable private CarrierPrivilegeRule[] mCarrierPrivilegeRules;

        public Builder() {}

        public Builder(EuiccProfile baseProfile) {
            mIccid = baseProfile.mIccid;
            mNickname = baseProfile.mNickname;
            mServiceProviderName = baseProfile.mServiceProviderName;
            mProfileName = baseProfile.mProfileName;
            mProfileClass = baseProfile.mProfileClass;
            mState = baseProfile.mState;
            mOperatorId = baseProfile.mOperatorId;
            mPolicyRules = baseProfile.mPolicyRules;
            mCarrierPrivilegeRules = baseProfile.mCarrierPrivilegeRules;
        }

        /**
         * Builds the profile instance. This builder should not be used anymore after this method is
         * called, otherwise {@link NullPointerException} will be thrown.
         */
        public EuiccProfile build() {
            if (mIccid == null) {
                throw new IllegalStateException("ICCID must be set for a profile.");
            }
            return new EuiccProfile(
                    mIccid,
                    mNickname,
                    mServiceProviderName,
                    mProfileName,
                    mProfileClass,
                    mState,
                    mOperatorId,
                    mPolicyRules,
                    mCarrierPrivilegeRules);
        }

        public Builder setIccid(String value) {
            mIccid = value;
            return this;
        }

        public Builder setNickname(String value) {
            mNickname = value;
            return this;
        }

        public Builder setServiceProviderName(String value) {
            mServiceProviderName = value;
            return this;
        }

        public Builder setProfileName(String value) {
            mProfileName = value;
            return this;
        }

        public Builder setState(@ProfileState int value) {
            mState = value;
            return this;
        }

        public Builder setProfileClass(@ProfileClass int value) {
            mProfileClass = value;
            return this;
        }

        public Builder setOperatorId(OperatorId operatorId) {
            mOperatorId = operatorId;
            return this;
        }

        public Builder setPolicyRules(@PolicyRule int value) {
            mPolicyRules = value;
            return this;
        }

        public Builder setCarrierPrivilegeRules(
                @Nullable CarrierPrivilegeRule[] carrierPrivilegeRules) {
            mCarrierPrivilegeRules = carrierPrivilegeRules;
            return this;
        }
    }

    private EuiccProfile(
            String iccid,
            String nickname,
            String serviceProviderName,
            String profileName,
            @ProfileClass int profileClass,
            @ProfileState int state,
            OperatorId operatorId,
            @PolicyRule int policyRules,
            @Nullable CarrierPrivilegeRule[] carrierPrivilegeRules) {
        mIccid = iccid;
        mNickname = nickname;
        mServiceProviderName = serviceProviderName;
        mProfileName = profileName;
        mOperatorId = operatorId;
        mProfileClass = profileClass;
        mState = state;
        mPolicyRules = policyRules;
        mCarrierPrivilegeRules = carrierPrivilegeRules;
    }

    /** @return The ICCID string. */
    public String getIccid() {
        return mIccid;
    }

    public String getNickname() {
        return mNickname;
    }

    public String getServiceProviderName() {
        return mServiceProviderName;
    }

    @ProfileClass
    public int getProfileClass() {
        return mProfileClass;
    }

    @ProfileState
    public int getState() {
        return mState;
    }

    public String getProfileName() {
        return mProfileName;
    }

    public OperatorId getOperatorId() {
        return mOperatorId;
    }

    @PolicyRule
    public int getPolicyRules() {
        return mPolicyRules;
    }

    public boolean hasPolicyRules() {
        return mPolicyRules != 0;
    }

    public boolean hasPolicyRule(@PolicyRule int policy) {
        return (mPolicyRules & policy) != 0;
    }

    @Nullable
    public CarrierPrivilegeRule[] getCarrierPrivilegeRules() {
        return mCarrierPrivilegeRules;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        EuiccProfile that = (EuiccProfile) obj;
        return Objects.equals(mIccid, that.mIccid)
                && Objects.equals(mNickname, that.mNickname)
                && Objects.equals(mServiceProviderName, that.mServiceProviderName)
                && Objects.equals(mProfileName, that.mProfileName)
                && mProfileClass == that.mProfileClass
                && mState == that.mState
                && Objects.equals(mOperatorId, that.mOperatorId)
                && mPolicyRules == that.mPolicyRules
                && Arrays.equals(mCarrierPrivilegeRules, that.mCarrierPrivilegeRules);
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = 31 * result + Objects.hashCode(mIccid);
        result = 31 * result + Objects.hashCode(mNickname);
        result = 31 * result + Objects.hashCode(mServiceProviderName);
        result = 31 * result + Objects.hashCode(mProfileName);
        result = 31 * result + mProfileClass;
        result = 31 * result + mState;
        result = 31 * result + Objects.hashCode(mOperatorId);
        result = 31 * result + mPolicyRules;
        result = 31 * result + Arrays.hashCode(mCarrierPrivilegeRules);
        return result;
    }

    @Override
    public String toString() {
        return "EuiccProfile (nickname="
                + mNickname
                + ", serviceProviderName="
                + mServiceProviderName
                + ", profileName="
                + mProfileName
                + ", operatorId="
                + mOperatorId.toString()
                + ", state="
                + mState
                + ", profileClass="
                + mProfileClass
                + ", policyRules="
                + mPolicyRules
                + ", accessRules="
                + Arrays.toString(mCarrierPrivilegeRules)
                + ")";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mIccid);
        dest.writeString(mNickname);
        dest.writeString(mServiceProviderName);
        dest.writeString(mProfileName);
        mOperatorId.writeToParcel(dest, flags);
        dest.writeInt(mProfileClass);
        dest.writeInt(mState);
        dest.writeInt(mPolicyRules);
        dest.writeTypedArray(mCarrierPrivilegeRules, flags);
    }

    private EuiccProfile(Parcel source) {
        mIccid = source.readString();
        mNickname = source.readString();
        mServiceProviderName = source.readString();
        mProfileName = source.readString();
        mOperatorId = OperatorId.CREATOR.createFromParcel(source);
        // noinspection WrongConstant
        mProfileClass = source.readInt();
        // noinspection WrongConstant
        mState = source.readInt();
        // noinspection WrongConstant
        mPolicyRules = source.readInt();
        mCarrierPrivilegeRules = source.createTypedArray(CarrierPrivilegeRule.CREATOR);
    }

    public static final Creator<EuiccProfile> CREATOR =
            new Creator<EuiccProfile>() {
                @Override
                public EuiccProfile createFromParcel(Parcel source) {
                    return new EuiccProfile(source);
                }

                @Override
                public EuiccProfile[] newArray(int size) {
                    return new EuiccProfile[size];
                }
            };
}
