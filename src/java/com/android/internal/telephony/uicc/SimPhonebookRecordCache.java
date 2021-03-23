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

package com.android.internal.telephony.uicc;

import android.annotation.RequiresFeature;
import android.content.Context;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.telephony.Rlog;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.RadioInterfaceCapabilityController;
import com.android.internal.telephony.uicc.AdnCapacity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;


/**
 * Used to store SIM phonebook records.
 * <p/>
 * This will be {@link #INVALID} if either is the case:
 * <ol>
 *   <li>The device does not support
 * {@link android.telephony.TelephonyManager#CAPABILITY_SIM_PHONEBOOK_IN_MODEM}.</li>
 * </ol>
 * {@hide}
 */
@RequiresFeature(
        enforcement = "com.android.internal.telephony.uicc.SimPhonebookRecordCache#isEnabled",
        value = "")
public final class SimPhonebookRecordCache extends Handler {
    // Instance Variables
    private static final String LOG_TAG = "SimPhoneBookRecordCache";
    private static final boolean DBG = true;
    // member variables
    private final CommandsInterface mCi;
    private int mPhoneId;
    private Context mContext;

    // Presenting ADN capacity, including ADN, EMAIL ANR, and so on.
    private AtomicReference<AdnCapacity> mAdnCapacity = new AtomicReference<AdnCapacity>(null);
    private Object mReadLock = new Object();
    private Object mUpdateLock = new Object();
    //private ArrayList<AdnRecord> mSimPbRecords;
    private List<AdnRecord> mSimPbRecords =
            Collections.synchronizedList(new ArrayList<AdnRecord>());
    // If true, clear the records in the cache and re-query from modem
    private AtomicBoolean mIsCacheInvalidated = new AtomicBoolean(false);
    private AtomicBoolean mIsRecordLoading = new AtomicBoolean(false);
    private AtomicBoolean mIsUpdatedByManual = new AtomicBoolean(false);
    private AtomicBoolean mIsInRetry = new AtomicBoolean(false);

    // People waiting for SIM phonebook records to be loaded
    ArrayList<Message> mAdnLoadingWaiters = new ArrayList<Message>();

    // Event Constants
    private static final int EVENT_PHONEBOOK_CHANGED = 1;
    private static final int EVENT_PHONEBOOK_RECORDS_RECEIVED = 2;
    private static final int EVENT_GET_PHONEBOOK_RECORDS_DONE = 3;
    private static final int EVENT_GET_PHONEBOOK_CAPACITY_DONE = 4;
    private static final int EVENT_UPDATE_PHONEBOOK_RECORD_DONE = 5;
    private static final int EVENT_SIM_REFRESH = 6;
    private static final int EVENT_GET_PHONEBOOK_RECORDS_RETRY = 7;

    private static final int MAX_RETRY_COUNT = 3;
    private static final int RETRY_INTERVAL = 3000; // 3S

    public SimPhonebookRecordCache(Context context, int phoneId, CommandsInterface ci) {
        mCi = ci;
        mPhoneId = phoneId;
        mContext = context;
        mSimPbRecords = new ArrayList<AdnRecord>();
        mCi.registerForSimPhonebookChanged(this, EVENT_PHONEBOOK_CHANGED, null);
        mCi.registerForIccRefresh(this, EVENT_SIM_REFRESH, null);
        mCi.registerForSimPhonebookRecordsReceived(this, EVENT_PHONEBOOK_RECORDS_RECEIVED, null);
    }

    /**
     * This is recommended to use in work thread like IccPhoneBookInterfaceManager
     * becase it can't block main thread.
     * @return true if this feature is supported
     */
    public static boolean isEnabled() {
        return RadioInterfaceCapabilityController
                .getInstance()
                .getCapabilities()
                .contains(TelephonyManager.CAPABILITY_SIM_PHONEBOOK_IN_MODEM);
    }

    public void dispose() {
        reset();
        mCi.unregisterForSimPhonebookChanged(this);
        mCi.unregisterForIccRefresh(this);
        mCi.unregisterForSimPhonebookRecordsReceived(this);
    }

