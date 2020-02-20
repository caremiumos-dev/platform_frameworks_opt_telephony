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

import com.android.internal.annotations.VisibleForTesting;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * CarrierPrivilegesTracker will track the Carrier Privileges for a specific {@link Phone}.
 * Telephony entities will be able to receive notifications when UIDs gain or lose these privileges.
 *
 * @hide
 */
public class CarrierPrivilegesTracker extends Handler {
    /**
     * Action for registering a Listener with this Tracker.
     * obj: {@link CarrierPrivilegesListener} to be registered
     */
    private static final int ACTION_REGISTER_LISTENER = 1;

    /**
     * Action for unregistering a Listener with this Tracker.
     * obj: {@link CarrierPrivilegesListener} to be unregistered
     */
    private static final int ACTION_UNREGISTER_LISTENER = 2;

    private final Phone mPhone;
    @VisibleForTesting public final Set<CarrierPrivilegesListener> mListeners;

    @VisibleForTesting public final Set<Integer> mAdministratorUids;

    /** @hide */
    public CarrierPrivilegesTracker(@NonNull Phone phone, @NonNull Looper looper) {
        super(looper);
        mPhone = phone;
        mListeners = new HashSet<>();

        mAdministratorUids = new HashSet<>();
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case ACTION_REGISTER_LISTENER: {
                CarrierPrivilegesListener listener = (CarrierPrivilegesListener) msg.obj;
                mListeners.add(listener);

                listener.onCarrierPrivilegesChanged(
                        Collections.unmodifiableSet(mAdministratorUids));
                break;
            }
            case ACTION_UNREGISTER_LISTENER: {
                CarrierPrivilegesListener listener = (CarrierPrivilegesListener) msg.obj;
                mListeners.remove(listener);
                break;
            }
        }
    }

    /**
     * Registers the given listener with this tracker.
     *
     * <p>After being registered, {@link CarrierPrivilegesListener#registerCarrierPrivilegeListener}
     * will be invoked with the current Carrier Privileged UIDs for this Phone.
     *
     * @hide
     */
    public void registerCarrierPrivilegeListener(@NonNull CarrierPrivilegesListener listener) {
        sendMessage(obtainMessage(ACTION_REGISTER_LISTENER, listener));
    }

    /**
     * Unregisters the given listener with this tracker.
     *
     * @hide
     */
    public void unregisterCallback(@NonNull CarrierPrivilegesListener listener) {
        sendMessage(obtainMessage(ACTION_UNREGISTER_LISTENER, listener));
    }

    /**
     * CarrierPrivilegesListener is an abstract class that Telephony entities can define in order to
     * receive updates when Carrier Privileged UIDs change.
     *
     * @hide
     */
    public abstract static class CarrierPrivilegesListener {
        /**
         * This method will be invoked whenever the CarrierPrivilegesTracker detects a change in
         * Carrier Privileged UIDs for the Phone this listener is registered under.
         *
         * @hide
         */
        public abstract void onCarrierPrivilegesChanged(@NonNull Set<Integer> uids);
    }
}
