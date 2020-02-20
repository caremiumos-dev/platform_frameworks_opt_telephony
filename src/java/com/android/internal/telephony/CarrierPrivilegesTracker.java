/*
 * Copyright (C) 2020 The Android Open Source Project
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

import android.annotation.NonNull;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.util.Log;

/**
 * CarrierPrivilegesTracker will track the Carrier Privileges for a specific {@link Phone}.
 * Registered Telephony entities will receive notifications when UIDs gain or lose these privileges.
 *
 * @hide
 */
public class CarrierPrivilegesTracker extends Handler {
    private static final String TAG = CarrierPrivilegesTracker.class.getSimpleName();

    private final RegistrantList mRegistrantList;
    private int[] mPrivilegedUids;

    /** @hide */
    public CarrierPrivilegesTracker(@NonNull Looper looper) {
        super(looper);
        mRegistrantList = new RegistrantList();
        mPrivilegedUids = new int[0];
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            default: {
                Log.e(TAG, "Received unknown msg type: " + msg.what);
                break;
            }
        }
    }

    /**
     * Registers the given Registrant with this tracker.
     *
     * <p>After being registered, the Registrant will be notified with the current Carrier
     * Privileged UIDs for this Phone.
     *
     * @hide
     */
    public void registerCarrierPrivilegesListener(Registrant registrant) {
        mRegistrantList.add(registrant);
        registrant.notifyResult(mPrivilegedUids);
    }

    /**
     * Unregisters the given listener with this tracker.
     *
     * @hide
     */
    public void unregisterCarrierPrivilegesListener(Handler handler) {
        mRegistrantList.remove(handler);
    }
}
