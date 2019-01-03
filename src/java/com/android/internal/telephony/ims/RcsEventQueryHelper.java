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

class RcsEventQueryHelper {
    private static final String PARTICIPANT_URI = "content://rcs/participant";
    private static final String ALIAS_CHANGE_EVENT = "alias_change_event";

    static final String EVENT_ID = "event_id";
    static final String EVENT_TYPE = "event_type";
    static final String TIMESTAMP = "timestamp";
    static final String RCS_PARTICIPANT_ID_COLUMN = "rcs_participant_id";
    static final String ORIGINATING_PARTICIPANT = "originating_participant";
    static final String DESTINATION_PARTICIPANT = "destination_participant";
    static final String OLD_ICON_URI = "old_icon_uri";
    static final String NEW_ICON_URI = "new_icon_uri";
    static final String OLD_NAME_URI = "old_name_uri";
    static final String NEW_NAME_URI = "new_name_uri";
    static final String OLD_ALIAS = "old_alias";
    static final String NEW_ALIAS = "new_alias";

    Uri getParticipantEventInsertionUri(int participantId) {
        return Uri.parse(PARTICIPANT_URI + '/' + participantId + '/' + ALIAS_CHANGE_EVENT);
    }
}
