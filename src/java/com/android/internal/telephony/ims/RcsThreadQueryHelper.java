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
package com.android.internal.telephony.ims;

import android.net.Uri;

/**
 * A helper class focused on querying RCS threads from the
 * {@link com.android.providers.telephony.RcsProvider}
 */
class RcsThreadQueryHelper {
    private static final String URI_AND_AUTHORITHY = "content://rcs/";

    static final Uri THREADS_URI = Uri.parse(URI_AND_AUTHORITHY + "thread");
    static final String THREAD_ID = "_id";

    private static final Uri RCS_1_TO_1_THREADS_URI = Uri.parse(URI_AND_AUTHORITHY + "p2p_thread");
    static final String FALLBACK_ID = "rcs_fallback_thread_id";

    private static final Uri GROUP_THREADS_URI = Uri.parse(URI_AND_AUTHORITHY + "group_thread");
    static final String GROUP_NAME = "group_name";
    static final String GROUP_ICON = "group_icon";
    static final String OWNER_PARTICIPANT = "owner_participant";
    static final String CONFERENCE_URI = "conference_uri";
    static final String PARTICIPANT_IN_THREAD = "participant";

    static final String RCS_THREAD_ID = "rcs_thread_id";
    static final String RCS_PARTICIPANT_ID = "rcs_participant_id";

    /**
     * RcsThread's can be accessed using queries on an SQL view that unifies 1 to 1 threads and
     * group threads. Please see {@link com.android.providers.telephony.RcsProviderThreadHelper} for
     * the unified view's schema.
     */
    static final int UNIFIED_QUERY_THREAD_ID_INDEX = 0;
    static final int UNIFIED_QUERY_FALLBACK_THREAD_ID_INDEX = 1;
    static final int UNIFIED_QUERY_OWNER_PARTICIPANT_INDEX = 2;
    static final int UNIFIED_QUERY_GROUP_NAME_INDEX = 3;
    static final int UNIFIED_QUERY_GROUP_ICON_INDEX = 4;
    static final int UNIFIED_QUERY_CONFERENCE_URI_INDEX = 5;
    static final int UNIFIED_QUERY_IS_GROUP_INDEX = 6;

    static Uri get1To1ThreadUri(int rcsThreadId) {
        return RcsThreadQueryHelper.RCS_1_TO_1_THREADS_URI.buildUpon().appendPath(
                Integer.toString(rcsThreadId)).build();
    }

    static Uri getGroupThreadUri(int rcsThreadId) {
        return GROUP_THREADS_URI.buildUpon().appendPath(Integer.toString(rcsThreadId)).build();
    }

    static Uri getAllParticipantsInThreadUri(int rcsThreadId) {
        return GROUP_THREADS_URI.buildUpon().appendPath(Integer.toString(rcsThreadId)).appendPath(
                PARTICIPANT_IN_THREAD).build();
    }

    static Uri getParticipantInThreadUri(int rcsThreadId, int participantId) {
        return GROUP_THREADS_URI.buildUpon().appendPath(
                Integer.toString(rcsThreadId)).appendPath(PARTICIPANT_IN_THREAD).appendPath(
                Integer.toString(participantId)).build();
    }
}
