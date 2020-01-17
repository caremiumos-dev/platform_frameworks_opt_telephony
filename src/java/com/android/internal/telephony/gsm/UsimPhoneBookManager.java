/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.internal.telephony.gsm;

import android.compat.annotation.UnsupportedAppUsage;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.telephony.Rlog;
import android.text.TextUtils;
import android.util.SparseArray;
import android.util.SparseIntArray;

import com.android.internal.telephony.GsmAlphabet;
import com.android.internal.telephony.uicc.AdnRecord;
import com.android.internal.telephony.uicc.AdnRecordCache;
import com.android.internal.telephony.uicc.IccConstants;
import com.android.internal.telephony.uicc.IccFileHandler;
import com.android.internal.telephony.uicc.IccUtils;

import java.util.ArrayList;

/**
 * This class implements reading and parsing USIM records.
 * Refer to Spec 3GPP TS 31.102 for more details.
 *
 * {@hide}
 */
public class UsimPhoneBookManager extends Handler implements IccConstants {
    private static final String LOG_TAG = "UsimPhoneBookManager";
    private static final boolean DBG = true;
    private ArrayList<PbrRecord> mPbrRecords;
    private Boolean mIsPbrPresent;

    private ArrayList<Integer> mAdnLengthList = null;
    private SparseArray<Integer> mIapSize = null;
    private SparseArray<Integer> mEmailSize = null;
    private SparseArray<SparseArray<Integer>> mAnrSize = null;

    @UnsupportedAppUsage
    private IccFileHandler mFh;
    private AdnRecordCache mAdnCache;
    @UnsupportedAppUsage
    private Object mLock = new Object();
    @UnsupportedAppUsage
    private ArrayList<AdnRecord> mPhoneBookRecords;
    private SparseArray<ArrayList<byte[]>> mIapFileRecord;
    private SparseArray<ArrayList<byte[]>> mEmailFileRecord;
    // EF_ANR files are managed as below.
    // recid / efSetIndex / recIndex
    private SparseArray<SparseArray<ArrayList<byte[]>>> mAnrFileRecord;

    private ArrayList<Integer>[] mEmailFlagsRecord;
    private SparseArray<ArrayList<Integer>> mEmailFlags;
    private SparseArray<ArrayList<Integer>[]> mAnrFlagsRecord;
    private SparseArray<SparseArray<ArrayList<Integer>>> mAnrFlags;

    // email list for each ADN record. The key would be
    // ADN's efid << 8 + record #
    private SparseArray<ArrayList<String>> mEmailsForAdnRec;
    private SparseArray<ArrayList<String>> mAnrsForAdnRec;

    // SFI to ADN Efid mapping table
    private SparseIntArray mSfiEfidTable;

    private boolean mRefreshCache = false;
    private boolean mIapPresent = false;
    private boolean mEmailPresentInIap = false;
    private boolean mAnrPresentInIap = false;

    private int maxAnrSetSize = -1;

    private boolean mSuccess = false;

    private static final int EVENT_PBR_LOAD_DONE = 1;
    private static final int EVENT_USIM_ADN_LOAD_DONE = 2;
    private static final int EVENT_IAP_LOAD_DONE = 3;
    private static final int EVENT_EMAIL_LOAD_DONE = 4;
    private static final int EVENT_ANR_LOAD_DONE = 5;
    private static final int EVENT_EF_IAP_RECORD_SIZE_DONE = 6;
    private static final int EVENT_UPDATE_IAP_RECORD_DONE = 7;
    private static final int EVENT_EF_EMAIL_RECORD_SIZE_DONE = 8;
    private static final int EVENT_UPDATE_EMAIL_RECORD_DONE = 9;
    private static final int EVENT_EF_ANR_RECORD_SIZE_DONE = 10;
    private static final int EVENT_UPDATE_ANR_RECORD_DONE = 11;

    private static final int USIM_TYPE1_TAG   = 0xA8;
    private static final int USIM_TYPE2_TAG   = 0xA9;
    private static final int USIM_TYPE3_TAG   = 0xAA;
    private static final int USIM_EFADN_TAG   = 0xC0;
    private static final int USIM_EFIAP_TAG   = 0xC1;
    private static final int USIM_EFEXT1_TAG  = 0xC2;
    private static final int USIM_EFSNE_TAG   = 0xC3;
    private static final int USIM_EFANR_TAG   = 0xC4;
    private static final int USIM_EFPBC_TAG   = 0xC5;
    private static final int USIM_EFGRP_TAG   = 0xC6;
    private static final int USIM_EFAAS_TAG   = 0xC7;
    private static final int USIM_EFGSD_TAG   = 0xC8;
    private static final int USIM_EFUID_TAG   = 0xC9;
    private static final int USIM_EFEMAIL_TAG = 0xCA;
    private static final int USIM_EFCCP1_TAG  = 0xCB;

    private static final int INVALID_SFI = -1;
    private static final byte INVALID_BYTE = -1;

    // for EF ANR
    private static final int MAX_NUMBER_SIZE_BYTES = 11;
    private static final int ANR_DESCRIPTION_ID = 0;
    private static final int ANR_BCD_NUMBER_LENGTH = 1;
    private static final int ANR_TON_NPI_ID = 2;
    private static final int ANR_ADDITIONAL_NUMBER_START_ID = 3;
    private static final int ANR_ADDITIONAL_NUMBER_END_ID = 12;
    private static final int ANR_CAPABILITY_ID = 13;
    private static final int ANR_EXTENSION_ID = 14;
    private static final int ANR_ADN_SFI_ID = 15;
    private static final int ANR_ADN_RECORD_IDENTIFIER_ID = 16;

    // class File represent a PBR record TLV object which points to the rest of the phonebook EFs
    private class File {
        // Phonebook reference file constructed tag defined in 3GPP TS 31.102
        // section 4.4.2.1 table 4.1
        private final int mParentTag;
        // EFID of the file
        private final int mEfid;
        // SFI (Short File Identification) of the file. 0xFF indicates invalid SFI.
        private final int mSfi;
        // The order of this tag showing in the PBR record.
        private final int mIndex;

        File(int parentTag, int efid, int sfi, int index) {
            mParentTag = parentTag;
            mEfid = efid;
            mSfi = sfi;
            mIndex = index;
        }

        public int getParentTag() { return mParentTag; }
        public int getEfid() { return mEfid; }
        public int getSfi() { return mSfi; }
        public int getIndex() { return mIndex; }
    }

    public UsimPhoneBookManager(IccFileHandler fh, AdnRecordCache cache) {
        mFh = fh;
        mPhoneBookRecords = new ArrayList<AdnRecord>();
        mPbrRecords = null;
        // We assume its present, after the first read this is updated.
        // So we don't have to read from UICC if its not present on subsequent reads.
        mIsPbrPresent = true;
        mAdnCache = cache;
        mEmailsForAdnRec = new SparseArray<ArrayList<String>>();
        mAnrsForAdnRec = new SparseArray<ArrayList<String>>();

        mSfiEfidTable = new SparseIntArray();
        mAdnLengthList = new ArrayList<Integer>();
        mIapFileRecord = new SparseArray<ArrayList<byte[]>>();
        mEmailFileRecord = new SparseArray<ArrayList<byte[]>>();
        mAnrFileRecord = new SparseArray<SparseArray<ArrayList<byte[]>>>();

        mEmailFlags = new SparseArray<ArrayList<Integer>>();
        mAnrFlags = new SparseArray<SparseArray<ArrayList<Integer>>>();

        mIapSize = new SparseArray<Integer>();
        mEmailSize = new SparseArray<Integer>();
        mAnrSize = new SparseArray<SparseArray<Integer>>();
    }

    @UnsupportedAppUsage
    public void reset() {
        if (mPbrRecords != null) {
            for (int i = 0; i < mPbrRecords.size(); i++) {
                if (mEmailFlagsRecord != null && mEmailFlagsRecord[i] != null) {
                    mEmailFlagsRecord[i].clear();
                }
                if (mAnrFlagsRecord != null && mAnrFlagsRecord[i] != null) {
                    mAnrFlagsRecord[i].clear();
                }
            }
        }
        mPhoneBookRecords.clear();
        mAdnLengthList.clear();
        mIapFileRecord.clear();
        mEmailFileRecord.clear();
        mAnrFileRecord.clear();
        mEmailFlags.clear();
        mAnrFlags.clear();
        mPbrRecords = null;
        mIsPbrPresent = true;
        mRefreshCache = false;
        mEmailsForAdnRec.clear();
        mAnrsForAdnRec.clear();
        mSfiEfidTable.clear();

        mIapSize.clear();
        mEmailSize.clear();
        mAnrSize.clear();
    }

