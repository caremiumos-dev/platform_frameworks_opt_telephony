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

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.telephony.ims.RcsParticipant;

class RcsParticipantQueryHelper {
    static final Uri CANONICAL_ADDRESSES_URI = Uri.parse("content://mms-sms/canonical-addresses");

    static final Uri PARTICIPANTS_URI = Uri.parse("content://rcs/participant");
    static final String RCS_ALIAS_COLUMN = "rcs_alias";
    static final String RCS_CANONICAL_ADDRESS_ID = "canonical_address_id";

    private final ContentResolver mContentResolver;

    RcsParticipantQueryHelper(ContentResolver contentResolver) {
        mContentResolver = contentResolver;
    }

    RcsParticipant getParticipantFromId(int participantId) {
        RcsParticipant participant = null;
        try (Cursor cursor = mContentResolver.query(
                PARTICIPANTS_URI.buildUpon().appendPath(Integer.toString(participantId)).build(),
                null, null, null)) {
            if (cursor != null && cursor.moveToNext()) {
                participant = new RcsParticipant(cursor.getInt(0), cursor.getString(1));
            }
        }
        return participant;
    }

    static Uri getUriForParticipant(int participantId) {
        return PARTICIPANTS_URI.buildUpon().appendPath(Integer.toString(participantId)).build();
    }
}
