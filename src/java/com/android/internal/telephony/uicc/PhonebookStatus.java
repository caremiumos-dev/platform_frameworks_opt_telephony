/*
 * Copyright (C) 2021 The Android Open Source Project
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
 * Represents the status of the received phonebook entries from the SIM.
 *
 * {@hide}
 */
public class PhonebookStatus {
    public enum ReceivedState {
        RS_NONE,
        RS_OK,
        RS_ERROR,
        RS_ABORT,
        RS_FINAL
    }

    private ReceivedState mState = ReceivedState.RS_NONE;

    public PhonebookStatus(int state) {
        setState(convertState(state));
    }

    public void setState(ReceivedState state) {
        mState = state;
    }

    public ReceivedState getState() {
        return mState;
    }

    public boolean isCompleted() {
        return mState == ReceivedState.RS_FINAL;
    }

    public boolean isRetryNeeded() {
        return mState == ReceivedState.RS_ABORT;
    }

    public boolean isOk() {
        return mState == ReceivedState.RS_OK;
    }

    public ReceivedState convertState(int state) {
        ReceivedState receivedState = ReceivedState.RS_NONE;
        switch(state) {
            case 0:
                receivedState = ReceivedState.RS_OK;
                break;
            case 1:
                receivedState = ReceivedState.RS_ERROR;
                break;
            case 2:
                receivedState = ReceivedState.RS_ABORT;
                break;
            case 3:
                receivedState = ReceivedState.RS_FINAL;
                break;
            default:
                throw new RuntimeException("Unrecognized ReceivedState: " + state);
        }
        return receivedState;
    }
}