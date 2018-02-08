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

/**
 * Link Bandwidth Information from the Radio
 */
public class LinkCapacityEstimate {
    /** Any field that is not reported shall be set to INVALID */
    public static final int INVALID = -1;

    /** LCE is active; Deprecated in HAL 1.2 */
    public static final int STATUS_ACTIVE = 0;

    /** LCE is suspended; Deprecated in HAL 1.2 */
    public static final int STATUS_SUSPENDED = 1;

    /** Downlink bandwidth in kbps */
    public final int downlinkBandwidth; // capacity in kbps

    /** Uplink bandwidth in kbps; added in HAL 1.2 */
    public final int uplinkBandwidth;

    /** Confidence of the downlink estimate; deprecated in HAL 1.2 */
    public final int confidence;

    /** Status of the LCE; deprecated in HAL 1.2 */
    public final int status; // either STATUS_ACTIVE, STATUS_SUSPENDED, or INVALID

    /** Constructor matching the estimate in Radio HAL v1.0 */
    public LinkCapacityEstimate(int downlinkBandwidth, int confidence, int status) {
        this.downlinkBandwidth = downlinkBandwidth;
        this.confidence = confidence;
        this.status = status;
        this.uplinkBandwidth = INVALID;
    }

    /** Constructor matching the estimate in Radio HAL v1.2 */
    public LinkCapacityEstimate(int downlinkBandwidth, int uplinkBandwidth) {
        this.downlinkBandwidth = downlinkBandwidth;
        this.uplinkBandwidth = uplinkBandwidth;
        this.confidence = INVALID;
        this.status = INVALID;
    }

    @Override
    public String toString() {
        return new StringBuilder()
                .append("{downlinkBandwidth=")
                .append(downlinkBandwidth)
                .append(", uplinkBandwidth=")
                .append(uplinkBandwidth)
                .append(", confidence=")
                .append(confidence)
                .append(", status=")
                .append(status)
                .toString();
    }
}
