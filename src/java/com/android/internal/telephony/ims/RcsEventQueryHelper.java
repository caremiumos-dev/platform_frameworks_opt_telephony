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

import static android.provider.Telephony.RcsColumns.RcsParticipantColumns.RCS_PARTICIPANT_ID_COLUMN;
import static android.provider.Telephony.RcsColumns.RcsParticipantColumns.RCS_PARTICIPANT_URI;
import static android.provider.Telephony.RcsColumns.RcsParticipantEventColumns.ALIAS_CHANGE_EVENT_URI_PART;
import static android.provider.Telephony.RcsColumns.RcsParticipantEventColumns.NEW_ALIAS_COLUMN;
import static android.provider.Telephony.RcsColumns.RcsParticipantEventColumns.OLD_ALIAS_COLUMN;
import static android.provider.Telephony.RcsColumns.RcsParticipantEventColumns.PARTICIPANT_ALIAS_CHANGED_EVENT_TYPE;
import static android.provider.Telephony.RcsColumns.RcsThreadColumns.RCS_THREAD_ID_COLUMN;
import static android.provider.Telephony.RcsColumns.RcsThreadEventColumns.DESTINATION_PARTICIPANT_COLUMN;
import static android.provider.Telephony.RcsColumns.RcsThreadEventColumns.EVENT_ID_COLUMN;
import static android.provider.Telephony.RcsColumns.RcsThreadEventColumns.EVENT_TYPE_COLUMN;
import static android.provider.Telephony.RcsColumns.RcsThreadEventColumns.ICON_CHANGED_EVENT_TYPE;
import static android.provider.Telephony.RcsColumns.RcsThreadEventColumns.NAME_CHANGED_EVENT_TYPE;
import static android.provider.Telephony.RcsColumns.RcsThreadEventColumns.NEW_ICON_URI_COLUMN;
import static android.provider.Telephony.RcsColumns.RcsThreadEventColumns.NEW_NAME_COLUMN;
import static android.provider.Telephony.RcsColumns.RcsThreadEventColumns.OLD_ICON_URI_COLUMN;
import static android.provider.Telephony.RcsColumns.RcsThreadEventColumns.OLD_NAME_COLUMN;
import static android.provider.Telephony.RcsColumns.RcsThreadEventColumns.ORIGINATING_PARTICIPANT_COLUMN;
import static android.provider.Telephony.RcsColumns.RcsThreadEventColumns.PARTICIPANT_JOINED_EVENT_TYPE;
import static android.provider.Telephony.RcsColumns.RcsThreadEventColumns.PARTICIPANT_LEFT_EVENT_TYPE;
import static android.provider.Telephony.RcsColumns.RcsThreadEventColumns.TIMESTAMP_COLUMN;
import static android.provider.Telephony.RcsColumns.RcsUnifiedEventHelper.RCS_EVENT_QUERY_URI;
import static android.telephony.ims.RcsEventQueryContinuationToken.EVENT_QUERY_CONTINUATION_TOKEN;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.telephony.Rlog;
import android.telephony.ims.RcsEvent;
import android.telephony.ims.RcsEventQueryContinuationToken;
import android.telephony.ims.RcsEventQueryResult;
import android.telephony.ims.RcsGroupThreadIconChangedEvent;
import android.telephony.ims.RcsGroupThreadNameChangedEvent;
import android.telephony.ims.RcsGroupThreadParticipantJoinedEvent;
import android.telephony.ims.RcsGroupThreadParticipantLeftEvent;
import android.telephony.ims.RcsParticipantAliasChangedEvent;

import java.util.ArrayList;
import java.util.List;

class RcsEventQueryHelper {
    private final ContentResolver mContentResolver;

    RcsEventQueryHelper(ContentResolver contentResolver) {
        mContentResolver = contentResolver;
    }

    Uri getParticipantEventInsertionUri(int participantId) {
        return RCS_PARTICIPANT_URI.buildUpon().appendPath(Integer.toString(participantId))
                .appendPath(ALIAS_CHANGE_EVENT_URI_PART).build();
    }