    // Load all phonebook related EFs from the SIM.
    @UnsupportedAppUsage
    public ArrayList<AdnRecord> loadEfFilesFromUsim() {
        synchronized (mLock) {
            if (!mPhoneBookRecords.isEmpty()) {
                if (mRefreshCache) {
                    mRefreshCache = false;
                    refreshCache();
                }
                return mPhoneBookRecords;
            }

            if (!mIsPbrPresent) return null;

            // Check if the PBR file is present in the cache, if not read it
            // from the USIM.
            if (mPbrRecords == null) {
                readPbrFileAndWait();
            }

            if (mPbrRecords == null)
                return null;

            int numRecs = mPbrRecords.size();

            if (mEmailFlagsRecord == null) {
                mEmailFlagsRecord = new ArrayList[numRecs];
            }
            if (mAnrFlagsRecord == null) {
                mAnrFlagsRecord = new ArrayList[numRecs];
            }

            log("loadEfFilesFromUsim: Loading adn and emails");
            for (int i = 0; i < numRecs; i++) {
                readAdnFileAndWait(i);
                readEmailFileAndWait(i);
                readAnrFileAndWait(i);
            }

            updatePhoneAdnRecord();
            // All EF files are loaded, return all the records
        }
        return mPhoneBookRecords;
    }

    // Refresh the phonebook cache.
    private void refreshCache() {
        if (mPbrRecords == null) return;
        mPhoneBookRecords.clear();

        int numRecs = mPbrRecords.size();
        for (int i = 0; i < numRecs; i++) {
            readAdnFileAndWait(i);
        }
    }

    // Invalidate the phonebook cache.
    public void invalidateCache() {
        mRefreshCache = true;
    }

    // Read the phonebook reference file EF_PBR.
    private void readPbrFileAndWait() {
        mFh.loadEFLinearFixedAll(EF_PBR, obtainMessage(EVENT_PBR_LOAD_DONE));
        try {
            mLock.wait();
        } catch (InterruptedException e) {
            Rlog.e(LOG_TAG, "Interrupted Exception in readAdnFileAndWait");
        }
    }

    // Read EF_EMAIL which contains the email records.
    private void readEmailFileAndWait(int recId) {
        SparseArray<File> files;
        files = mPbrRecords.get(recId).mFileIds;
        if (files == null) return;

        File email = files.get(USIM_EFEMAIL_TAG);
        if (email != null) {

            /**
             * Check if the EF_EMAIL is a Type 1 file or a type 2 file.
             * If mEmailPresentInIap is true, its a type 2 file.
             * So we read the IAP file and then read the email records.
             * instead of reading directly.
             */
            if (email.getParentTag() == USIM_TYPE2_TAG) {
                if (files.get(USIM_EFIAP_TAG) == null) {
                    Rlog.e(LOG_TAG, "Can't locate EF_IAP in EF_PBR.");
                    return;
                }

                log("EF_IAP exists. Loading EF_IAP to retrieve the index.");
                readIapFileAndWait(files.get(USIM_EFIAP_TAG).getEfid(), recId);
                if (mIapFileRecord.get(recId) == null) {
                    Rlog.e(LOG_TAG, "Error: IAP file is empty");
                    return;
                }

                log("EF_EMAIL order in PBR record: " + email.getIndex());
            }

            int emailEfid = email.getEfid();
            log("EF_EMAIL exists in PBR. efid = 0x" +
                    Integer.toHexString(emailEfid).toUpperCase());

            /**
             * Make sure this EF_EMAIL was never read earlier. Sometimes two PBR record points
             */
            // to the same EF_EMAIL
            for (int i = 0; i < recId; i++) {
                if (mPbrRecords.get(i) != null) {
                    SparseArray<File> previousFileIds = mPbrRecords.get(i).mFileIds;
                    if (previousFileIds != null) {
                        File id = previousFileIds.get(USIM_EFEMAIL_TAG);
                        if (id != null && id.getEfid() == emailEfid) {
                            log("Skipped this EF_EMAIL which was loaded earlier");
                            return;
                        }
                    }
                }
            }

            // Read the EFEmail file.
            mFh.loadEFLinearFixedAll(emailEfid,
                    obtainMessage(EVENT_EMAIL_LOAD_DONE, recId, 0));
            try {
                mLock.wait();
            } catch (InterruptedException e) {
                Rlog.e(LOG_TAG, "Interrupted Exception in readEmailFileAndWait");
            }

            if (mEmailFileRecord.get(recId) == null) {
                Rlog.e(LOG_TAG, "Error: Email file is empty");
                return;
            }

            // create and clear FlagRecords
            mEmailFlagsRecord[recId] = new ArrayList<Integer>();
            for (int i = 0; i < mEmailFileRecord.get(recId).size(); i++) {
                mEmailFlagsRecord[recId].add(0);
            }
            mEmailFlags.put(recId, mEmailFlagsRecord[recId]);

            // Build email list
            if (email.getParentTag() == USIM_TYPE2_TAG && mIapFileRecord.get(recId) != null) {
                // If the tag is type 2 and EF_IAP exists, we need to build tpe 2 email list
                buildType2EmailList(recId);
            }
            else {
                // If one the followings is true, we build type 1 email list
                // 1. EF_IAP does not exist or it is failed to load
                // 2. ICC cards can be made such that they have an IAP file but all
                //    records are empty. In that case buildType2EmailList will fail and
                //    we need to build type 1 email list.

                // Build type 1 email list
                buildType1EmailList(recId);
            }
        }
    }

    // Build type 1 email list
    private void buildType1EmailList(int recId) {
        /**
         * If this is type 1, the number of records in EF_EMAIL would be same as the record number
         * in the master/reference file.
         */
        if (mPbrRecords.get(recId) == null)
            return;

        int numRecs = mPbrRecords.get(recId).mMasterFileRecordNum;
        log("Building type 1 email list. recId = "
                + recId + ", numRecs = " + numRecs);

        File file = mPbrRecords.get(recId).mFileIds.get(USIM_EFADN_TAG);
        if (file == null) {
            log("Error: Improper ICC card. EF_ADN does not exist in PBR");
            return;
        }
        int adnEfid = file.getEfid();

        byte[] emailRec;
        for (int i = 0; i < numRecs; i++) {
            try {
                emailRec = mEmailFileRecord.get(recId).get(i);
            } catch (IndexOutOfBoundsException e) {
                Rlog.e(LOG_TAG, "Error: Improper ICC card: No email record for ADN, continuing");
                continue;
            }

            /**
             *  3GPP TS 31.102 4.4.2.13 EF_EMAIL (e-mail address)
             *
             *  The fields below are mandatory if and only if the file
             *  is not type 1 (as specified in EF_PBR)
             *
             *  Byte [X + 1]: ADN file SFI (Short File Identification)
             *  Byte [X + 2]: ADN file Record Identifier
             *
             *  TODO: is it needed check sfi efid table during builtType2EmailList
             *  as below comments of 3GPP TS 31.102?
             *
             *  NOTE: The fields marked C above are mandatory if and only if the file is not type 1
             * (as specified in EF_PBR)
             */
            int adnRecId = (mPhoneBookRecords.get(i)).getRecId();
            /**
             * SIM record numbers are 1 based.
             * The key is constructed by efid and record index.
             */
            int index = (((adnEfid & 0xFFFF) << 8) | ((adnRecId - 1) & 0xFF));
            ArrayList<String> emailList = mEmailsForAdnRec.get(index);
            if (emailList == null) {
                emailList = new ArrayList<String>();
            }

            String email = readEmailRecord(recId, i);
            if (TextUtils.isEmpty(email)) {
                continue;
            }

            log("Adding email #" + i + " list to index 0x"
                    + Integer.toHexString(index).toUpperCase());
            emailList.add(email);
            // set index of numRecs to mEmailFlags
            mEmailFlags.get(recId).set(i, 1);
            mEmailsForAdnRec.put(index, emailList);
        }
    }

