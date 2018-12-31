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

import static com.android.internal.telephony.ims.RcsMessageQueryHelper.ARRIVAL_TIMESTAMP;
import static com.android.internal.telephony.ims.RcsMessageQueryHelper.DELIVERED_TIMESTAMP_COLUMN;
import static com.android.internal.telephony.ims.RcsMessageQueryHelper.GLOBAL_ID_COLUMN;
import static com.android.internal.telephony.ims.RcsMessageQueryHelper.NOTIFIED_TIMESTAMP;
import static com.android.internal.telephony.ims.RcsMessageQueryHelper.ORIGINATION_TIMESTAMP_COLUMN;
import static com.android.internal.telephony.ims.RcsMessageQueryHelper.RCS_PARTICIPANT_ID_COLUMN;
import static com.android.internal.telephony.ims.RcsMessageQueryHelper.SEEN_TIMESTAMP_COLUMN;
import static com.android.internal.telephony.ims.RcsMessageQueryHelper.STATUS_COLUMN;
import static com.android.internal.telephony.ims.RcsMessageQueryHelper.SUB_ID_COLUMN;
import static com.android.internal.telephony.ims.RcsParticipantQueryHelper.getUriForParticipant;
import static com.android.internal.telephony.ims.RcsThreadQueryHelper.CONFERENCE_URI;
import static com.android.internal.telephony.ims.RcsThreadQueryHelper.FALLBACK_ID;
import static com.android.internal.telephony.ims.RcsThreadQueryHelper.GROUP_ICON;
import static com.android.internal.telephony.ims.RcsThreadQueryHelper.GROUP_NAME;
import static com.android.internal.telephony.ims.RcsThreadQueryHelper.OWNER_PARTICIPANT;
import static com.android.internal.telephony.ims.RcsThreadQueryHelper.RCS_PARTICIPANT_ID;
import static com.android.internal.telephony.ims.RcsThreadQueryHelper.RCS_THREAD_ID;
import static com.android.internal.telephony.ims.RcsThreadQueryHelper.THREADS_URI;
import static com.android.internal.telephony.ims.RcsThreadQueryHelper.UNIFIED_QUERY_CONFERENCE_URI_INDEX;
import static com.android.internal.telephony.ims.RcsThreadQueryHelper.UNIFIED_QUERY_FALLBACK_THREAD_ID_INDEX;
import static com.android.internal.telephony.ims.RcsThreadQueryHelper.UNIFIED_QUERY_GROUP_ICON_INDEX;
import static com.android.internal.telephony.ims.RcsThreadQueryHelper.UNIFIED_QUERY_GROUP_NAME_INDEX;
import static com.android.internal.telephony.ims.RcsThreadQueryHelper.UNIFIED_QUERY_IS_GROUP_INDEX;
import static com.android.internal.telephony.ims.RcsThreadQueryHelper.UNIFIED_QUERY_OWNER_PARTICIPANT_INDEX;
import static com.android.internal.telephony.ims.RcsThreadQueryHelper.UNIFIED_QUERY_THREAD_ID_INDEX;
import static com.android.internal.telephony.ims.RcsThreadQueryHelper.get1To1ThreadUri;
import static com.android.internal.telephony.ims.RcsThreadQueryHelper.getAllParticipantsInThreadUri;
import static com.android.internal.telephony.ims.RcsThreadQueryHelper.getGroupThreadUri;
import static com.android.internal.telephony.ims.RcsThreadQueryHelper.getParticipantInThreadUri;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.ServiceManager;
import android.telephony.Rlog;
import android.telephony.ims.Rcs1To1Thread;
import android.telephony.ims.RcsGroupThread;
import android.telephony.ims.RcsMessage;
import android.telephony.ims.RcsMessageStore;
import android.telephony.ims.RcsPart;
import android.telephony.ims.RcsParticipant;
import android.telephony.ims.RcsThread;
import android.telephony.ims.RcsThreadQueryContinuationToken;
import android.telephony.ims.RcsThreadQueryParameters;
import android.telephony.ims.RcsThreadQueryResult;
import android.telephony.ims.aidl.IRcs;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;


