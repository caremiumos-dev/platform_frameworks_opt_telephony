/*
 * Copyright 2017 The Android Open Source Project
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

package com.android.internal.telephony.uicc;

/**
 * This class represents the status of the physical UICC slots.
 *
 * {@hide}
 */
public class IccSlotStatus {

    public enum CardState {
        CARDSTATE_ABSENT,
        CARDSTATE_PRESENT,
        CARDSTATE_ERROR,
        CARDSTATE_RESTRICTED;
    }

    public enum SlotState {
        SLOTSTATE_INACTIVE,
        SLOTSTATE_ACTIVE;
    }

    public CardState  cardState;
    public SlotState  slotState;
    public int        logicalSlotIndex;
    public String[]   iccid;

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("IccSlotStatus {").append(cardState).append(",")
        .append(slotState).append(",")
        .append("logicalSlotIndex=").append(logicalSlotIndex);

        if (iccid != null) {
            for (String id : iccid) {
                sb.append(id).append(",");
            }
        }

        sb.append("}");
        return sb.toString();
    }

}