    // Build type 2 email list
    private boolean buildType2EmailList(int recId) {

        if (mPbrRecords.get(recId) == null)
            return false;

        int numRecs = mPbrRecords.get(recId).mMasterFileRecordNum;
        log("Building type 2 email list. recId = "
                + recId + ", numRecs = " + numRecs);

        /**
         * 3GPP TS 31.102 4.4.2.1 EF_PBR (Phone Book Reference file) table 4.1

         * The number of records in the IAP file is same as the number of records in the master
         * file (e.g EF_ADN). The order of the pointers in an EF_IAP shall be the same as the
         * order of file IDs that appear in the TLV object indicated by Tag 'A9' in the
         * reference file record (e.g value of mEmailTagNumberInIap)
         */

        File adnFile = mPbrRecords.get(recId).mFileIds.get(USIM_EFADN_TAG);
        if (adnFile == null) {
            Rlog.e(LOG_TAG, "Error: Improper ICC card: EF_ADN does not exist in PBR files");
            return false;
        }
        int adnEfid = adnFile.getEfid();

        for (int i = 0; i < numRecs; i++) {
            byte[] record;
            int emailRecId;
            try {
                record = mIapFileRecord.get(recId).get(i);
                emailRecId =
                        record[mPbrRecords.get(recId).mFileIds
                                .get(USIM_EFEMAIL_TAG).getIndex()];
            } catch (IndexOutOfBoundsException e) {
                Rlog.e(LOG_TAG, "Error: Improper ICC card: Corrupted EF_IAP");
                continue;
            }

            String email = readEmailRecord(recId, emailRecId - 1);
            if (email != null && !email.equals("")) {
                // The key is constructed by efid and record index.
                int index = (((adnEfid & 0xFFFF) << 8) | (i & 0xFF));
                ArrayList<String> emailList = mEmailsForAdnRec.get(index);
                if (emailList == null) {
                    emailList = new ArrayList<String>();
                }
                emailList.add(email);
                // set index of numRecs to mEmailFlags
                mEmailFlags.get(recId).set(emailRecId - 1, 1);
                log("Adding email list to index 0x"
                        + Integer.toHexString(index).toUpperCase());
                mEmailsForAdnRec.put(index, emailList);
            }
        }
        return true;
    }

    // Read EF_EMAIL which contains the email records.
    private void readAnrFileAndWait(int recId) {
        SparseArray<File> files;
        files = mPbrRecords.get(recId).mFileIds;
        if (files == null) return;

        File anr = files.get(USIM_EFANR_TAG);
        if (anr != null) {

            /**
             * Check if the EF_ANR is a Type 1 file or a type 2 file.
             * If mAnrPresentInIap is true, its a type 2 file.
             * So we read the IAP file and then read the anr records.
             * instead of reading directly.
             */
            if (anr.getParentTag() == USIM_TYPE2_TAG) {
                if (files.get(USIM_EFIAP_TAG) == null) {
                    Rlog.e(LOG_TAG, "Can't locate EF_IAP in EF_PBR.");
                    return;
                }

                log("EF_IAP exists. Loading EF_IAP to retrieve the index.");
                readIapFileAndWait(files.get(USIM_EFIAP_TAG).getEfid(), recId);
                if (mIapFileRecord.get(recId) == null) {
                    Rlog.e(LOG_TAG, "Error: IAP file is empty");
                    return;
                }

                log("EF_ANR order in PBR record: " + anr.getIndex());
            }

            int anrEfid = anr.getEfid();
            log("EF_ANR exists in PBR. efid = 0x" +
                    Integer.toHexString(anrEfid).toUpperCase());

            /**
             * Make sure this EF_ANR was never read earlier. Sometimes two PBR record points
             */
            // to the same EF_ANR
            for (int i = 0; i < recId; i++) {
                if (mPbrRecords.get(i) != null) {
                    SparseArray<File> previousFileIds = mPbrRecords.get(i).mFileIds;
                    if (previousFileIds != null) {
                        File id = previousFileIds.get(USIM_EFANR_TAG);
                        if (id != null && id.getEfid() == anrEfid) {
                            log("Skipped this EF_ANR which was loaded earlier");
                            return;
                        }
                    }
                }
            }

            maxAnrSetSize = mPbrRecords.get(recId).mAnrFileIds.size();
            log("The number of EF_ANR sets is:" + maxAnrSetSize);
            for (int efSetIndex = 0; efSetIndex < maxAnrSetSize; efSetIndex++) {
                int tmpRecIdWithSetIndex = ((efSetIndex << 8) & 0xff00) | (recId & 0xff);

                // Read the EFAnr file.
                mFh.loadEFLinearFixedAll(mPbrRecords.get(recId).mAnrFileIds.get(efSetIndex),
                        obtainMessage(EVENT_ANR_LOAD_DONE, tmpRecIdWithSetIndex, 0));
                try {
                    mLock.wait();
                } catch (InterruptedException e) {
                    Rlog.e(LOG_TAG, "Interrupted Exception in readAnrFileAndWait");
                }

                if (mAnrFileRecord.get(recId) == null
                        || mAnrFileRecord.get(recId).get(efSetIndex) == null) {
                    Rlog.e(LOG_TAG, "Error: Anr file is empty");
                    return;
                }

                // create and clear FlagRecords
                if (mAnrFlagsRecord.get(efSetIndex) != null) {
                    for (int i = 0; i < mAnrFileRecord.get(recId).get(efSetIndex).size(); i++) {
                        (mAnrFlagsRecord.get(efSetIndex))[recId].add(0);
                    }
                    SparseArray<ArrayList<Integer>> tmpFlags =
                            new SparseArray<ArrayList<Integer>>();
                    tmpFlags.put(efSetIndex, (mAnrFlagsRecord.get(efSetIndex))[recId]);
                    mAnrFlags.put(recId, tmpFlags);
                } else {
                    log("mAnrFlagsRecord for recId:" + recId
                            + ", efSetIndex:" + efSetIndex + " is null");
                }

                // Build anr list
                if (anr.getParentTag() == USIM_TYPE2_TAG && mIapFileRecord.get(recId) != null) {
                    // If the tag is type 2 and EF_IAP exists, we need to build tpe 2 email list
                    buildType2AnrList(recId);
                }
                else {
                    // If one the followings is true, we build type 1 anr list
                    // 1. EF_IAP does not exist or it is failed to load
                    // 2. ICC cards can be made such that they have an IAP file but all
                    //    records are empty. In that case buildType2AnrList will fail and
                    //    we need to build type 1 anr list.

                    // Build type 1 anr list
                    buildType1AnrList(recId);
                }
            }
        }
    }

    // Build type 1 anr list
    private void buildType1AnrList(int recId) {
        /**
         * If this is type 1, the number of records in EF_ANR would be same as the record number
         * in the master/reference file.
         */
        if (mPbrRecords.get(recId) == null)
            return;

        int numRecs = mPbrRecords.get(recId).mMasterFileRecordNum;
        log("Building type 1 anr list. recId = "
                + recId + ", numRecs = " + numRecs);

        File file = mPbrRecords.get(recId).mFileIds.get(USIM_EFADN_TAG);
        if (file == null) {
            log("Error: Improper ICC card. EF_ADN does not exist in PBR");
            return;
        }
        int adnEfid = file.getEfid();

        for (int i = 0; i < maxAnrSetSize; i++) {
            // check efSetIndex file record is valid.
            if (mAnrFileRecord.get(recId) == null
                    || mAnrFileRecord.get(recId).get(i) == null) {
                continue;
            }

            for (int j = 0; j < numRecs; j++) {
                try {
                    byte[] anrRec = mAnrFileRecord.get(recId).get(i).get(j);
                } catch (IndexOutOfBoundsException e) {
                    Rlog.e(LOG_TAG, "Error: Improper ICC card: No anr record for ADN, continuing");
                    continue;
                }

                int adnRecId = (mPhoneBookRecords.get(j)).getRecId();

                /**
                 * SIM record numbers are 1 based.
                 * The key is constructed by efid and record index.
                 */
                int index = (((adnEfid & 0xFFFF) << 8) | ((adnRecId - 1) & 0xFF));
                ArrayList<String> anrList = mAnrsForAdnRec.get(index);
                if (anrList == null) {
                    anrList = new ArrayList<String>();
                }
                String anr = readAnrRecord(recId, i, j);
                if (TextUtils.isEmpty(anr)) {
                    continue;
                }
                anrList.add(anr);
                mAnrFlags.get(recId).get(i).set(j, 1);

                log("Adding anr #" + i + " list to index 0x" +
                        Integer.toHexString(index).toUpperCase());

                mAnrsForAdnRec.put(index, anrList);
            }
        }
    }