/** Backing implementation of {@link RcsMessageStore}. */
public class RcsMessageStoreController extends IRcs.Stub {
    private static final String TAG = "RcsStoreController";
    private static final String RCS_SERVICE_NAME = "ircs";

    private static RcsMessageStoreController sInstance;

    private final ContentResolver mContentResolver;
    private final RcsParticipantQueryHelper mParticipantQueryHelper;
    private final RcsMessageQueryHelper mMessageQueryHelper;

    /** Initialize the instance. Should only be called once. */
    public static RcsMessageStoreController init(Context context) {
        synchronized (RcsMessageStoreController.class) {
            if (sInstance == null) {
                sInstance = new RcsMessageStoreController(context.getContentResolver());
            } else {
                Rlog.e(TAG, "init() called multiple times! sInstance = " + sInstance);
            }
        }
        return sInstance;
    }

    private RcsMessageStoreController(ContentResolver contentResolver) {
        mContentResolver = contentResolver;
        mParticipantQueryHelper = new RcsParticipantQueryHelper(contentResolver);
        mMessageQueryHelper = new RcsMessageQueryHelper();
        if (ServiceManager.getService(RCS_SERVICE_NAME) == null) {
            ServiceManager.addService(RCS_SERVICE_NAME, this);
        }
    }

    @VisibleForTesting
    public RcsMessageStoreController(ContentResolver contentResolver, Void unused) {
        mContentResolver = contentResolver;
        mParticipantQueryHelper = new RcsParticipantQueryHelper(contentResolver);
        mMessageQueryHelper = new RcsMessageQueryHelper();
    }

    @Override
    public void deleteThread(int threadId) {
        mContentResolver.delete(THREADS_URI, "_id=" + threadId, null);
    }

    @Override
    public int getMessageCount(int rcsThreadId) {
        // TODO(109759350) - add implementation. Return a magic number for now to test the RPC calls
        return 1018;
    }

    @Override
    public RcsThreadQueryResult getRcsThreads(RcsThreadQueryParameters queryParameters) {
        List<RcsThread> rcsThreadList = new ArrayList<>();
        try (Cursor cursor = mContentResolver.query(THREADS_URI, null, null, null, null)) {
            while (cursor != null && cursor.moveToNext()) {
                // TODO(109759350) - limit the query as per queryParameters
                boolean isGroup = cursor.getInt(UNIFIED_QUERY_IS_GROUP_INDEX) == 1;

                int ownerParticipantId = cursor.getInt(UNIFIED_QUERY_OWNER_PARTICIPANT_INDEX);
                RcsParticipant ownerParticipant = mParticipantQueryHelper.getParticipantFromId(
                        ownerParticipantId);

                if (isGroup) {
                    Uri iconUri = null;
                    String iconUriAsString = cursor.getString(UNIFIED_QUERY_GROUP_ICON_INDEX);
                    if (!TextUtils.isEmpty(iconUriAsString)) {
                        iconUri = Uri.parse(iconUriAsString);
                    }

                    RcsGroupThread rcsGroupThread = new RcsGroupThread(
                            cursor.getInt(UNIFIED_QUERY_THREAD_ID_INDEX),
                            ownerParticipant,
                            cursor.getString(UNIFIED_QUERY_GROUP_NAME_INDEX),
                            iconUri,
                            cursor.getString(UNIFIED_QUERY_CONFERENCE_URI_INDEX));
                    rcsThreadList.add(rcsGroupThread);

                    // TODO(109759350) - load group participants
                } else {
                    Rcs1To1Thread rcs1To1Thread = new Rcs1To1Thread(
                            cursor.getInt(UNIFIED_QUERY_THREAD_ID_INDEX), ownerParticipant,
                            cursor.getInt(UNIFIED_QUERY_FALLBACK_THREAD_ID_INDEX));
                    rcsThreadList.add(rcs1To1Thread);
                }
            }
        }
        return new RcsThreadQueryResult(null, rcsThreadList);
    }