    private void reset() {
        mAdnCapacity.set(null);
        mSimPbRecords.clear();
        mIsCacheInvalidated.set(false);
        mIsUpdatedByManual.set(false);
        mIsRecordLoading.set(false);
        mIsInRetry.set(false);
    }

    private void sendErrorResponse(Message response, String errString) {
        if (response != null) {
            Exception e = new RuntimeException(errString);
            AsyncResult.forMessage(response).exception = e;
            response.sendToTarget();
        }
    }

    private void notifyAndClearWaiters() {
        synchronized (mReadLock) {
            for (Message response : mAdnLoadingWaiters){
                if (response != null) {
                    AsyncResult.forMessage(response).result = mSimPbRecords;
                    response.sendToTarget();
                }
            }
            mAdnLoadingWaiters.clear();
        }
    }

    private void sendResponsesToWaitersWithError() {
        synchronized (mReadLock) {
            mReadLock.notify();

            for (Message response : mAdnLoadingWaiters) {
                sendErrorResponse(response, "Query adn record failed");
            }
            mAdnLoadingWaiters.clear();
        }
    }

    private void getSimPhonebookCapacity() {
        mCi.getSimPhonebookRecords(obtainMessage(EVENT_GET_PHONEBOOK_CAPACITY_DONE));
    }

    public AdnCapacity getAdnCapacity() {
        return mAdnCapacity.get();
    }

    private void fillCache() {
        fillCacheWithoutWaiting();

        synchronized (mReadLock) {
            try {
                mReadLock.wait();
            } catch (InterruptedException e) {
                loge("Interrupted Exception in queryAdnRecord");
            }
        }
    }

    private void fillCacheWithoutWaiting() {
        logd("start to queryAdnRecord");
        if (mIsRecordLoading.compareAndSet(false, true)) {
            mCi.getSimPhonebookRecords(obtainMessage(EVENT_GET_PHONEBOOK_RECORDS_DONE));
        }
    }

    public void requestLoadAllPbRecords(Message response) {
        synchronized (mReadLock) {
            mAdnLoadingWaiters.add(response);
            final int pendingSize = mAdnLoadingWaiters.size();
            if (pendingSize > 1) {
                return;
            }
        }
        if (!mIsRecordLoading.get() && !mIsInRetry.get()) {
            logd("ADN cache has already filled in");
            if (!refreshSimPbCache()) {
                notifyAndClearWaiters();
            }
            return;
        }
        fillCache();
    }

    private void notifyAdnLoadingWaiters() {
        synchronized (mReadLock) {
            mReadLock.notify();
        }
        notifyAndClearWaiters();
    }

    public void updateSimPbAdnBySearch(AdnRecord oldAdn, AdnRecord newAdn, Message response) {
        if (mIsRecordLoading.get() || mIsInRetry.get()) {
            // If SIM phonebook isn't loaded completely, let's reject this update request to
            // avoid any mismatch.
            sendErrorResponse(response, "SIM Phonebook record is yet loading");
            return;
        }
        int index = -1;
        if (oldAdn.isEmpty() && !newAdn.isEmpty()) {
            // add contact
            index = 0;
        } else {
            int count = 1;
            // delete or update contact
            for (Iterator<AdnRecord> it = mSimPbRecords.iterator(); it.hasNext();) {
                if (oldAdn.isEqual(it.next())) {
                    index = count;
                    break;
                }
                count++;
            }
        }
        if (index == -1) {
            sendErrorResponse(response, "SIM Phonebook record don't exist for " + oldAdn);
            return;
        }

        if (index == 0 && mAdnCapacity.get() != null && mAdnCapacity.get().isSimFull()) {
            sendErrorResponse(response, "SIM Phonebook record is full");
            return;
        }

        int recordIndex = (index == 0) ? 0 : mSimPbRecords.get(index - 1).getRecordNumber();

        SimPhonebookRecord updateAdn = new SimPhonebookRecord.Builder()
                .setRecordIndex(recordIndex)
                .setAlphaTag(newAdn.getAlphaTag())
                .setNumber(newAdn.getNumber())
                .setEmails(newAdn.getEmails())
                .setAdditionalNumbers(newAdn.getAdditionalNumbers())
                .build();
        UpdateRequest updateRequest = new UpdateRequest(index, newAdn, response);
        mIsUpdatedByManual.set(true);
        mCi.updateSimPhonebookRecord(updateAdn,
                obtainMessage(EVENT_UPDATE_PHONEBOOK_RECORD_DONE, updateRequest));
        // Allow one client every time only
        synchronized (mUpdateLock) {
            try {
                mUpdateLock.wait();
            } catch (InterruptedException e) {
                loge("Interrupted Exception in updateSimPbAdnBySearch");
            }
        }
    }