    RcsEventQueryResult performEventQuery(Bundle bundle) throws RemoteException {
        RcsEventQueryContinuationToken continuationToken = null;
        List<RcsEvent> eventList = new ArrayList<>();

        try (Cursor cursor = mContentResolver.query(RCS_EVENT_QUERY_URI, null, bundle, null)) {
            if (cursor == null) {
                throw new RemoteException("Event query failed, bundle: " + bundle);
            }

            while (cursor.moveToNext()) {
                int eventType = cursor.getInt(cursor.getColumnIndex(EVENT_TYPE_COLUMN));
                switch (eventType) {
                    case PARTICIPANT_ALIAS_CHANGED_EVENT_TYPE:
                        eventList.add(createNewParticipantAliasChangedEvent(cursor));
                        break;
                    case PARTICIPANT_JOINED_EVENT_TYPE:
                        eventList.add(createNewParticipantJoinedEvent(cursor));
                        break;
                    case PARTICIPANT_LEFT_EVENT_TYPE:
                        eventList.add(createNewParticipantLeftEvent(cursor));
                        break;
                    case NAME_CHANGED_EVENT_TYPE:
                        eventList.add(createNewGroupNameChangedEvent(cursor));
                        break;
                    case ICON_CHANGED_EVENT_TYPE:
                        eventList.add(createNewGroupIconChangedEvent(cursor));
                        break;
                    default:
                        Rlog.e(RcsMessageStoreController.TAG,
                                "RcsEventQueryHelper: invalid event type: " + eventType);
                }
            }

            Bundle cursorExtras = cursor.getExtras();
            if (cursorExtras != null) {
                continuationToken = cursorExtras.getParcelable(EVENT_QUERY_CONTINUATION_TOKEN);
            }
        }

        return new RcsEventQueryResult(continuationToken, eventList);
    }

    private RcsGroupThreadIconChangedEvent createNewGroupIconChangedEvent(Cursor cursor) {
        String oldIcon = cursor.getString(cursor.getColumnIndex(OLD_ICON_URI_COLUMN));
        String newIcon = cursor.getString(cursor.getColumnIndex(NEW_ICON_URI_COLUMN));

        return new RcsGroupThreadIconChangedEvent(
                cursor.getInt(cursor.getColumnIndex(EVENT_ID_COLUMN)),
                cursor.getLong(cursor.getColumnIndex(TIMESTAMP_COLUMN)),
                cursor.getInt(cursor.getColumnIndex(RCS_THREAD_ID_COLUMN)),
                cursor.getInt(cursor.getColumnIndex(ORIGINATING_PARTICIPANT_COLUMN)),
                oldIcon == null ? null : Uri.parse(oldIcon),
                newIcon == null ? null : Uri.parse(newIcon));
    }

    private RcsGroupThreadNameChangedEvent createNewGroupNameChangedEvent(Cursor cursor) {
        return new RcsGroupThreadNameChangedEvent(
                cursor.getInt(cursor.getColumnIndex(EVENT_ID_COLUMN)),
                cursor.getLong(cursor.getColumnIndex(TIMESTAMP_COLUMN)),
                cursor.getInt(cursor.getColumnIndex(RCS_THREAD_ID_COLUMN)),
                cursor.getInt(cursor.getColumnIndex(ORIGINATING_PARTICIPANT_COLUMN)),
                cursor.getString(cursor.getColumnIndex(OLD_NAME_COLUMN)),
                cursor.getString(cursor.getColumnIndex(NEW_NAME_COLUMN)));
    }

    private RcsGroupThreadParticipantLeftEvent createNewParticipantLeftEvent(Cursor cursor) {
        return new RcsGroupThreadParticipantLeftEvent(
                cursor.getInt(cursor.getColumnIndex(EVENT_ID_COLUMN)),
                cursor.getLong(cursor.getColumnIndex(TIMESTAMP_COLUMN)),
                cursor.getInt(cursor.getColumnIndex(RCS_THREAD_ID_COLUMN)),
                cursor.getInt(cursor.getColumnIndex(ORIGINATING_PARTICIPANT_COLUMN)),
                cursor.getInt(cursor.getColumnIndex(DESTINATION_PARTICIPANT_COLUMN)));
    }

    private RcsGroupThreadParticipantJoinedEvent createNewParticipantJoinedEvent(Cursor cursor) {
        return new RcsGroupThreadParticipantJoinedEvent(
                cursor.getInt(cursor.getColumnIndex(EVENT_ID_COLUMN)),
                cursor.getLong(cursor.getColumnIndex(TIMESTAMP_COLUMN)),
                cursor.getInt(cursor.getColumnIndex(RCS_THREAD_ID_COLUMN)),
                cursor.getInt(cursor.getColumnIndex(ORIGINATING_PARTICIPANT_COLUMN)),
                cursor.getInt(cursor.getColumnIndex(DESTINATION_PARTICIPANT_COLUMN)));
    }

    private RcsParticipantAliasChangedEvent createNewParticipantAliasChangedEvent(Cursor cursor) {
        return new RcsParticipantAliasChangedEvent(
                cursor.getInt(cursor.getColumnIndex(EVENT_ID_COLUMN)),
                cursor.getLong(cursor.getColumnIndex(TIMESTAMP_COLUMN)),
                cursor.getInt(cursor.getColumnIndex(RCS_PARTICIPANT_ID_COLUMN)),
                cursor.getString(cursor.getColumnIndex(OLD_ALIAS_COLUMN)),
                cursor.getString(cursor.getColumnIndex(NEW_ALIAS_COLUMN)));
    }
}