    // Build type 2 anr list
    private boolean buildType2AnrList(int recId) {
        if (mPbrRecords.get(recId) == null)
            return false;

        int numRecs = mPbrRecords.get(recId).mMasterFileRecordNum;
        log("Building type 2 anr list. recId = "
                + recId + ", numRecs = " + numRecs);

        /**
         * 3GPP TS 31.102 4.4.2.1 EF_PBR (Phone Book Reference file) table 4.1

         * The number of records in the IAP file is same as the number of records in the master
         * file (e.g EF_ADN). The order of the pointers in an EF_IAP shall be the same as the
         * order of file IDs that appear in the TLV object indicated by Tag 'A9' in the
         * reference file record
         */

        File adnFile = mPbrRecords.get(recId).mFileIds.get(USIM_EFADN_TAG);
        if (adnFile == null) {
            Rlog.e(LOG_TAG, "Error: Improper ICC card: EF_ADN does not exist in PBR files");
            return false;
        }
        int adnEfid = adnFile.getEfid();

        for (int i = 0; i < maxAnrSetSize; i++) {
            // check efSetIndex file record is valid.
            if (mAnrFileRecord.get(recId) == null
                    || mAnrFileRecord.get(recId).get(i) == null) {
                continue;
            }

            for (int j = 0; j < numRecs; j++) {
                byte[] record;
                int anrRecId;
                try {
                    record = mIapFileRecord.get(recId).get(j);
                    anrRecId =
                            record[mPbrRecords.get(recId).mFileIds
                                    .get(USIM_EFANR_TAG).getIndex() + i];
                } catch (IndexOutOfBoundsException e) {
                    Rlog.e(LOG_TAG, "Error: Improper ICC card: Corrupted EF_IAP");
                    continue;
                }

                // The key is constructed by efid and record index.
                int index = (((adnEfid & 0xFFFF) << 8) | (i & 0xFF));
                ArrayList<String> anrList = mAnrsForAdnRec.get(index);
                if (anrList == null) {
                    anrList = new ArrayList<String>();
                }

                String anr = readAnrRecord(recId, i, anrRecId - 1);
                if (TextUtils.isEmpty(anr)) {
                    continue;
                }
                anrList.add(anr);
                mAnrsForAdnRec.put(index, anrList);

                mAnrFlags.get(recId).get(j).set(anrRecId - 1, 1);
                log("Adding anr list to index 0x"
                        + Integer.toHexString(index).toUpperCase());
            }
        }
        return true;
    }

    // Read Phonebook Index Admistration EF_IAP file
    private void readIapFileAndWait(int efid, int recId) {
        mFh.loadEFLinearFixedAll(efid, obtainMessage(EVENT_IAP_LOAD_DONE, recId, 0));
        try {
            mLock.wait();
        } catch (InterruptedException e) {
            Rlog.e(LOG_TAG, "Interrupted Exception in readIapFileAndWait");
        }
    }

    private void updatePhoneAdnRecord() {

        int numAdnRecs = mPhoneBookRecords.size();

        for (int i = 0; i < numAdnRecs; i++) {

            AdnRecord rec = mPhoneBookRecords.get(i);

            int adnEfid = rec.getEfid();
            int adnRecId = rec.getRecId();

            int index = (((adnEfid & 0xFFFF) << 8) | ((adnRecId - 1) & 0xFF));

            ArrayList<String> emailList;
            ArrayList<String> anrList = null;
            try {
                emailList = mEmailsForAdnRec.get(index);
                anrList = mAnrsForAdnRec.get(index);
            } catch (IndexOutOfBoundsException e) {
                continue;
            }

            if (emailList != null) {
                String[] emails = new String[emailList.size()];
                System.arraycopy(emailList.toArray(), 0, emails, 0, emailList.size());
                rec.setEmails(emails);
            }

            if (anrList != null) {
                String[] anrs = new String[anrList.size()];
                System.arraycopy(anrList.toArray(), 0, anrs, 0, anrList.size());
                rec.setAnrs(anrs);
            }
            mPhoneBookRecords.set(i, rec);
        }
    }

    // Read email from the record of EF_EMAIL
    private String readEmailRecord(int recId, int index) {
        byte[] emailRec;
        if (mEmailFileRecord.get(recId) == null) {
            return null;
        }
        try {
            emailRec = mEmailFileRecord.get(recId).get(index);
        } catch (IndexOutOfBoundsException e) {
            return null;
        }

        // The length of the record is X+2 byte, where X bytes is the email address
        return IccUtils.adnStringFieldToString(emailRec, 0, emailRec.length - 2);
    }

    // Read anr from the record of EF_ANR
    private String readAnrRecord(int recId, int efSetIndex, int recIndex) {
        if (mAnrFileRecord.get(recId) == null
                || mAnrFileRecord.get(recId).get(efSetIndex) == null) {
            return null;
        }

        byte[] anrRec;
        try {
            anrRec = mAnrFileRecord.get(recId).get(efSetIndex).get(recIndex);
        } catch (IndexOutOfBoundsException e) {
            return null;
        }

        int numberLength = 0xff & anrRec[1];
        if (numberLength > MAX_NUMBER_SIZE_BYTES) {
            //log("Invalid number length[" + numberLength + "] in anr record: " + recNum +
            //        " recId:" + recId + " offSet:" + offSet);
            return "";
        }
        return PhoneNumberUtils.calledPartyBCDToString(anrRec, 2, numberLength);
    }

    /**
    * added for updating IAP record of Adn record.
    */
    private boolean updateIapFile(int adnRecNum, String oldValue, String newValue,
            int tag, int efSetIndex) {
        int recId = getPbrIndexBy(adnRecNum - 1);
        int efid = getEfidByTag(recId, USIM_EFIAP_TAG, 0);
        mSuccess = false;
        int recordNumber = -1;

        if (efid == -1) {
            return mSuccess;
        }

        switch (tag) {
            case USIM_EFEMAIL_TAG:
                recordNumber = getEmailRecNumber(adnRecNum - 1,
                        mPhoneBookRecords.size(), oldValue);
            break;
        }

        if (TextUtils.isEmpty(newValue)) {
            recordNumber = -1;
        }

        log("updateIapFile  efid=" + efid + ", recordNumber= " + recordNumber
                + ", adnRecNum= " + adnRecNum);

        synchronized (mLock) {
            if (mIapSize != null && mIapSize.get(recId) > 0) {
                log("mIapSize is not null, Loading EF_IAP_RECORD_SIZE_DONE");
                int adnRecIndex = adnRecNum - 1;
                int[] recordSize = new int[3];
                int iap_size = mIapSize.get(recId);
                recordSize[0] = (iap_size >> 16) & 0xFF;
                recordSize[2] = iap_size & 0xFFFF;
                byte[] data = null;
                int recordIndex = adnRecIndex - getInitIndexBy(recId);
                log("handleIAP_RECORD_SIZE_DONE adnRecIndex is: "
                        + adnRecIndex + ", recordNumber is: " + recordNumber
                        + ", recordIndex is: " + recordIndex);

                if (recordSize.length != 3 || recordIndex + 1 > recordSize[2]
                        || recordNumber == 0) {
                    return false;
                }

                if (mIapFileRecord.get(recId) != null) {
                    data = mIapFileRecord.get(recId).get(recordIndex);
                    byte[] record_data = new byte[data.length];
                    System.arraycopy(data, 0, record_data, 0, record_data.length);

                    switch (tag) {
                        case USIM_EFEMAIL_TAG:
                            record_data[mPbrRecords.get(recId).mFileIds
                                    .get(USIM_EFEMAIL_TAG).getIndex()] =
                                    (byte) recordNumber;
                        break;
                    }
                    log(" IAP  efid= " + efid + ", update IAP index= " + (recordIndex)
                            + " with value= " + IccUtils.bytesToHexString(record_data));

                    mFh.updateEFLinearFixed(efid, recordIndex + 1, record_data, null,
                            obtainMessage(EVENT_UPDATE_IAP_RECORD_DONE,
                                    adnRecIndex, recordNumber, record_data));
                }
            } else {
                adnRecNum = ((efSetIndex << 8) & 0xff00) | (adnRecNum & 0xff);
                mFh.getEFLinearRecordSize(efid,
                        obtainMessage(EVENT_EF_IAP_RECORD_SIZE_DONE, adnRecNum,
                                recordNumber, tag));
            }

            try {
                mLock.wait();
            } catch (InterruptedException e) {
                log("interrupted while trying to update by search");
            }
        }
        return mSuccess;
    }

