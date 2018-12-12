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

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.os.ServiceManager;
import android.telephony.Rlog;
import android.telephony.ims.Rcs1To1Thread;
import android.telephony.ims.RcsMessageStore;
import android.telephony.ims.RcsParticipant;
import android.telephony.ims.RcsThread;
import android.telephony.ims.RcsThreadQueryContinuationToken;
import android.telephony.ims.RcsThreadQueryParameters;
import android.telephony.ims.RcsThreadQueryResult;
import android.telephony.ims.aidl.IRcs;

import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;


/** Backing implementation of {@link RcsMessageStore}. */
public class RcsMessageStoreController extends IRcs.Stub {
    private static final String TAG = "RcsStoreController";
    private static final String RCS_SERVICE_NAME = "ircs";

    private static RcsMessageStoreController sInstance;

    private final ContentResolver mContentResolver;

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
        if (ServiceManager.getService(RCS_SERVICE_NAME) == null) {
            ServiceManager.addService(RCS_SERVICE_NAME, this);
        }
    }

    @VisibleForTesting
    public RcsMessageStoreController(ContentResolver contentResolver, Void unused) {
        mContentResolver = contentResolver;
    }

    @Override
    public void deleteThread(int threadId) {
        // TODO - add implementation
    }

    @Override
    public int getMessageCount(int rcsThreadId) {
        // TODO - add implementation. Return a magic number for now to test the RPC calls
        return 1018;
    }

    @Override
    public RcsThreadQueryResult getRcsThreads(RcsThreadQueryParameters queryParameters) {
        // TODO - refine implementation to include tokens for the next query
        Cursor rcsThreadsCursor = mContentResolver.query(
                RcsThreadQueryHelper.THREADS_URI,
                RcsThreadQueryHelper.THREAD_PROJECTION,
                RcsThreadQueryHelper.buildWhereClause(queryParameters),
                null,
                queryParameters.isAscending()
                        ? RcsThreadQueryHelper.ASCENDING : RcsThreadQueryHelper.DESCENDING);

        List<RcsThread> rcsThreadList = new ArrayList<>();

        // TODO - currently this only creates 1 to 1 threads - fix this
        while (rcsThreadsCursor != null && rcsThreadsCursor.moveToNext()) {
            Rcs1To1Thread rcs1To1Thread = new Rcs1To1Thread(rcsThreadsCursor.getInt(0));
            rcsThreadList.add(rcs1To1Thread);
        }

        return new RcsThreadQueryResult(null, rcsThreadList);
    }

    @Override
    public RcsThreadQueryResult getRcsThreadsWithToken(
            RcsThreadQueryContinuationToken continuationToken) {
        // TODO - implement
        return null;
    }

    @Override
    public Rcs1To1Thread createRcs1To1Thread(RcsParticipant recipient) {
        ContentValues contentValues = new ContentValues(0);
        mContentResolver.insert(RcsThreadQueryHelper.THREADS_URI, contentValues);

        return null;
    }

    @Override
    public RcsParticipant createRcsParticipant(String canonicalAddress) {
        // Lookup the participant in RcsProvider to get the canonical row id in MmsSmsProvider
        int rowInCanonicalAddressesTable = Integer.MIN_VALUE;
        try (Cursor cursor = mContentResolver.query(
                RcsParticipantQueryHelper.CANONICAL_ADDRESSES_URI,
                new String[]{RcsThreadQueryHelper._ID}, "address=" + canonicalAddress, null,
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
     * TODO(sahinc) Instead of sending the update query directly to RcsProvider, this function
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

        mContentResolver.update(RcsParticipantQueryHelper.PARTICIPANTS_URI, contentValues,
                "_id=" + id, null);
    }
}