    @Override
    public RcsThreadQueryResult getRcsThreadsWithToken(
            RcsThreadQueryContinuationToken continuationToken) {
        // TODO(109759350) - implement
        return null;
    }

    @Override
    public Rcs1To1Thread createRcs1To1Thread(RcsParticipant recipient) {
        ContentValues contentValues = new ContentValues(0);
        mContentResolver.insert(RcsThreadQueryHelper.THREADS_URI, contentValues);

        return null;
    }

    /**
     * TODO(109759350) Instead of sending the update query directly to RcsProvider, this function
     * orchestrates between RcsProvider and MmsSmsProvider. This is because we are not fully decided
     * on whether we should have RCS storage in a separate database file.
     */
    @Override
    public RcsParticipant createRcsParticipant(String canonicalAddress) {
        // Lookup the participant in RcsProvider to get the canonical row id in MmsSmsProvider
        int rowInCanonicalAddressesTable = Integer.MIN_VALUE;
        try (Cursor cursor = mContentResolver.query(
                RcsParticipantQueryHelper.CANONICAL_ADDRESSES_URI,
                new String[]{RcsThreadQueryHelper.THREAD_ID}, "address=" + canonicalAddress, null,
                null)) {
            if (cursor != null && cursor.getCount() == 1 && cursor.moveToNext()) {
                rowInCanonicalAddressesTable = cursor.getInt(0);
            }
        }

        ContentValues contentValues = new ContentValues();
        contentValues.put("address", canonicalAddress);

        if (rowInCanonicalAddressesTable == Integer.MIN_VALUE) {
            // We couldn't find any existing canonical addresses. Add a new one.
            Uri newCanonicalAddress = mContentResolver.insert(
                    RcsParticipantQueryHelper.CANONICAL_ADDRESSES_URI, contentValues);
            if (newCanonicalAddress != null) {
                try {
                    rowInCanonicalAddressesTable = Integer.parseInt(
                            newCanonicalAddress.getLastPathSegment());
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Uri returned after canonical address insertion is malformed: "
                            + newCanonicalAddress, e);
                    return null;
                }
            }
        }

        contentValues.clear();
        contentValues.put(RcsParticipantQueryHelper.RCS_CANONICAL_ADDRESS_ID,
                rowInCanonicalAddressesTable);

        Uri newParticipantUri = mContentResolver.insert(RcsParticipantQueryHelper.PARTICIPANTS_URI,
                contentValues);
        int newParticipantRowId;

        try {
            if (newParticipantUri != null) {
                newParticipantRowId = Integer.parseInt(newParticipantUri.getLastPathSegment());
            } else {
                Log.e(TAG, "Error inserting new participant into RcsProvider");
                return null;
            }
        } catch (NumberFormatException e) {
            Log.e(TAG,
                    "Uri returned after creating a participant is malformed: " + newParticipantUri);
            return null;
        }

