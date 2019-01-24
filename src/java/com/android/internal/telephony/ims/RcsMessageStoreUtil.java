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

import static android.provider.Telephony.RcsColumns.RcsGroupThreadColumns.RCS_GROUP_THREAD_URI;
import static android.provider.Telephony.RcsColumns.RcsIncomingMessageColumns.INCOMING_MESSAGE_URI;
import static android.provider.Telephony.RcsColumns.RcsOutgoingMessageColumns.OUTGOING_MESSAGE_URI;
import static android.provider.Telephony.RcsColumns.RcsParticipantEventTypes.ICON_CHANGED_EVENT_TYPE;
import static android.provider.Telephony.RcsColumns.RcsParticipantEventTypes.NAME_CHANGED_EVENT_TYPE;
import static android.provider.Telephony.RcsColumns.RcsParticipantEventTypes.PARTICIPANT_JOINED_EVENT_TYPE;
import static android.provider.Telephony.RcsColumns.RcsParticipantEventTypes.PARTICIPANT_LEFT_EVENT_TYPE;
import static android.provider.Telephony.RcsColumns.RcsThreadEventColumns.EVENT_TYPE_COLUMN;
import static android.provider.Telephony.RcsColumns.RcsThreadEventColumns.ICON_CHANGED_URI_PART;
import static android.provider.Telephony.RcsColumns.RcsThreadEventColumns.NAME_CHANGED_URI_PART;
import static android.provider.Telephony.RcsColumns.RcsThreadEventColumns.ORIGINATING_PARTICIPANT_COLUMN;
import static android.provider.Telephony.RcsColumns.RcsThreadEventColumns.PARTICIPANT_JOINED_URI_PART;
import static android.provider.Telephony.RcsColumns.RcsThreadEventColumns.PARTICIPANT_LEFT_URI_PART;
import static android.provider.Telephony.RcsColumns.RcsThreadEventColumns.TIMESTAMP_COLUMN;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.text.TextUtils;

/**
 * Utility functions for {@link RcsMessageStoreController}
 *
 * @hide
 */
public class RcsMessageStoreUtil {
    private ContentResolver mContentResolver;

    RcsMessageStoreUtil(ContentResolver contentResolver) {
        mContentResolver = contentResolver;
    }

    int createGroupThreadEvent(int eventType, long timestamp, int threadId,
            int originationParticipantId, ContentValues eventSpecificValues)
            throws RemoteException {
        ContentValues values = new ContentValues(eventSpecificValues);
        values.put(EVENT_TYPE_COLUMN, eventType);
        values.put(TIMESTAMP_COLUMN, timestamp);
        values.put(ORIGINATING_PARTICIPANT_COLUMN, originationParticipantId);

        Uri eventUri = RCS_GROUP_THREAD_URI.buildUpon().appendPath(
                Integer.toString(threadId)).appendPath(getPathForEventType(eventType)).build();
        Uri insertionUri = mContentResolver.insert(eventUri, values);

        int eventId = 0;
        if (insertionUri != null) {
            eventId = Integer.parseInt(insertionUri.getLastPathSegment());
        }

        if (eventId <= 0) {
            throw new RemoteException(
                    "Could not create event with type: " + eventType + " on thread: " + threadId);
        }
        return eventId;
    }

    private String getPathForEventType(int eventType) throws RemoteException {
        switch (eventType) {
            case PARTICIPANT_JOINED_EVENT_TYPE:
                return PARTICIPANT_JOINED_URI_PART;
            case PARTICIPANT_LEFT_EVENT_TYPE:
                return PARTICIPANT_LEFT_URI_PART;
            case NAME_CHANGED_EVENT_TYPE:
                return NAME_CHANGED_URI_PART;
            case ICON_CHANGED_EVENT_TYPE:
                return ICON_CHANGED_URI_PART;
            default:
                throw new RemoteException("Event type unrecognized: " + eventType);
        }
    }

