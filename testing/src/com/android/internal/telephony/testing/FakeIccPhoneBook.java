/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.internal.telephony.testing;

import android.content.ContentValues;
import android.util.Pair;

import com.android.internal.telephony.IIccPhoneBook;
import com.android.internal.telephony.IccProvider;
import com.android.internal.telephony.uicc.AdnCapacity;
import com.android.internal.telephony.uicc.AdnRecord;
import com.android.internal.telephony.uicc.IccConstants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fake implementation of the IIccPhoneBook service.
 */
public final class FakeIccPhoneBook extends IIccPhoneBook.Default {

    private static final int DEFAULT_RECORD_SIZE = 30;
    private static final int DEFAULT_RECORDS_COUNT = 100;

    // The key for both maps is the (subscription ID, efid)
    private Map<Pair<Integer, Integer>, AdnRecord[]> mRecords = new HashMap<>();
    // The value is the single record size
    private Map<Pair<Integer, Integer>, Integer> mRecordSizes = new HashMap<>();

    private int mDefaultSubscriptionId = 101;

    private void addRecord(Pair<Integer, Integer> key, AdnRecord record) {
        // Assume that if records are being added then the test wants it to be a valid
        // elementary file so set sizes as well.
        if (!mRecordSizes.containsKey(key)) {
            setRecordsSize(key.first, key.second,
                    Math.max(record.getRecId(), DEFAULT_RECORDS_COUNT), DEFAULT_RECORD_SIZE);
        }
        mRecords.get(key)[record.getRecId() - 1] = record;
    }

    /** Adds a record to the SIM with the default subscription ID. */
    public void addRecord(AdnRecord record) {
        addRecord(Pair.create(mDefaultSubscriptionId, record.getEfid()), record);
    }

    /** Adds a record to the SIM with the specified subscription ID. */
    public void addRecord(int subscriptionId, AdnRecord record) {
        addRecord(Pair.create(subscriptionId, record.getEfid()), record);
    }

    /**
     * Adds a record with the specified name and phone number to the specified elementary file on
     * the SIM with the specified subscription ID.
     */
    public void addRecord(int subscriptionId, int efId, String name, String phoneNumber) {
        Pair<Integer, Integer> key = Pair.create(subscriptionId, efId);
        AdnRecord[] records = mRecords.computeIfAbsent(key, unused ->
                createEmptyRecords(efId, 100));
        int recordIndex = -1;
        for (int i = 0; i < records.length; i++) {
            if (records[i].isEmpty()) {
                recordIndex = i;
                break;
            }
        }
        if (recordIndex == -1) {
            throw new IllegalStateException("");
        }
        addRecord(key, new AdnRecord(efId, recordIndex + 1, name, phoneNumber));
    }

    /** Adds a record to the ADN file on the SIM with the specified subscription ID. */
    public void addAdnRecord(int subscriptionId, String name, String phoneNumber) {
        addRecord(subscriptionId, IccConstants.EF_ADN, name, phoneNumber);
    }

    /** Adds a record to the ADN file on the SIM with the default subscription ID. */
    public void addAdnRecord(String name, String phoneNumber) {
        addRecord(mDefaultSubscriptionId, IccConstants.EF_ADN, name, phoneNumber);
    }

    /** Returns all non-empty records from all SIMs. */
    public List<AdnRecord> getAllValidRecords() {
        List<AdnRecord> result = new ArrayList<>();
        for (AdnRecord[] records : mRecords.values()) {
            for (AdnRecord record : records) {
                if (!record.isEmpty()) {
                    result.add(record);
                }
            }
        }
        return result;
    }

    /**
     * Sets up the fake to return valid records size for all elementary files for default
     * subscription ID.
     */
    public void makeAllEfsSupported() {
        makeAllEfsSupported(mDefaultSubscriptionId);
    }

    /**
     * Sets up the fake to return valid records size for all elementary files for the provided
     * subscription IDs.
     */
    public void makeAllEfsSupported(int... subscriptionIds) {
        for (int subId : subscriptionIds) {
            makeAllEfsSupported(subId);
        }
    }

    /**
     * Sets up the fake to return valid records size for all elementary files for the provided
     * subscription IDs.
     */
    public void makeAllEfsSupported(int subscriptionId) {
        setRecordsSize(subscriptionId, IccConstants.EF_ADN, DEFAULT_RECORDS_COUNT,
                DEFAULT_RECORD_SIZE);
        setRecordsSize(subscriptionId, IccConstants.EF_FDN, DEFAULT_RECORDS_COUNT,
                DEFAULT_RECORD_SIZE);
        setRecordsSize(subscriptionId, IccConstants.EF_SDN, DEFAULT_RECORDS_COUNT,
                DEFAULT_RECORD_SIZE);
    }