        return new RcsParticipant(newParticipantRowId, canonicalAddress);
    }

    /**
     * TODO(109759350) Instead of sending the update query directly to RcsProvider, this function
     * orchestrates between RcsProvider and MmsSmsProvider. This is because we are not fully decided
     * on whether we should have RCS storage in a separate database file.
     */
    @Override
    public void updateRcsParticipantCanonicalAddress(int id, String canonicalAddress) {
        // Lookup the participant in RcsProvider to get the canonical row id in MmsSmsProvider
        int rowInCanonicalAddressesTable;
        try (Cursor cursor = mContentResolver.query(RcsParticipantQueryHelper.PARTICIPANTS_URI,
                new String[]{RcsParticipantQueryHelper.RCS_CANONICAL_ADDRESS_ID}, "id=" + id, null,
                null)) {
            if (cursor != null && cursor.getCount() == 1 && cursor.moveToNext()) {
                rowInCanonicalAddressesTable = cursor.getInt(0);
            } else {
                Log.e(TAG,
                        "RcsMessageStoreController: Could not find canonical address for "
                                + "participant "
                                + "id: "
                                + id);
                return;
            }
        }

        ContentValues contentValues = new ContentValues(1);
        contentValues.put(RcsParticipantQueryHelper.CANONICAL_ADDRESS_COLUMN, canonicalAddress);
        mContentResolver.update(Uri.parse(
                RcsParticipantQueryHelper.INDIVIDUAL_CANONICAL_ADDRESS_URI_AS_STRING
                        + rowInCanonicalAddressesTable), contentValues, null, null);
    }

    @Override
    public void updateRcsParticipantAlias(int id, String alias) {
        ContentValues contentValues = new ContentValues(1);
        contentValues.put(RcsParticipantQueryHelper.RCS_ALIAS_COLUMN, alias);
        mContentResolver.update(getUriForParticipant(id), contentValues, null, null);
    }

    @Override
    public void set1To1ThreadFallbackThreadId(int rcsThreadId, int fallbackId) {
        ContentValues contentValues = new ContentValues(1);
        contentValues.put(FALLBACK_ID, fallbackId);
        mContentResolver.update(get1To1ThreadUri(rcsThreadId), contentValues, null, null);
    }

    @Override
    public void setGroupThreadName(int rcsThreadId, String groupName) {
        ContentValues contentValues = new ContentValues(1);
        contentValues.put(GROUP_NAME, groupName);
        mContentResolver.update(getGroupThreadUri(rcsThreadId), contentValues, null, null);
    }

    @Override
    public void setGroupThreadIcon(int rcsThreadId, Uri groupIcon) {
        ContentValues contentValues = new ContentValues(1);
        contentValues.put(GROUP_ICON, groupIcon.toString());
        mContentResolver.update(getGroupThreadUri(rcsThreadId), contentValues, null, null);
    }

    @Override
    public void setGroupThreadOwner(int rcsThreadId, RcsParticipant participant) {
        ContentValues contentValues = new ContentValues(1);
        contentValues.put(OWNER_PARTICIPANT, participant.getId());
        mContentResolver.update(getGroupThreadUri(rcsThreadId), contentValues, null, null);
    }

    @Override
    public void setGroupThreadConferenceUri(int rcsThreadId, String uri) {
        ContentValues contentValues = new ContentValues(1);
        contentValues.put(CONFERENCE_URI, uri);
        mContentResolver.update(getGroupThreadUri(rcsThreadId), contentValues, null, null);
    }

    @Override
    public void addParticipantToGroupThread(int rcsThreadId, RcsParticipant participant) {
        ContentValues contentValues = new ContentValues(2);
        contentValues.put(RCS_THREAD_ID, rcsThreadId);
        contentValues.put(RCS_PARTICIPANT_ID, participant.getId());

        mContentResolver.insert(getAllParticipantsInThreadUri(rcsThreadId), contentValues);
    }

    @Override
    public void removeParticipantFromGroupThread(int rcsThreadId, RcsParticipant participant) {
        mContentResolver.delete(getParticipantInThreadUri(rcsThreadId, participant.getId()), null,
                null);
    }

    @Override
    public void addMessage(RcsMessage rcsMessage, int rcsThreadId) {
        ContentValues contentValues =
                mMessageQueryHelper.createContentValuesForMessage(rcsMessage, rcsThreadId);
        mContentResolver.insert(mMessageQueryHelper.getMessageInsertionUri(rcsMessage.isIncoming()),
                contentValues);
    }

    @Override
    public void deleteMessage(int messageId, boolean isIncoming, int rcsThreadId, boolean isGroup) {
        mContentResolver.delete(
                mMessageQueryHelper.getMessageDeletionUri(messageId, isIncoming, rcsThreadId,
                        isGroup),
                null, null);
    }

    @Override
    public void setMessageSubId(int messageId, boolean isIncoming, int subId) {
        ContentValues contentValues = new ContentValues(1);
        contentValues.put(SUB_ID_COLUMN, subId);

        mContentResolver.update(mMessageQueryHelper.getMessageUpdateUri(messageId, isIncoming),
                contentValues,
                null, null);
    }

    @Override
    public void setMessageStatus(int messageId, boolean isIncoming, int status) {
        ContentValues contentValues = new ContentValues(1);
        contentValues.put(STATUS_COLUMN, status);

        mContentResolver.update(mMessageQueryHelper.getMessageUpdateUri(messageId, isIncoming),
                contentValues,
                null, null);
    }

    @Override
    public void setMessageOriginationTimestamp(int messageId, boolean isIncoming,
            long originationTimestamp) {
        ContentValues contentValues = new ContentValues(1);
        contentValues.put(ORIGINATION_TIMESTAMP_COLUMN, originationTimestamp);

        mContentResolver.update(mMessageQueryHelper.getMessageUpdateUri(messageId, isIncoming),
                contentValues,
                null, null);
    }

    @Override
    public void setGlobalMessageIdForMessage(int messageId, boolean isIncoming, String globalId) {
        ContentValues contentValues = new ContentValues(1);
        contentValues.put(GLOBAL_ID_COLUMN, globalId);

        mContentResolver.update(mMessageQueryHelper.getMessageUpdateUri(messageId, isIncoming),
                contentValues,
                null, null);
    }

    @Override
    public void addPartToMessage(int messageId, boolean isIncoming, RcsPart rcsPart) {
        // TODO(109759350) - implement
    }

    @Override
    public void removePartFromMessage(int messageId, boolean isIncoming, RcsPart rcsPart) {
        // TODO(109759350) - implement
    }

    @Override
    public void setMessageArrivalTimestamp(int messageId, boolean isIncoming,
            long arrivalTimestamp) {
        ContentValues contentValues = new ContentValues(1);
        contentValues.put(ARRIVAL_TIMESTAMP, arrivalTimestamp);

        mContentResolver.update(mMessageQueryHelper.getMessageUpdateUri(messageId, isIncoming),
                contentValues,
                null, null);
    }

    @Override
    public void setMessageNotifiedTimestamp(int messageId, boolean isIncoming,
            long notifiedTimestamp) {
        ContentValues contentValues = new ContentValues(1);
        contentValues.put(NOTIFIED_TIMESTAMP, notifiedTimestamp);

        mContentResolver.update(mMessageQueryHelper.getMessageUpdateUri(messageId, isIncoming),
                contentValues,
                null, null);
    }

    @Override
    public void createOutgoingDelivery(int messageId, int participantId, long seenTimestamp,
            long deliveredTimestamp) {
        ContentValues contentValues = new ContentValues(3);
        contentValues.put(RCS_PARTICIPANT_ID_COLUMN, participantId);
        contentValues.put(DELIVERED_TIMESTAMP_COLUMN, deliveredTimestamp);
        contentValues.put(SEEN_TIMESTAMP_COLUMN, seenTimestamp);

        mContentResolver.insert(mMessageQueryHelper.getMessageDeliveryUri(messageId, participantId),
                contentValues);
    }

    @Override
    public void setOutgoingDeliveryDeliveredTimestamp(int messageId, int participantId,
            long deliveredTimestamp) {
        ContentValues contentValues = new ContentValues(1);
        contentValues.put(DELIVERED_TIMESTAMP_COLUMN, deliveredTimestamp);

        mContentResolver.update(mMessageQueryHelper.getMessageDeliveryUri(messageId, participantId),
                contentValues, null, null);
    }

    @Override
    public void setOutgoingDeliverySeenTimestamp(int messageId, int participantId,
            long seenTimestamp) {
        ContentValues contentValues = new ContentValues(1);
        contentValues.put(DELIVERED_TIMESTAMP_COLUMN, seenTimestamp);

        mContentResolver.update(mMessageQueryHelper.getMessageDeliveryUri(messageId, participantId),
                contentValues, null, null);
    }
}