    int getIntValueFromTableRow(Uri tableUri, String valueColumn, String idColumn,
            int idValue) throws RemoteException {
        try (Cursor cursor = getValueFromTableRow(tableUri, valueColumn, idColumn, idValue)) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getInt(0);
            } else {
                throw new RemoteException("The row with (" + idColumn + " = " + idValue
                        + ") could not be found in " + tableUri);
            }
        }
    }

    long getLongValueFromTableRow(Uri tableUri, String valueColumn, String idColumn,
            int idValue) throws RemoteException {
        try (Cursor cursor = getValueFromTableRow(tableUri, valueColumn, idColumn, idValue)) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getLong(0);
            } else {
                throw new RemoteException("The row with (" + idColumn + " = " + idValue
                        + ") could not be found in " + tableUri);
            }
        }
    }

    double getDoubleValueFromTableRow(Uri tableUri, String valueColumn, String idColumn,
            int idValue) throws RemoteException {
        try (Cursor cursor = getValueFromTableRow(tableUri, valueColumn, idColumn, idValue)) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getDouble(0);
            } else {
                throw new RemoteException("The row with (" + idColumn + " = " + idValue
                        + ") could not be found in " + tableUri);
            }
        }
    }

    String getStringValueFromTableRow(Uri tableUri, String valueColumn, String idColumn,
            int idValue) throws RemoteException {
        try (Cursor cursor = getValueFromTableRow(tableUri, valueColumn, idColumn, idValue)) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(0);
            } else {
                throw new RemoteException("The row with (" + idColumn + " = " + idValue
                        + ") could not be found in " + tableUri);
            }
        }
    }

    Uri getUriValueFromTableRow(Uri tableUri, String valueColumn, String idColumn,
            int idValue) throws RemoteException {
        try (Cursor cursor = getValueFromTableRow(tableUri, valueColumn, idColumn, idValue)) {
            if (cursor != null && cursor.moveToFirst()) {
                String uriAsString = cursor.getString(0);

                if (!TextUtils.isEmpty(uriAsString)) {
                    return Uri.parse(uriAsString);
                }
                return null;
            } else {
                throw new RemoteException("The row with (" + idColumn + " = " + idValue
                        + ") could not be found in " + tableUri);
            }
        }
    }

    void updateValueOfProviderUri(Uri uri, String valueColumn, int value, String errorMessage)
            throws RemoteException {
        ContentValues contentValues = new ContentValues(1);
        contentValues.put(valueColumn, value);
        performUpdate(uri, contentValues, errorMessage);
    }

    void updateValueOfProviderUri(Uri uri, String valueColumn, double value, String errorMessage)
            throws RemoteException {
        ContentValues contentValues = new ContentValues(1);
        contentValues.put(valueColumn, value);
        performUpdate(uri, contentValues, errorMessage);
    }

    void updateValueOfProviderUri(Uri uri, String valueColumn, long value, String errorMessage)
            throws RemoteException {
        ContentValues contentValues = new ContentValues(1);
        contentValues.put(valueColumn, value);
        performUpdate(uri, contentValues, errorMessage);
    }

    void updateValueOfProviderUri(Uri uri, String valueColumn, String value, String errorMessage)
            throws RemoteException {
        ContentValues contentValues = new ContentValues(1);
        contentValues.put(valueColumn, value);
        performUpdate(uri, contentValues, errorMessage);
    }

    void updateValueOfProviderUri(Uri uri, String valueColumn, Uri value, String errorMessage)
            throws RemoteException {
        ContentValues contentValues = new ContentValues(1);
        contentValues.put(valueColumn, value == null ? null : value.toString());
        performUpdate(uri, contentValues, errorMessage);
    }

    private void performUpdate(Uri uri, ContentValues contentValues, String errorMessage)
            throws RemoteException {
        int updateCount = mContentResolver.update(uri, contentValues, null, null);
        if (updateCount <= 0) {
            throw new RemoteException(errorMessage);
        }
    }

    private Cursor getValueFromTableRow(Uri tableUri, String valueColumn, String idColumn,
            int idValue) {
        return mContentResolver.query(tableUri, new String[]{valueColumn}, idColumn + "=?",
                new String[]{Integer.toString(idValue)}, null);
    }

    static Uri getMessageTableUri(boolean isIncoming) {
        return isIncoming ? INCOMING_MESSAGE_URI : OUTGOING_MESSAGE_URI;
    }


}
