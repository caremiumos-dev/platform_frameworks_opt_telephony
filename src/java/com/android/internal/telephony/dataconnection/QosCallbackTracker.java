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
import android.annotation.Nullable;
import android.net.LinkAddress;
import android.telephony.data.QosFilter;
import android.telephony.data.QosSession;

import com.android.telephony.Rlog;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Matches filters with qos sessions and send corresponding available and lost events.
 *
 * Note: This class is <b>NOT</b> thread-safe
 *
 * {@hide}
 */
public class QosCallbackTracker {
    private static final String LOG_TAG = QosCallbackTracker.class.getSimpleName();
    @NonNull private final DcNetworkAgent mDcNetworkAgent;
    @NonNull private final Map<Integer, QosSession> mQosSessions;

    // We perform an exact match on the address
    @NonNull private final Map<Integer, IFilter> mCallbacksToFilter;

    /**
     * Construct a new tracker
     * @param dcNetworkAgent the network agent to send events to
     */
    public QosCallbackTracker(@NonNull final DcNetworkAgent dcNetworkAgent) {
        mQosSessions = new HashMap<>();
        mCallbacksToFilter = new HashMap<>();
        mDcNetworkAgent = dcNetworkAgent;
    }

    /**
     * Add new filter that is to receive events
     *
     * @param callbackId the associated callback id
     * @param filter provides the matching logic
     */
    public void addFilter(final int callbackId, final IFilter filter) {
        logd("addFilter: callbackId=" + callbackId);
        // Called from mDcNetworkAgent
        mCallbacksToFilter.put(callbackId, filter);

        //On first change. Check all sessions and send.
        for (final QosSession session : mQosSessions.values()) {
            if (doFiltersMatch(session, filter)) {
                sendSessionAvailable(session, filter);
            }
        }
    }

    /**
     * Remove the filter with the associated callback id
     *
     * @param callbackId the qos callback id
     */
    public void removeFilter(final int callbackId) {
        logd("removeFilter: callbackId=" + callbackId);
        mCallbacksToFilter.remove(callbackId);
    }

    /**
     * Update the list of qos sessions and send out corresponding events
     *
     * @param sessions the new list of qos sessions
     */
    public void updateSessions(@NonNull final List<QosSession> sessions) {
        logd("updateSessions: sessions size=" + sessions.size());
        final List<QosSession> sessionsToAdd = new ArrayList<>();
        final Map<Integer, QosSession> incomingSessions = new HashMap<>();
        for (final QosSession incomingSession : sessions) {
            incomingSessions.put(incomingSession.getQosSessionId(), incomingSession);

            final QosSession existingSession = mQosSessions.get(incomingSession.getQosSessionId());
            for (final int callbackId : mCallbacksToFilter.keySet()) {
                final IFilter filter = mCallbacksToFilter.get(callbackId);

                final boolean incomingSessionMatch = doFiltersMatch(incomingSession, filter);
                final boolean existingSessionMatch =
                        existingSession != null && doFiltersMatch(existingSession, filter);

                if (existingSessionMatch && !incomingSessionMatch) {
                    // The filter matched last time, but doesn't anymore
                    sendSessionLost(existingSession, filter);
                }

                if (!existingSessionMatch && incomingSessionMatch) {
                    // The filter matches now and didn't match earlier
                    sendSessionAvailable(incomingSession, filter);
                }
            }
            sessionsToAdd.add(incomingSession);
        }

        final List<Integer> sessionsToRemove = new ArrayList<>();
        // Find sessions that no longer exist
        for (final QosSession existingSession : mQosSessions.values()) {
            if (!incomingSessions.containsKey(existingSession.getQosSessionId())) {
                for (final int callbackId : mCallbacksToFilter.keySet()) {
                    final IFilter filter = mCallbacksToFilter.get(callbackId);
                    // The filter matches which means it was previously available, and now is lost
                    if (doFiltersMatch(existingSession, filter)) {
                        sendSessionLost(existingSession, filter);
                    }
                }
                sessionsToRemove.add(existingSession.getQosSessionId());
            }
        }

        // Add in the new or existing sessions with updated information
        for (final QosSession sessionToAdd : sessionsToAdd) {
            mQosSessions.put(sessionToAdd.getQosSessionId(), sessionToAdd);
        }

        // Remove any old sessions
        for (final int sessionToRemove : sessionsToRemove) {
            mQosSessions.remove(sessionToRemove);
        }
    }

    private boolean doFiltersMatch(final QosSession qosSession, final IFilter filter) {
        for (final QosFilter sessionFilter : qosSession.getQosFilterList()) {
            for (final LinkAddress qosAddress : sessionFilter.getLocalAddresses()) {
                if (filter.matchesLocalAddress(qosAddress.getAddress(),
                        sessionFilter.getLocalPort().getStart(),
                        sessionFilter.getLocalPort().getEnd())) {
                    return true;
                }
            }
        }
        return false;
    }

    private void sendSessionAvailable(@NonNull final QosSession session,
            @NonNull final IFilter filter) {
        //send to mDcNetworkAgent when connectivity piece is ready
    }

    private void sendSessionLost(@NonNull final QosSession session,
            @NonNull final IFilter filter) {
        //send to mDcNetworkAgent when connectivity piece is ready
    }

    public interface IFilter {
        boolean matchesLocalAddress(InetAddress address, int startPort, int endPort);
    }

    /**
     * Log with debug level
     *
     * @param s is string log
     */
    private void logd(String s) {
        Rlog.d(LOG_TAG, s);
    }
}