    /**
    * added for updating Email record of Adn record.
    */
    public boolean updateEmailFile(int adnRecNum, String oldEmail, String newEmail,
            int efSetIndex) {
        int recId = getPbrIndexBy(adnRecNum - 1);
        int efid = getEfidByTag(recId, USIM_EFEMAIL_TAG, efSetIndex);
        mSuccess = false;

        if (oldEmail == null) {
            oldEmail = "";
        }
        if (newEmail == null) {
            newEmail = "";
        }

        String emails = oldEmail + "," + newEmail;
        log("updateEmailFile oldEmail: [" + oldEmail + "], newEmail:[" + newEmail + "], emails:["
                + emails + "], efid" + efid + ", adnRecNum: " + adnRecNum);

        if (efid == -1) {
            return mSuccess;
        }

        // for create and update scenario
        if (mEmailPresentInIap && (TextUtils.isEmpty(oldEmail) && !TextUtils.isEmpty(newEmail))) {
            if (getEmptyEmailNum_Pbrindex(recId) == 0) {
                log("updateEmailFile getEmptyEmailNum_Pbrindex=0, recId is " + recId);
                mSuccess = true;
                return mSuccess;
            }

            mSuccess = updateIapFile(adnRecNum, oldEmail, newEmail, USIM_EFEMAIL_TAG, efSetIndex);
        } else {
            mSuccess = true;
        }

        if (mSuccess) {
            synchronized (mLock) {
                if (mEmailSize != null && mEmailSize.get(recId) > 0) {
                    log("mEmailSize is not null, Loading EF_EMAIL_RECORD_SIZE_DONE");
                    int adnRecIndex = adnRecNum - 1;
                    int[] recordSize = new int[3];
                    int email_size = mEmailSize.get(recId);
                    recordSize[0] = (email_size >> 16) & 0xFF;
                    recordSize[2] = email_size & 0xFFFF;
                    int recordNumber = getEmailRecNumber(adnRecIndex,
                            mPhoneBookRecords.size(), oldEmail);
                    log("recordSize[0]: " + recordSize[0] + ", recordSize[2]: " + recordSize[2]
                            + "recordNumber:" + recordNumber);
                    if (recordSize.length != 3 || recordNumber > recordSize[2]
                            || recordNumber <= 0) {
                        return false;
                    }

                    byte[] data = buildEmailData(recordSize[0], adnRecIndex, newEmail);
                    mFh.updateEFLinearFixed(
                            efid, recordNumber, data, null,
                            obtainMessage(EVENT_UPDATE_EMAIL_RECORD_DONE,
                                    recordNumber, adnRecIndex, data));
                } else {
                    mFh.getEFLinearRecordSize(
                            efid, obtainMessage(EVENT_EF_EMAIL_RECORD_SIZE_DONE,
                                    adnRecNum, efid, emails));
                }

                try {
                    mLock.wait();
                } catch (InterruptedException e) {
                    Rlog.e(LOG_TAG, "interrupted while trying to update by search");
                }
            }
        }

        //for delete scenario
        if (mEmailPresentInIap && mSuccess
                && (!TextUtils.isEmpty(oldEmail) && TextUtils.isEmpty(newEmail))) {
            mSuccess = updateIapFile(adnRecNum, oldEmail, newEmail, USIM_EFEMAIL_TAG, efSetIndex);
        }
        return mSuccess;
    }

    /**
    * added for updating Anr record of Adn record.
    */
    public boolean updateAnrFile(int adnRecNum, String oldAnr, String newAnr, int efSetIndex) {
        int recId = getPbrIndexBy(adnRecNum - 1);
        if (mPbrRecords.get(recId).mAnrFileIds.size() <= efSetIndex) {
            log("updateAnrFile: This sim doesn't support ANR SET #" + efSetIndex);
            return true;
        }
        int efid = getEfidByTag(recId, USIM_EFANR_TAG, efSetIndex);
        if (oldAnr == null)
            oldAnr = "";
        if (newAnr == null)
            newAnr = "";
        String anrs = oldAnr + "," + newAnr;
        mSuccess = false;
        log("updateAnrFile: oldAnr : " + oldAnr + ", newAnr:" + newAnr + " anrs:" + anrs + ", efid"
                + Integer.toHexString(efid).toUpperCase()
                + ", adnRecNum: " + adnRecNum);

        // for create and update scenario
        if (mAnrPresentInIap && (TextUtils.isEmpty(oldAnr) && !TextUtils.isEmpty(newAnr))) {
            if (getEmptyAnrNum_Pbrindex(recId, efSetIndex) == 0) {
                log("updateAnrFile: getEmptyAnrNum_Pbrindex = 0, recId is "
                        + recId + ", efSetIndex is " + efSetIndex);
                mSuccess = true;
                return mSuccess;
            }
            mSuccess = updateIapFile(adnRecNum, oldAnr, newAnr, USIM_EFANR_TAG, efSetIndex);
        } else {
            mSuccess = true;
        }
        if (mSuccess) {
            synchronized (mLock) {
                if (mAnrSize != null && mAnrSize.get(recId) != null
                        && mAnrSize.get(recId).get(efSetIndex) > 0) {
                    log("mAnrSize is not null, Loading EF_ANR_RECORD_SIZE_DONE");
                    int adnRecIndex = adnRecNum - 1;
                    int[] recordSize = new int[3];
                    int anr_size = mAnrSize.get(recId).get(efSetIndex);
                    recordSize[0] = (anr_size >> 16) & 0xFF;
                    recordSize[2] = anr_size & 0xFFFF;
                    int recordNumber = getAnrRecNumber(adnRecIndex,
                            mPhoneBookRecords.size(), oldAnr, efSetIndex);
                    if (recordSize.length != 3 || recordNumber > recordSize[2]
                            || recordNumber <= 0) {
                        return false;
                    }
                    byte[] data = buildAnrData(recordSize[0], adnRecIndex,
                            newAnr, adnRecNum);
                    if (data == null) {
                        return false;
                    }

                    int actualRecNumber = recordNumber;
                    log("ANR index:" + efSetIndex + " efid:" + efid
                            + ", recordNumber:" + recordNumber);
                    adnRecIndex = ((efSetIndex << 8) & 0xff00) | (adnRecIndex & 0xff);
                    mFh.updateEFLinearFixed(
                            efid,
                            recordNumber,
                            data,
                            null,
                            obtainMessage(EVENT_UPDATE_ANR_RECORD_DONE, recordNumber,
                                    adnRecIndex, data));
                } else {
                    adnRecNum = ((efSetIndex << 8) & 0xff00) | (adnRecNum & 0xff);
                    mFh.getEFLinearRecordSize(efid,
                            obtainMessage(EVENT_EF_ANR_RECORD_SIZE_DONE, adnRecNum, efid, anrs));
                }
                try {
                    mLock.wait();
                } catch (InterruptedException e) {
                    Rlog.e(LOG_TAG, "interrupted while trying to update by search");
                }
            }
        }
        // for delete scenario
        if (mAnrPresentInIap && mSuccess
                && (!TextUtils.isEmpty(oldAnr) && TextUtils.isEmpty(newAnr))) {
            mSuccess = updateIapFile(adnRecNum, oldAnr, newAnr, USIM_EFANR_TAG, efSetIndex);
        }
        return mSuccess;
    }


    // Read EF_ADN file
    private void readAdnFileAndWait(int recId) {
        SparseArray<File> files;
        files = mPbrRecords.get(recId).mFileIds;
        if (files == null || files.size() == 0) return;

        int extEf = 0;
        // Only call fileIds.get while EF_EXT1_TAG is available
        if (files.get(USIM_EFEXT1_TAG) != null) {
            extEf = files.get(USIM_EFEXT1_TAG).getEfid();
        }

        if (files.get(USIM_EFADN_TAG) == null)
            return;

        int previousSize = mPhoneBookRecords.size();
        mAdnCache.requestLoadAllAdnLike(files.get(USIM_EFADN_TAG).getEfid(),
                extEf, obtainMessage(EVENT_USIM_ADN_LOAD_DONE, recId));
        try {
            mLock.wait();
        } catch (InterruptedException e) {
            Rlog.e(LOG_TAG, "Interrupted Exception in readAdnFileAndWait");
        }

        /**
         * The recent added ADN record # would be the reference record size
         * for the rest of EFs associated within this PBR.
         */
        mPbrRecords.get(recId).mMasterFileRecordNum = mPhoneBookRecords.size() - previousSize;
    }

    // Create the phonebook reference file based on EF_PBR
    private void createPbrFile(ArrayList<byte[]> records) {
        if (records == null) {
            mPbrRecords = null;
            mIsPbrPresent = false;
            return;
        }

        mPbrRecords = new ArrayList<PbrRecord>();
        for (int i = 0; i < records.size(); i++) {
            // Some cards have two records but the 2nd record is filled with all invalid char 0xff.
            // So we need to check if the record is valid or not before adding into the PBR records.
            if (records.get(i)[0] != INVALID_BYTE) {
                mPbrRecords.add(new PbrRecord(records.get(i)));
            }
        }

        for (PbrRecord record : mPbrRecords) {
            File file = record.mFileIds.get(USIM_EFADN_TAG);
            // If the file does not contain EF_ADN, we'll just skip it.
            if (file != null) {
                int sfi = file.getSfi();
                if (sfi != INVALID_SFI) {
                    mSfiEfidTable.put(sfi, record.mFileIds.get(USIM_EFADN_TAG).getEfid());
                }
            }
        }
    }

