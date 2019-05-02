/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.internal.telephony.cdnr;

/**
 * A container of carrier display name.
 */
public class CarrierDisplayNameData {
    /** Service provider name. */
    private final String mSpn;

    /** Data service provider name. */
    private final String mDataSpn;

    /** PLMN network name */
    private final String mPlmn;

    /** {@code True} if display service provider name is required. */
    private final boolean mShowSpn;

    /** {@code True} if display PLMN network name is required. */
    private final boolean mShowPlmn;

    private CarrierDisplayNameData(String spn, String dataSpn, boolean showSpn, String plmn,
            boolean showPlmn) {
        this.mSpn = spn;
        this.mDataSpn = dataSpn;
        this.mShowSpn = showSpn;
        this.mPlmn = plmn;
        this.mShowPlmn = showPlmn;
    }

    /**
     * Get the service provider name.
     * @return service provider name.
     */
    public String getSpn() {
        return mSpn;
    }

    /**
     * Get the service provider name of data provider.
     * @return service provider name of data provider.
     */
    public String getDataSpn() {
        return mDataSpn;
    }

    /**
     * Get the PLMN network name.
     * @return PLMN network name.
     */
    public String getPlmn() {
        return mPlmn;
    }

    /**
     * Whether the spn should be displayed.
     * @return
     */
    public boolean shouldShowSpn() {
        return mShowSpn;
    }

    /**
     * Whether the PLMN should be displayed.
     * @return
     */
    public boolean shouldShowPlmn() {
        return mShowPlmn;
    }

    @Override
    public String toString() {
        return String.format("{ spn = %s, dataSpn = %s, showSpn = %b, plmn = %s, showPlmn = %b",
                mSpn, mDataSpn, mShowSpn, mPlmn, mShowPlmn);
    }

    /** Builder class for {@link com.android.internal.telephony.cdnr.CarrierDisplayNameData}. */
    public static final class Builder {
        private String mSpn;
        private String mDataSpn;
        private String mPlmn;
        private boolean mShowSpn;
        private boolean mShowPlmn;

        public Builder() {
            mSpn = null;
            mDataSpn = null;
            mPlmn = null;
            mShowPlmn = false;
            mShowSpn = false;
        }

        /** Create a {@link com.android.internal.telephony.cdnr.CarrierDisplayNameData} instance. */
        public CarrierDisplayNameData build() {
            return new CarrierDisplayNameData(mSpn, mDataSpn, mShowSpn, mPlmn, mShowPlmn);
        }

        /** Set service provider name. */
        public Builder setSpn(String spn) {
            mSpn = spn;
            return this;
        }

        /** Set data service provider name. */
        public Builder setDataSpn(String dataSpn) {
            mDataSpn = dataSpn;
            return this;
        }

        /** Set PLMN network name. */
        public Builder setPlmn(String plmn) {
            mPlmn = plmn;
            return this;
        }

        /** Set whether the service provider name should be displayed. */
        public Builder setShowSpn(boolean showSpn) {
            mShowSpn = showSpn;
            return this;
        }

        /** Set whether the PLMN network name should be displayed. */
        public Builder setShowPlmn(boolean showPlmn) {
            mShowPlmn = showPlmn;
            return this;
        }
    }
}
