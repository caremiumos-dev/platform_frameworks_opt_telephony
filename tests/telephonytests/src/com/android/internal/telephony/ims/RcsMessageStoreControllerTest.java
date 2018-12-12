/*
 * Copyright 2019 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.telephony.ims.RcsParticipant;
import android.telephony.ims.RcsThreadQueryParameters;
import android.test.mock.MockContentProvider;
import android.test.mock.MockContentResolver;

import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class RcsMessageStoreControllerTest extends TelephonyTest {

    private RcsMessageStoreController mRcsMessageStoreController;
    private MockContentResolver mContentResolver;
    private FakeProviderWithAsserts mFakeRcsProvider;
    private FakeProviderWithAsserts mFakeMmsSmsProvider;

    @Mock
    RcsParticipant mMockParticipant;

    @Before
    public void setUp() throws Exception {
        super.setUp("RcsMessageStoreControllerTest");
        MockitoAnnotations.initMocks(this);

        mFakeRcsProvider = new FakeProviderWithAsserts();
        mFakeMmsSmsProvider = new FakeProviderWithAsserts();
        mContentResolver = (MockContentResolver) mContext.getContentResolver();
        mContentResolver.addProvider("rcs", mFakeRcsProvider);
        mContentResolver.addProvider("mms-sms", mFakeMmsSmsProvider);

        mRcsMessageStoreController = new RcsMessageStoreController(mContentResolver, null);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testGetRcsThreads() {
        doReturn(123).when(mMockParticipant).getId();
        RcsThreadQueryParameters queryParameters =
                RcsThreadQueryParameters.builder().withParticipant(mMockParticipant).isGroupThread(
                        true).limitResultsTo(30).sort(true).build();

        mFakeRcsProvider.setExpectedQueryParameters(Uri.parse("content://rcs/thread"),
                new String[]{"_id"}, "isGroup=true&limit=30&participant=123", null, "ASCENDING");
        mRcsMessageStoreController.getRcsThreads(queryParameters);
    }

    @Test
    public void testCreateRcsParticipant() {
        // verify the first query to existing canonical addresses
        mFakeMmsSmsProvider.setExpectedQueryParameters(
                Uri.parse("content://mms-sms/canonical-addresses"), new String[]{"_id"},
                "address=+5551234567", null, null);

        // verify the insert on canonical addresses
        ContentValues expectedMmsSmsValues = new ContentValues(1);
        expectedMmsSmsValues.put("address", "+5551234567");
        mFakeMmsSmsProvider.setInsertReturnValue(
                Uri.parse("content://mms-sms/canonical-address/456"));
        mFakeMmsSmsProvider.setExpectedInsertParameters(
                Uri.parse("content://mms-sms/canonical-addresses"), expectedMmsSmsValues);

        // verify the final insert on rcs participants
        ContentValues expectedRcsValues = new ContentValues(1);
        expectedRcsValues.put("canonical_address_id", 456);
        mFakeRcsProvider.setInsertReturnValue(Uri.parse("content://rcs/participant/1001"));
        mFakeRcsProvider.setExpectedInsertParameters(Uri.parse("content://rcs/participant"),
                expectedRcsValues);

        RcsParticipant participant = mRcsMessageStoreController.createRcsParticipant("+5551234567");

        assertThat(participant.getId()).isEqualTo(1001);
        assertThat(participant.getCanonicalAddress()).isEqualTo("+5551234567");
    }

    @Test
    public void testUpdateRcsParticipantCanonicalAddress() {
        // Setup a mock cursor to return a mock canonical address id.
        Cursor mockCursor = mock(Cursor.class);
        doReturn(1).when(mockCursor).getCount();
        doReturn(true).when(mockCursor).moveToNext();
        doReturn(998).when(mockCursor).getInt(0);

        // Make the first call to Rcs Provider return the mock cursor
        mFakeRcsProvider.setExpectedQueryParameters(Uri.parse("content://rcs/participant"),
                new String[]{"canonical_address_id"}, "id=7", null, null);
        mFakeRcsProvider.setQueryReturnValue(mockCursor);

        // Verify the update on MmsSmsProvider
        ContentValues expectedValues = new ContentValues(1);
        expectedValues.put("address", "+987654321");
        mFakeMmsSmsProvider.setExpectedUpdateParameters(
                Uri.parse("content://mms-sms/canonical-address/998"), expectedValues, null, null);


        mRcsMessageStoreController.updateRcsParticipantCanonicalAddress(7, "+987654321");
    }

    @Test
    public void testUpdateRcsParticipantAlias() {
        ContentValues contentValues = new ContentValues(1);
        contentValues.put("rcs_alias", "New Alias");
        mFakeRcsProvider.setExpectedUpdateParameters(Uri.parse("content://rcs/participant"),
                contentValues, "_id=551", null);

        mRcsMessageStoreController.updateRcsParticipantAlias(551, "New Alias");
    }

    /**
     * TODO(sahinc): fix the test once there is an implementation in place
     */
    @Test
    public void testGetMessageCount() {
        assertEquals(1018, mRcsMessageStoreController.getMessageCount(0));
    }

    /**
     * Mocking/spying ContentProviders break in different ways. Use a fake instead. The
     * RcsMessageStoreController doesn't care if RcsProvider works as expected (and doesn't have
     * visibility into it) - so verifying whether we use the correct parameters should suffice.
     */
    class FakeProviderWithAsserts extends MockContentProvider {
        private Uri mExpectedUri;
        private String[] mExpectedProjection;
        private String mExpectedWhereClause;
        private String[] mExpectedWhereArgs;
        private String mExpectedSortOrder;
        private ContentValues mExpectedContentValues;

        private Cursor mQueryReturnValue;
        private Uri mInsertReturnValue;

        void setExpectedQueryParameters(Uri uri, String[] projection, String whereClause,
                String[] whereArgs, String sortOrder) {
            mExpectedUri = uri;
            mExpectedProjection = projection;
            mExpectedWhereClause = whereClause;
            mExpectedWhereArgs = whereArgs;
            mExpectedSortOrder = sortOrder;
        }

        void setExpectedInsertParameters(Uri uri, ContentValues contentValues) {
            mExpectedUri = uri;
            mExpectedContentValues = contentValues;
        }

        void setExpectedUpdateParameters(Uri uri, ContentValues contentValues, String whereClause,
                String[] whereArgs) {
            mExpectedUri = uri;
            mExpectedContentValues = contentValues;
            mExpectedWhereClause = whereClause;
            mExpectedWhereArgs = whereArgs;
        }

        void setInsertReturnValue(Uri returnValue) {
            mInsertReturnValue = returnValue;
        }

        void setQueryReturnValue(Cursor cursor) {

        }

        @Override
        public Cursor query(Uri uri, String[] projection, String whereClause, String[] whereArgs,
                String sortOrder) {
            assertThat(uri).isEqualTo(mExpectedUri);
            assertThat(projection).isEqualTo(mExpectedProjection);
            assertThat(whereClause).isEqualTo(mExpectedWhereClause);
            assertThat(whereArgs).isEqualTo(mExpectedWhereArgs);
            assertThat(sortOrder).isEqualTo(mExpectedSortOrder);
            return null;
        }

        @Override
        public Uri insert(Uri uri, ContentValues contentValues) {
            assertThat(uri).isEqualTo(mExpectedUri);
            assertThat(contentValues).isEqualTo(mExpectedContentValues);
            return mInsertReturnValue;
        }

        @Override
        public int update(Uri uri, ContentValues contentValues, String whereClause,
                String[] whereArgs) {
            assertThat(uri).isEqualTo(mExpectedUri);
            assertThat(contentValues).isEqualTo(mExpectedContentValues);
            assertThat(whereClause).isEqualTo(mExpectedWhereClause);
            assertThat(whereArgs).isEqualTo(mExpectedWhereArgs);
            return 0;
        }
    }
}