    @Override
    public void handleMessage(Message msg) {
        AsyncResult ar;
        int recId = -1;
        int adnRecIndex = -1;
        int recordNumber = 0;
        int efSetIndex = -1;
        int efid = 0;
        int[] recordSize;
        int recordIndex = 0;
        byte[] data;
        String oldEmail = null;
        String newEmail = null;
        String oldAnr = null;
        String newAnr = null;

        switch(msg.what) {
        case EVENT_PBR_LOAD_DONE:
            log("Loading PBR records done");
            ar = (AsyncResult) msg.obj;
            if (ar.exception == null) {
                createPbrFile((ArrayList<byte[]>) ar.result);
            }
            synchronized (mLock) {
                mLock.notify();
            }
            break;
        case EVENT_USIM_ADN_LOAD_DONE:
            log("Loading USIM ADN records done");
            ar = (AsyncResult) msg.obj;
            recId = (Integer) ar.userObj;
            if (ar.exception == null) {
                mPhoneBookRecords.addAll((ArrayList<AdnRecord>) ar.result);
                mAdnLengthList.add(recId, ((ArrayList<AdnRecord>) ar.result).size());
            }
            synchronized (mLock) {
                mLock.notify();
            }
            break;
        case EVENT_IAP_LOAD_DONE:
            log("Loading USIM IAP records done");
            ar = (AsyncResult) msg.obj;
            recId = msg.arg1;
            if (ar.exception == null) {
                mIapSize.put(recId, msg.arg2);
                mIapFileRecord.put(recId, ((ArrayList<byte[]>) ar.result));
            }
            synchronized (mLock) {
                mLock.notify();
            }
            break;
        case EVENT_EMAIL_LOAD_DONE:
            log("Loading USIM Email records done");
            ar = (AsyncResult) msg.obj;
            recId = msg.arg1;
            if (ar.exception == null) {
                mEmailSize.put(recId, msg.arg2);
                mEmailFileRecord.put(recId, ((ArrayList<byte[]>) ar.result));
            }

            synchronized (mLock) {
                mLock.notify();
            }
            break;
        case EVENT_ANR_LOAD_DONE:
            log("Loading USIM Anr record done");
            ar = (AsyncResult) msg.obj;
            if (ar.exception == null) {
                efSetIndex = (msg.arg1 >> 8) & 0xff;
                recId = msg.arg1 & 0xff;
                log("[EVENT_ANR_LOAD_DONE] recId:" + recId + ", efSetIndex:" + efSetIndex);
                SparseArray<Integer> tmpAnrSize = mAnrSize.get(recId);
                if (tmpAnrSize == null) {
                    tmpAnrSize = new SparseArray<Integer>();
                }
                tmpAnrSize.put(efSetIndex, msg.arg2);
                mAnrSize.put(recId, tmpAnrSize);

                SparseArray<ArrayList<byte[]>> tmpRecord = new SparseArray<ArrayList<byte[]>>();
                tmpRecord.put(efSetIndex, (ArrayList<byte[]>) ar.result);
                mAnrFileRecord.put(recId, tmpRecord);
            }

            synchronized (mLock) {
                mLock.notify();
            }
            break;
        case EVENT_EF_IAP_RECORD_SIZE_DONE:
            ar = (AsyncResult) (msg.obj);
            log("Loading USIM IAP records size done");

            if (ar.exception == null) {
                int tag = (Integer) ar.userObj;
                efSetIndex = (int) ((msg.arg1 >> 8) & 0xff);
                adnRecIndex = ((int) (msg.arg1 & 0xff)) - 1;
                recordNumber = (int) msg.arg2;
                recId = getPbrIndexBy(adnRecIndex);
                efid = getEfidByTag(recId, USIM_EFIAP_TAG, efSetIndex);
                recordSize = (int[]) ar.result;
                data = null;
                recordIndex = adnRecIndex - getInitIndexBy(recId);
                log("efSetIndex: " + efSetIndex + ", recId:" + recId + " for efid:" + efid
                    + ", recordSize: " + recordSize[0] + ", adnRecIndex is: "
                    + adnRecIndex + ", recordNumber is: " + recordNumber
                    + ", recordIndex is: " + recordIndex);

                if (recordSize.length != 3 || recordIndex + 1 > recordSize[2]
                        || recordNumber == 0) {
                    mSuccess = false;

                    synchronized (mLock) {
                        mLock.notify();
                    }
                    return;
                }

                if (mIapFileRecord.get(recId) != null) {
                    data = mIapFileRecord.get(recId).get(recordIndex);
                    byte[] record_data = new byte[data.length];
                    System.arraycopy(data, 0, record_data, 0, record_data.length);

                    switch (tag) {
                        case USIM_EFEMAIL_TAG:
                            record_data[
                                    mPbrRecords.get(recId).mFileIds.get(USIM_EFEMAIL_TAG)
                                            .getIndex()] = (byte) recordNumber;
                        break;
                    }

                    log("IAP  efid= " + efid + ", update IAP index= " + recordIndex
                            + " with value= " + IccUtils.bytesToHexString(record_data));

                    mFh.updateEFLinearFixed(efid, recordIndex + 1, record_data, null,
                            obtainMessage(EVENT_UPDATE_IAP_RECORD_DONE, adnRecIndex,
                                    recordNumber, record_data));
                }
            }
            break;
        case EVENT_EF_EMAIL_RECORD_SIZE_DONE:
            ar = (AsyncResult) msg.obj;
            log("Loading EF_EMAIL size done");

            if (ar.exception == null) {
                String emails = (String) ar.userObj;
                adnRecIndex = ((int) msg.arg1) - 1;
                efid = (int) msg.arg2;
                String[] email = emails.split(",");

                if (email.length == 1) {
                    oldEmail = email[0];
                    newEmail = "";
                } else if (email.length > 1) {
                    oldEmail = email[0];
                    newEmail = email[1];
                }

                recordSize = (int[]) ar.result;
                log("recordSize: " + recordSize[0] + ", adnRecIndex: " + adnRecIndex);
                recordNumber = getEmailRecNumber(adnRecIndex,
                        mPhoneBookRecords.size(), oldEmail);

                if (recordSize.length != 3 || recordNumber > recordSize[2]
                        || recordNumber <= 0) {
                    mSuccess = false;

                    synchronized (mLock) {
                        mLock.notify();
                    }
                    return;
                }

                data = buildEmailData(recordSize[0], adnRecIndex, newEmail);
                mFh.updateEFLinearFixed(efid, recordNumber, data, null,
                        obtainMessage(EVENT_UPDATE_EMAIL_RECORD_DONE, recordNumber,
                                adnRecIndex, data));
            }
            break;
        case EVENT_EF_ANR_RECORD_SIZE_DONE:
            ar = (AsyncResult) (msg.obj);
            log("Loading USIM ANR records size done");

            if (ar.exception == null) {
                String anrs = (String) (ar.userObj);
                efSetIndex = (int)((msg.arg1 >> 8) & 0xff);
                adnRecIndex = ((int) (msg.arg1 & 0xff)) - 1;
                efid = (int) msg.arg2;
                String[] anr = anrs.split(",");
                if (anr.length == 1) {
                    oldAnr = anr[0];
                    newAnr = "";
                } else if (anr.length > 1) {
                    oldAnr = anr[0];
                    newAnr = anr[1];
                }

                recordSize = (int[]) ar.result;
                log("recordSize: " + recordSize[0]
                        + ", adnRecIndex: " + adnRecIndex + " , efSetIndex:" + efSetIndex);
                recordNumber = getAnrRecNumber(adnRecIndex, mPhoneBookRecords.size(),
                        oldAnr, efSetIndex);
                if (recordSize.length != 3 || recordNumber > recordSize[2]
                        || recordNumber <= 0) {
                    mSuccess = false;
                    synchronized (mLock) {
                        mLock.notify();
                    }
                    return;
                }
                data = buildAnrData(recordSize[0], adnRecIndex, newAnr, null, 0);
                if (data == null) {
                    mSuccess = false;
                    synchronized (mLock) {
                        mLock.notify();
                    }
                    return;
                }

                adnRecIndex = ((efSetIndex << 8) & 0xff00) | (adnRecIndex & 0xff);
                mFh.updateEFLinearFixed(
                        efid,
                        recordNumber,
                        data,
                        null,
                        obtainMessage(EVENT_UPDATE_ANR_RECORD_DONE, recordNumber,
                                adnRecIndex, data));
            }
            break;
        case EVENT_UPDATE_IAP_RECORD_DONE:
            ar = (AsyncResult) (msg.obj);
            log("Updating IAP record done");

            if (ar.exception == null) {
                data = (byte[]) ar.userObj;
                adnRecIndex = (int) msg.arg1;
                recId = getPbrIndexBy(adnRecIndex);
                recordIndex = adnRecIndex - getInitIndexBy(recId);
                mIapFileRecord.get(recId).set(recordIndex, data);
                mSuccess = true;
            }

            synchronized (mLock) {
                mLock.notify();
            }
            break;
        case EVENT_UPDATE_EMAIL_RECORD_DONE:
            ar = (AsyncResult) msg.obj;
            log("Updating EFEMAIL record done");

            if (ar.exception == null) {
                data = (byte[]) (ar.userObj);
                recordNumber = (int) msg.arg1;
                adnRecIndex = (int) msg.arg2;
                recId = getPbrIndexBy(adnRecIndex);
                mEmailFileRecord.get(recId).set(recordNumber - 1, data);

                for (int i = 0; i < data.length; i++) {
                    if (data[i] != (byte) 0xff) {
                        log("Updated Email record data[" + i + "]:" + data[i]);
                        mEmailFlags.get(recId).set(recordNumber - 1, 1);
                        break;
                    }
                    mEmailFlags.get(recId).set(recordNumber - 1, 0);
                }
                mSuccess = true;
            }

            synchronized (mLock) {
                mLock.notify();
            }
            break;
        case EVENT_UPDATE_ANR_RECORD_DONE:
            ar = (AsyncResult) msg.obj;
            log("Updating EFANR record done");

            if (ar.exception == null) {
                data = (byte[]) (ar.userObj);
                recordNumber = (int) msg.arg1;
                efSetIndex = (int)((msg.arg2 >> 8) & 0xff);
                adnRecIndex = (int) (msg.arg2 & 0xff);
                recId = getPbrIndexBy(adnRecIndex);
                log("recordNum:" + recordNumber
                        + ", efSetIndex:" + efSetIndex
                        + " , adnRecIndex:" + adnRecIndex + ", recId:" + recId);
                mSuccess = true;
                mAnrFileRecord.get(recId).get(efSetIndex).set(recordNumber - 1, data);

                for (int i = 0; i < data.length; i++) {
                    if (data[i] != (byte) 0xff) {
                        mAnrFlags.get(recId).get(efSetIndex).set(recordNumber - 1, 1);
                        break;
                    }
                    mAnrFlags.get(recId).get(efSetIndex).set(recordNumber - 1, 0);
                }
            }

            synchronized (mLock) {
                mLock.notify();
            }
            break;
        }
    }

