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

package com.android.internal.telephony.uicc.euicc.apdu;

/**
 * Parts of an APDU command.
 *
 * @hide
 */
class ApduCommand {
    public final int channel;
    public final int cla;
    public final int ins;
    public final int p1;
    public final int p2;
    public final int p3;
    public final String cmdHex;

    ApduCommand(int channel, int cla, int ins, int p1, int p2, int p3, String cmdHex) {
        this.channel = channel;
        this.cla = cla;
        this.ins = ins;
        this.p1 = p1;
        this.p2 = p2;
        this.p3 = p3;
        this.cmdHex = cmdHex;
    }

    @Override
    public String toString() {
        return "ApduCommand(channel="
                + channel
                + ", cla="
                + cla
                + ", ins="
                + ins
                + ", p1="
                + p1
                + ", p2="
                + p2
                + ", p3="
                + p3
                + ", cmd="
                + cmdHex
                + ")";
    }
}