    @Override
    public void handleMessage(Message msg) {
        AsyncResult ar = (AsyncResult)msg.obj;

        switch(msg.what) {
            case EVENT_PHONEBOOK_CHANGED:
                logd("EVENT_PHONEBOOK_CHANGED");
                handlePhonebookChanged();
                break;
            case EVENT_GET_PHONEBOOK_RECORDS_DONE:
                logd("EVENT_GET_PHONEBOOK_RECORDS_DONE");
                if (ar != null && ar.exception != null) {
                    loge("Failed to gain phonebook records");
                    invalidateSimPbCache();
                    sendResponsesToWaitersWithError();
                }
                break;
            case EVENT_GET_PHONEBOOK_CAPACITY_DONE:
                if (ar != null && ar.exception == null) {
                    AdnCapacity capacity = (AdnCapacity)ar.result;
                    mAdnCapacity.set(capacity);
                    // there is nothing from PB, so notify waiters directly
                    if (capacity != null && capacity.getMaxAdnCount() == 0) {
                        notifyAdnLoadingWaiters();
                    } else {
                        fillCacheWithoutWaiting();
                    }
                }
                break;
            case EVENT_PHONEBOOK_RECORDS_RECEIVED:
                logd("Loading ADN record done");
                if (ar.exception != null) {
                    loge("Unexpected exception happened");
                    break;
                }

                handlePhonebookRecordReceived((ReceivedPhonebookRecords)(ar.result));
                break;
            case EVENT_UPDATE_PHONEBOOK_RECORD_DONE:
                logd("Update ADN record done");
                handleUpdatePhonebookRecordDone(ar);
                break;
            case EVENT_SIM_REFRESH:
                ar = (AsyncResult)msg.obj;
                logd("SIM REFRESH occurred");
                if (ar.exception == null) {
                    handleSimRefresh((IccRefreshResponse)ar.result);
                } else {
                    logd("SIM refresh Exception: " + ar.exception);
                }
                break;
            case EVENT_GET_PHONEBOOK_RECORDS_RETRY:
                int retryCount = msg.arg1;
                if (retryCount < MAX_RETRY_COUNT) {
                    sendGettingPhonebookRecordsRetry(++retryCount);
                } else {
                    mIsRecordLoading.set(false);
                    mIsInRetry.set(false);
                    notifyAdnLoadingWaiters();
                }
                break;
            default:
                loge("Unexpected event: " + msg.what);
        }

    }

    private void handlePhonebookChanged() {
        if (mIsUpdatedByManual.compareAndSet(true, false)) {
            logd("Do nothing for manual update");
        } else {
            invalidateSimPbCache();
            // If this event is received, means this feature is supported.
            getSimPhonebookCapacity();
        }
    }

    private void handlePhonebookRecordReceived(ReceivedPhonebookRecords records) {
        if (records != null) {
            PhonebookStatus status = records.getPhonebookStatus();
            if (status.isOk()) {
                logd("Partial data is received");
                populateAdnRecords(records.getPhonebookRecords());
            } else if (status.isCompleted()) {
                logd("The whole loading process is finished");
                populateAdnRecords(records.getPhonebookRecords());
                mIsRecordLoading.set(false);
                mIsInRetry.set(false);
                notifyAdnLoadingWaiters();
            } else if (status.isRetryNeeded() && !mIsInRetry.get()) {
                logd("Start to retry as aborted");
                sendGettingPhonebookRecordsRetry(0);
            } else {
                loge("Error happened");
                // Let's keep the stale data, in example of SIM getting removed during loading,
                // expects to finish the whole process.
                mIsRecordLoading.set(false);
                mIsInRetry.set(false);
                notifyAdnLoadingWaiters();
            }
        } else {
            loge("No records there");
        }
    }