    // PbrRecord represents a record in EF_PBR
    private class PbrRecord {
        // TLV tags
        private SparseArray<File> mFileIds;
        private ArrayList<Integer> mEmailFileIds;
        private ArrayList<Integer> mAnrFileIds;

        /**
         * 3GPP TS 31.102 4.4.2.1 EF_PBR (Phone Book Reference file)
         * If this is type 1 files, files that contain as many records as the
         * reference/master file (EF_ADN, EF_ADN1) and are linked on record number
         * bases (Rec1 -> Rec1). The master file record number is the reference.
         */
        private int mMasterFileRecordNum;

        PbrRecord(byte[] record) {
            mFileIds = new SparseArray<File>();
            mEmailFileIds = new ArrayList<Integer>();
            mAnrFileIds = new ArrayList<Integer>();
            SimTlv recTlv;
            log("PBR rec: " + IccUtils.bytesToHexString(record));
            recTlv = new SimTlv(record, 0, record.length);
            parseTag(recTlv);
        }

        void parseTag(SimTlv tlv) {
            SimTlv tlvEfSfi;
            int tag;
            byte[] data;
            ArrayList<Integer> emailList = new ArrayList<Integer>();
            ArrayList<Integer> anrList = new ArrayList<Integer>();

            do {
                tag = tlv.getTag();
                switch(tag) {
                    case USIM_TYPE1_TAG: // A8
                    case USIM_TYPE3_TAG: // AA
                    case USIM_TYPE2_TAG: // A9
                        data = tlv.getData();
                        tlvEfSfi = new SimTlv(data, 0, data.length);
                        parseEfAndSFI(tlvEfSfi, tag, emailList, anrList);
                        break;
                }
            } while (tlv.nextObject());

            if (0 != emailList.size()) {
                log("parseTag: EMAIL file list: " + emailList);
                mEmailFileIds = emailList;
            }

            if (0 != anrList.size()) {
                log("parseTag: ANR file list: " + anrList);
                mAnrFileIds = anrList;
            }
        }

