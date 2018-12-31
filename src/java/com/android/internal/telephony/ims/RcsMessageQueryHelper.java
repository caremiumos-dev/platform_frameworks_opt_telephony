/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.content.ContentValues;
import android.net.Uri;
import android.telephony.ims.RcsIncomingMessage;
import android.telephony.ims.RcsMessage;

/**
 * A helper class focused on querying RCS messages from the
 * {@link com.android.providers.telephony.RcsProvider}
 */
public class RcsMessageQueryHelper {
    private static final String AUTHORITY = "content://rcs/";

    static final String RCS_THREAD_ID_COLUMN = "rcs_thread_id";
    static final String GLOBAL_ID_COLUMN = "rcs_message_global_id";
    static final String SUB_ID_COLUMN = "sub_id";
    static final String STATUS_COLUMN = "status";
    static final String ORIGINATION_TIMESTAMP_COLUMN = "origination_timestamp";
    static final String ARRIVAL_TIMESTAMP = "arrival_timestamp";
    static final String NOTIFIED_TIMESTAMP = "notified_timestamp";
    static final String SENDER_PARTICIPANT_COLUMN = "sender_participant";
    static final String RCS_PARTICIPANT_ID_COLUMN = "rcs_participant_id";
    static final String DELIVERED_TIMESTAMP_COLUMN = "delivered_timestamp";
    static final String SEEN_TIMESTAMP_COLUMN = "seen_timestamp";

    RcsMessageQueryHelper() {

    }

    ContentValues createContentValuesForMessage(RcsMessage rcsMessage, int rcsThreadId) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(GLOBAL_ID_COLUMN, rcsMessage.getRcsMessageId());
        contentValues.put(SUB_ID_COLUMN, rcsMessage.getSubId());
        contentValues.put(STATUS_COLUMN, rcsMessage.getStatus());
        contentValues.put(ORIGINATION_TIMESTAMP_COLUMN, rcsMessage.getOriginationTimestamp());
        contentValues.put(RCS_THREAD_ID_COLUMN, rcsThreadId);

        if (rcsMessage.isIncoming() && rcsMessage instanceof RcsIncomingMessage) {
            RcsIncomingMessage incomingMessage = (RcsIncomingMessage) rcsMessage;
            contentValues.put(ARRIVAL_TIMESTAMP, incomingMessage.getArrivalTimestamp());
            contentValues.put(NOTIFIED_TIMESTAMP, incomingMessage.getArrivalTimestamp());
            contentValues.put(SENDER_PARTICIPANT_COLUMN, incomingMessage.getArrivalTimestamp());
        }

        return contentValues;
    }

    Uri getMessageInsertionUri(boolean isIncoming) {
        return Uri.parse(AUTHORITY + getMessagePathSegment(isIncoming));
    }

    Uri getMessageDeletionUri(int messageId, boolean isIncoming, int rcsThreadId, boolean isGroup) {
        return Uri.parse(AUTHORITY
                + (isGroup ? "group_thread" : "p2p_thread") + '/'
                + rcsThreadId + '/'
                + getMessagePathSegment(isIncoming) + '/'
                + messageId);
    }

    Uri getMessageUpdateUri(int messageId, boolean isIncoming) {
        return Uri.parse(AUTHORITY + getMessagePathSegment(isIncoming) + '/' + messageId);
    }

    Uri getMessageDeliveryUri(int messageId, int participantId) {
        return Uri.parse(AUTHORITY + getMessagePathSegment(false) + '/' + messageId + "/delivery/"
                + participantId);
    }

    private String getMessagePathSegment(boolean isIncoming) {
        return isIncoming ? "incoming_message" : "outgoing_message";
    }
}
