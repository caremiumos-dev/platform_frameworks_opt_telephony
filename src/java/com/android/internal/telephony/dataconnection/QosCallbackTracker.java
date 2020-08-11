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

package com.android.internal.telephony.dataconnection;

import android.annotation.NonNull;
import android.telephony.data.QosFilter;
import android.telephony.data.QosSession;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * This is not a thread safe class, all methods should be called from DataConnection
 * {@hide}
 */
public class QosCallbackTracker {
    @NonNull private final DcNetworkAgent mDcNetworkAgent;
    @NonNull private final Map<Integer, QosSession> mQosSessions;

    // We perform an exact match on the address
    @NonNull private final Map<Integer, InetSocketAddress> mCallbackToAddress;

    public QosCallbackTracker(@NonNull final DcNetworkAgent dcNetworkAgent) {
        mQosSessions = new HashMap<>();
        mCallbackToAddress = new HashMap<>();
        mDcNetworkAgent = dcNetworkAgent;
    }

    public void addFilter(int callbackId, InetSocketAddress address) {
        mCallbackToAddress.put(callbackId, address);

        //On first change. Check all sessions and send.
        for (final QosSession session : mQosSessions.values()) {
            for (final QosFilter filter :
                    session.filterLocalAddressMatch(address.getAddress(), address.getPort())) {

                //QosSessionAttributes attributes = convertToQosSessionAttributes(filter, qos);
                //mDcNetworkAgent.sendSessionAvailable(attributes);
            }
        }
    }

    // TODO: DataConnection should call this
    public void inspectSessions(@NonNull final List<QosSession> sessions) {
        //We collect all the deltas in the runnable list and then execute at the end.
        List<Runnable> runnables = new ArrayList<>();

        for (final QosSession incomingSession : sessions) {
            QosSession existingSession = mQosSessions.get(incomingSession.getQosSessionId());
            if (existingSession == null) {
                //session is new
                runnables.add(() -> sendSessionAvailable(incomingSession));
            }
            else {
                runnables.add(() -> sessionMaybeChanged(existingSession, incomingSession));
            }
        }

        final Map<Integer, QosSession> incomingSessions =
                sessions.stream().collect(Collectors.toMap(QosSession::getQosSessionId, s -> s));
        for (final Integer existingQosSessionId : mQosSessions.keySet()) {
            if (!incomingSessions.containsKey(existingQosSessionId)) {
                //session is lost
                QosSession existingSession = mQosSessions.get(existingQosSessionId);
                runnables.add(() -> sendSessionLost(existingSession));
            }
        }

        for (final Runnable r : runnables) {
            r.run();
        }
    }

    private void sendSessionLost(@NonNull final QosSession session) {
        mQosSessions.remove(session.getQosSessionId());
        for (int callbackId : mCallbackToAddress.keySet()) {
            InetSocketAddress address = mCallbackToAddress.get(callbackId);
            for (final QosFilter filter :
                    session.filterLocalAddressMatch(address.getAddress(), address.getPort())) {
                //mDcNetworkAgent.sendSessionLost(callbackId);
            }
        }
    }

    private void sendSessionAvailable(@NonNull final QosSession session) {
        mQosSessions.put(session.getQosSessionId(), session);
        for (int callbackId : mCallbackToAddress.keySet()) {
            InetSocketAddress address = mCallbackToAddress.get(callbackId);
            for (final QosFilter filter :
                    session.filterLocalAddressMatch(address.getAddress(), address.getPort())) {
                //QosSessionAttributes attributes = convertToQosSessionAttributes(filter, qos);
                //mDcNetworkAgent.sendSessionAvailable(callbackId, attributes);
            }
        }
    }

    private void sessionMaybeChanged(@NonNull final QosSession existingSession,
            @NonNull final QosSession incomingSession) {
        if (!existingSession.same(incomingSession)) {
            sendSessionAvailable(incomingSession);
        }
    }
}
