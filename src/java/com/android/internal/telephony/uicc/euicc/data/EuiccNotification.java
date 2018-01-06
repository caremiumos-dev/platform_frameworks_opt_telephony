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

/**
 * This represents a signed notification which is defined in SGP.22. It can be either a profile
 * installation result or a notification generated for profile operations (e.g., enabling,
 * disabling, or deleting).
 */
public class EuiccNotification implements Parcelable {
    /** Event */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
            value = {Event.INSTALL, Event.ENABLE, Event.DISABLE, Event.DELETE},
            flag = true
    )
    public @interface Event {
        /** A profile is downloaded and installed. */
        int INSTALL = 1;
        /** A profile is enabled. */
        int ENABLE = 1 << 1;
        /** A profile is disabled. */
        int DISABLE = 1 << 2;
        /** A profile is deleted. */
        int DELETE = 1 << 3;
    }

    /** Value of the bits of all above events */
    @Event
    public static final int ALL_EVENTS =
            Event.INSTALL | Event.ENABLE | Event.DISABLE | Event.DELETE;

    private final int mSeq;
    private final String mTargetAddr;
    @Event private final int mEvent;
    @Nullable private final byte[] mData;

    /**
     * Creates an instance.
     *
     * @param seq The sequence number of this notification.
     * @param targetAddr The target server where to send this notification.
     * @param event The event which causes this notification.
     * @param data The data which needs to be sent to the target server. This can be null for
     *     building a list of notification metadata without data.
     */
    public EuiccNotification(int seq, String targetAddr, @Event int event, @Nullable byte[] data) {
        mSeq = seq;
        mTargetAddr = targetAddr;
        mEvent = event;
        mData = data;
    }

    /** @return The sequence number of this notification. */
    public int getSeq() {
        return mSeq;
    }

    /** @return The target server address where this notification should be sent to. */
    public String getTargetAddr() {
        return mTargetAddr;
    }

    /** @return The event of this notification. */
    @Event
    public int getEvent() {
        return mEvent;
    }

    /** @return The notification data which needs to be sent to the target server. */
    @Nullable
    public byte[] getData() {
        return mData;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        EuiccNotification that = (EuiccNotification) obj;
        return mSeq == that.mSeq
                && Objects.equals(mTargetAddr, that.mTargetAddr)
                && mEvent == that.mEvent
                && Arrays.equals(mData, that.mData);
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = 31 * result + mSeq;
        result = 31 * result + Objects.hashCode(mTargetAddr);
        result = 31 * result + mEvent;
        result = 31 * result + Arrays.hashCode(mData);
        return result;
    }

    @Override
    public String toString() {
        return "EuiccNotification (seq="
                + mSeq
                + ", targetAddr="
                + mTargetAddr
                + ", event="
                + mEvent
                + ", data="
                + (mData == null ? "null" : "byte[" + mData.length + "]")
                + ")";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mSeq);
        dest.writeString(mTargetAddr);
        dest.writeInt(mEvent);
        dest.writeByteArray(mData);
    }

    private EuiccNotification(Parcel source) {
        mSeq = source.readInt();
        mTargetAddr = source.readString();
        mEvent = source.readInt();
        mData = source.createByteArray();
    }

    public static final Creator<EuiccNotification> CREATOR =
            new Creator<EuiccNotification>() {
                @Override
                public EuiccNotification createFromParcel(Parcel source) {
                    return new EuiccNotification(source);
                }

                @Override
                public EuiccNotification[] newArray(int size) {
                    return new EuiccNotification[size];
                }
            };
}
