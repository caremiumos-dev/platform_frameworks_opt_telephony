/*
 * Copyright (C) 2006 The Android Open Source Project
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

import android.annotation.UnsupportedAppUsage;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.telephony.Rlog;

import com.android.internal.telephony.uicc.IccConstants;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class AdnRecordLoader extends Handler {
    final static String LOG_TAG = "AdnRecordLoader";
    final static boolean VDBG = false;

    //***** Instance Variables

    @UnsupportedAppUsage
    private IccFileHandler mFh;
    int mEf;
    int mExtensionEF;
    int mPendingExtLoads;
    Message mUserResponse;
    String mPin2;
    int mAdnNum;
    int[] mRecordSize;
    Integer mSneEfid;
    Integer mSneNum;
    Integer mSneEfIndex;

    // For "load one"
    int mRecordNumber;

    // for "load all"
    ArrayList<AdnRecord> mAdns; // only valid after EVENT_ADN_LOAD_ALL_DONE

    // Either an AdnRecord or a reference to adns depending
    // if this is a load one or load all operation
    Object mResult;

    private final Object mLock = new Object();

    //***** Event Constants

    static final int EVENT_ADN_LOAD_DONE = 1;
    static final int EVENT_EXT_RECORD_LOAD_DONE = 2;
    static final int EVENT_ADN_LOAD_ALL_DONE = 3;
    static final int EVENT_EF_LINEAR_RECORD_SIZE_DONE = 4;
    static final int EVENT_UPDATE_RECORD_DONE = 5;
    static final int EVENT_UPDATE_EXT_RECORD_DONE = 6;
    static final int EVENT_EXT_LOAD_ALL_DONE = 7;
    static final int EVENT_EF_PBR_SNE_LINEAR_RECORD_SIZE_DONE = 8;
    static final int EVENT_UPDATE_ANR_RECORD_DONE = 9;
    static final int EVENT_UPDATE_SNE_RECORD_DONE = 10;

    //***** Constructor

    @UnsupportedAppUsage
    AdnRecordLoader(IccFileHandler fh) {
        // The telephony unit-test cases may create AdnRecords
        // in secondary threads
        super(Looper.getMainLooper());
        mFh = fh;
    }

    @UnsupportedAppUsage
    private String getEFPath(int efid) {
        if (efid == IccConstants.EF_ADN) {
            return IccConstants.MF_SIM + IccConstants.DF_TELECOM;
        }

        return null;
    }

    /**
     * Resulting AdnRecord is placed in response.obj.result
     * or response.obj.exception is set
     */
    @UnsupportedAppUsage
    public void
    loadFromEF(int ef, int extensionEF, int recordNumber,
                Message response) {
        mEf = ef;
        mExtensionEF = extensionEF;
        mRecordNumber = recordNumber;
        mUserResponse = response;

       mFh.loadEFLinearFixed(
               ef, getEFPath(ef), recordNumber,
               obtainMessage(EVENT_ADN_LOAD_DONE));
    }


    /**
     * Resulting ArrayList&lt;adnRecord> is placed in response.obj.result
     * or response.obj.exception is set
     */
    public void
    loadAllFromEF(int ef, int extensionEF,
                Message response) {
        mEf = ef;
        mExtensionEF = extensionEF;
        mUserResponse = response;

        /* If we are loading from EF_ADN, specifically
         * specify the path as well, since, on some cards,
         * the fileid is not unique.
         */
        mFh.loadEFLinearFixedAll(
                ef, getEFPath(ef),
                obtainMessage(EVENT_ADN_LOAD_ALL_DONE));
    }

    /**
     * Resulting ArrayList<byte[]> is placed in response.obj.result
     * or response.obj.exception is set
     */
    public void loadAllExtFromEF(int ef, int extensionEF,
                     Message response) {
        mEf = ef;
        mExtensionEF = extensionEF;
        mUserResponse = response;
        mFh.loadEFLinearFixedAll(
                extensionEF, getEFPath(extensionEF),
                obtainMessage(EVENT_EXT_LOAD_ALL_DONE));
    }

    /**
     * Write adn to a EF SIM record
     * It will get the record size of EF record and compose hex adn array
     * then write the hex array to EF record
     *
     * @param adn is set with alphaTag and phone number
     * @param ef EF fileid
     * @param extensionEF extension EF fileid
     * @param recordNumber 1-based record index
     * @param pin2 for CHV2 operations, must be null if pin2 is not needed
     * @param response will be sent to its handler when completed
     */
    @UnsupportedAppUsage
    public void
    updateEF(AdnRecord adn, int ef, int extensionEF, int recordNumber,
            String pin2, Message response) {
        mEf = ef;
        mExtensionEF = extensionEF;
        mRecordNumber = recordNumber;
        mUserResponse = response;
        mPin2 = pin2;
 
        mFh.getEFLinearRecordSize( ef, getEFPath(ef),
                obtainMessage(EVENT_EF_LINEAR_RECORD_SIZE_DONE, adn));
     }

    /**
     * Write adn extension to a EF SIM record
     * It will compose hex ext array, then write the hex array to EF record
     *
     * @param adn is set with alphaTag and phone number
     * @param ef EF fileid
     * @param extensionEF extension EF fileid
     * @param recordNumber 1-based record index
     * @param pin2 for CHV2 operations, must be null if pin2 is not needed
     * @param response will be sent to its handler when completed
     */
    @UnsupportedAppUsage
    public void updateExtEF(AdnRecord adn, int ef, int extensionEF, int recordNumber,
                String pin2, Message response) {
        mEf = ef;
        mExtensionEF = extensionEF;
        mRecordNumber = recordNumber;
        mUserResponse = response;
        mPin2 = pin2;
        byte[] extData = adn.buildExtString();
        mPendingExtLoads = 1;

        mFh.updateEFLinearFixed(extensionEF, recordNumber,
                extData, pin2, obtainMessage(EVENT_UPDATE_EXT_RECORD_DONE,
                        extensionEF, recordNumber, extData));
    }

    /**
     * Write iap to a EF SIM record
     * It will compose hex iap array, then write the hex array to EF record
     *
     * @param adn is set with alphaTag and phone number
     * @param iapEF EF_IAP fileid
     * @param adnNum adn record index
     * @param record record will be written to EF_IAP
     * @param pin2 for CHV2 operations, must be null if pin2 is not needed
     * @param response will be sent to its handler when completed
     */
    public void updateEFIapToUsim(AdnRecord adn, int iapEF, int adnNum,
                                  byte[] record, String pin2, Message response) {
        byte[] data;
        mUserResponse = response;
        mPin2 = pin2;
        data = record;

        mFh.updateEFLinearFixed(iapEF, adnNum, data, pin2,
                obtainMessage(EVENT_UPDATE_RECORD_DONE));
        mPendingExtLoads = 1;
    }

    /**
     * Write email to a EF SIM record
     * It will compose hex iap array, then write the hex array to EF record
     *
     * @param adn is set with alphaTag and phone number
     * @param emailEfid EF_EMAIL fileId
     * @param emailNum email record index
     * @param efid  EF_ADN fileId
     * @param adnNum adn record index
     * @param emailEfIndex email file index
     * @param pin2 for CHV2 operations, must be null if pin2 is not needed
     * @param response will be sent to its handler when completed
     * @param size email record size
     */
    public void updateEFEmailToUsim(AdnRecord adn, Integer emailEfid,
                                    Integer emailNum, int efid, int adnNum, Integer emailEfIndex,
                                    String pin2, Message response, int[] size) {
        mUserResponse = response;

        if (size == null) {
            throw new RuntimeException("EMAIL record size is null");
        }

        if (emailEfid != 0) {
            byte[] data = adn.buildEmailString(size[0], emailEfIndex, mEf, adnNum);
            if (data == null) {
                throw new RuntimeException("wrong EMAIL format");
            }
            mFh.updateEFLinearFixed(emailEfid, emailNum, data,
                    pin2, obtainMessage(EVENT_UPDATE_RECORD_DONE));
        }
        mPendingExtLoads = 1;
    }

    /**
     * Write sne to a EF SIM record
     * It will compose hex iap array, then write the hex array to EF record
     *
     * @param adn is set with alphaTag and phone number
     * @param sneEfid EF_SNE fileId
     * @param sneNum sne record index
     * @param efid  EF_ADN fileId
     * @param adnNum adn record index
     * @param sneEfIndex email file index
     * @param pin2 for CHV2 operations, must be null if pin2 is not needed
     * @param response will be sent to its handler when completed
     */
    public void updateEFSneToUsim(AdnRecord adn, Integer sneEfid,
                                  Integer sneNum, int efid, int adnNum,
                                  Integer sneEfIndex, String pin2, Message response) {
        mSneEfid = sneEfid;
        mSneNum = sneNum;
        mSneEfIndex = sneEfIndex;
        mEf = efid;
        mAdnNum = adnNum;
        mUserResponse = response;
        mPin2 = pin2;
        mFh.getEFLinearRecordSize(sneEfid, obtainMessage(
                EVENT_EF_PBR_SNE_LINEAR_RECORD_SIZE_DONE, adn));
    }

    /**
     * Write anr to a EF SIM record
     * It will compose hex iap array, then write the hex array to EF record
     *
     * @param adn is set with alphaTag and phone number
     * @param anrefid EF_ANR fileId
     * @param efid  EF_ADN fileId
     * @param adnNum adn record index
     * @param anrNum anr record index
     * @param anrEfIndex anr file index
     * @param pin2 for CHV2 operations, must be null if pin2 is not needed
     * @param response will be sent to its handler when completed
     * @param size email record size
     * @param aasIndex aas record index
     */
    public void updateEFAnrToUsim(AdnRecord adn, Integer anrefid,
                                  int efid, int adnNum, Integer anrNum,  Integer anrEfIndex,
                                  String pin2, Message response, int[] size, int aasIndex) {
        mUserResponse = response;

        if (size == null) {
            throw new RuntimeException("ANR record size is null");
        }

        if (anrefid != 0) {
            byte[] data = adn.buildAnrString(size[0], anrEfIndex, mEf,
                    adnNum, aasIndex);
            if (data == null) {
                throw new RuntimeException("wrong ANR format");
            }
            mFh.updateEFLinearFixed(anrefid,
                    anrNum, data, pin2,
                    obtainMessage(EVENT_UPDATE_ANR_RECORD_DONE));
        }
        mPendingExtLoads = 1;
    }

    /**
     * Write grp to a EF SIM record
     * It will compose hex iap array, then write the hex array to EF record
     *
     * @param grpEfId  EF_GRP fileId
     * @param recordNum adn record index
     * @param data grp record
     * @param pin2 for CHV2 operations, must be null if pin2 is not needed
     */
    public void updateEFGrpToUsim(int grpEfId, int recordNum, byte[] data, String pin2) {
        mFh.updateEFLinearFixed(grpEfId, recordNum, data, pin2,
                obtainMessage(EVENT_UPDATE_RECORD_DONE));
    }

    /**
     * Write grp to a EF SIM record
     * It will compose hex iap array, then write the hex array to EF record
     *
     * @param gasEfId  EF_GAS fileId
     * @param recordNum adn record index
     * @param data gas record
     * @param pin2 for CHV2 operations, must be null if pin2 is not needed
     */
    public void updateEFGasToUsim(int gasEfId, int recordNum, byte[] data, String pin2) {
        mFh.updateEFLinearFixed(gasEfId, recordNum, data, pin2,
                obtainMessage(EVENT_UPDATE_RECORD_DONE));
    }

    /**
     * Write adn to a EF SIM record
     * It will get the record size of EF record and compose hex adn array
     * then write the hex array to EF record
     *
     * @param adn is set with alphaTag and phone number
     * @param ef EF_ADN fileid
     * @param extensionEF extension EF fileid
     * @param recordNumber 1-based record index
     * @param pin2 for CHV2 operations, must be null if pin2 is not needed
     * @param response will be sent to its handler when completed
     * @param size adn record size
     */
    public void updateEFAdnToUsim(AdnRecord adn, int ef, int extensionEF,
                                  int recordNumber, String pin2, Message response, int[] size) {
        mUserResponse = response;
        if (size == null) {
            throw new RuntimeException("ADN record size is null");
        }

        byte[] data = adn.buildAdnString(size[0]);
        if (data == null) {
            throw new RuntimeException("wrong ADN format" + adn.getErrorCode());
        }

        mFh.updateEFLinearFixed(ef, recordNumber,
                data, pin2, obtainMessage(EVENT_UPDATE_RECORD_DONE));
        mPendingExtLoads = 1;
    }


    //***** Overridden from Handler

    @Override
    public void
    handleMessage(Message msg) {
        AsyncResult ar;
        byte data[];
        AdnRecord adn;

        try {
            switch (msg.what) {
                case EVENT_EF_LINEAR_RECORD_SIZE_DONE:
                    ar = (AsyncResult)(msg.obj);
                    adn = (AdnRecord)(ar.userObj);

                    if (ar.exception != null) {
                        throw new RuntimeException("get EF record size failed",
                                ar.exception);
                    }

                    int[] recordSize = (int[])ar.result;
                    // recordSize is int[3] array
                    // int[0]  is the record length
                    // int[1]  is the total length of the EF file
                    // int[2]  is the number of records in the EF file
                    // So int[0] * int[2] = int[1]
                   if (recordSize.length != 3 || mRecordNumber > recordSize[2]) {
                        throw new RuntimeException("get wrong EF record size format",
                                ar.exception);
                    }

                    data = adn.buildAdnString(recordSize[0]);

                    if(data == null) {
                        throw new RuntimeException("wrong ADN format",
                                ar.exception);
                    }


                    mFh.updateEFLinearFixed(mEf, getEFPath(mEf), mRecordNumber,
                            data, mPin2, obtainMessage(EVENT_UPDATE_RECORD_DONE));

                    mPendingExtLoads = 1;

                    break;
                case EVENT_UPDATE_RECORD_DONE:
                    ar = (AsyncResult)(msg.obj);
                    if (ar.exception != null) {
                        throw new RuntimeException("update EF adn record failed",
                                ar.exception);
                    }
                    mPendingExtLoads = 0;
                    mResult = null;
                    break;
                case EVENT_ADN_LOAD_DONE:
                    ar = (AsyncResult)(msg.obj);
                    data = (byte[]) (ar.result);

                    if (ar.exception != null) {
                        throw new RuntimeException("load failed", ar.exception);
                    }

                    if (VDBG) {
                        Rlog.d(LOG_TAG,"ADN EF: 0x"
                            + Integer.toHexString(mEf)
                            + ":" + mRecordNumber
                            + "\n" + IccUtils.bytesToHexString(data));
                    }

                    adn = new AdnRecord(mEf, mRecordNumber, data);
                    mResult = adn;

                    if (adn.hasExtendedRecord()) {
                        // If we have a valid value in the ext record field,
                        // we're not done yet: we need to read the corresponding
                        // ext record and append it

                        mPendingExtLoads = 1;

                        mFh.loadEFLinearFixed(
                            mExtensionEF, adn.mExtRecord,
                            obtainMessage(EVENT_EXT_RECORD_LOAD_DONE, adn));
                    }
                break;

                case EVENT_EXT_RECORD_LOAD_DONE:
                    ar = (AsyncResult)(msg.obj);
                    data = (byte[])(ar.result);
                    adn = (AdnRecord)(ar.userObj);

                    if (ar.exception == null) {
                        Rlog.d(LOG_TAG,"ADN extension EF: 0x"
                                + Integer.toHexString(mExtensionEF)
                                + ":" + adn.mExtRecord
                                + "\n" + IccUtils.bytesToHexString(data));

                        adn.appendExtRecord(data);
                    }
                    else {
                        // If we can't get the rest of the number from EF_EXT1, rather than
                        // providing the partial number, we clear the number since it's not
                        // dialable anyway. Do not throw exception here otherwise the rest
                        // of the good records will be dropped.

                        Rlog.e(LOG_TAG, "Failed to read ext record. Clear the number now.");
                        adn.setNumber("");
                    }

                    mPendingExtLoads--;
                    // result should have been set in
                    // EVENT_ADN_LOAD_DONE or EVENT_ADN_LOAD_ALL_DONE
                break;

                case EVENT_ADN_LOAD_ALL_DONE:
                    ar = (AsyncResult)(msg.obj);
                    ArrayList<byte[]> datas = (ArrayList<byte[]>)(ar.result);

                    if (ar.exception != null) {
                        throw new RuntimeException("load failed", ar.exception);
                    }

                    mAdns = new ArrayList<AdnRecord>(datas.size());
                    mResult = mAdns;
                    mPendingExtLoads = 0;

                    for(int i = 0, s = datas.size() ; i < s ; i++) {
                        adn = new AdnRecord(mEf, 1 + i, datas.get(i));
                        mAdns.add(adn);

                        if (adn.hasExtendedRecord()) {
                            // If we have a valid value in the ext record field,
                            // we're not done yet: we need to read the corresponding
                            // ext record and append it

                            mPendingExtLoads++;

                            mFh.loadEFLinearFixed(
                                mExtensionEF, adn.mExtRecord,
                                obtainMessage(EVENT_EXT_RECORD_LOAD_DONE, adn));
                        }
                    }
                break;

                case EVENT_EXT_LOAD_ALL_DONE:
                    ar = (AsyncResult) (msg.obj);
                    if (ar.exception != null) {
                        throw new RuntimeException("load all ext failed", ar.exception);
                    }

                    ArrayList<byte[]> extDatas = (ArrayList<byte[]>) (ar.result);
                    mResult = extDatas;
                    break;

                case EVENT_UPDATE_EXT_RECORD_DONE:
                    ar = (AsyncResult) (msg.obj);
                    mExtensionEF = msg.arg1;
                    if (ar.exception != null) {
                        throw new RuntimeException("update EF ext record failed", ar.exception);
                    }
                    mResult = ar.userObj;
                    mPendingExtLoads = 0;
                    break;

                case EVENT_EF_PBR_SNE_LINEAR_RECORD_SIZE_DONE:
                    ar = (AsyncResult) (msg.obj);
                    adn = (AdnRecord) (ar.userObj);
                    if (ar.exception != null) {
                        AsyncResult.forMessage(mUserResponse)
                            .exception = ar.exception;
                        mUserResponse.sendToTarget();
                        mUserResponse = null;
                        return;
                    }
                    // recordSize is int[3] array
                    //     int[0] is the record length
                    //     int[1] is the total length of the EF file
                    //     int[2] is the number of records in the EF file
                    recordSize = (int[]) ar.result;

                    if (mSneEfid != 0) {
                        data = adn.buildSneString(recordSize[0], mSneEfIndex, mEf, mAdnNum);
                        if (data == null) {
                            throw new RuntimeException("wrong SNE format");
                        }
                        mFh.updateEFLinearFixed(mSneEfid, mSneNum, data,
                                mPin2, obtainMessage(EVENT_UPDATE_SNE_RECORD_DONE));
                    }
                    mPendingExtLoads = 1;
                    break;

                case EVENT_UPDATE_SNE_RECORD_DONE:
                    ar = (AsyncResult) (msg.obj);
                    if (ar.exception != null) {
                        AsyncResult.forMessage(mUserResponse)
                                .exception = ar.exception;
                        mUserResponse.sendToTarget();
                        mUserResponse = null;
                        return;
                    }
                    mPendingExtLoads = 0;
                    break;

                case EVENT_UPDATE_ANR_RECORD_DONE:
                    ar = (AsyncResult) (msg.obj);
                    if (ar.exception != null) {
                        AsyncResult.forMessage(mUserResponse)
                                .exception = ar.exception;
                        mUserResponse.sendToTarget();
                        mUserResponse = null;
                        return;
                    }

                    mPendingExtLoads = 0;
                    mResult = null;
                    break;

            }
        } catch (RuntimeException exc) {
            if (mUserResponse != null) {
                AsyncResult.forMessage(mUserResponse)
                                .exception = exc;
                mUserResponse.sendToTarget();
                // Loading is all or nothing--either every load succeeds
                // or we fail the whole thing.
                mUserResponse = null;
            }
            return;
        }

        if (mUserResponse != null && mPendingExtLoads == 0) {
            AsyncResult.forMessage(mUserResponse).result
                = mResult;

            mUserResponse.sendToTarget();
            mUserResponse = null;
        }
    }

    protected void waitForResult(AtomicBoolean status) {
        while (!status.get()) {
            try {
                mLock.wait();
            } catch (InterruptedException e) {
                Rlog.e(LOG_TAG, "interrupted while trying to update by search");
            }
        }
    }

    private void notifyPending(AsyncResult ar) {
        if (ar.userObj == null) {
            return;
        }

        AtomicBoolean status = (AtomicBoolean) ar.userObj;
        status.set(true);
        mLock.notifyAll();
    }
}