        void parseEfAndSFI(SimTlv tlv, int parentTag, ArrayList<Integer> emailList,
                ArrayList<Integer> anrList) {
            int tag;
            byte[] data;
            int tagNumberWithinParentTag = 0;
            do {
                tag = tlv.getTag();

                // Check if EFIAP is present. EFIAP must be under TYPE 1 tag.
                if (parentTag == USIM_TYPE1_TAG && tag == USIM_EFIAP_TAG) {
                    mIapPresent = true;
                }

                if (parentTag == USIM_TYPE2_TAG && mIapPresent && tag == USIM_EFEMAIL_TAG) {
                    mEmailPresentInIap = true;
                    log("mEmailPresentInIap is true");
                }

                if (parentTag == USIM_TYPE2_TAG && mIapPresent && tag == USIM_EFANR_TAG) {
                    mAnrPresentInIap = true;
                    log("mAnrPresentInIap is true");
                }

                switch(tag) {
                    case USIM_EFEMAIL_TAG:
                    case USIM_EFADN_TAG:
                    case USIM_EFEXT1_TAG:
                    case USIM_EFANR_TAG:
                    case USIM_EFPBC_TAG:
                    case USIM_EFGRP_TAG:
                    case USIM_EFAAS_TAG:
                    case USIM_EFGSD_TAG:
                    case USIM_EFUID_TAG:
                    case USIM_EFCCP1_TAG:
                    case USIM_EFIAP_TAG:
                    case USIM_EFSNE_TAG:
                        /** 3GPP TS 31.102, 4.4.2.1 EF_PBR (Phone Book Reference file)
                         *
                         * The SFI value assigned to an EF which is indicated in EF_PBR shall
                         * correspond to the SFI indicated in the TLV object in EF_PBR.

                         * The primitive tag identifies clearly the type of data, its value
                         * field indicates the file identifier and, if applicable, the SFI
                         * value of the specified EF. That is, the length value of a primitive
                         * tag indicates if an SFI value is available for the EF or not:
                         * - Length = '02' Value: 'EFID (2 bytes)'
                         * - Length = '03' Value: 'EFID (2 bytes)', 'SFI (1 byte)'
                         */

                        int sfi = INVALID_SFI;
                        data = tlv.getData();

                        if (data.length < 2 || data.length > 3) {
                            log("Invalid TLV length: " + data.length);
                            break;
                        }

                        if (data.length == 3) {
                            sfi = data[2] & 0xFF;
                        }

                        int efid = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);

                        if (parentTag == USIM_TYPE1_TAG) {
                            if (tag == USIM_EFEMAIL_TAG) {
                                emailList.add(efid);
                            } else if (tag == USIM_EFANR_TAG) {
                                anrList.add(efid);
                            }
                        }

                        mFileIds.put(tag, new File(parentTag, efid, sfi, tagNumberWithinParentTag));
                        break;
                }
                tagNumberWithinParentTag++;
            } while(tlv.nextObject());
        }
    }

    /**
    *  Get Pbr Index using adn index.
    */
    public int getPbrIndexBy(int adnIndex) {
        int size = 0;
        for (int i = 0; i < mAdnLengthList.size(); i++) {
            size += mAdnLengthList.get(i);
            if (adnIndex < size) {
                return i;
            }
        }
        return -1;
    }

    /**
    *  Get the number of empty email record for pbr record for recId
    */
    public int getEmptyEmailNum_Pbrindex(int recId) {
        int count = 0;
        int size = 0;

        if (!mEmailPresentInIap) {
            //for Type1 Email, the number is always equal to ADN
            //log("getEmptyEmailNum_Pbrindex recId:" + recId + " default to 1");
            return 1;
        }

        if (mEmailFlags.get(recId) != null) {
            size = mEmailFlags.get(recId).size();
            for (int i = 0; i < size; i++) {
                if (0 == mEmailFlags.get(recId).get(i)) count++;
            }
        }
        return count;
    }

    /**
    *  Get the number of empty anr record for pbr record for recId
    */
    public int getEmptyAnrNum_Pbrindex(int recId, int efSetIndex) {
        int count = 0;
        int size = 0;

        if (!mAnrPresentInIap) {
            //for Type1 Anr, the number is always equals to ADN
            //log("getEmptyAnrNum_Pbrindex recId:" + recId + " default to 1");
            return 1;
        }

        if (mAnrFlags.get(recId) != null
                && mAnrFlags.get(recId).get(efSetIndex) != null) {
            size = mAnrFlags.get(recId).get(efSetIndex).size();
            for (int i = 0; i < size; i++) {
                if (0 == mAnrFlags.get(recId).get(efSetIndex).get(i)) count++;
            }
        }
        return count;
    }

    /**
    *  Get the number of email record for Adn record
    */
    public int getEmailFilesCountEachAdn() {
        SparseArray<File> fileIds;
        int emailFilesCount = 0;

        if (mPbrRecords == null) {
            log("mPbrRecords is NULL, exiting from getEmailFilesCountEachAdn");
            return 0;
        } else {
            if (mEmailPresentInIap) return 1;

            for (int recId = 0; recId < mPbrRecords.size(); recId++) {
                fileIds = mPbrRecords.get(recId).mFileIds;
                if (fileIds == null) continue;

                File email = fileIds.get(USIM_EFEMAIL_TAG);
                if (email != null) {
                    emailFilesCount += mPbrRecords.get(recId).mEmailFileIds.size();
                } else {
                    log("email files for " + recId + " is null");
                }
            }
        }
        log("getEmailFilesCountEachAdn: " + emailFilesCount);
        return emailFilesCount;
    }

    /**
    *  Get the number of email record for Adn record
    */
    public int getAnrFilesCountEachAdn() {
        SparseArray<File> fileIds;
        int anrFilesCount = 0;

        if (mPbrRecords == null) {
            log("mPbrRecords is NULL, exiting from getAnrFilesCountEachAdn");
            return 0;
        } else {
            if (mAnrPresentInIap) return 1;

            for (int recId = 0; recId < mPbrRecords.size(); recId++) {
                fileIds = mPbrRecords.get(recId).mFileIds;
                if (fileIds == null) continue;

                File anr = fileIds.get(USIM_EFANR_TAG);
                if (anr != null) {
                    anrFilesCount += mPbrRecords.get(recId).mAnrFileIds.size();
                } else {
                    log("anr files for " + recId + " is null");
                }
            }
        }
        log("getAnrFilesCountEachAdn: " + anrFilesCount);
        return anrFilesCount;
    }

    private int getEfidByTag(int recId, int tag, int efSetIndex) {
        SparseArray<File> fileIds = mPbrRecords.get(recId).mFileIds;
        int efid = -1;

        if (fileIds == null || fileIds.get(tag) == null) {
            return efid;
        }

        if (tag == USIM_EFANR_TAG) {
            return mPbrRecords.get(recId).mAnrFileIds.get(efSetIndex);
        }

        efid = fileIds.get(tag).getEfid();
        return efid;
    }

    private int getInitIndexBy(int recId) {
        int index = 0;
        while (recId > 0) {
            index += mAdnLengthList.get(recId - 1);
            recId--;
        }
        return index;
    }

    private int getEmailRecNumber(int adnRecIndex, int numRecs, String oldEmail) {
        int recId = getPbrIndexBy(adnRecIndex);
        int recordIndex = adnRecIndex - getInitIndexBy(recId);
        int recordNumber = -1;
        log("getEmailRecNumber adnRecIndex is: " + adnRecIndex + ", recId: " + recId
                + ", recordIndex: " + recordIndex);

        if (mEmailFileRecord.get(recId) == null) {
            log("getEmailRecNumber recordNumber is: " + recordNumber);
            return recordNumber;
        }

        if (mEmailPresentInIap && mIapFileRecord.get(recId) != null) {
            byte[] record = null;
            try {
                record = mIapFileRecord.get(recId).get(recordIndex);
            } catch (IndexOutOfBoundsException e) {
                Rlog.e(LOG_TAG, "IndexOutOfBoundsException in getEmailRecNumber");
            }

            if ((record != null) && (record[mPbrRecords.get(recId).mFileIds
                    .get(USIM_EFEMAIL_TAG).getIndex()] > 0)) {
                recordNumber = record[mPbrRecords.get(recId).mFileIds
                        .get(USIM_EFEMAIL_TAG).getIndex()];

                log("getEmailRecNumber: record is "
                        + IccUtils.bytesToHexString(record)
                        + ", the email recordNumber is :" + recordNumber);
                return recordNumber;
            } else {
                int recsSize = mEmailFileRecord.get(recId).size();
                log("getEmailRecNumber recsSize is: " + recsSize);

                if (TextUtils.isEmpty(oldEmail)) {
                    for (int i = 0; i < recsSize; i++) {
                        String emailRecord = readEmailRecord(i, recId);

                        if (TextUtils.isEmpty(emailRecord)) {
                            log("Got empty record. Email record num is :" + (i + 1));
                            return i + 1;
                        }
                    }
                }
            }
        } else {
            recordNumber = recordIndex + 1;
            return recordNumber;
        }
        log("getEmailRecNumber: no email record index found");
        return recordNumber;
    }

    private int getAnrRecNumber(int adnRecIndex, int numRecs, String oldAnr, int efSetIndex) {
        int recId = getPbrIndexBy(adnRecIndex);
        int recordIndex = adnRecIndex - getInitIndexBy(recId);
        int recordNumber = -1;
        if (mAnrFileRecord.get(recId) == null
                || mAnrFileRecord.get(recId).get(efSetIndex) == null) {
            return recordNumber;
        }

        if (mAnrPresentInIap && mIapFileRecord.get(recId) != null) {
            byte[] record = null;
            try {
                record = mIapFileRecord.get(recId).get(recordIndex);
            } catch (IndexOutOfBoundsException e) {
                Rlog.e(LOG_TAG, "IndexOutOfBoundsException in getAnrRecNumber");
            }

            if (record != null && record[mPbrRecords.get(recId).mFileIds
                    .get(USIM_EFANR_TAG).getIndex() + efSetIndex] > 0) {
                recordNumber = record[mPbrRecords.get(recId).mFileIds
                        .get(USIM_EFANR_TAG).getIndex() + efSetIndex];
                log("getAnrRecNumber: recnum from iap is :" + recordNumber);
                return recordNumber;
            } else {
                int recsSize = mAnrFileRecord.get(recId).get(efSetIndex).size();
                log("getAnrRecNumber: anr record size is :" + recsSize);
                if (TextUtils.isEmpty(oldAnr)) {
                    for (int i = 0; i < recsSize; i++) {
                        String anrRecord = readAnrRecord(i, recId, efSetIndex);
                        if (TextUtils.isEmpty(anrRecord)) {
                            log("getAnrRecNumber: Empty anr record. Anr record num is :"
                                    + (i + 1));
                            return i + 1;
                        }
                    }
                }
            }
        } else {
            recordNumber = recordIndex + 1;
            return recordNumber;
        }
        log("getAnrRecNumber: no anr record index found");
        return recordNumber;
    }

    private byte[] buildEmailData(int length, int adnRecIndex, String email) {
        byte[] data = new byte[length];
        for (int i = 0; i < length; i++) {
            data[i] = (byte) 0xff;
        }

        if (TextUtils.isEmpty(email)) {
            log("buildEmailData Empty email record");
            return data; // return the empty record for delete
        }

        byte[] byteEmail = GsmAlphabet.stringToGsm8BitPacked(email);
        System.arraycopy(byteEmail, 0, data, 0, byteEmail.length);

        int recId = getPbrIndexBy(adnRecIndex);
        int recordIndex = adnRecIndex - adnRecIndex - getInitIndexBy(recId);

        if (mEmailPresentInIap) {
            data[length - 1] = (byte) (recordIndex + 1);
        }

        log("buildEmailData: data is" + IccUtils.bytesToHexString(data));
        return data;
    }

    private byte[] buildAnrData(int length, int adnRecIndex, String anr,
            int adnRecNum) {
        byte[] data = new byte[length];
        int recordNumber = 0 ;
        for (int i = 0; i < length; i++) {
            data[i] = (byte) 0xff;
        }

        if (TextUtils.isEmpty(anr)) {
            log("buildAnrData: Empty anr record");
            return data; // return the empty record for delete
        }

        data[ANR_DESCRIPTION_ID] = (byte) 0x0;
        byte[] byteAnr = PhoneNumberUtils.numberToCalledPartyBCD(anr);

        // If the phone number does not matching format, like "+" return null
        if (byteAnr == null) {
            return null;
        }

        int maxlength = ANR_ADDITIONAL_NUMBER_END_ID - ANR_ADDITIONAL_NUMBER_START_ID + 1;
        if (byteAnr.length > maxlength) {
            System.arraycopy(byteAnr, 0, data, ANR_TON_NPI_ID, maxlength);
            data[ANR_BCD_NUMBER_LENGTH] = (byte) maxlength;
        } else {
            System.arraycopy(byteAnr, 0, data, ANR_TON_NPI_ID, byteAnr.length);
            data[ANR_BCD_NUMBER_LENGTH] = (byte) byteAnr.length;
        }

        data[ANR_CAPABILITY_ID] = (byte) 0xff;
        data[ANR_EXTENSION_ID] = (byte) 0xff;

        if (length == 17) {
            int recId = getPbrIndexBy(adnRecIndex);
            int recordIndex = adnRecIndex - getInitIndexBy(recId);
            data[ANR_ADN_RECORD_IDENTIFIER_ID] = (byte) (recordIndex + 1);
        }
        log("buildAnrData: data is" + IccUtils.bytesToHexString(data));
        return data;
    }

    public int getSupportedAnrSetCount() {
        return maxAnrSetSize;
    }

    @UnsupportedAppUsage
    private void log(String msg) {
        if(DBG) Rlog.d(LOG_TAG, msg);
    }
}