    private void handleUpdatePhonebookRecordDone(AsyncResult ar) {
        Exception e = null;
        UpdateRequest updateRequest = (UpdateRequest)ar.userObj;
        if (ar.exception == null) {
            int index = updateRequest.index;
            AdnRecord adn = updateRequest.adnRecord;
            int recordIndex = ((int[]) (ar.result))[0];

            if (index == 0) {
                // add contact
                addSimPbRecord(adn, recordIndex);
            } else if (adn.isEmpty()){
                // delete contact
                AdnRecord deletedRecord = mSimPbRecords.get(index - 1);
                int adnRecordIndex = deletedRecord.getRecordNumber();
                logd("Record number for deleted ADN is " + adnRecordIndex);
                if(recordIndex == adnRecordIndex) {
                    deleteSimPbRecord(deletedRecord, index); ;
                } else {
                    e = new RuntimeException(
                            "The index for deleted ADN record did not match");
                }
            } else {
                // Change contact
                if (mSimPbRecords.size() > index - 1) {
                    AdnRecord changedRecord = mSimPbRecords.get(index - 1);
                    int adnRecordIndex = changedRecord.getRecordNumber();
                    logd("Record number for changed ADN is " + adnRecordIndex);
                    if(recordIndex == adnRecordIndex) {
                        updateSimPbRecord(changedRecord, adn, recordIndex, index);
                    } else {
                        e = new RuntimeException(
                                "The index for changed ADN record did not match");
                    }
                } else {
                    e = new RuntimeException(
                            "The index for changed ADN record is out of the border");
                }
            }
        } else {
            e = new RuntimeException("Update adn record failed", ar.exception);
        }
        synchronized (mUpdateLock) {
            mUpdateLock.notify();
        }
        AsyncResult.forMessage(updateRequest.response, null, e);
        updateRequest.response.sendToTarget();
    }

    private void handleSimRefresh(IccRefreshResponse iccRefreshResponse) {
        if (refreshRsp != null) {
            if (refreshRsp.refreshResult == IccRefreshResponse.REFRESH_RESULT_FILE_UPDATE
                    && (refreshRsp.efId == IccConstants.EF_PBR ||
                    refreshRsp.efId == IccConstants.EF_ADN) ||
                    refreshRsp.refreshResult == IccRefreshResponse.REFRESH_RESULT_INIT) {
                invalidateSimPbCache();
                getSimPhonebookCapacity();
            }
        } else {
            logd("IccRefreshResponse received is null");
        }
    }

    private void populateAdnRecords(List<SimPhonebookRecord> records) {
        if (records != null) {
            List<AdnRecord> newRecords = records.stream().map(record -> {return new AdnRecord(0,
                    record.getRecordIndex(),
                    record.getAlphaTag(),
                    record.getNumber(),
                    record.getEmails(),
                    record.getAdditionalNumbers());}).collect(Collectors.toList());
            mSimPbRecords.addAll(newRecords);
        }
    }

    private void sendGettingPhonebookRecordsRetry (int times) {
        Message message = obtainMessage(EVENT_GET_PHONEBOOK_RECORDS_RETRY, 1, 0);
        sendMessageDelayed(message, RETRY_INTERVAL);
    }

    private void addSimPbRecord(AdnRecord addedRecord, int recordIndex) {
        logd("Record number for added ADN is " + recordIndex);
        AdnCapacity capacity = mAdnCapacity.get();
        if (capacity == null) {
            loge("capacity is null");
            return;
        }
        // updated the used email count in cache
        if(addedRecord.getEmails() != null) {
            int usedEmailCount = capacity.getUsedEmailCount() + addedRecord.getEmails().length;
            usedEmailCount = Math.min(usedEmailCount, capacity.getMaxEmailCount());
            capacity.setUsedEmailCount(usedEmailCount);
        }
        // updated the used additional number count in cache
        if(addedRecord.getAdditionalNumbers() != null) {
            int usedAnrCount =
                    capacity.getUsedAnrCount() + addedRecord.getAdditionalNumbers().length;
            usedAnrCount = Math.min(usedAnrCount, capacity.getMaxAnrCount());
            capacity.setUsedAnrCount(usedAnrCount);
        }

        addedRecord.setRecordNumber(recordIndex);
        mSimPbRecords.add(addedRecord);
        int usedAdn = capacity.getUsedAdnCount() + 1;
        usedAdn = Math.min(usedAdn, capacity.getMaxAdnCount());
        capacity.setUsedAdnCount(usedAdn);
    }


