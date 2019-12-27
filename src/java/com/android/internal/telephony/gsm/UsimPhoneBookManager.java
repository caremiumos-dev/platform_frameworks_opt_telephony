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

import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.text.TextUtils;
import android.util.SparseArray;
import android.util.SparseIntArray;

import com.android.internal.telephony.uicc.AdnRecord;
import com.android.internal.telephony.uicc.AdnRecordCache;
import com.android.internal.telephony.uicc.IccConstants;
import com.android.internal.telephony.uicc.IccFileHandler;
import com.android.internal.telephony.uicc.IccUtils;

import dalvik.annotation.compat.UnsupportedAppUsage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

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
    @UnsupportedAppUsage
    private IccFileHandler mFh;
    private AdnRecordCache mAdnCache;
    @UnsupportedAppUsage
    private Object mLock = new Object();
    @UnsupportedAppUsage
    private ArrayList<AdnRecord> mPhoneBookRecords;
    private ArrayList<byte[]> mIapFileRecord;
    private ArrayList<byte[]> mEmailFileRecord;
    private ArrayList<byte[]> mAnrFileRecord;
    private ArrayList<byte[]> mGrpFileRecord;
    private ArrayList<byte[]> mGasFileRecord;
    private ArrayList<byte[]> mSneFileRecord;
    private ArrayList<byte[]> mAasFileRecord;
    private ArrayList<byte[]> mPbcFileRecord;
    private ArrayList<String> mAasList;
    private ArrayList<String> mGasList;

    // email list for each ADN record. The key would be
    // ADN's efid << 8 + record #
    private SparseArray<ArrayList<String>> mEmailsForAdnRec;

    // SFI to ADN Efid mapping table
    private SparseIntArray mSfiEfidTable;

    private boolean mRefreshCache = false;


    private static final int EVENT_PBR_LOAD_DONE = 1;
    private static final int EVENT_USIM_ADN_LOAD_DONE = 2;
    private static final int EVENT_IAP_LOAD_DONE = 3;
    private static final int EVENT_EMAIL_LOAD_DONE = 4;
    private static final int EVENT_AAS_LOAD_DONE = 5;
    private static final int EVENT_SNE_LOAD_DONE = 6;
    private static final int EVENT_GRP_LOAD_DONE = 7;
    private static final int EVENT_GAS_LOAD_DONE = 8;
    private static final int EVENT_ANR_LOAD_DONE = 9;
    private static final int EVENT_GET_RECORDS_COUNT = 10;
    private static final int EVENT_ANR_RECORD_LOAD_DONE = 11;
    public static final int EVENT_EF_CC_LOAD_DONE = 12;
    private static final int EVENT_UPDATE_RECORD_DONE = 13;
    private static final int EVENT_LOAD_EF_PBC_RECORD_DONE = 14;
    private static final int EVENT_EF_PUID_LOAD_DONE = 15;
    private static final int EVENT_UPDATE_UID_DONE = 16;
    private static final int EVENT_EF_PSC_LOAD_DONE = 17;
    private static final int EVENT_UPDATE_CC_DONE = 18;

    private static final int USIM_TYPE1_TAG   = 0xA8;
    private static final int USIM_TYPE2_TAG   = 0xA9;
    private static final int USIM_TYPE3_TAG   = 0xAA;
    public static final int USIM_EFADN_TAG   = 0xC0;
    public static final int USIM_EFIAP_TAG   = 0xC1;
    public static final int USIM_EFEXT1_TAG  = 0xC2;
    private static final int USIM_EFSNE_TAG   = 0xC3;
    public static final int USIM_EFANR_TAG   = 0xC4;
    private static final int USIM_EFPBC_TAG   = 0xC5;
    public static final int USIM_EFGRP_TAG   = 0xC6;
    public static final int USIM_EFAAS_TAG   = 0xC7;
    public static final int USIM_EFGAS_TAG   = 0xC8;
    private static final int USIM_EFUID_TAG   = 0xC9;
    private static final int USIM_EFEMAIL_TAG = 0xCA;
    private static final int USIM_EFCCP1_TAG  = 0xCB;

    private static final int INVALID_SFI = -1;
    private static final byte INVALID_BYTE = -1;

    // Type for email/anr/grp/aas/sne
    public static final int USIM_SUBJCET_EMAIL = 0;
    public static final int USIM_SUBJCET_ANR = 1;
    public static final int USIM_SUBJCET_GRP = 2;
    public static final int USIM_SUBJCET_AAS = 3;
    public static final int USIM_SUBJCET_SNE = 4;

    // LinkedList<SubjectIndexOfAdn> for anr/email/aas/sne
    private LinkedList<SubjectIndexOfAdn> mAnrInfoFromPBR = new LinkedList<SubjectIndexOfAdn>();
    private LinkedList<SubjectIndexOfAdn> mEmailInfoFromPBR = new LinkedList<SubjectIndexOfAdn>();
    private LinkedList<SubjectIndexOfAdn> mAasInfoFromPBR = new LinkedList<SubjectIndexOfAdn>();
    private LinkedList<SubjectIndexOfAdn> mSneInfoFromPBR = new LinkedList<SubjectIndexOfAdn>();

    // true for type2
    private boolean mEmailPresentInIap = false;
    private boolean mSnePresentInIap = false;
    private boolean mAnrPresentInIap = false;

    private boolean mIsPbrFileExisting = true;
    private boolean mIsContainAdnInPbr = true;

    private int[] mAdnRecordSize = null;
    public int[] mAdnRecordSizeArray;
    private int[] mEmailRecordSizeArray;
    private int[] mIapRecordSizeArray;
    private int[] mAnrRecordSizeArray;
    // <efid, record size>
    public HashMap<Integer, int[]> mRecordsSize;

    private Object[] mIapFileRecordArray;

    private AtomicInteger mPendingIapLoads = new AtomicInteger(0);
    private AtomicInteger mPendingGrpLoads = new AtomicInteger(0);
    private AtomicInteger mPendingAnrLoads = new AtomicInteger(0);
    private AtomicInteger mPendingPbcLoads = new AtomicInteger(0);
    private AtomicBoolean mIsNotify = new AtomicBoolean(false);

   // adn record footer size
    static final int ADN_FOOTER_SIZE_BYTES = 14;

    // group name max length
    public int mGrpLength = 0;
    // change counter of phone book
    private int mChangedCounter;

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

    /**
     * This class preserves information of email/sne/anr/aas.
     *
     */
    public class SubjectIndexOfAdn {
        public int adnEfid;
        public int[] type;
        // <efid, record index>
        public Map<Integer, Integer> recordNumInIap;
        // map<efid, ArrayList<byte[]>fileRecord >
        public Map<Integer, ArrayList<byte[]>> record;
        public int[] efids;
        // used email record index
        public Object[] usedSet;
    };

    public UsimPhoneBookManager(IccFileHandler fh, AdnRecordCache cache) {
        mFh = fh;
        mPhoneBookRecords = new ArrayList<AdnRecord>();
        mPbrRecords = null;
        // We assume its present, after the first read this is updated.
        // So we don't have to read from UICC if its not present on subsequent reads.
        mIsPbrPresent = true;
        mAdnCache = cache;
        mEmailsForAdnRec = new SparseArray<ArrayList<String>>();
        mSfiEfidTable = new SparseIntArray();
        mGasList = new ArrayList<String>();
        mAasList = new ArrayList<String>();
    }

    @UnsupportedAppUsage
    public void reset() {
        log("reset");
        mPhoneBookRecords.clear();
        mIapFileRecord = null;
        mEmailFileRecord = null;
        mPbrRecords = null;
        mIsPbrPresent = true;
        mRefreshCache = false;
        mAnrFileRecord = null;
        mGrpFileRecord = null;
        mGasFileRecord = null;
        mPbcFileRecord = null;
        mAdnRecordSize = null;
        mAasList.clear();
        mGasList.clear();
        mEmailsForAdnRec.clear();
        mSfiEfidTable.clear();
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
            if (mPbrRecords == null || mPbrRecords.size() == 0) {
                readPbrFileAndWait();
            }

            if (mPbrRecords == null || mPbrRecords.size() == 0) {
                Rlog.e(LOG_TAG, "Error: Pbr file is empty");
                mIsPbrFileExisting = false;
                return null;
            }

            int numRecs = mPbrRecords.size();

            mIapFileRecordArray = new Object[numRecs];
            mAdnRecordSizeArray = new int[numRecs];
            mEmailRecordSizeArray = new int[numRecs];
            mIapRecordSizeArray = new int[numRecs];
            mAnrRecordSizeArray = new int[numRecs];

            log("loadEfFilesFromUsim: Loading adn, emails, anr and group");
            for (int i = 0; i < numRecs; i++) {
                log("loadEfFilesFromUsim, the current record num is " + i);

                int offSet = mPhoneBookRecords.size();

                readAdnFileAndWait(i);
                if (!mIsContainAdnInPbr) {
                    Rlog.e(LOG_TAG, "First pbr special in this card, adn record abnormal");
                    return null;
                }

                // If EF_ANR or EF_EMAIL or EF_SNE file is type2, read EF_IAP file
                if (mAnrPresentInIap || mEmailPresentInIap || mSnePresentInIap) {
                    readIapFileAndWait(i, offSet);
                }

                readEmailFileAndWait(i);
                readSneFileAndWait(i);
                readAnrFileAndWait(i, offSet);
                readGrpFileAndWait(i, offSet);
                updateAdnRecord(i);
            }

            updateAdnRecordNum();
            updatePbcAndCc();

            //updatePhoneAdnRecord();
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
        log("readEmailFileAndWait");
        SparseArray<File> files;
        files = mPbrRecords.get(recId).mFileIds;
        if (files == null || files.size() == 0) return;

        File email = files.get(USIM_EFEMAIL_TAG);
        if (email != null) {
            SubjectIndexOfAdn records = getSubjectIndex(USIM_SUBJCET_EMAIL, recId);
            if (records == null) {
                return;
            }
            /**
             * Check if the EF_EMAIL is a Type 1 file or a type 2 file.
             * If mEmailPresentInIap is true, its a type 2 file.
             * So we read the IAP file and then read the email records.
             * instead of reading directly.
             */

            if (mEmailPresentInIap) {
                if (mIapFileRecord == null) {
                    Rlog.e(LOG_TAG, "Error: IAP file is empty");
                    setSubjectIndex(USIM_SUBJCET_EMAIL, recId, null);
                    return;
                }
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
                    obtainMessage(EVENT_EMAIL_LOAD_DONE));
            try {
                mLock.wait();
            } catch (InterruptedException e) {
                Rlog.e(LOG_TAG, "Interrupted Exception in readEmailFileAndWait");
            }

            if (mEmailFileRecord == null) {
                Rlog.e(LOG_TAG, "Error: Email file is empty");
                setSubjectIndex(USIM_SUBJCET_EMAIL, recId, null);
                return;
            }

            records.record = new HashMap<Integer, ArrayList<byte[]>>();
            records.record.put(emailEfid, mEmailFileRecord);
            setSubjectIndex(USIM_SUBJCET_EMAIL, recId, records);
            initSubjectUsedNum(USIM_SUBJCET_EMAIL, recId);

            // Build email list
            if (email.getParentTag() == USIM_TYPE2_TAG && mIapFileRecord != null) {
                // If the tag is type 2 and EF_IAP exists, we need to build type 2 email list
                buildType2EmailList(recId, records, emailEfid);
            } else {
                // If one the followings is true, we build type 1 email list
                // 1. EF_IAP does not exist or it is failed to load
                // 2. ICC cards can be made such that they have an IAP file but all
                //    records are empty. In that case buildType2EmailList will fail and
                //    we need to build type 1 email list.

                // Build type 1 email list
                buildType1EmailList(recId);
            }
            mEmailFileRecord = null;
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

        byte[] emailRec;
        for (int i = 0; i < numRecs; i++) {
            try {
                emailRec = mEmailFileRecord.get(i);
            } catch (IndexOutOfBoundsException e) {
                Rlog.e(LOG_TAG, "Error: Improper ICC card: No email record for ADN, continuing");
                break;
            }

            /**
             *  3GPP TS 31.102 4.4.2.13 EF_EMAIL (e-mail address)
             *
             *  The fields below are mandatory if and only if the file
             *  is not type 1 (as specified in EF_PBR)
             *
             *  Byte [X + 1]: ADN file SFI (Short File Identification)
             *  Byte [X + 2]: ADN file Record Identifier
             */
            int sfi = emailRec[emailRec.length - 2];
            int adnRecId = emailRec[emailRec.length - 1];

            String email = readEmailRecord(i);

            if (email == null || email.equals("")) {
                continue;
            }

            // Get the associated ADN's efid first.
            int adnEfid = 0;
            if (sfi == INVALID_SFI || mSfiEfidTable.get(sfi) == 0) {

                // If SFI is invalid or cannot be mapped to any ADN, use the ADN's efid
                // in the same PBR files.
                File file = mPbrRecords.get(recId).mFileIds.get(USIM_EFADN_TAG);
                if (file == null)
                    continue;
                adnEfid = file.getEfid();
            }
            else {
                adnEfid = mSfiEfidTable.get(sfi);
            }
            /**
             * SIM record numbers are 1 based.
             * The key is constructed by efid and record index.
             */
            int index = (((adnEfid & 0xFFFF) << 8) | ((adnRecId - 1) & 0xFF));
            ArrayList<String> emailList = mEmailsForAdnRec.get(index);
            if (emailList == null) {
                emailList = new ArrayList<String>();
            }
            log("Adding email #" + i + " list to index 0x" +
                    Integer.toHexString(index).toUpperCase());
            emailList.add(email);
            mEmailsForAdnRec.put(index, emailList);
        }
    }

    // Build type 2 email list
    private boolean buildType2EmailList(int recId, SubjectIndexOfAdn emailInfo, int efid) {
        if (mPbrRecords.get(recId) == null)
            return false;

        if (emailInfo == null || emailInfo.efids == null || emailInfo.efids.length == 0) {
            return false;
        }

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
        mIapFileRecordArray[recId] = mIapFileRecord;

        for (int i = 0; i < numRecs; i++) {
            byte[] record;
            int emailRecId;

            int emailIndex = getEfIndex(efid, emailInfo);
            if (emailIndex == -1) {
                continue;
            }

            int emailTagNumberInIap = emailInfo.recordNumInIap.get(efid);
            mEmailFileRecord = emailInfo.record.get(efid);

            if (mEmailFileRecord == null) {
                continue;
            }

            try {
                record = mIapFileRecord.get(i);
                emailRecId =
                        record[mPbrRecords.get(recId).mFileIds.get(USIM_EFEMAIL_TAG).getIndex()];
            } catch (IndexOutOfBoundsException e) {
                Rlog.e(LOG_TAG, "Error: Improper ICC card: Corrupted EF_IAP");
                continue;
            }

            String email = "";
            if (emailRecId != 0xFF) {
                email = readEmailRecord(emailRecId - 1);
                if (TextUtils.isEmpty(email)) {
                    setIapFileRecord(recId, i, (byte) 0xFF, emailTagNumberInIap);
                    continue;
                }

                Set<Integer> set = (Set<Integer>) emailInfo.usedSet[emailIndex];
                set.add(new Integer(emailRecId));
                emailInfo.usedSet[emailIndex] = set;
                setSubjectIndex(USIM_SUBJCET_EMAIL, recId, emailInfo);
            }

            // The key is constructed by efid and record index.
            int index = (((adnEfid & 0xFFFF) << 8) | (i & 0xFF));
            ArrayList<String> emailList = mEmailsForAdnRec.get(index);
            if (emailList == null) {
                emailList = new ArrayList<String>();
            }
            emailList.add(email);
            log("Adding email list to index 0x"
                    + Integer.toHexString(index).toUpperCase());
            mEmailsForAdnRec.put(index, emailList);

        }
        return true;
    }

    // Read Phonebook Index Admistration EF_IAP file
    private void readIapFileAndWait(int efid) {
        mFh.loadEFLinearFixedAll(efid, obtainMessage(EVENT_IAP_LOAD_DONE));
        try {
            mLock.wait();
        } catch (InterruptedException e) {
            Rlog.e(LOG_TAG, "Interrupted Exception in readIapFileAndWait");
        }
    }

    // Read Phonebook Index Admistration EF_IAP file
    private void readIapFileAndWait(int recNum, int offSet) {
        SparseArray<File> files = null;
        files = mPbrRecords.get(recNum).mFileIds;

        if (files == null || files.size() == 0 || files.get(USIM_EFIAP_TAG) == null) {
            return;
        }

        int efid = files.get(USIM_EFIAP_TAG).getEfid();

        int[] size;
        if (mRecordsSize != null && mRecordsSize.containsKey(efid)) {
            size = mRecordsSize.get(efid);
        } else {
            size = readFileSizeAndWait(efid);
        }

        if (mIapFileRecord == null) {
            mIapFileRecord = new ArrayList<byte[]>();
        }

        ArrayList<byte[]> fileRecord = initArraylist(size[0], size[2]);
        mIapFileRecord.addAll(0, fileRecord);

        for (int i = offSet; i < mPhoneBookRecords.size() && i < (offSet + size[2]); i++) {
            if (!TextUtils.isEmpty(mPhoneBookRecords.get(i).getAlphaTag())
                    || !TextUtils.isEmpty(mPhoneBookRecords.get(i).getNumber())) {
                mPendingIapLoads.addAndGet(1);
                mFh.loadEFLinearFixed(efid, i + 1 - offSet, size[0],
                        obtainMessage(EVENT_IAP_LOAD_DONE, i - offSet, recNum));
            }
        }

        if (mPendingIapLoads.get() == 0) {
            mIsNotify.set(false);
            return;
        } else {
            mIsNotify.set(true);
        }

        try {
            mLock.wait();
        } catch (InterruptedException e) {
            loge("Interrupted Exception in readIapFileAndWait");
        }
    }

    // Create an empty record
    private ArrayList<byte[]> initArraylist(int fileLength, int fileSize) {
        ArrayList<byte[]> fileRecord = new ArrayList<byte[]>();
        byte[] emptyValue = new byte[fileLength];

        for (byte value : emptyValue) {
            value = (byte) 0xFF;
        }

        for (int i = 0; i < fileSize; i++) {
            fileRecord.add(i, emptyValue);
        }
        return fileRecord;
    }

    // Read EF_SNE file
    private void readSneFileAndWait(int recNum) {
        SparseArray<File> files = null;
        files = mPbrRecords.get(recNum).mFileIds;

        if (files == null || files.size() == 0 || files.get(USIM_EFSNE_TAG) == null) {
            return;
        }

        int efid = files.get(USIM_EFSNE_TAG).getEfid();
        SubjectIndexOfAdn records = getSubjectIndex(USIM_SUBJCET_SNE, recNum);
        if (records == null) {
            return;
        }

        mFh.loadEFLinearFixedAll(efid, obtainMessage(EVENT_SNE_LOAD_DONE));
        try {
            mLock.wait();
        } catch (InterruptedException e) {
            loge("Interrupted Exception in readSneFileAndWait");
        }

        if (mSneFileRecord == null) {
            setSubjectIndex(USIM_SUBJCET_SNE, recNum, null);
            return;
        }

        records.record = new HashMap<Integer, ArrayList<byte[]>>();
        records.record.put(efid, mSneFileRecord);
        setSubjectIndex(USIM_SUBJCET_SNE, recNum, records);
        initSubjectUsedNum(USIM_SUBJCET_SNE, recNum);
        mSneFileRecord = null;
    }

    // Read EF_ANR file
    private void readAnrFileAndWait(int recNum, int offSet) {
        SparseArray<File> files = null;
        files = mPbrRecords.get(recNum).mFileIds;

        if (files == null || files.size() == 0 || files.get(USIM_EFANR_TAG) == null) {
            return;
        }

        SubjectIndexOfAdn records = getSubjectIndex(USIM_SUBJCET_ANR, recNum);
        if (records == null) {
            return;
        }
        records.record = new HashMap<Integer, ArrayList<byte[]>>();
        if (records.efids == null || records.efids.length == 0) {
            return;
        }

        int anrFileCount = records.efids.length;
        for (int i = 0; i < anrFileCount; i++) {
            if (records.type[i] == USIM_TYPE1_TAG) {
                int[] size;
                if (mRecordsSize != null && mRecordsSize.containsKey(records.efids[i])) {
                    size = mRecordsSize.get(records.efids[i]);
                } else {
                    size = readFileSizeAndWait(records.efids[i]);
                }

                if (mAnrFileRecord == null) {
                    mAnrFileRecord = new ArrayList<byte[]>();
                }
                ArrayList<byte[]> fileRecord = initArraylist(size[0], size[2]);
                mAnrFileRecord.addAll(0, fileRecord);

                for (int j = offSet; j < mPhoneBookRecords.size() && j < (offSet + size[2]); j++) {
                    if (!TextUtils.isEmpty(mPhoneBookRecords.get(j).getAlphaTag())
                            || !TextUtils.isEmpty(mPhoneBookRecords.get(j).getNumber())) {
                        mPendingAnrLoads.addAndGet(1);
                        mFh.loadEFLinearFixed(records.efids[i], j + 1 - offSet, size[0],
                                obtainMessage(EVENT_ANR_RECORD_LOAD_DONE, j - offSet, recNum));
                    }
                }

                if (mPendingAnrLoads.get() == 0) {
                    mIsNotify.set(false);
                } else {
                    mIsNotify.set(true);
                    try {
                        mLock.wait();
                    } catch (InterruptedException e) {
                        loge("Interrupted Exception in readEmailFileAndWait");
                    }
                }
            } else if (records.type[i] == USIM_TYPE2_TAG) {
                mFh.loadEFLinearFixedAll(records.efids[i],
                        obtainMessage(EVENT_ANR_LOAD_DONE));
                try {
                    mLock.wait();
                } catch (InterruptedException e) {
                    loge("Interrupted Exception in readEmailFileAndWait");
                }
            }

            if (mAnrFileRecord == null) {
                records.efids[i] = 0;
                continue;
            }

            records.record.put(records.efids[i], mAnrFileRecord);
            mAnrFileRecord = null;
        }
        handleReadFileResult(records);
        setSubjectIndex(USIM_SUBJCET_ANR, recNum, records);
        initSubjectUsedNum(USIM_SUBJCET_ANR, recNum);
    }

    // Remove invalid efid from records
    private void handleReadFileResult(SubjectIndexOfAdn records) {
        int i = 0;
        ArrayList<Integer> efs = new ArrayList<Integer>();
        if (records == null || records.efids == null) {
            return;
        }

        for (i = 0; i < records.efids.length; i++) {
            if (records.efids[i] != 0) {
                efs.add(records.efids[i]);
            } else {
                if (records.recordNumInIap != null
                        && records.recordNumInIap.containsKey(records.efids[i])) {
                    records.recordNumInIap.remove(records.efids[i]);
                }
            }
        }

        int[] validEfs = new int[efs.size()];
        for (i = 0; i < efs.size(); i++) {
            validEfs[i] = efs.get(i);
        }
        records.efids = validEfs;
    }

    // Read EF_GRP file
    private void readGrpFileAndWait(int recNum, int offSet) {
        SparseArray<File> files = null;
        files = mPbrRecords.get(recNum).mFileIds;

        if (files == null || files.size() == 0 || files.get(USIM_EFGRP_TAG) == null) {
            return;
        }

        int efId = files.get(USIM_EFGRP_TAG).getEfid();

        int[] size;
        if (mRecordsSize != null && mRecordsSize.containsKey(efId)) {
            size = mRecordsSize.get(efId);
        } else {
            size = readFileSizeAndWait(efId);
        }

        if (mGrpFileRecord == null) {
            mGrpFileRecord = new ArrayList<byte[]>();
        }
        ArrayList<byte[]> fileRecord = initArraylist(size[0], size[2]);
        if (mGrpFileRecord != null && mGrpFileRecord.size() == offSet) {
            mGrpFileRecord.addAll(offSet, fileRecord);
        } else {
            return;
        }

        for (int i = offSet; i < mPhoneBookRecords.size() && i < (offSet + size[2]); i++) {
            if (!TextUtils.isEmpty(mPhoneBookRecords.get(i).getAlphaTag())
                    || !TextUtils.isEmpty(mPhoneBookRecords.get(i).getNumber())) {
                mPendingGrpLoads.addAndGet(1);
                mFh.loadEFLinearFixed(efId, i + 1 - offSet, size[0],
                        obtainMessage(EVENT_GRP_LOAD_DONE, i - offSet, offSet));
            }
        }
        if (mPendingGrpLoads.get() == 0) {
            mIsNotify.set(false);
            return;
        } else {
            mIsNotify.set(true);
        }
        try {
            mLock.wait();
        } catch (InterruptedException e) {
            loge("Interrupted Exception in readGrpFileAndWait");
        }
    }

    // Update AdnRecord list
    private void updateAdnRecord(int recId) {
        SubjectIndexOfAdn emailInfo = null;
        SubjectIndexOfAdn anrInfo = null;
        SubjectIndexOfAdn aasInfo = null;
        SubjectIndexOfAdn sneInfo = null;
        String snes = null;
        String anr = null;
        byte[] record = null;
        int numIapRec = 0;
        int doneAdnCount = 0;

        int numAdnRecs = mPhoneBookRecords.size();
        if (numAdnRecs == 0) {
            return;
        }

        mAdnRecordSizeArray[recId] = numAdnRecs;
        for (int i = 0; i < recId; i++) {
            doneAdnCount += mAdnRecordSizeArray[i];
            mAdnRecordSizeArray[recId] -= mAdnRecordSizeArray[i];
        }

        emailInfo = getSubjectIndex(USIM_SUBJCET_EMAIL, recId);
        anrInfo = getSubjectIndex(USIM_SUBJCET_ANR, recId);
        aasInfo = getSubjectIndex(USIM_SUBJCET_AAS, recId);
        sneInfo = getSubjectIndex(USIM_SUBJCET_SNE, recId);

        if (mIapFileRecord != null) {
            // The number of records in the IAP file is same as the number of
            // records in ADN file.
            // The order of the pointers in an EFIAP shall be the same as the
            // order of file IDs
            // that appear in the TLV object indicated by Tag 'A9' in the
            // reference file record.
            // i.e value of mEmailTagNumberInIap

            numIapRec = mIapFileRecord.size();
            mIapFileRecordArray[recId] = mIapFileRecord;
            mIapRecordSizeArray[recId] = numIapRec;

            numIapRec = ((numAdnRecs - doneAdnCount) > numIapRec) ? numIapRec
                    : (numAdnRecs - doneAdnCount);
        } else {
            numIapRec = numAdnRecs - doneAdnCount;
        }

        for (int i = doneAdnCount; i < (doneAdnCount + numIapRec); i++) {
            record = null;
            if (mIapFileRecord != null) {
                record = mIapFileRecord.get((i - doneAdnCount));
            }

            // set email to AdnRecord
            if (mEmailsForAdnRec != null) {
                AdnRecord rec = mPhoneBookRecords.get(i);
                int adnEfid = rec.getEfid();
                int adnRecId = rec.getRecId();
                int index = (((adnEfid & 0xFFFF) << 8) | ((adnRecId - 1) & 0xFF));
                ArrayList<String> emailList = mEmailsForAdnRec.get(index);

                if (emailList != null) {
                    String[] emails = new String[emailList.size()];
                    System.arraycopy(emailList.toArray(), 0, emails, 0, emailList.size());
                    setEmailandAnr(i, emails, null);
                }
            }

            // set anr to AdnRecord
            if (anrInfo != null) {
                anr = getAnr(recId, anrInfo, aasInfo, i, record, (i - doneAdnCount));
                setEmailandAnr(i, null, anr);
            }

            // set sne to AdnRecord
            if (sneInfo != null) {
                snes = getSne(recId, sneInfo, record, (i - doneAdnCount));
                if (snes != null) {
                    setSne(i, snes);
                }
            }
        }
        mIapFileRecord = null;
    }

    // Set email or anr to AdnRecord
    private void setEmailandAnr(int adnNum, String[] emails, String anr) {
        AdnRecord rec = mPhoneBookRecords.size() > adnNum ? mPhoneBookRecords.get(adnNum) : null;
        if (rec == null && (emails != null || anr != null)) {
            rec = new AdnRecord("", "");
        }

        if (emails != null) {
            rec.setEmails(emails);
        }

        if (anr != null) {
            rec.setAnr(anr);
        }

        mPhoneBookRecords.set(adnNum, rec);
    }

    // Get anr string
    private String getAnr(int recId, SubjectIndexOfAdn anrInfo,
            SubjectIndexOfAdn aasInfo, int index, byte[] record,
            int adnNum) {
        String anrGroup = null;
        String anr = null;
        String aasGroup = null;
        String aas = null;

        if (anrInfo.efids == null || anrInfo.efids.length == 0) {
            return null;
        }

        for (int i = 0; i < anrInfo.efids.length; i++) {
            ArrayList<Integer> aasIndex = new ArrayList<Integer>();
            if (anrInfo.type[i] == USIM_TYPE1_TAG) {
                anr = getType1Anr(recId, anrInfo, adnNum, anrInfo.efids[i], aasIndex);
            }

            if (anrInfo.type[i] == USIM_TYPE2_TAG
                    && anrInfo.recordNumInIap != null && record != null) {
                anr = getType2Anr(recId, anrInfo, record, adnNum, anrInfo.efids[i], aasIndex);
            }

            if (i < aasIndex.size() && aasIndex.get(i) > 0) {
                aas = getAas(aasInfo, aasIndex.get(i));
            }

            if (i == 0) {
                anrGroup = anr;
                aasGroup = aas;
            } else {
                anrGroup = anrGroup + ";" + anr;
                aasGroup = aasGroup + ";" + aas;
            }
        }

        if (aasInfo != null && aasGroup != null) {
            setAas(index, aasGroup);
        }

        return anrGroup;
    }

    // Get type1 anr string
    private String getType1Anr(int num, SubjectIndexOfAdn anrInfo, int adnNum,
            int efid, ArrayList<Integer> aasIndex) {
        byte[] anrRec;
        String anr = "";
        ArrayList<byte[]> anrFileRecord = anrInfo.record.get(efid);

        if (anrFileRecord == null) {
            return anr;
        }

        if (adnNum < anrFileRecord.size()) {
            anrRec = anrFileRecord.get(adnNum);
            anr = PhoneNumberUtils.calledPartyBCDToString(anrRec, 2,
                    (0xff & anrRec[1]));
            aasIndex.add((int) anrRec[0]);
        }
        return anr;
    }

    // Get type2 anr string
    private String getType2Anr(int num, SubjectIndexOfAdn anrInfo,
            byte[] record, int adnNum, int efid, ArrayList<Integer> aasIndex) {
        byte[] anrRec;
        String anr = "";
        int anrTagNumberInIap = anrInfo.recordNumInIap.get(efid);
        ArrayList<byte[]> anrFileRecord = anrInfo.record.get(efid);

        if (anrFileRecord == null || record.length <= anrTagNumberInIap) {
            return anr;
        }

        int index = getEfIndex(efid, anrInfo);
        if (index == -1) {
            return anr;
        }

        int recNum = (int) (record[anrTagNumberInIap] & 0xFF);
        recNum = ((recNum == 0xFF) ? (-1) : recNum);

        if (recNum > 0 && recNum <= anrFileRecord.size()) {
            anrRec = anrFileRecord.get(recNum - 1);
            anr = PhoneNumberUtils.calledPartyBCDToString(anrRec, 2,
                    (0xff & anrRec[1]));
            aasIndex.add((int) anrRec[0]);

            if (TextUtils.isEmpty(anr)) {
                setIapFileRecord(num, adnNum, (byte) 0xFF, anrTagNumberInIap);
                return anr;
            }

            Set<Integer> set = (Set<Integer>) anrInfo.usedSet[index];
            set.add(new Integer(recNum));
            anrInfo.usedSet[index] = set;
            setSubjectIndex(USIM_SUBJCET_ANR, num, anrInfo);
        }
        return anr;
    }

    // Get aas record
    private String getAas(SubjectIndexOfAdn aasInfo, int aasIndex) {
        if (aasInfo == null || aasInfo.efids == null || aasInfo.efids.length == 0) {
            return null;
        }

        aasIndex = aasIndex - 1;
        String aas = getTypeAas(aasInfo, aasIndex, aasInfo.efids[0]);
        return aas;
    }

    // Get aas record
    private String getTypeAas(SubjectIndexOfAdn aasInfo, int adnNum, int efid) {
        mAasFileRecord = aasInfo.record.get(efid);
        if (mAasFileRecord == null) {
            return null;
        }

        byte[] aasRec = mAasFileRecord.get(adnNum);
        String aas = IccUtils.adnStringFieldToString(aasRec, 0, aasRec.length);

        if (TextUtils.isEmpty(aas)) {
            return null;
        }
        return aas;
    }

    private void setAas(int adnNum, String aas) {
        AdnRecord rec = mPhoneBookRecords.size() > adnNum ? mPhoneBookRecords.get(adnNum) : null;
        if (rec == null) {
            rec = new AdnRecord("", "");
        }

        rec.setAas(aas);
        mPhoneBookRecords.set(adnNum, rec);
    }

    private String getSne(int recId, SubjectIndexOfAdn sneInfo, byte[] record,
                          int adnNum) {
        String sneGroup = null;
        String sne = null;

        if (sneInfo == null || sneInfo.record == null
                || sneInfo.efids == null || sneInfo.efids.length == 0) {
            return null;
        }

        for (int i = 0; i < sneInfo.efids.length; i++) {
            if (sneInfo.type[i] == USIM_TYPE1_TAG) {
                sne = getType1Sne(recId, sneInfo, adnNum, sneInfo.efids[i]);
            }

            if (sneInfo.type[i] == USIM_TYPE2_TAG
                    && sneInfo.recordNumInIap != null && record != null) {
                sne = getType2Sne(recId, sneInfo, record, adnNum, sneInfo.efids[i]);
            }

            if (i == 0) {
                sneGroup = sne;
            } else {
                sneGroup = sneGroup + ";" + sne;
            }
        }
        return sneGroup;
    }

    private String getType1Sne(int recId, SubjectIndexOfAdn sneInfo, int adnNum,
                               int efid) {
        mSneFileRecord = sneInfo.record.get(efid);
        if (mSneFileRecord == null) {
            return null;
        }

        return readSneRecord(adnNum);
    }

    private String getType2Sne(int recId, SubjectIndexOfAdn sneInfo,
                               byte[] record, int adnNum, int efid) {
        String snes = null;
        int index = getEfIndex(efid, sneInfo);
        if (index == -1) {
            return snes;
        }

        int sneTagNumberInIap = sneInfo.recordNumInIap.get(efid);
        mSneFileRecord = sneInfo.record.get(efid);
        if (mSneFileRecord == null) {
            return snes;
        }

        int recNum = (int) (record[sneTagNumberInIap] & 0xFF);
        recNum = ((recNum == 0xFF) ? (-1) : recNum);
        if (recNum > 0) {
            snes = readSneRecord(recNum - 1);
            if (TextUtils.isEmpty(snes)) {
                setIapFileRecord(recId, adnNum, (byte) 0xFF, sneTagNumberInIap);
                return null;
            }

            Set<Integer> set = (Set<Integer>) sneInfo.usedSet[index];
            set.add(new Integer(recNum));
            sneInfo.usedSet[index] = set;
            setSubjectIndex(USIM_SUBJCET_SNE, recId, sneInfo);
        }
        return snes;
    }

    private String readSneRecord(int adnNum) {
        byte[] sneRec = mSneFileRecord.get(adnNum);

        // The length of the record is X+2 byte, where X bytes is the sne
        // address
        String sne = IccUtils.adnStringFieldToString(sneRec, 0, sneRec.length - 2);
        return sne;
    }

    private void setSne(int adnNum, String snes) {
        AdnRecord rec = mPhoneBookRecords.size() > adnNum ? mPhoneBookRecords.get(adnNum) : null;

        if (rec == null) {
            rec = new AdnRecord("", "");
        }
        rec.setSne(snes);

        try {
            mPhoneBookRecords.set(adnNum, rec);
        } catch (IndexOutOfBoundsException e) {
            loge("setSne, IndexOutOfBoundsException");
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
            try {
                emailList = mEmailsForAdnRec.get(index);
            } catch (IndexOutOfBoundsException e) {
                continue;
            }

            if (emailList == null) continue;

            String[] emails = new String[emailList.size()];
            System.arraycopy(emailList.toArray(), 0, emails, 0, emailList.size());
            rec.setEmails(emails);
            log("Adding email list to ADN (0x"
                    + Integer.toHexString(mPhoneBookRecords.get(i).getEfid()).toUpperCase()
                    + ") record #" + mPhoneBookRecords.get(i).getRecId());
            mPhoneBookRecords.set(i, rec);
        }
    }

    // Read email from the record of EF_EMAIL
    private String readEmailRecord(int recId) {
        byte[] emailRec;
        try {
            emailRec = mEmailFileRecord.get(recId);
        } catch (IndexOutOfBoundsException e) {
            return null;
        }

        // The length of the record is X+2 byte, where X bytes is the email address
        return IccUtils.adnStringFieldToString(emailRec, 0, emailRec.length - 2);
    }

    // Read EF_ADN file
    private void readAdnFileAndWait(int recId) {
        SparseArray<File> files;
        files = mPbrRecords.get(recId).mFileIds;
        if (files == null || files.size() == 0 || files.get(USIM_EFADN_TAG) == null) {
            mIsContainAdnInPbr = false;
            return;
        }

        int extEf = 0;
        // Only call fileIds.get while EF_EXT1_TAG is available
        if (files.get(USIM_EFEXT1_TAG) != null) {
            extEf = files.get(USIM_EFEXT1_TAG).getEfid();
        }

        int previousSize = mPhoneBookRecords.size();
        mAdnCache.requestLoadAllAdnLike(files.get(USIM_EFADN_TAG).getEfid(),
                extEf, obtainMessage(EVENT_USIM_ADN_LOAD_DONE));
        try {
            mLock.wait();
        } catch (InterruptedException e) {
            Rlog.e(LOG_TAG, "Interrupted Exception in readAdnFileAndWait");
        }

        if (mPhoneBookRecords.size() == previousSize) {
            mIsContainAdnInPbr = false;
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
        byte[] data;

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
                if (ar.exception == null) {
                    mPhoneBookRecords.addAll((ArrayList<AdnRecord>) ar.result);
                }
                synchronized (mLock) {
                    mLock.notify();
                }
                break;

            case EVENT_IAP_LOAD_DONE:
                log("Loading USIM IAP records done, index is " + msg.arg1);
                ar = (AsyncResult) msg.obj;
                if (ar.exception == null) {
                    mIapFileRecord.set(msg.arg1, (byte[]) ar.result);
                }

                mPendingIapLoads.decrementAndGet();
                if (mPendingIapLoads.get() == 0) {
                    if (mIsNotify.get()) {
                        mIsNotify.set(false);
                        synchronized (mLock) {
                            mLock.notify();
                        }
                        log("Loading USIM IAP records done notify");
                    }
                }
                break;

            case EVENT_EMAIL_LOAD_DONE:
                log("Loading USIM Email records done");
                ar = (AsyncResult) msg.obj;
                if (ar.exception == null) {
                    mEmailFileRecord = ((ArrayList<byte[]>) ar.result);
                }

                synchronized (mLock) {
                    mLock.notify();
                }
                break;

            case EVENT_ANR_RECORD_LOAD_DONE:
                log("Loading USIM ANR record done, index is " + msg.arg1);
                ar = (AsyncResult) msg.obj;
                if (ar.exception == null && mAnrFileRecord != null) {
                    mAnrFileRecord.set(msg.arg1, (byte[]) ar.result);
                }

                mPendingAnrLoads.decrementAndGet();
                if (mPendingAnrLoads.get() == 0 && mIsNotify.get()) {
                    mIsNotify.set(false);
                    synchronized (mLock) {
                        mLock.notify();
                    }
                    log("Loading USIM ANR records done notify");
                }
                break;

            case EVENT_ANR_LOAD_DONE:
                log("Loading USIM ANR records done");
                ar = (AsyncResult) msg.obj;
                if (ar.exception == null) {
                    if (mAnrFileRecord == null) {
                        mAnrFileRecord = new ArrayList<byte[]>();
                    }
                    mAnrFileRecord.addAll((ArrayList<byte[]>) ar.result);
                }

                synchronized (mLock) {
                    mLock.notify();
                }
                break;

            case EVENT_GRP_LOAD_DONE:
                ar = (AsyncResult) msg.obj;
                int index = msg.arg1 + msg.arg2;
                log("Loading USIM Grp record done, index is " + index);
                if (ar.exception == null) {
                    if (mGrpFileRecord == null) {
                        mGrpFileRecord = new ArrayList<byte[]>();
                    }

                    try {
                        mGrpFileRecord.set(index, (byte[]) ar.result);
                    } catch (IndexOutOfBoundsException exc) {
                        loge("IndexOutOfBoundsException readGrpFileAndWait");
                    }
                }

                mPendingGrpLoads.decrementAndGet();
                if (mPendingGrpLoads.get() == 0 && mIsNotify.get()) {
                    mIsNotify.set(false);
                    synchronized (mLock) {
                        mLock.notify();
                    }
                    log("Loading USIM Grp records done notify");
                }
                break;

            case EVENT_GAS_LOAD_DONE:
                log("Loading USIM Gas records done");
                ar = (AsyncResult) msg.obj;
                if (ar.exception == null) {
                    if (mGasFileRecord == null) {
                        mGasFileRecord = new ArrayList<byte[]>();
                    }
                    mGasFileRecord.clear();
                    mGasFileRecord.addAll((ArrayList<byte[]>) ar.result);
                }

                synchronized (mLock) {
                    mLock.notify();
                }
                break;

            case EVENT_AAS_LOAD_DONE:
                log("Loading USIM AAS records done");
                ar = (AsyncResult) msg.obj;
                if (ar.exception == null) {
                    if (mAasFileRecord == null) {
                        mAasFileRecord = new ArrayList<byte[]>();
                    }
                    mAasFileRecord.clear();
                    mAasFileRecord.addAll((ArrayList<byte[]>) ar.result);
                }

                synchronized (mLock) {
                    mLock.notify();
                }
                break;

            case EVENT_SNE_LOAD_DONE:
                log("Loading USIM SNE records done");
                ar = (AsyncResult) msg.obj;
                if (ar.exception == null) {
                    if (mSneFileRecord == null) {
                        mSneFileRecord = new ArrayList<byte[]>();
                    }
                    mSneFileRecord.clear();
                    mSneFileRecord.addAll((ArrayList<byte[]>) ar.result);
                }

                synchronized (mLock) {
                    mLock.notify();
                }
                break;

            case EVENT_GET_RECORDS_COUNT:
                log("Loading EVENT_GET_RECORDS_COUNT, Efid is " + msg.arg1);
                ar = (AsyncResult) msg.obj;
                synchronized (mLock) {
                    if (ar.exception == null) {
                        int[] recordSize = (int[]) ar.result;
                        // recordSize[0] is the record length
                        // recordSize[1] is the total length of the EF file
                        // recordSize[2] is the number of records in the EF file
                        log("EVENT_GET_RECORDS_COUNT, Size is " + Arrays.toString(recordSize));

                        if (mRecordsSize == null) {
                            mRecordsSize = new HashMap<Integer, int[]>();
                        }
                        mRecordsSize.put(msg.arg1, recordSize);
                    }
                    mLock.notify();
                }
                break;

            case EVENT_LOAD_EF_PBC_RECORD_DONE:
                log("Loading EVENT_LOAD_EF_PBC_RECORD_DONE, msg.arg1 = " + msg.arg1);
                ar = (AsyncResult) (msg.obj);
                if (ar.exception == null && mPbcFileRecord != null) {
                    mPbcFileRecord.set(msg.arg1, (byte[]) ar.result);
                }

                mPendingPbcLoads.decrementAndGet();
                if (mPendingPbcLoads.get() == 0) {
                    if (mIsNotify.get()) {
                        mIsNotify.set(false);
                        synchronized (mLock) {
                            mLock.notify();
                        }
                        log("Loading USIM Pbc records done notify");
                    }
                }
                break;

            case EVENT_EF_CC_LOAD_DONE:
                log("Loading EVENT_EF_CC_LOAD_DONE");
                ar = (AsyncResult) (msg.obj);
                data = (byte[]) (ar.result);
                int temp = msg.arg1;
                int isUpdateUid = msg.arg2;
                if (ar.exception != null || data == null) {
                    break;
                }

                // update EFcc
                byte[] counter = new byte[2];
                int cc = ((data[0] << 8) & 0xFF00) | (data[1] & 0xFF);
                mChangedCounter = cc;
                cc += temp;

                if (cc > 0xFFFF) {
                    counter[0] = (byte) 0x00;
                    counter[1] = (byte) 0x01;
                } else {
                    counter[1] = (byte) (cc & 0xFF);
                    counter[0] = (byte) (cc >> 8 & 0xFF);
                }

                if (isUpdateUid == 1) {
                    mFh.updateEFTransparent(IccConstants.EF_CC, counter,
                            obtainMessage(EVENT_UPDATE_CC_DONE));
                } else {
                    mFh.updateEFTransparent(IccConstants.EF_CC, counter,
                            obtainMessage(EVENT_UPDATE_RECORD_DONE));
                }
                break;

            case EVENT_EF_PUID_LOAD_DONE:
                log("Loading EVENT_EF_PUID_LOAD_DONE");
                ar = (AsyncResult) (msg.obj);
                data = (byte[]) (ar.result);
                int recNum = msg.arg1;
                int recordNum = msg.arg2;

                if (ar.exception != null) {
                    break;
                }
                // update EFuid and EFpuid
                byte[] uidData = new byte[2];
                byte[] newPuid = new byte[2];
                int puid = ((data[0] << 8) & 0xFF00) | (data[1] & 0xFF);
                if (mChangedCounter == 0xFFFF || puid == 0xFFFF) {
                    // update psc
                    synchronized (mLock) {
                        mFh.loadEFTransparent(IccConstants.EF_PSC, 4,
                                obtainMessage(EVENT_EF_PSC_LOAD_DONE));
                    }
                    // regenerate uids
                    int uid = 0;
                    int[] adnRecordSizeArray = getAdnRecordSizeArray();
                    for (int num = 0; num < getNumRecs(); num++) {
                        for (int i = 0; i < adnRecordSizeArray[num]; i++) {
                            int adnRecNum = i;
                            for (int j = 0; j < num; j++) {
                                adnRecNum += adnRecordSizeArray[j];
                            }

                            if (adnRecNum < mPhoneBookRecords.size()
                                    && (!TextUtils.isEmpty(
                                            mPhoneBookRecords.get(adnRecNum).getAlphaTag())
                                    || !TextUtils.isEmpty(
                                            mPhoneBookRecords.get(adnRecNum).getNumber()))) {
                                uid++;
                                uidData = intToBytes(uid, 2);
                                synchronized (mLock) {
                                    mFh.updateEFLinearFixed(
                                            findEFInfo(num, USIM_EFUID_TAG), i + 1, uidData,
                                            null, obtainMessage(EVENT_UPDATE_UID_DONE, -1));
                                }
                            }
                        }
                    }
                    // update puid
                    newPuid = intToBytes(uid, 2);
                    mFh.updateEFTransparent(IccConstants.EF_PUID, newPuid,
                            obtainMessage(EVENT_UPDATE_RECORD_DONE));
                } else {
                    uidData[1] = (byte) ((puid + 1) & 0xFF);
                    uidData[0] = (byte) ((puid + 1) >> 8 & 0xFF);
                    newPuid[0] = uidData[0];
                    newPuid[1] = uidData[1];
                    synchronized (mLock) {
                        // update uid
                        mFh.updateEFLinearFixed(findEFInfo(recNum, USIM_EFUID_TAG), recordNum,
                                uidData, null, obtainMessage(EVENT_UPDATE_UID_DONE, puid + 1));
                    }
                }
                break;

            case EVENT_UPDATE_UID_DONE:
                log("Loading EVENT_UPDATE_UID_DONE");
                // update puid
                ar = (AsyncResult) (msg.obj);
                int puid1 = (Integer) (ar.userObj);
                if (puid1 != -1) {
                    mFh.updateEFTransparent(IccConstants.EF_PUID,
                            intToBytes(puid1, 2), obtainMessage(EVENT_UPDATE_RECORD_DONE));
                }
                break;

            case EVENT_UPDATE_CC_DONE:
                log("Loading EVENT_UPDATE_CC_DONE");
                mFh.loadEFTransparent(IccConstants.EF_PUID, 2,
                        obtainMessage(EVENT_EF_PUID_LOAD_DONE));
                break;

            case EVENT_EF_PSC_LOAD_DONE:
                log("Loading EVENT_EF_PSC_LOAD_DONE");
                ar = (AsyncResult) (msg.obj);
                data = (byte[]) (ar.result);
                if (ar.exception != null) {
                    break;
                }

                byte[] psc = new byte[4];
                int oldPsc = bytesToInt(data);
                if (oldPsc != -1) {
                    if (oldPsc == 0xFFFFFFFF) {
                        psc = intToBytes(1, 4);
                    } else {
                        psc = intToBytes(oldPsc + 1, 4);
                    }
                }

                mFh.updateEFTransparent(IccConstants.EF_PSC, psc,
                        obtainMessage(EVENT_UPDATE_RECORD_DONE));
                break;

            case EVENT_UPDATE_RECORD_DONE:
                log("Loading EVENT_UPDATE_RECORD_DONE");
                ar = (AsyncResult) (msg.obj);
                if (ar.exception != null) {
                    throw new RuntimeException("update EF records failed",
                            ar.exception);
                }

                break;
        }
    }

    // PbrRecord represents a record in EF_PBR
    private class PbrRecord {
        // TLV tags
        private SparseArray<File> mFileIds;

        /**
         * 3GPP TS 31.102 4.4.2.1 EF_PBR (Phone Book Reference file)
         * If this is type 1 files, files that contain as many records as the
         * reference/master file (EF_ADN, EF_ADN1) and are linked on record number
         * bases (Rec1 -> Rec1). The master file record number is the reference.
         */
        private int mMasterFileRecordNum;

        PbrRecord(byte[] record) {
            mFileIds = new SparseArray<File>();
            SimTlv recTlv;
            log("PBR rec: " + IccUtils.bytesToHexString(record));
            recTlv = new SimTlv(record, 0, record.length);
            parseTag(recTlv);
        }

        void parseTag(SimTlv tlv) {
            SimTlv tlvEfSfi;
            int tag;
            byte[] data;
            ArrayList<Integer> emailEfs = new ArrayList<Integer>();
            ArrayList<Integer> anrEfs = new ArrayList<Integer>();
            ArrayList<Integer> emailType = new ArrayList<Integer>();
            ArrayList<Integer> anrType = new ArrayList<Integer>();
            SubjectIndexOfAdn emailInfo = new SubjectIndexOfAdn();
            emailInfo.recordNumInIap = new HashMap<Integer, Integer>();
            SubjectIndexOfAdn anrInfo = new SubjectIndexOfAdn();
            anrInfo.recordNumInIap = new HashMap<Integer, Integer>();
            ArrayList<Integer> aasEfs = new ArrayList<Integer>();
            ArrayList<Integer> aasType = new ArrayList<Integer>();
            SubjectIndexOfAdn aasInfo = new SubjectIndexOfAdn();
            aasInfo.recordNumInIap = new HashMap<Integer, Integer>();
            ArrayList<Integer> sneEfs = new ArrayList<Integer>();
            ArrayList<Integer> sneType = new ArrayList<Integer>();
            SubjectIndexOfAdn sneInfo = new SubjectIndexOfAdn();
            sneInfo.recordNumInIap = new HashMap<Integer, Integer>();
            do {
                tag = tlv.getTag();
                switch(tag) {
                    case USIM_TYPE1_TAG: // A8
                    case USIM_TYPE3_TAG: // AA
                    case USIM_TYPE2_TAG: // A9
                        data = tlv.getData();
                        tlvEfSfi = new SimTlv(data, 0, data.length);
                        parseEfAndSFI(tlvEfSfi, tag, emailInfo, anrInfo, emailEfs,
                                anrEfs, emailType, anrType, aasInfo, aasEfs,
                                aasType, sneInfo, sneEfs, sneType);
                        break;
                }
            } while (tlv.nextObject());

            if (emailEfs.size() > 0) {
                emailInfo.efids = new int[emailEfs.size()];
                emailInfo.type = new int[emailEfs.size()];

                for (int i = 0; i < emailEfs.size(); i++) {
                    emailInfo.efids[i] = emailEfs.get(i);
                    emailInfo.type[i] = emailType.get(i);
                }
            }

            if (anrEfs.size() > 0) {
                anrInfo.efids = new int[anrEfs.size()];
                anrInfo.type = new int[anrEfs.size()];

                for (int i = 0; i < anrEfs.size(); i++) {
                    anrInfo.efids[i] = anrEfs.get(i);
                    anrInfo.type[i] = anrType.get(i);

                }
            }

            if (aasEfs.size() > 0) {
                aasInfo.efids = new int[aasEfs.size()];
                aasInfo.type = new int[aasEfs.size()];

                for (int i = 0; i < aasEfs.size(); i++) {
                    aasInfo.efids[i] = aasEfs.get(i);
                    aasInfo.type[i] = aasType.get(i);
                }
            }

            if (sneEfs.size() > 0) {
                sneInfo.efids = new int[sneEfs.size()];
                sneInfo.type = new int[sneEfs.size()];

                for (int i = 0; i < sneEfs.size(); i++) {
                    sneInfo.efids[i] = sneEfs.get(i);
                    sneInfo.type[i] = sneType.get(i);
                }
            }

            if (mPhoneBookRecords != null && mPhoneBookRecords.isEmpty()) {
                if (mAnrInfoFromPBR != null) {
                    mAnrInfoFromPBR.add(anrInfo);
                }

                if (mEmailInfoFromPBR != null) {
                    mEmailInfoFromPBR.add(emailInfo);
                }

                if (mAasInfoFromPBR != null) {
                    mAasInfoFromPBR.add(aasInfo);
                }

                if (mSneInfoFromPBR != null) {
                    mSneInfoFromPBR.add(sneInfo);
                }
            }
        }

        void parseEfAndSFI(SimTlv tlv, int parentTag,
                SubjectIndexOfAdn emailInfo, SubjectIndexOfAdn anrInfo,
                ArrayList<Integer> emailEFS, ArrayList<Integer> anrEFS,
                ArrayList<Integer> emailTypes, ArrayList<Integer> anrTypes,
                SubjectIndexOfAdn aasInfo, ArrayList<Integer> aasEFS,
                ArrayList<Integer> aasTypes, SubjectIndexOfAdn sneInfo, ArrayList<Integer> sneEFS,
                ArrayList<Integer> sneTypes) {
            int tag;
            byte[] data;
            int tagNumberWithinParentTag = 0;

            do {
                tag = tlv.getTag();
                switch(tag) {
                    case USIM_EFEMAIL_TAG:
                    case USIM_EFADN_TAG:
                    case USIM_EFEXT1_TAG:
                    case USIM_EFANR_TAG:
                    case USIM_EFPBC_TAG:
                    case USIM_EFGRP_TAG:
                    case USIM_EFAAS_TAG:
                    case USIM_EFGAS_TAG:
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

                        if (tag == USIM_EFADN_TAG) {
                            emailInfo.adnEfid = efid;
                            anrInfo.adnEfid = efid;
                            aasInfo.adnEfid = efid;
                            sneInfo.adnEfid = efid;
                        }

                        if (parentTag == USIM_TYPE2_TAG && tag == USIM_EFEMAIL_TAG) {
                            mEmailPresentInIap = true;
                        }

                        if (tag == USIM_EFEMAIL_TAG) {
                            if (parentTag == USIM_TYPE2_TAG) {
                                emailInfo.recordNumInIap.put(efid, tagNumberWithinParentTag);
                            }

                            emailEFS.add(efid);
                            emailTypes.add(parentTag);
                        }

                        if (tag == USIM_EFANR_TAG) {
                            if (parentTag == USIM_TYPE2_TAG) {
                                mAnrPresentInIap = true;
                                anrInfo.recordNumInIap.put(efid, tagNumberWithinParentTag);
                            }

                            anrEFS.add(efid);
                            anrTypes.add(parentTag);
                        }

                        if (tag == USIM_EFAAS_TAG) {
                            aasEFS.add(efid);
                            aasTypes.add(parentTag);
                        }

                        if (tag == USIM_EFSNE_TAG) {
                            if (parentTag == USIM_TYPE2_TAG) {
                                mSnePresentInIap = true;
                                sneInfo.recordNumInIap.put(efid, tagNumberWithinParentTag);
                            }
                            sneEFS.add(efid);
                            sneTypes.add(parentTag);
                        }

                        // Just support one email, one anr and one sne
                        if (mFileIds.get(tag) != null && (tag == USIM_EFEMAIL_TAG
                                || tag == USIM_EFANR_TAG || tag == USIM_EFSNE_TAG)) {
                            log("parseTag, tag already exist.");
                        } else {
                            mFileIds.put(tag,
                                    new File(parentTag, efid, sfi, tagNumberWithinParentTag));
                        }
                        break;
                }
                tagNumberWithinParentTag++;
            } while(tlv.nextObject());
        }
    }

    @UnsupportedAppUsage
    private void log(String msg) {
        if (DBG) Rlog.d(LOG_TAG, msg);
    }

    private void loge(String msg) {
        Rlog.e(LOG_TAG, msg);
    }

    /**
     * Load aas from the SIM.
     *
     * @return list of aas string
     */
    public ArrayList<String> loadAasFromUsim() {
        log("loadAasFromUsim");
        if (!mAasList.isEmpty()) {
            return mAasList;
        }

        if (mAasFileRecord == null) {
            // the aas file is Type3 in Pbr
            readAasFileAndWait(0);
        }

        if (mAasFileRecord == null) {
            return null;
        }

        byte[] aasRec;
        int aasSize = mAasFileRecord.size();

        for (int i = 0; i < aasSize; i++) {
            aasRec = mAasFileRecord.get(i);
            if (aasRec != null) {
                mAasList.add(IccUtils.adnStringFieldToString(aasRec, 0, aasRec.length));
            }
        }
        return mAasList;
    }

    /**
     * Read EF_AAS file.
     *
     * @param recNum pbr number
     */
    public void readAasFileAndWait(int recNum) {
        synchronized (mLock) {
            if (mPbrRecords == null || mPbrRecords.size() == 0) {
                readPbrFileAndWait();
            }
        }
        if (mPbrRecords == null || mPbrRecords.size() == 0) {
            return;
        }

        SparseArray<File> files = null;
        try {
            files = mPbrRecords.get(recNum).mFileIds;
        } catch (IndexOutOfBoundsException e) {
            loge("readAasFileAndWait, IndexOutOfBoundsException");
            return;
        }

        if (files == null || files.size() == 0 || files.get(USIM_EFAAS_TAG) == null) {
            return;
        }

        SubjectIndexOfAdn records = getSubjectIndex(USIM_SUBJCET_AAS, recNum);
        if (records == null) {
            return;
        }

        records.record = new HashMap<Integer, ArrayList<byte[]>>();
        if (records.efids == null || records.efids.length == 0) {
            return;
        }

        synchronized (mLock) {
            mFh.loadEFLinearFixedAll(records.efids[0], obtainMessage(EVENT_AAS_LOAD_DONE));
            try {
                mLock.wait();
            } catch (InterruptedException e) {
                loge("Interrupted Exception in readAasFileAndWait");
            }
        }

        if (mAasFileRecord == null) {
            records.efids[0] = 0;
        } else {
            records.record.put(records.efids[0], mAasFileRecord);
        }

        handleReadFileResult(records);
        setSubjectIndex(USIM_SUBJCET_AAS, recNum, records);
        initSubjectUsedNum(USIM_SUBJCET_AAS, recNum);
    }

    /**
     * Load gas from the SIM.
     *
     * @return list of gas string
     */
    public ArrayList<String> loadGasFromUsim() {
        log("loadGasFromUsim");
        if (!mGasList.isEmpty()) {
            return mGasList;
        }

        if (mGasFileRecord == null) {
            // the gas file is Type3 in Pbr
            readGasFileAndWait(0);
        }

        if (mGasFileRecord == null) {
            return null;
        }

        byte[] gasRec;
        int gasSize = mGasFileRecord.size();

        for (int i = 0; i < gasSize; i++) {
            gasRec = mGasFileRecord.get(i);
            if (gasRec != null) {
                mGasList.add(IccUtils.adnStringFieldToString(gasRec, 0,
                        gasRec.length));
            }
        }
        return mGasList;
    }

    /**
     * Read EF_GAS file.
     *
     * @param recNum pbr number
     */
    private void readGasFileAndWait(int recNum) {
        synchronized (mLock) {
            if (mPbrRecords == null || mPbrRecords.size() == 0) {
                readPbrFileAndWait();
            }
        }
        if (mPbrRecords == null || mPbrRecords.size() == 0) {
            loge("Error: Pbr file is empty");
            return;
        }

        SparseArray<File> files = null;
        try {
            files = mPbrRecords.get(recNum).mFileIds;
        } catch (IndexOutOfBoundsException e) {
            loge("readGasFileAndWait IndexOutOfBoundsException");
            return;
        }
        if (files == null || files.size() == 0 || files.get(USIM_EFGAS_TAG) == null) {
            return;
        }

        synchronized (mLock) {
            mFh.loadEFLinearFixedAll(
                    files.get(USIM_EFGAS_TAG).getEfid(), obtainMessage(EVENT_GAS_LOAD_DONE));
            try {
                mLock.wait();
            } catch (InterruptedException e) {
                loge("Interrupted Exception in readGasFileAndWait");
            }
        }
    }

    /**
     * Update gas list.
     *
     * @param groupName group name to be stored
     * @param groupId gas index to be updated
     */
    public void updateGasList(String groupName, int groupId) {
        if (!mGasList.isEmpty()) {
            mGasList.set(groupId - 1, groupName);
        }
    }

    /**
     * Update aas list.
     *
     * @param aas aas to be stored
     * @param aasIndex aas index to be updated
     */
    public void updateAasList(String aas, int aasIndex) {
        if (!mAasList.isEmpty()) {
            mAasList.set(aasIndex - 1, aas);
        }
    }

    // Read EF_ADN record size
    private int[] readAdnFileSizeAndWait(int recNum) {
        SparseArray<File> files = null;
        files = mPbrRecords.get(recNum).mFileIds;

        if (files == null || files.size() == 0 || files.get(USIM_EFADN_TAG) == null) {
            mIsContainAdnInPbr = false;
            return null;
        }

        int efid = files.get(USIM_EFADN_TAG).getEfid();
        int[] recordSize = null;

        if (mRecordsSize != null && mRecordsSize.containsKey(efid)) {
            recordSize = mRecordsSize.get(efid);
        } else {
            recordSize = readFileSizeAndWait(efid);
        }

        if (recordSize == null || recordSize.length < 3 || recordSize[0] < ADN_FOOTER_SIZE_BYTES) {
            mIsContainAdnInPbr = false;
            return null;
        }
        return recordSize;
    }

    /**
     * Get the max number of records in efid
     *
     * @param efId the EF id
     * @return  int[3] array
     *            recordSizes[0]  is the single record length
     *            recordSizes[1]  is the total length of the EF file
     *            recordSizes[2]  is the number of records in the EF file
     */
    public int[] readFileSizeAndWait(int efId) {
        log("readFileSizeAndWait efId is " + efId);
        synchronized (mLock) {
            mFh.getEFLinearRecordSize(efId, obtainMessage(EVENT_GET_RECORDS_COUNT, efId, 0));
            try {
                mLock.wait();
            } catch (InterruptedException e) {
                loge("Interrupted Exception in readAdnFileAndWait");
            }
        }

        int[] size = mRecordsSize != null ? mRecordsSize.get(efId) : null;
        return size;
    }

    // get SubjectIndexOfAdn of email/anr/aas/sne
    private SubjectIndexOfAdn getSubjectIndex(int type, int num) {
        LinkedList<SubjectIndexOfAdn> lst = null;
        SubjectIndexOfAdn index = null;
        switch (type) {
            case USIM_SUBJCET_EMAIL:
                lst = mEmailInfoFromPBR;
                break;
            case USIM_SUBJCET_ANR:
                lst = mAnrInfoFromPBR;
                break;
            case USIM_SUBJCET_AAS:
                lst = mAasInfoFromPBR;
                break;
            case USIM_SUBJCET_SNE:
                lst = mSneInfoFromPBR;
                break;
            default:
                break;
        }

        if (lst != null && lst.size() != 0) {
            index = lst.get(num);
            return index;
        }
        return null;
    }

    // set SubjectIndexOfAdn of email/anr/aas/sne
    private void setSubjectIndex(int type, int num,
                                 SubjectIndexOfAdn subjectIndex) {
        switch (type) {
            case USIM_SUBJCET_EMAIL:
                if (mEmailInfoFromPBR != null) {
                    mEmailInfoFromPBR.set(num, subjectIndex);
                }
                break;

            case USIM_SUBJCET_ANR:
                if (mAnrInfoFromPBR != null) {
                    mAnrInfoFromPBR.set(num, subjectIndex);
                }
                break;

            case USIM_SUBJCET_AAS:
                if (mAasInfoFromPBR != null) {
                    mAasInfoFromPBR.set(num, subjectIndex);
                }
                break;

            case USIM_SUBJCET_SNE:
                if (mSneInfoFromPBR != null) {
                    mSneInfoFromPBR.set(num, subjectIndex);
                }
                break;

            default:
                break;
        }
    }

    private void initSubjectUsedNum(int type, int num) {
        SubjectIndexOfAdn index = getSubjectIndex(type, num);
        if (index == null || index.efids == null) {
            return;
        }

        int size = index.efids.length;
        index.usedSet = new Object[size];

        for (int i = 0; i < size; i++) {
            index.usedSet[i] = new HashSet<Integer>();
        }
        setSubjectIndex(type, num, index);
    }

    // Get file index
    private int getEfIndex(int efid, SubjectIndexOfAdn index) {
        int count = -1;
        if (index != null && index.efids != null) {
            for (int k = 0; k < index.efids.length; k++) {
                if (index.type[k] == USIM_TYPE2_TAG) {
                    count++;
                    if (index.efids[k] == efid) {
                        return count;
                    }
                }
            }
        }
        return -1;
    }

    /**
     * Get Efids of email/anr/sne
     *
     * @param type type of email/anr/sne
     * @param recId pbr number
     * @return Efids of email/anr/sne
     */
    public int[] getSubjectEfids(int type, int recId) {
        SubjectIndexOfAdn index = getSubjectIndex(type, recId);
        if (index == null) {
            return null;
        }

        return index.efids;
    }

    /**
     * Get index in iap record for email/anr/sne
     *
     * @param type type of email/anr/sne
     * @param recId pbr number
     * @return index in iap record for email/anr/sne
     */
    public int[][] getSubjectTagNumberInIap(int type, int recId) {
        boolean isInIap = false;
        SubjectIndexOfAdn index = getSubjectIndex(type, recId);
        if (index == null) {
            return null;
        }

        int[][] result = new int[index.efids.length][2];  //[efid][numberInIap]
        Map<Integer, Integer> anrTagMap = index.recordNumInIap;
        if (anrTagMap == null || anrTagMap.size() == 0) {
            return null;
        }

        for (int i = 0; i < index.efids.length; i++) {
            if (anrTagMap.containsKey(index.efids[i])) {
                result[i][1] = anrTagMap.get(index.efids[i]);
                result[i][0] = index.efids[i];
                isInIap = true;
            }
        }

        if (!isInIap) {
            result = null;
        }

        return result;
    }

    /**
     * Check if ef is type2
     *
     * @param type type of email/anr/sne
     * @param recId pbr number
     * @param indexOfEfids index of ef
     * @return true for type2
     */
    public boolean isSubjectRecordInIap(int type, int recId, int indexOfEfids) {
        SubjectIndexOfAdn index = getSubjectIndex(type, recId);
        if (index == null) {
            return false;
        }

        if (index.type[indexOfEfids] == USIM_TYPE2_TAG
                && index.recordNumInIap.size() > 0) {
            return true;
        }

        return false;
    }

    /**
     * Get iap record
     *
     * @param recId pbr number
     * @return iap record
     */
    public ArrayList<byte[]> getIapFileRecord(int recId) {
        int efid = findEFInfo(recId, USIM_EFIAP_TAG);
        if (efid < 0) {
            return null;
        }

        return (ArrayList<byte[]>) mIapFileRecordArray[recId];
    }

    /**
     * Get unused record number
     *
     * @param type type of email/anr/sne
     * @param recId pbr number
     * @param efid ef id
     * @param index index of ef
     * @param adnNum adn record number
     * @param isInIap if ef is type2
     * @return unused record number
     */
    public int getNewSubjectNumber(int type, int recId, int efid, int index,
            int adnNum, boolean isInIap) {
        SubjectIndexOfAdn idx = getSubjectIndex(type, recId);
        if (idx == null || idx.record == null || !idx.record.containsKey(efid)) {
            return -1;
        }

        int newSubjectNum = -1;
        int count = idx.record.get(efid).size();

        if (isInIap) {
            Set<Integer> set = (Set<Integer>) idx.usedSet[index];
            for (int i = 1; i <= count; i++) {
                Integer subjectNum = new Integer(i);
                if (!set.contains(subjectNum)) {
                    newSubjectNum = subjectNum;
                    set.add(subjectNum);
                    idx.usedSet[index] = set;
                    setSubjectIndex(type, recId, idx);
                    break;
                }
            }
        } else {
            if (adnNum <= count) {
                return adnNum;
            }
        }
        return newSubjectNum;
    }

    /**
     * Remove unused record number from usedSet
     *
     * @param type type of email/anr/sne
     * @param recId pbr number
     * @param efid ef id
     * @param index index of ef
     * @param recordNum record number to be removed
     */
    public void removeSubjectNumFromSet(int type, int recId, int efid, int index,
            int recordNum) {
        SubjectIndexOfAdn subject = getSubjectIndex(type, recId);

        if (subject == null) {
            return;
        }

        Integer delNum = new Integer(recordNum);
        Set<Integer> set = (Set<Integer>) subject.usedSet[index];
        set.remove(delNum);

        subject.usedSet[index] = set;
        setSubjectIndex(type, recId, subject);
    }

    /**
     * Update iap record
     *
     * @param recId pbr number
     * @param index index of ef
     * @param value iap record value to be updated
     * @param numInIap index in iap record
     */
    public void setIapFileRecord(int recId, int index, byte value, int numInIap) {
        ArrayList<byte[]> tmpIapFileRecord = (ArrayList<byte[]>) mIapFileRecordArray[recId];
        byte[] record = tmpIapFileRecord.get(index);
        byte[] temp = new byte[record.length];
        for (int i = 0; i < temp.length; i++) {
            temp[i] = record[i];
        }

        temp[numInIap] = value;
        tmpIapFileRecord.set(index, temp);
        mIapFileRecordArray[recId] = tmpIapFileRecord;
    }

    // Update adn record number and grp info
    private void updateAdnRecordNum() {
        for (int i = 0; i < mPhoneBookRecords.size(); i++) {
            AdnRecord adn = mPhoneBookRecords.get(i);
            if (adn == null) {
                continue;
            }
            adn.setRecordNumber(i + 1);

            if (mGrpFileRecord != null && i < mGrpFileRecord.size()) {
                adn.setGrp(getGrp(i));
            }
        }
    }

    // Get grp string
    private String getGrp(int adnNum) {
        if (mGrpFileRecord == null) {
            return null;
        }

        String retGrp = null;
        byte[] grpData = mGrpFileRecord.get(adnNum);
        mGrpLength = grpData.length;

        for (int i = 0; i < mGrpLength; i++) {
            int temp = grpData[i] & 0xFF;
            if (temp == 0 || temp == 0xFF) {
                continue; // 0X'00' -- no group indicated;
            }
            if (retGrp == null) {
                retGrp = Integer.toString(temp);
            } else {
                retGrp = retGrp + AdnRecord.ANR_SPLIT_FLG + temp;
            }
        }
        return retGrp;
    }

    /**
     * Get pbr record size
     *
     * @return pbr record size
     */
    public int getNumRecs() {
        synchronized (mLock) {
            if (mPbrRecords == null || mPbrRecords.size() == 0) {
                readPbrFileAndWait();
            }
            if (mPbrRecords == null || mPbrRecords.size() == 0) {
                loge("Error: Pbr file is empty");
                return 0;
            }
        }
        return mPbrRecords.size();
    }

    /**
     * Get efid of file
     *
     * @param recId pbr number
     * @param fileType file type
     * @return efid
     */
    public int findEFInfo(int recId, int fileType) {
        loge("findEFInfo, recId: " + recId);
        synchronized (mLock) {
            if (mPbrRecords == null || mPbrRecords.size() == 0) {
                readPbrFileAndWait();
            }
        }

        if (mPbrRecords == null || mPbrRecords.size() == 0) {
            loge("Error: Pbr file is empty");
            return -1;
        }

        SparseArray<File> files = null;
        try {
            files = mPbrRecords.get(recId).mFileIds;
        } catch (IndexOutOfBoundsException e) {
            loge("findEFInfo, IndexOutOfBoundsException");
            return -1;
        }

        if (files == null || files.size() == 0 || files.get(fileType) == null) {
            return -1;
        }

        return files.get(fileType).getEfid();
    }

    /**
     * Get adn record size
     *
     * @return adn record size
     */
    public int[] getAdnRecordSizeArray() {
        return mAdnRecordSizeArray;
    }

    /**
     * Get record size
     *
     * @return HashMap<Integer, int[]>
     *     Integer is efid
     *     int[] is record size
     */
    public HashMap<Integer, int[]> getRecordsSize() {
        return mRecordsSize;
    }

    /**
     * Get EF_ADN record size
     *
     * @return  int[3] array
     *            recordSizes[0]  is the single record length
     *            recordSizes[1]  is the total length of the EF file
     *            recordSizes[2]  is the number of records in the EF file
     */
    public synchronized int[] getAdnRecordsSize() {
        log("getAdnRecordsSize");
        synchronized (mLock) {
            if (mAdnRecordSize != null) {
                return mAdnRecordSize;
            }

            if (mPbrRecords == null || mPbrRecords.size() == 0) {
                readPbrFileAndWait();
            }
            if (mPbrRecords == null || mPbrRecords.size() == 0) {
                return null;
            }

            int numRecs = mPbrRecords.size();
            mAdnRecordSize = new int[3];

            for (int i = 0; i < numRecs; i++) {
                int[] size = readAdnFileSizeAndWait(i);
                if (size != null) {
                    if (mAdnRecordSize != null) {
                        mAdnRecordSize[0] = size[0];
                        mAdnRecordSize[1] += size[1];
                        mAdnRecordSize[2] += size[2];
                    } else {
                        return null;
                    }
                }
            }
        }
        return mAdnRecordSize;
    }

    /**
     * Update AdnRecord list
     *
     * @param index adn record index
     * @param adn adn record
     */
    public void setPhoneBookRecords(int index, AdnRecord adn) {
        mPhoneBookRecords.set(index, adn);
    }

    /**
     * Get AdnRecord size
     *
     * @return AdnRecord size
     */
    public int getPhoneBookRecordsNum() {
        return mPhoneBookRecords.size();
    }

    /**
     * Get whether PBR file exists
     *
     * @return true for PBR file existence
     */
    public boolean isPbrFileExisting() {
        return mIsPbrFileExisting;
    }

    /**
     * Get whether ADN file exists
     *
     * @return true for ADN file existence
     */
    public boolean isContainAdnInPbr() {
        return mIsContainAdnInPbr;
    }

    /**
     * Converts a byte array into a integer value.
     *
     * @param data an array of bytes
     *
     * @return integer value of bytes array
     */
    public static int bytesToInt(byte[] data) {
        if (data == null) {
            return -1;
        }
        int value = 0;
        for (int i = 0; i < data.length; i++) {
            value |= (data[i] & 0xFF) << ((data.length - i - 1) * 8);
        }
        return value;
    }

    /**
     * Converts  a integer value into a byte array.
     *
     * @param integer value ,the length of bytes
     *
     * @return bytes array
     */
    private byte[] intToBytes(int value, int len) {
        byte[] data = new byte[len];

        for (int i = 0; i < len; i++) {
            data[i] = (byte) ((value >> ((len - i - 1) * 8)) & 0xFF);
        }
        return data;
    }

    private void updatePbcAndCc() {
        log("update EFpbc begin");
        SparseArray<File> files = null;
        if (mPbrRecords == null) {
            return;
        }

        try {
            files = mPbrRecords.get(0).mFileIds;
        } catch (IndexOutOfBoundsException e) {
            loge("updatePbcAndCc, IndexOutOfBoundsException");
            return;
        }

        if (files == null || files.size() == 0 || files.get(USIM_EFPBC_TAG) == null) {
            return;
        }

        int efPbcId = files.get(USIM_EFPBC_TAG).getEfid();

        int[] size;
        if (mRecordsSize != null && mRecordsSize.containsKey(efPbcId)) {
            size = mRecordsSize.get(efPbcId);
        } else {
            size = readFileSizeAndWait(efPbcId);
        }

        if (size == null || size.length < 3) {
            return;
        }

        ArrayList<byte[]> fileRecord = initArraylist(size[0], size[2]);

        if (mPbcFileRecord == null) {
            mPbcFileRecord = new ArrayList<byte[]>();
        }
        mPbcFileRecord.addAll(0, fileRecord);

        for (int i = 0; (i < mPbcFileRecord.size() && i < mPhoneBookRecords.size()); i++) {
            if (!TextUtils.isEmpty(mPhoneBookRecords.get(i).getAlphaTag())
                    || !TextUtils.isEmpty(mPhoneBookRecords.get(i).getNumber())) {
                mPendingPbcLoads.addAndGet(1);
                mFh.loadEFLinearFixed(efPbcId, i + 1, size[0],
                        obtainMessage(EVENT_LOAD_EF_PBC_RECORD_DONE, i, 0));
            }
        }
        if (mPendingPbcLoads.get() == 0) {
            mIsNotify.set(false);
        } else {
            mIsNotify.set(true);
            try {
                mLock.wait();
            } catch (InterruptedException e) {
                loge("Interrupted Exception in updatePbcAndCc");
            }
        }

        int changeCounter = 0;
        for (int i = 0; i < size[2]; i++) {
            byte[] temp = null;
            if (mPbcFileRecord != null) {
                temp = mPbcFileRecord.get(i);
            } else {
                return;
            }
            if (temp != null && temp.length > 0 && ((temp[0] & 0xFF) == 0x01)) {
                changeCounter++;
                byte[] data = new byte[2];
                data[0] = (byte) 0x00;
                data[1] = (byte) 0x00;
                // udpate EF pbc
                mFh.updateEFLinearFixed(efPbcId, i + 1, data, null,
                        obtainMessage(EVENT_UPDATE_RECORD_DONE));
            }
        }
        // update EFcc
        if (changeCounter > 0) {
            // get Change Counter
            mFh.loadEFTransparent(IccConstants.EF_CC, 2,
                    obtainMessage(EVENT_EF_CC_LOAD_DONE, changeCounter, 0));
        }
        return;
    }

    /**
     * Update uid for adn.
     *
     * @param recId pbr record index
     */
    public void updateUidForAdn(int recId) {
        if (findEFInfo(recId, USIM_EFUID_TAG) <= 0) {
            return;
        }

        mFh.loadEFTransparent(IccConstants.EF_CC, 2,
                obtainMessage(EVENT_EF_CC_LOAD_DONE, 1, 1));
    }
}