    /** Sets the records sizes for the specified EF on the SIM with specified subscription ID. */
    public void setRecordsSize(int subscriptionId, int efid, int maxRecordCount,
            int maxRecordSize) {
        Pair<Integer, Integer> key = Pair.create(subscriptionId, efid);
        mRecordSizes.put(key, maxRecordSize);
        AdnRecord[] records = mRecords.computeIfAbsent(key, unused ->
                createEmptyRecords(efid, maxRecordCount));
        if (records.length < maxRecordCount) {
            throw new IllegalStateException("Records already initialized with a smaller size");
        }
    }

    private AdnRecord[] createEmptyRecords(int efid, int count) {
        AdnRecord[] records = new AdnRecord[count];
        for (int i = 0; i < records.length; i++) {
            if (records[i] == null) {
                records[i] = new AdnRecord(efid, i + 1, "", "");
            }
        }
        return records;
    }

    /** Sets the default subscription ID to use for methods where it is left unspecified. */
    public void setDefaultSubscriptionId(int defaultSubscriptionId) {
        mDefaultSubscriptionId = defaultSubscriptionId;
    }

    /** Reset the records and supported EFs. */
    public void reset() {
        mRecords.clear();
        mRecordSizes.clear();
    }

    @Override
    public List<AdnRecord> getAdnRecordsInEf(int efid) {
        return getAdnRecordsInEfForSubscriber(mDefaultSubscriptionId, efid);
    }

    @Override
    public List<AdnRecord> getAdnRecordsInEfForSubscriber(int subId, int efid) {
        return Arrays.asList(
                mRecords.getOrDefault(Pair.create(subId, efid), new AdnRecord[0]));
    }

    @Override
    public boolean updateAdnRecordsInEfBySearchForSubscriber(int subId, int efid,
            ContentValues values, String pin2) {
        final String oldTag = values.getAsString(IccProvider.STR_TAG);
        final String oldPhoneNumber = values.getAsString(IccProvider.STR_NUMBER);
        final String newTag = values.getAsString(IccProvider.STR_NEW_TAG);
        final String newPhoneNumber = values.getAsString(IccProvider.STR_NEW_NUMBER);
        return updateAdnRecordsInEfBySearchForSubscriber(subId, efid, oldTag, oldPhoneNumber,
                newTag, newPhoneNumber, pin2);

    }

    private boolean updateAdnRecordsInEfBySearchForSubscriber(int subId, int efid, String oldTag,
            String oldPhoneNumber, String newTag, String newPhoneNumber, String pin2) {
        if (!oldTag.isEmpty() || !oldPhoneNumber.isEmpty()) {
            throw new IllegalArgumentException(
                    "updateAdnRecordsInEfBySearchForSubscriber only supports insert");
        }
        addRecord(subId, efid, newTag, newPhoneNumber);
        return true;
    }

    @Override
    public boolean updateAdnRecordsInEfByIndexForSubscriber(int subId, int efid,
            ContentValues values, int index, String pin2) {
        final String newTag = values.getAsString(IccProvider.STR_NEW_TAG);
        final String newPhoneNumber = values.getAsString(IccProvider.STR_NEW_NUMBER);
        return updateAdnRecordsInEfByIndexForSubscriber(subId, efid, newTag, newPhoneNumber,
                index, pin2);

    }

    private boolean updateAdnRecordsInEfByIndexForSubscriber(int subId, int efid, String newTag,
            String newPhoneNumber, int index, String pin2) {
        AdnRecord[] records = mRecords.computeIfAbsent(Pair.create(subId, efid), unused ->
                createEmptyRecords(efid, 100));
        records[index - 1] = new AdnRecord(efid, index, newTag, newPhoneNumber);
        return true;
    }

    @Override
    public int[] getAdnRecordsSize(int efid) {
        return getAdnRecordsSizeForSubscriber(mDefaultSubscriptionId, efid);
    }

    @Override
    public int[] getAdnRecordsSizeForSubscriber(int subId, int efid) {
        Pair<Integer, Integer> key = Pair.create(subId, efid);
        Integer recordSize = mRecordSizes.get(key);
        if (recordSize == null) {
            return new int[]{0, 0, 0};
        }
        int count = mRecords.get(key).length;
        return new int[]{recordSize, recordSize * count, count};
    }

    @Override
    public AdnCapacity getAdnRecordsCapacityForSubscriber(int subId) {
        return new AdnCapacity(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }
}