    private void deleteSimPbRecord(AdnRecord deletedRecord, int index) {
        logd("Record number for deleted ADN is " + index);
        AdnCapacity capacity = mAdnCapacity.get();
        if (capacity == null) {
            loge("capacity is null");
            return;
        }
        // updated the used email count in cache
        if(deletedRecord.getEmails() != null) {
            int usedEmailCount =
                    capacity.getUsedEmailCount() - deletedRecord.getEmails().length;
            usedEmailCount = Math.max(usedEmailCount, 0);
            capacity.setUsedEmailCount(usedEmailCount);
        }
        // updated the used additional number count in cache
        if(deletedRecord.getAdditionalNumbers() != null) {
            int usedAnrCount =
                   capacity.getUsedAnrCount() - deletedRecord.getAdditionalNumbers().length;
            usedAnrCount = Math.max(usedAnrCount, 0);
            capacity.setUsedAnrCount(usedAnrCount);
        }

        mSimPbRecords.remove(index - 1);
        int usedAdn = capacity.getUsedAdnCount() - 1;
        usedAdn = Math.max(usedAdn, 0);
        capacity.setUsedAdnCount(usedAdn);
    }

    private void updateSimPbRecord(AdnRecord oldRecord, AdnRecord newRecord,
            int recordIndex, int index) {
        logd("Record number for updated ADN is " + recordIndex);
        AdnCapacity capacity = mAdnCapacity.get();
        if (capacity == null) {
            loge("capacity is null");
            return;
        }
        // updated the used email count in cache
        int oldEmailCount = oldRecord.getEmails() == null
                ? 0 : oldRecord.getEmails().length;
        int newEmailCount =
            newRecord.getEmails() == null ? 0 : newRecord.getEmails().length;
        int usedEmailCount = capacity.getUsedEmailCount() - oldEmailCount + newEmailCount;
        usedEmailCount = usedEmailCount > 0 && usedEmailCount <= capacity.getMaxEmailCount()
                ? usedEmailCount : capacity.getUsedEmailCount();
        capacity.setUsedEmailCount(usedEmailCount);
        // updated the used additional number count in cache
        int oldAnrCount = oldRecord.getAdditionalNumbers() == null
                ? 0 : oldRecord.getAdditionalNumbers().length;
        int newAnrCount = newRecord.getAdditionalNumbers() == null
                ? 0 : newRecord.getAdditionalNumbers().length;
        int usedAnrCount = capacity.getUsedAnrCount() - oldAnrCount + newAnrCount;
        usedAnrCount = usedAnrCount > 0 && usedAnrCount <= capacity.getMaxAnrCount()
                ? usedAnrCount : capacity.getUsedAnrCount();
        capacity.setUsedAnrCount(usedAnrCount);
        newRecord.setRecordNumber(recordIndex);
        mSimPbRecords.set(index - 1, newRecord);
    }

    private void invalidateSimPbCache() {
        logd("invalidateSimPbCache");
        mIsCacheInvalidated.set(true);
        mSimPbRecords.clear();
    }

    private boolean refreshSimPbCache() {
        logd("refreshSimPbCache");
        if (mIsCacheInvalidated.get()) {
            fillCache();
            return true;
        }
        return false;
    }

    private void logd(String msg) {
        if (DBG) {
            Rlog.d(LOG_TAG, msg);
        }
    }

    private void loge(String msg) {
        if (DBG) {
            Rlog.e(LOG_TAG, msg);
        }
    }

    private final static class UpdateRequest {
        public int index;
        public Message response;
        public AdnRecord adnRecord;

        UpdateRequest(int index, AdnRecord record, Message response) {
            this.index = index;
            this.adnRecord = record;
            this.response = response;
        }
    }
}
