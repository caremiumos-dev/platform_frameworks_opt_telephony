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
import android.os.Message;
import android.telephony.Rlog;
import android.text.TextUtils;
import android.util.SparseArray;

import com.android.internal.telephony.GsmAlphabet;
import com.android.internal.telephony.gsm.UsimPhoneBookManager;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@hide}
 */
public class AdnRecordCache extends Handler implements IccConstants {
    //***** Instance Variables
    private static final String TAG = "AdnRecordCache";
    private static final boolean DBG = true;

    @UnsupportedAppUsage
    private IccFileHandler mFh;
    @UnsupportedAppUsage
    private UsimPhoneBookManager mUsimPhoneBookManager;

    // The updated or inserted adn record index
    public int mInsertId = -1;

    // Indexed by EF ID
    SparseArray<ArrayList<AdnRecord>> mAdnLikeFiles
        = new SparseArray<ArrayList<AdnRecord>>();
    SparseArray<ArrayList<byte[]>> mExtLikeFiles =
            new SparseArray<ArrayList<byte[]>>();

    // People waiting for ADN-like files to be loaded
    @UnsupportedAppUsage
    SparseArray<ArrayList<Message>> mAdnLikeWaiters
        = new SparseArray<ArrayList<Message>>();

    // People waiting for adn record to be updated
    @UnsupportedAppUsage
    SparseArray<Message> mUserWriteResponse = new SparseArray<Message>();

    //***** Event Constants

    static final int EVENT_LOAD_ALL_ADN_LIKE_DONE = 1;
    static final int EVENT_UPDATE_ADN_DONE = 2;
    static final int EVENT_UPDATE_EXT_DONE = 3;
    static final int EVENT_UPDATE_USIM_ADN_DONE = 4;
    static final int EVENT_LOAD_ALL_EXT_LIKE_DONE = 5;
    //***** Constructor



    AdnRecordCache(IccFileHandler fh) {
        mFh = fh;
        mUsimPhoneBookManager = new UsimPhoneBookManager(mFh, this);
    }

    //***** Called from SIMRecords

    /**
     * Called from SIMRecords.onRadioNotAvailable and SIMRecords.handleSimRefresh.
     */
    @UnsupportedAppUsage
    public void reset() {
        mAdnLikeFiles.clear();
        mExtLikeFiles.clear();
        mUsimPhoneBookManager.reset();

        clearWaiters();
        clearUserWriters();

    }

    private void clearWaiters() {
        int size = mAdnLikeWaiters.size();
        for (int i = 0; i < size; i++) {
            ArrayList<Message> waiters = mAdnLikeWaiters.valueAt(i);
            AsyncResult ar = new AsyncResult(null, null, new RuntimeException("AdnCache reset"));
            notifyWaiters(waiters, ar);
        }
        mAdnLikeWaiters.clear();
    }

    private void clearUserWriters() {
        int size = mUserWriteResponse.size();
        for (int i = 0; i < size; i++) {
            sendErrorResponse(mUserWriteResponse.valueAt(i), "AdnCace reset");
        }
        mUserWriteResponse.clear();
    }

    /**
     * @return List of AdnRecords for efid if we've already loaded them this
     * radio session, or null if we haven't
     */
    @UnsupportedAppUsage
    public ArrayList<AdnRecord>
    getRecordsIfLoaded(int efid) {
        return mAdnLikeFiles.get(efid);
    }

    /**
     * Returns extension ef associated with ADN-like EF or -1 if
     * we don't know.
     *
     * See 3GPP TS 51.011 for this mapping
     */
    @UnsupportedAppUsage
    public int extensionEfForEf(int efid) {
        switch (efid) {
            case EF_MBDN: return EF_EXT6;
            case EF_ADN: return EF_EXT1;
            case EF_SDN: return EF_EXT3;
            case EF_FDN: return EF_EXT2;
            case EF_MSISDN: return EF_EXT1;
            case EF_PBR: return 0; // The EF PBR doesn't have an extension record
            default: return -1;
        }
    }

    @UnsupportedAppUsage
    private void sendErrorResponse(Message response, String errString) {
        if (response != null) {
            Exception e = new RuntimeException(errString);
            AsyncResult.forMessage(response).exception = e;
            response.sendToTarget();
        }
    }

    /**
     * Update an ADN-like record in EF by record index
     *
     * @param efid must be one among EF_ADN, EF_FDN, and EF_SDN
     * @param adn is the new adn to be stored
     * @param recordIndex is the 1-based adn record index
     * @param pin2 is required to update EF_FDN, otherwise must be null
     * @param response message to be posted when done
     *        response.exception hold the exception in error
     */
    @UnsupportedAppUsage
    public void updateAdnByIndex(int efid, AdnRecord newAdn, int recordIndex, String pin2,
            Message response) {

        int extensionEF = extensionEfForEf(efid);
        if (extensionEF < 0) {
            sendErrorResponse(response, "EF is not known ADN-like EF:0x" +
                    Integer.toHexString(efid).toUpperCase());
            return;
        }

        Message pendingResponse = mUserWriteResponse.get(efid);
        if (pendingResponse != null) {
            sendErrorResponse(response, "Have pending update for EF:0x" +
                    Integer.toHexString(efid).toUpperCase());
            return;
        }

        mInsertId = recordIndex;

        ArrayList<AdnRecord> oldAdnList = getRecordsIfLoaded(efid);
        if (oldAdnList == null) {
            sendErrorResponse(response,
                    "Adn list not exist for EF:0x" + Integer.toHexString(efid).toUpperCase());
            return;
        }

        mUserWriteResponse.put(efid, response);
        AdnRecord oldAdn = oldAdnList.get(recordIndex - 1);

        if (newAdn.extRecordIsNeeded()) {
            byte extIndex = (byte) 0xff;
            if (oldAdn.mExtRecord != 0xff) {
                extIndex = (byte) oldAdn.mExtRecord;
            } else {
                extIndex = getAvailableExtIndex(extensionEF, efid);
            }

            if (extIndex == (byte) 0xFF) {
                mUserWriteResponse.delete(efid);
                sendErrorResponse(response,
                        "ext list full");
                return;
            }

            newAdn.mExtRecord = (int) extIndex;
            new AdnRecordLoader(mFh).updateExtEF(newAdn, efid, extensionEF, extIndex,
                    pin2, obtainMessage(EVENT_UPDATE_EXT_DONE, extensionEF, extIndex));
        } else {
            if (oldAdn.mExtRecord != 0xff) {
                new AdnRecordLoader(mFh).updateExtEF(newAdn, efid, extensionEF, oldAdn.mExtRecord,
                        pin2, obtainMessage(EVENT_UPDATE_EXT_DONE, extensionEF, oldAdn.mExtRecord));
            }
        }
        oldAdn.mExtRecord = 0xff;

        new AdnRecordLoader(mFh).updateEF(newAdn, efid, extensionEF, recordIndex,
                pin2, obtainMessage(EVENT_UPDATE_ADN_DONE, efid, recordIndex, newAdn));
    }

    /**
     * Update an ADN-like record in EF by record index
     *
     * @param efid must be one among EF_ADN, EF_FDN, and EF_SDN
     * @param newAdn is the new adn to be stored
     * @param simIndex is the 1-based adn record index
     * @param pin2 is required to update EF_FDN, otherwise must be null
     * @param response message to be posted when done
     *        response.exception hold the exception in error
     */
    public synchronized void updateUSIMAdnByIndex(int efid, int simIndex,
                                                  AdnRecord newAdn, String pin2, Message response) {

        int extensionEF = 0;
        int adnIndex = -1;
        int iapEF = 0;
        int recNum = 0;
        AdnRecord oldAdn;

        int pbrRecNum = mUsimPhoneBookManager.getNumRecs();
        if (DBG) {
            log("updateUSIMAdnByIndex efid = 0x" + Integer.toHexString(efid).toUpperCase()
                    + ", RecsNum = " + pbrRecNum
                    + ", simIndex = " + simIndex);
        }

        if (simIndex < 0 || simIndex > mUsimPhoneBookManager.getPhoneBookRecordsNum()) {
            sendErrorResponse(response,
                    "the sim index is invalid");
            return;
        }

        efid = mUsimPhoneBookManager.findEFInfo(recNum, mUsimPhoneBookManager.USIM_EFADN_TAG);
        extensionEF = mUsimPhoneBookManager.findEFInfo(recNum,
                mUsimPhoneBookManager.USIM_EFEXT1_TAG);
        iapEF = mUsimPhoneBookManager.findEFInfo(recNum, mUsimPhoneBookManager.USIM_EFIAP_TAG);

        if (efid < 0 || extensionEF < 0) {
            sendErrorResponse(response,
                    "EF is not known ADN-like EF: efid:0x" + Integer.toHexString(efid).toUpperCase()
                            + ", extensionEF:0x" + Integer.toHexString(extensionEF).toUpperCase());
            return;
        }

        int baseNum = mUsimPhoneBookManager.getAdnRecordSizeArray()[0];
        for (int i = 0; i < pbrRecNum; i++) {
            if (simIndex <= baseNum) {
                recNum = i;
                baseNum -= mUsimPhoneBookManager.getAdnRecordSizeArray()[i];
                break;
            }
            baseNum += mUsimPhoneBookManager.getAdnRecordSizeArray()[i + 1];
        }
        adnIndex = simIndex - baseNum;
        mInsertId = simIndex;

        ArrayList<AdnRecord> oldAdnList = getRecordsIfLoaded(efid);
        if (oldAdnList == null) {
            sendErrorResponse(response,
                    "Adn list not exist for EF:0x" + Integer.toHexString(efid).toUpperCase());
            return;
        }
        oldAdn = oldAdnList.get(adnIndex - 1);


        if (!checkAnrLength(newAdn)) {
            sendErrorResponse(response, "Anr number too long");
            return;
        }

        Message pendingResponse = mUserWriteResponse.get(efid);
        if (pendingResponse != null) {
            sendErrorResponse(response,
                    "Have pending update for EF:0x" + Integer.toHexString(efid).toUpperCase());
            return;
        }
        mUserWriteResponse.put(efid, response);

        AdnRecordLoader adnRecordLoader = new AdnRecordLoader(mFh);
        int[] aasIndex = null;

        newAdn.mAas = newAdn.mAas == null ? "" : newAdn.mAas;
        ArrayList<String> aasArr = loadAasFromUsim();

        if (aasArr != null && newAdn.mAas != null) {
            String[] aas = newAdn.mAas.split(AdnRecord.ANR_SPLIT_FLG);
            aasIndex = new int[aas.length];
            for (int i = 0; i < aas.length; i++) {
                aasIndex[i] = aasArr.indexOf(aas[i].toString()) + 1;
            }
        }

        int updateAnrResult = updateSubjectOfAdn(UsimPhoneBookManager.USIM_SUBJCET_ANR, recNum,
                adnRecordLoader, mInsertId, adnIndex, efid, oldAdn, newAdn,
                iapEF, pin2, aasIndex);
        if (updateAnrResult < 0) {
            mUserWriteResponse.delete(efid);
            sendErrorResponse(response,
                    "Anr capacity full");
            return;
        }

        int updateEmailResult = updateSubjectOfAdn(UsimPhoneBookManager.USIM_SUBJCET_EMAIL, recNum,
                adnRecordLoader, mInsertId, adnIndex, efid, oldAdn, newAdn,
                iapEF, pin2, null);
        if (updateEmailResult < 0) {
            mUserWriteResponse.delete(efid);
            sendErrorResponse(response,
                    "Email capacity full");
            return;
        }

        int updateSneResult = updateSubjectOfAdn(UsimPhoneBookManager.USIM_SUBJCET_SNE, recNum,
                adnRecordLoader, mInsertId, adnIndex, efid, oldAdn,
                newAdn, iapEF, pin2, null);
        if (updateSneResult < 0) {
            mUserWriteResponse.delete(efid);
            sendErrorResponse(response,
                    "Sne capacity full");
            return;
        }

        updateGrpOfAdn(adnRecordLoader, adnIndex, recNum, oldAdn, newAdn, pin2, efid, response);

        if (newAdn.extRecordIsNeeded()) {
            byte extIndex = (byte) 0xff;
            if (oldAdn.mExtRecord != 0xff) {
                extIndex = (byte) oldAdn.mExtRecord;
            } else {
                extIndex = getAvailableExtIndex(extensionEF, efid);
            }

            if (extIndex == (byte) 0xFF) {
                mUserWriteResponse.delete(efid);
                sendErrorResponse(response,
                        "ext list full");
                return;
            }

            newAdn.mExtRecord = (int) extIndex;
            new AdnRecordLoader(mFh).updateExtEF(newAdn, efid, extensionEF, extIndex,
                    pin2, obtainMessage(EVENT_UPDATE_EXT_DONE, extensionEF, extIndex));
        } else {
            if (oldAdn.mExtRecord != 0xff) {
                new AdnRecordLoader(mFh).updateExtEF(newAdn, efid, extensionEF, oldAdn.mExtRecord,
                        pin2, obtainMessage(EVENT_UPDATE_EXT_DONE, extensionEF, oldAdn.mExtRecord));
            }
        }
        oldAdn.mExtRecord = 0xff;

        new AdnRecordLoader(mFh).updateEFAdnToUsim(newAdn, efid, extensionEF, adnIndex,
                pin2, obtainMessage(EVENT_UPDATE_USIM_ADN_DONE, efid, adnIndex, newAdn),
                getRecordsSizeByEf(efid));
        if (DBG) log("updateUSIMAdnByIndex  finish");
    }

    /**
     * Replace oldAdn with newAdn in ADN-like record in EF
     *
     * The ADN-like records must be read through requestLoadAllAdnLike() before
     *
     * @param efid must be one of EF_ADN, EF_FDN, and EF_SDN
     * @param oldAdn is the adn to be replaced
     *        If oldAdn.isEmpty() is ture, it insert the newAdn
     * @param newAdn is the adn to be stored
     *        If newAdn.isEmpty() is true, it delete the oldAdn
     * @param pin2 is required to update EF_FDN, otherwise must be null
     * @param response message to be posted when done
     *        response.exception hold the exception in error
     */
    public void updateAdnBySearch(int efid, AdnRecord oldAdn, AdnRecord newAdn,
            String pin2, Message response) {

        int extensionEF;
        extensionEF = extensionEfForEf(efid);

        if (extensionEF < 0) {
            sendErrorResponse(response, "EF is not known ADN-like EF:0x" +
                    Integer.toHexString(efid).toUpperCase());
            return;
        }

        ArrayList<AdnRecord>  oldAdnList;

        if (efid == EF_PBR) {
            oldAdnList = mUsimPhoneBookManager.loadEfFilesFromUsim();
        } else {
            oldAdnList = getRecordsIfLoaded(efid);
        }

        if (oldAdnList == null) {
            sendErrorResponse(response, "Adn list not exist for EF:0x" +
                    Integer.toHexString(efid).toUpperCase());
            return;
        }

        int index = -1;
        int count = 1;
        for (Iterator<AdnRecord> it = oldAdnList.iterator(); it.hasNext(); ) {
            if (oldAdn.isEqual(it.next())) {
                index = count;
                break;
            }
            count++;
        }

        if (index == -1) {
            sendErrorResponse(response, "Adn record don't exist for " + oldAdn);
            return;
        }

        if (efid == EF_PBR) {
            AdnRecord foundAdn = oldAdnList.get(index - 1);
            efid = foundAdn.mEfid;
            extensionEF = foundAdn.mExtRecord;
            index = foundAdn.mRecordNumber;

            newAdn.mEfid = efid;
            newAdn.mExtRecord = extensionEF;
            newAdn.mRecordNumber = index;
        }

        Message pendingResponse = mUserWriteResponse.get(efid);

        if (pendingResponse != null) {
            sendErrorResponse(response, "Have pending update for EF:0x" +
                    Integer.toHexString(efid).toUpperCase());
            return;
        }

        mUserWriteResponse.put(efid, response);

        oldAdn.mExtRecord = oldAdnList.get(index - 1).mExtRecord;
        if (newAdn.extRecordIsNeeded()) {
            byte extIndex;
            if (oldAdn.mExtRecord != 0xff) {
                extIndex = (byte) oldAdn.mExtRecord;
            } else {
                extIndex = getAvailableExtIndex(extensionEF, efid);
            }

            if (extIndex == (byte) 0xff) {
                mUserWriteResponse.delete(efid);
                sendErrorResponse(response, "ext list full");
                return;
            }
            newAdn.mExtRecord = (int) extIndex;
            new AdnRecordLoader(mFh).updateExtEF(newAdn, efid, extensionEF, extIndex, pin2,
                    obtainMessage(EVENT_UPDATE_EXT_DONE, extensionEF, extIndex));
        } else {
            if (oldAdn.mExtRecord != 0xff) {
                new AdnRecordLoader(mFh).updateExtEF(newAdn, efid, extensionEF, oldAdn.mExtRecord,
                        pin2, obtainMessage(EVENT_UPDATE_EXT_DONE, extensionEF, oldAdn.mExtRecord));
            }
        }
        oldAdn.mExtRecord = 0xff;

        new AdnRecordLoader(mFh).updateEF(newAdn, efid, extensionEF,
                index, pin2,
                obtainMessage(EVENT_UPDATE_ADN_DONE, efid, index, newAdn));
    }

    /**
     * Replace oldAdn with newAdn in ADN-like record in EF for USIM
     *
     * The ADN-like records must be read through requestLoadAllAdnLike() before
     *
     * @param efid must be one of EF_ADN, EF_FDN, and EF_SDN
     * @param oldAdn is the adn to be replaced
     *        If oldAdn.isEmpty() is ture, it insert the newAdn
     * @param newAdn is the adn to be stored
     *        If newAdn.isEmpty() is true, it delete the oldAdn
     * @param pin2 is required to update EF_FDN, otherwise must be null
     * @param response message to be posted when done
     *        response.exception hold the exception in error
     */
    public synchronized void updateUSIMAdnBySearch(int efid,
            AdnRecord oldAdn, AdnRecord newAdn, String pin2,
            Message response) {
        int extensionEF;
        int iapEF;
        int index = -1;
        int recNum = mUsimPhoneBookManager.getNumRecs();

        for (int recId = 0; recId < recNum; recId++) {
            index = -1;
            efid = mUsimPhoneBookManager.findEFInfo(recId, mUsimPhoneBookManager.USIM_EFADN_TAG);
            extensionEF = mUsimPhoneBookManager.findEFInfo(
                    recId, mUsimPhoneBookManager.USIM_EFEXT1_TAG);
            iapEF = mUsimPhoneBookManager.findEFInfo(recId, mUsimPhoneBookManager.USIM_EFIAP_TAG);
            if (DBG) {
                log("efid:0x" + Integer.toHexString(efid).toUpperCase()
                        + ", extensionEF:0x" + Integer.toHexString(extensionEF).toUpperCase()
                        + ", iapEF:0x" + Integer.toHexString(iapEF).toUpperCase());
            }

            if (extensionEF < 0) {
                sendErrorResponse(response, "EF is not known ADN-like EF:0x"
                        + Integer.toHexString(efid).toUpperCase());
                return;
            }

            ArrayList<AdnRecord> oldAdnList;
            oldAdnList = getRecordsIfLoaded(efid);
            if (oldAdnList == null) {
                if (recId == recNum - 1) {
                    sendErrorResponse(response,
                            "Adn list not exist for EF:0x"
                                    + Integer.toHexString(efid).toUpperCase());
                    return;
                } else {
                    continue;
                }
            }

            int count = 1;
            boolean find_index = false;
            for (Iterator<AdnRecord> it = oldAdnList.iterator(); it.hasNext();) {
                if (oldAdn.isEqual(it.next())) {
                    find_index = true;
                    index = count;
                    mInsertId = index;
                    break;
                }
                count++;
            }

            if (find_index) {
                for (int i = 0; i < recId; i++) {
                    mInsertId += mUsimPhoneBookManager.getAdnRecordSizeArray()[i];
                }

                oldAdn = oldAdnList.get(index - 1);

                if (!checkAnrLength(newAdn)) {
                    sendErrorResponse(response, "Anr number too long");
                    return;
                }

                Message pendingResponse = mUserWriteResponse.get(efid);
                if (pendingResponse != null) {
                    sendErrorResponse(response,
                            "Have pending update for EF:0x"
                                    + Integer.toHexString(efid).toUpperCase());
                    return;
                }
                mUserWriteResponse.put(efid, response);

                AdnRecordLoader adnRecordLoader = new AdnRecordLoader(mFh);
                int updateEmailResult = updateSubjectOfAdn(UsimPhoneBookManager.USIM_SUBJCET_EMAIL,
                        recId, adnRecordLoader, mInsertId, index, efid, oldAdn,
                        newAdn, iapEF, pin2, null);
                if (updateEmailResult < 0) {
                    mUserWriteResponse.delete(efid);
                    // in the first pbr, no subject found, search in the second pbr
                    if (recId == recNum - 1) {
                        sendErrorResponse(response, "Email capacity full");
                        return;
                    } else {
                        continue;
                    }
                }

                int[] aasIndex = null;
                newAdn.mAas = newAdn.mAas == null ? "" : newAdn.mAas;
                ArrayList<String> aasArr = loadAasFromUsim();

                if (aasArr != null && newAdn.mAas != null) {
                    String[] aas = newAdn.mAas.split(AdnRecord.ANR_SPLIT_FLG);
                    aasIndex = new int[aas.length];
                    for (int i = 0; i < aas.length; i++) {
                        aasIndex[i] = aasArr.indexOf(aas[i].toString()) + 1;
                    }
                }

                int updateAnrResult = updateSubjectOfAdn(UsimPhoneBookManager.USIM_SUBJCET_ANR,
                        recId, adnRecordLoader, mInsertId, index, efid, oldAdn, newAdn,
                        iapEF, pin2, aasIndex);
                if (updateAnrResult < 0) {
                    mUserWriteResponse.delete(efid);
                    // in the first pbr, no subject found, search in the second pbr
                    if (recId == recNum - 1) {
                        sendErrorResponse(response, "update anr failed");
                        return;
                    } else {
                        continue;
                    }
                }

                int updateSneResult = updateSubjectOfAdn(UsimPhoneBookManager.USIM_SUBJCET_SNE,
                        recId, adnRecordLoader, mInsertId, index, efid, oldAdn,
                        newAdn, iapEF, pin2, null);
                if (updateSneResult < 0) {
                    mUserWriteResponse.delete(efid);
                    sendErrorResponse(response, "sne capacity full");
                    return;
                }

                updateGrpOfAdn(adnRecordLoader, index, recId, oldAdn,
                        newAdn, pin2, efid, response);

                if (newAdn.extRecordIsNeeded()) {
                    byte extIndex;
                    if (oldAdn.mExtRecord != 0xFF) {
                        extIndex = (byte) oldAdn.mExtRecord;
                    } else {
                        extIndex = getAvailableExtIndex(extensionEF, efid);
                    }

                    if (extIndex == (byte) 0xFF) {
                        mUserWriteResponse.delete(efid);
                        sendErrorResponse(response, "ext list full");
                        return;
                    }
                    newAdn.mExtRecord = (int) extIndex;
                    new AdnRecordLoader(mFh).updateExtEF(newAdn, efid, extensionEF, extIndex, pin2,
                            obtainMessage(EVENT_UPDATE_EXT_DONE, extensionEF, extIndex));
                } else {
                    if (oldAdn.mExtRecord != 0xFF) {
                        new AdnRecordLoader(mFh).updateExtEF(newAdn, efid, extensionEF,
                                oldAdn.mExtRecord, pin2, obtainMessage(EVENT_UPDATE_EXT_DONE,
                                extensionEF, oldAdn.mExtRecord));
                    }
                }
                oldAdn.mExtRecord = 0xff;

                new AdnRecordLoader(mFh).updateEFAdnToUsim(newAdn, efid, extensionEF, index,
                        pin2, obtainMessage(EVENT_UPDATE_USIM_ADN_DONE, efid, index, newAdn),
                        getRecordsSizeByEf(efid));
                break;
            }
        }
        if (index == -1) {
            sendErrorResponse(response, "Adn record don't exist for " + oldAdn);
            return;
        }
    }


    private int updateSubjectOfAdn(int type, int recId,
            AdnRecordLoader adnRecordLoader, int adnNum, int index,
            int efid, AdnRecord oldAdn, AdnRecord newAdn, int iapEF,
            String pin2, Object obj) {
        if (DBG) {
            log("updateSubjectOfAdn, file type = " + type + ", pbrNum = " + recId
                    + ", adnNum = " + adnNum + ", index = " + index);
        }

        if (compareSubject(type, oldAdn, newAdn)) {
            return 0;
        }

        int[] efids = mUsimPhoneBookManager.getSubjectEfids(type, recId);
        int[] updateSubjectFlag = getUpdateSubjectFlag(recId, type, oldAdn, newAdn, efids);

        if (updateSubjectFlag == null || efids == null || efids.length == 0) {
            return 0;
        }

        ArrayList<Integer> subjectEfids = new ArrayList<Integer>();
        ArrayList<Integer> subjectNums = new ArrayList<Integer>();
        // efid ,numberInIap
        int[][] anrTagMap = mUsimPhoneBookManager.getSubjectTagNumberInIap(type, recId);
        int[] subjectNum = new int[efids.length];
        int efIndex = 0;

        for (int m = 0; m < efids.length; m++) {
            subjectEfids.add(efids[m]);

            if (updateSubjectFlag[m] == 0) {
                subjectNums.add(0);
                efIndex++;
                continue;
            }

            if (mUsimPhoneBookManager.isSubjectRecordInIap(type, recId, m)) {
                if (anrTagMap == null) {
                    Rlog.e(TAG, "anrTagMap is null");
                    return -1;
                }

                byte[] record = null;
                try {
                    ArrayList<byte[]> mIapFileRecord =
                            mUsimPhoneBookManager.getIapFileRecord(recId);
                    if (mIapFileRecord != null) {
                        record = mIapFileRecord.get(index - 1);
                    } else {
                        Rlog.e(TAG, "updateSubjectOfAdn mIapFileRecord is null ");
                        return -1;
                    }
                } catch (IndexOutOfBoundsException e) {
                    Rlog.e(TAG, "Error: Improper ICC card: No IAP record for ADN, continuing");
                    return -1;
                }

                if (record != null) {
                    if (anrTagMap[m][1] >= record.length) {
                        Rlog.e(TAG, "anrTagMap[m][1] Error");
                        return -1;
                    }

                    subjectNum[m] = (int) (record[anrTagMap[m][1]] & 0xFF);
                    subjectNum[m] = subjectNum[m] == 0xFF ? (-1) : subjectNum[m];
                } else {
                    Rlog.e(TAG, "record is null");
                    return -1;
                }

                if (subjectNum[m] < 1) {
                    subjectNum[m] = mUsimPhoneBookManager.getNewSubjectNumber(type, recId,
                            anrTagMap[m][0], efIndex, index, true);
                }

                if (subjectNum[m] != -1) {
                    subjectNums.add(subjectNum[m]);
                } else {
                    Rlog.e(TAG, "file capacity full");
                    return -1;
                }

                if (isCleanRecord(recId, type, oldAdn, newAdn, m)) {
                    mUsimPhoneBookManager.removeSubjectNumFromSet(type, recId, anrTagMap[m][0],
                            efIndex, subjectNum[m]);
                    mUsimPhoneBookManager.setIapFileRecord(
                            recId, index - 1, (byte) 0xFF, anrTagMap[m][1]);
                    record[anrTagMap[m][1]] = (byte) 0xFF;
                } else {
                    mUsimPhoneBookManager.setIapFileRecord(
                            recId, index - 1, (byte) (subjectNum[m] & 0xFF),
                            anrTagMap[m][1]);
                    record[anrTagMap[m][1]] = (byte) (subjectNum[m] & 0xFF);
                }

                if (anrTagMap[m][0] > 0) {
                    adnRecordLoader = new AdnRecordLoader(mFh);
                    adnRecordLoader.updateEFIapToUsim(newAdn, iapEF,
                            index, record, pin2, null);
                }
                efIndex++;
            } else {
                if (mUsimPhoneBookManager.getNewSubjectNumber(
                        type, recId, efids[m], 0, index, false) == index) {
                    subjectNums.add(index);
                } else {
                    Rlog.e(TAG, "updateSubjectOfAdn fail to get  new subject ");
                    return -1;
                }
            }
        }

        for (int i = 0; i < subjectEfids.size(); i++) {
            if (subjectNums.get(i) != 0) {
                adnRecordLoader = new AdnRecordLoader(mFh);

                if (type == UsimPhoneBookManager.USIM_SUBJCET_EMAIL) {
                    adnRecordLoader.updateEFEmailToUsim(
                            newAdn, subjectEfids.get(i), subjectNums.get(i),
                            efid, index, i, pin2, null, getRecordsSizeByEf(subjectEfids.get(0)));
                }

                if (type == UsimPhoneBookManager.USIM_SUBJCET_SNE) {
                    adnRecordLoader.updateEFSneToUsim(
                            newAdn, subjectEfids.get(i), subjectNums.get(i), efid,
                            index, i, pin2, null);
                }

                if (type == UsimPhoneBookManager.USIM_SUBJCET_ANR) {
                    int[] data = null;
                    if (obj != null) {
                        data = (int[]) obj;
                    }
                    int aasIndex = (data != null && i < data.length) ? data[i] : 0;

                    adnRecordLoader.updateEFAnrToUsim(newAdn, subjectEfids.get(i),
                                efid, index, subjectNums.get(i), i, pin2,
                                null, getRecordsSizeByEf(subjectEfids.get(0)), aasIndex);
                }
            }
        }
        return 1;
    }

    private boolean compareSubject(int type, AdnRecord oldAdn, AdnRecord newAdn) {
        boolean isEqual = true;
        switch (type) {
            case UsimPhoneBookManager.USIM_SUBJCET_EMAIL:
                isEqual = oldAdn.stringCompareEmails(oldAdn.mEmails, newAdn.mEmails);
                break;

            case UsimPhoneBookManager.USIM_SUBJCET_ANR:
                isEqual = oldAdn.stringCompareAnr(oldAdn.mAnr, newAdn.mAnr)
                        && oldAdn.stringCompareAnr(oldAdn.mAas, newAdn.mAas);
                break;

            case UsimPhoneBookManager.USIM_SUBJCET_GRP:
                isEqual = oldAdn.stringCompareAnr(oldAdn.mGrp, newAdn.mGrp);
                break;

            case UsimPhoneBookManager.USIM_SUBJCET_AAS:
                isEqual = oldAdn.stringCompareAnr(oldAdn.mAas, newAdn.mAas);
                break;

            case UsimPhoneBookManager.USIM_SUBJCET_SNE:
                isEqual = oldAdn.stringCompareAnr(oldAdn.mSne, newAdn.mSne);
                break;

            default:
                break;
        }
        return isEqual;
    }

    private int[] getUpdateSubjectFlag(int recId, int type, AdnRecord oldAdn,
             AdnRecord newAdn, int[] efids) {
        if (efids == null) {
            return null;
        }

        String str1;
        String str2;
        String aas1;
        String aas2;
        int oldCount = 0;
        int newCount = 0;
        String[] strArr1 = getSubjectString(type, oldAdn);
        String[] strArr2 = getSubjectString(type, newAdn);
        String[] aasArr1 = getSubjectAasString(type, oldAdn);
        String[] aasArr2 = getSubjectAasString(type, newAdn);

        int count = efids.length;
        int[] flag = new int[count];

        if (strArr1 != null) {
            oldCount = strArr1.length;
        }
        if (strArr2 != null) {
            newCount = strArr2.length;
        }

        for (int i = 0; i < count; i++) {
            str1 = "";
            str2 = "";
            aas1 = "";
            aas2 = "";
            if (i < oldCount && strArr1[i] != null) {
                str1 = strArr1[i];
            }

            if (i < newCount && strArr2[i] != null) {
                str2 = strArr2[i];
            }
            if (i < oldCount && aasArr1 != null && i < aasArr1.length && aasArr1[i] != null) {
                aas1 = aasArr1[i];
            }
            if (i < newCount && aasArr2 != null && i < aasArr2.length &&  aasArr2[i] != null) {
                aas2 = aasArr2[i];
            }

            flag[i] = (!(str1.trim().equals(str2.trim()))
                    || !(aas1.trim().equals(aas2.trim()))) ? 1 : 0;
        }
        return flag;
    }

    private String[] getSubjectString(int type, AdnRecord adn) {
        String[] s1 = null;
        switch (type) {
            case UsimPhoneBookManager.USIM_SUBJCET_EMAIL:
                s1 = adn.mEmails;
                break;
            case UsimPhoneBookManager.USIM_SUBJCET_ANR:
                s1 = getAnrNumGroup(adn.mAnr);
                break;
            case UsimPhoneBookManager.USIM_SUBJCET_AAS:
                s1 = new String[] {adn.mAas};
                break;
            case UsimPhoneBookManager.USIM_SUBJCET_SNE:
                s1 = new String[] {adn.mSne};
                break;
            default:
                break;
        }
        return s1;
    }

    private String[] getAnrNumGroup(String anr) {
        String[] pair = null;
        if (!TextUtils.isEmpty(anr)) {
            pair = anr.split(";");
        }
        return pair;
    }


    private String[] getSubjectAasString(int type, AdnRecord adn) {
        String[] aas = null;
        switch (type) {
            case UsimPhoneBookManager.USIM_SUBJCET_ANR:
                aas = getAnrNumGroup(adn.mAas);
                break;
            default:
                break;
        }
        return aas;
    }

    private boolean isCleanRecord(int num, int type, AdnRecord oldAdn,
                                  AdnRecord newAdn, int index) {
        String str1, str2, aas1, aas2;
        String[] strArr1 = getSubjectString(type, oldAdn);
        String[] strArr2 = getSubjectString(type, newAdn);
        String[] aasArr1 = getSubjectAasString(type, oldAdn);
        String[] aasArr2 = getSubjectAasString(type, newAdn);
        int oldCount = 0, newCount = 0;

        if (strArr1 != null) {
            oldCount = strArr1.length;
        }
        if (strArr2 != null) {
            newCount = strArr2.length;
        }

        int[] efids = mUsimPhoneBookManager.getSubjectEfids(type, num);
        if (efids == null) {
            return false;
        }
        int count = efids.length;

        for (int i = 0; i < count; i++) {
            if (i < oldCount && strArr1[i] != null) {
                str1 = strArr1[i];
            } else {
                str1 = "";
            }

            if (i < newCount && strArr2[i] != null) {
                str2 = strArr2[i];
            } else {
                str2 = "";
            }

            if (i < oldCount && aasArr1 != null && i < aasArr1.length && aasArr1[i] != null) {
                aas1 = aasArr1[i];
            } else {
                aas1 = "";
            }

            if (i < newCount && aasArr2 != null && i < aasArr2.length && aasArr2[i] != null) {
                aas2 = aasArr2[i];
            } else {
                aas2 = "";
            }

            if (index == i && (!(str1.trim().equals(str2.trim()))
                    || !(aas1.trim().equals(aas2.trim()))) && TextUtils.isEmpty(str2)) {
                return true;
            }
        }
        return false;
    }

    private void updateGrpOfAdn(AdnRecordLoader adnRecordLoader, int index,
              int recId, AdnRecord oldAdn, AdnRecord newAdn, String pin2,
              int efid, Message response) {
        if (compareSubject(UsimPhoneBookManager.USIM_SUBJCET_GRP, oldAdn, newAdn)) {
            return;
        }

        byte[] data = new byte[mUsimPhoneBookManager.mGrpLength];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) 0x00;
        }

        String grp = newAdn.getGrp();
        if (!TextUtils.isEmpty(grp)) {
            String[] groups = grp.split(AdnRecord.ANR_SPLIT_FLG);
            if (groups.length > data.length) {
                mUserWriteResponse.delete(efid);
                sendErrorResponse(response,
                        "over the length of grp");
                return;
            }

            for (int i = 0; i < groups.length && i < data.length; i++) {
                int groupId = Integer.valueOf(groups[i]);
                data[i] = (byte) groupId;
            }
        }

        int grpEfId = mUsimPhoneBookManager.findEFInfo(recId,
                UsimPhoneBookManager.USIM_EFGRP_TAG);

        adnRecordLoader.updateEFGrpToUsim(grpEfId, index, data, pin2);
    }


    private int[] getRecordsSizeByEf(int efId) {
        int[] size;
        if (mUsimPhoneBookManager.getRecordsSize() != null
                && mUsimPhoneBookManager.getRecordsSize().containsKey(efId)) {
            size = mUsimPhoneBookManager.getRecordsSize().get(efId);
        } else {
            size = mUsimPhoneBookManager.readFileSizeAndWait(efId);
        }
        return size;
    }

    /**
     * Responds with exception (in response) if efid is not a known ADN-like
     * record
     */
    public void
    requestLoadAllAdnLike (int efid, int extensionEf, Message response) {
        ArrayList<Message> waiters;
        ArrayList<AdnRecord> result;

        if (efid == EF_PBR) {
            result = mUsimPhoneBookManager.loadEfFilesFromUsim();
        } else {
            result = getRecordsIfLoaded(efid);
            if (result != null && result.size() == 0) {
                result = null;
            }
        }

        // If load record from USIM file fail, then load record from SIM file
        if (efid == EF_PBR && result == null) {
            efid = EF_ADN;
            extensionEf = extensionEfForEf(efid);
            if (DBG) log("PBR is empty, read 0x6F3A");
            result = getRecordsIfLoaded(efid);
            if (result != null && result.size() == 0) {
                result = null;
            }
        }

        // Have we already loaded this efid?
        if (result != null) {
            if (response != null) {
                AsyncResult.forMessage(response).result = result;
                response.sendToTarget();
            }

            return;
        }

        // Have we already *started* loading this efid?

        waiters = mAdnLikeWaiters.get(efid);

        if (waiters != null) {
            // There's a pending request for this EF already
            // just add ourselves to it

            waiters.add(response);
            return;
        }

        // Start loading efid

        waiters = new ArrayList<Message>();
        waiters.add(response);

        mAdnLikeWaiters.put(efid, waiters);


        if (extensionEf < 0) {
            // respond with error if not known ADN-like record

            if (response != null) {
                AsyncResult.forMessage(response).exception
                    = new RuntimeException("EF is not known ADN-like EF:0x" +
                        Integer.toHexString(efid).toUpperCase());
                response.sendToTarget();
            }

            return;
        }

        new AdnRecordLoader(mFh).loadAllExtFromEF(efid, extensionEf,
                obtainMessage(EVENT_LOAD_ALL_EXT_LIKE_DONE, efid, extensionEf));
    }

    //***** Private methods

    private void
    notifyWaiters(ArrayList<Message> waiters, AsyncResult ar) {

        if (waiters == null) {
            return;
        }

        for (int i = 0, s = waiters.size() ; i < s ; i++) {
            Message waiter = waiters.get(i);

            AsyncResult.forMessage(waiter, ar.result, ar.exception);
            waiter.sendToTarget();
        }
    }

    //***** Overridden from Handler

    @Override
    public void
    handleMessage(Message msg) {
        AsyncResult ar;
        int efid;
        int index;
        int extensionEf;

        switch(msg.what) {
            case EVENT_LOAD_ALL_ADN_LIKE_DONE:
                /* arg1 is efid, obj.result is ArrayList<AdnRecord>*/
                ar = (AsyncResult) msg.obj;
                efid = msg.arg1;
                ArrayList<Message> waiters;

                waiters = mAdnLikeWaiters.get(efid);
                mAdnLikeWaiters.delete(efid);

                if (ar.exception == null) {
                    mAdnLikeFiles.put(efid, (ArrayList<AdnRecord>) ar.result);
                }
                notifyWaiters(waiters, ar);
                break;
            case EVENT_LOAD_ALL_EXT_LIKE_DONE:
                ar = (AsyncResult) msg.obj;
                efid = msg.arg1;
                extensionEf = msg.arg2;
                if (ar.exception == null) {
                    mExtLikeFiles.put(extensionEf, (ArrayList<byte[]>) ar.result);
                }

                new AdnRecordLoader(mFh).loadAllFromEF(efid, extensionEf,
                        obtainMessage(EVENT_LOAD_ALL_ADN_LIKE_DONE, efid, extensionEf));
                break;
            case EVENT_UPDATE_ADN_DONE:
                ar = (AsyncResult)msg.obj;
                efid = msg.arg1;
                index = msg.arg2;
                AdnRecord adn = (AdnRecord) (ar.userObj);

                if (ar.exception == null && mAdnLikeFiles.get(efid) != null) {
                    mAdnLikeFiles.get(efid).set(index - 1, adn);
                    adn.setRecordNumber(mInsertId);
                    mUsimPhoneBookManager.invalidateCache();
                }

                Message response = mUserWriteResponse.get(efid);
                mUserWriteResponse.delete(efid);

                // response may be cleared when simrecord is reset,
                // so we should check if it is null.
                if (response != null) {
                    AsyncResult.forMessage(response, index, ar.exception);
                    response.sendToTarget();
                }
                break;
            case EVENT_UPDATE_EXT_DONE:
                ar = (AsyncResult) msg.obj;
                extensionEf = msg.arg1;
                index = msg.arg2;
                byte[] extData = (byte[]) ar.result;

                if (ar.exception == null) {
                    mExtLikeFiles.get(extensionEf).set(index - 1, extData);
                }
                break;

            case EVENT_UPDATE_USIM_ADN_DONE:
                ar = (AsyncResult) msg.obj;
                efid = msg.arg1;
                index = msg.arg2;
                adn = (AdnRecord) (ar.userObj);
                int recNum = -1;

                for (int num = 0; num < mUsimPhoneBookManager.getNumRecs(); num++) {
                    int adnEF = mUsimPhoneBookManager.findEFInfo(
                            num, mUsimPhoneBookManager.USIM_EFADN_TAG);
                    if (efid == adnEF) {
                        recNum = num;
                    }
                }

                if (recNum == -1) {
                    break;
                }

                int[] mAdnRecordSizeArray = mUsimPhoneBookManager.getAdnRecordSizeArray();
                int adnRecNum = index - 1;
                for (int i = 0; i < recNum; i++) {
                    adnRecNum += mAdnRecordSizeArray[i];
                }

                if (ar.exception == null && mAdnLikeFiles.get(efid) != null) {
                    adn.setRecordNumber(mInsertId);
                    mUsimPhoneBookManager.setPhoneBookRecords(adnRecNum, adn);
                    mAdnLikeFiles.get(efid).set(index - 1, adn);

                    // update EFuid
                    mUsimPhoneBookManager.updateUidForAdn(recNum);
                }

                response = mUserWriteResponse.get(efid);
                mUserWriteResponse.delete(efid);
                if (response != null) {
                    AsyncResult.forMessage(response, null, ar.exception);
                    response.sendToTarget();
                }
                break;
        }
    }

    private byte getAvailableExtIndex(int extEfId, int efid) {
        byte[] emptyExtRecord = new byte[AdnRecord.EXT_RECORD_LENGTH_BYTES];
        for (int i = 0; i < emptyExtRecord.length; i++) {
            emptyExtRecord[i] = (byte) 0xFF;
        }

        ArrayList<byte[]> extList = mExtLikeFiles.get(extEfId);
        byte index = findEmptyExt(extList);

        if ((index & 0xFF) == 0xFF) {
            // there are no empty ext records, clean unused ext records
            ArrayList<Integer> usedExtIndexs = getUsedExtRecordIndex(efid);
            if (usedExtIndexs == null || extList == null) {
                return (byte) 0xFF;
            }

            int extListSize = extList.size();
            for (int i = 0; i < extListSize; i++) {
                boolean isUsed = false;
                for (Iterator<Integer> it = usedExtIndexs.iterator(); it.hasNext();) {
                    if (i + 1 == it.next()) {
                        isUsed = true;
                        break;
                    }
                }
                if (!isUsed) {
                    mExtLikeFiles.get(extEfId).set(i, emptyExtRecord);
                }
            }
            // find empty ext record
            extList = mExtLikeFiles.get(extEfId);
            return findEmptyExt(extList);
        } else {
            return (byte) index;
        }
    }

    private byte findEmptyExt(ArrayList<byte[]> extList) {
        if (extList == null) {
            return (byte) 0xFF;
        }

        byte count = 1;
        for (Iterator<byte[]> it = extList.iterator(); it.hasNext();) {
            if ((byte) 0xFF == it.next()[0]) {
                return count;
            }
            count++;
        }
        return (byte) 0xFF;
    }

    private ArrayList<Integer> getUsedExtRecordIndex(int efid) {
        ArrayList<Integer> usedIndex = new ArrayList<Integer>();
        ArrayList<AdnRecord> adnList = getRecordsIfLoaded(efid);
        if (adnList == null) {
            return null;
        }

        int index;
        for (Iterator<AdnRecord> it = adnList.iterator(); it.hasNext();) {
            index = it.next().mExtRecord;
            if (index != 0xFF && index != 0) {
                if (!usedIndex.contains(index)) {
                    usedIndex.add(index);
                }
            }
        }
        return usedIndex;
    }

    /**
     * Loads the aas in efid and returns them as a list of aas
     *
     * @return list of aas string
     */
    public ArrayList<String> loadAasFromUsim() {
        return mUsimPhoneBookManager.loadAasFromUsim();
    }

    /**
     * Loads the gas in efid and returns them as a list of gas
     *
     * @return list of gas string
     */
    public ArrayList<String> loadGasFromUsim() {
        return mUsimPhoneBookManager.loadGasFromUsim();
    }

    /**
     * Replace oldAas with newAas in AAS-like record in EF for USIM
     *
     * @param oldAas is the aas to be replaced
     *        If oldAas.isEmpty() is ture, it insert the newAas
     * @param newAas is the aas to be stored
     *        If newAas.isEmpty() is true, it delete the oldAas
     */
    public int updateAasBySearch(String oldAas, String newAas) {
        ArrayList<String> oldAasList = mUsimPhoneBookManager.loadAasFromUsim();

        if (oldAasList == null) {
            return -1;
        }

        int index = -1;
        int count = 1;
        for (Iterator<String> it = oldAasList.iterator(); it.hasNext();) {
            if (oldAas.equals(it.next())) {
                index = count;
                break;
            }
            count++;
        }

        if (index == -1) {
            return -1;
        }

        int aasEfId = mUsimPhoneBookManager.findEFInfo(0, mUsimPhoneBookManager.USIM_EFAAS_TAG);
        int[] aasSize = getRecordsSizeByEf(aasEfId);
        if (aasSize == null) {
            return -1;
        }

        byte[] data = gasToByte(newAas, aasSize[0]);
        if (data == null) {
            return -1;
        }

        new AdnRecordLoader(mFh).updateEFGasToUsim(aasEfId, index, data, null);
        mUsimPhoneBookManager.updateAasList(newAas, index);
        return index;
    }

    /**
     * Update an AAS-like record in EF by record index
     *
     * @param newAas is the new adn to be stored
     * @param aasIndex is the 1-based aas record index
     */
    public int updateAasByIndex(String newAas, int aasIndex) {
        int aasEfId = mUsimPhoneBookManager.findEFInfo(0, mUsimPhoneBookManager.USIM_EFAAS_TAG);
        int[] aasSize = getRecordsSizeByEf(aasEfId);
        if (aasSize == null) {
            return -1;
        }

        byte[] data = gasToByte(newAas, aasSize[0]);
        if (data == null) {
            return -1;
        }

        new AdnRecordLoader(mFh).updateEFGasToUsim(aasEfId, aasIndex, data, null);
        mUsimPhoneBookManager.updateAasList(newAas, aasIndex);
        return aasIndex;
    }

    private byte[] gasToByte(String gas, int recordSize) {
        byte[] gasByte = new byte[recordSize];
        byte[] data;
        for (int i = 0; i < recordSize; i++) {
            gasByte[i] = (byte) 0xFF;
        }

        if (!TextUtils.isEmpty(gas)) {
            if (isContainChineseChar(gas)) {
                try {
                    data = gas.getBytes("utf-16be");
                    System.arraycopy(data, 0, gasByte, 1, data.length);
                    gasByte[0] = (byte) 0x80;
                } catch (java.io.UnsupportedEncodingException ex2) {
                    Rlog.e(TAG, "gas convert byte exception");
                } catch (ArrayIndexOutOfBoundsException e) {
                    Rlog.e(TAG, "over the length of group name");
                    return null;
                }
            } else {
                try {
                    data = GsmAlphabet.stringToGsm8BitPacked(gas);
                    System.arraycopy(data, 0, gasByte, 0, data.length);
                } catch (ArrayIndexOutOfBoundsException ex) {
                    Rlog.e(TAG, "over the length of group name");
                    return null;
                }
            }

        }
        return gasByte;
    }

    /**
     * Replace oldGas with newGas in GAS-like record in EF for USIM
     *
     * @param oldGas is the gas to be replaced
     *        If oldGas.isEmpty() is ture, it insert the newGas
     * @param newGas is the gas to be stored
     *        If newGas.isEmpty() is true, it delete the oldGas
     */
    public int updateGasBySearch(String oldGas, String newGas) {
        ArrayList<String> oldGasList = mUsimPhoneBookManager.loadGasFromUsim();

        if (oldGasList == null) {
            return -1;
        }

        int index = -1;
        int count = 1;
        for (Iterator<String> it = oldGasList.iterator(); it.hasNext();) {
            if (oldGas.equals(it.next())) {
                index = count;
                break;
            }
            count++;
        }

        if (index == -1) {
            return -1;
        }

        int gasEfId = mUsimPhoneBookManager.findEFInfo(0, mUsimPhoneBookManager.USIM_EFGAS_TAG);
        int[] gasSize = getRecordsSizeByEf(gasEfId);
        if (gasSize == null) {
            return -1;
        }

        byte[] data = gasToByte(newGas, gasSize[0]);
        if (data == null) {
            return -1;
        }

        new AdnRecordLoader(mFh).updateEFGasToUsim(gasEfId, index, data, null);
        mUsimPhoneBookManager.updateGasList(newGas, index);
        return index;
    }

    /**
     * Update an GAS-like record in EF by record index
     *
     * @param newGas is the new adn to be stored
     * @param groupId is the 1-based gas record index
     */
    public int updateGasByIndex(String newGas, int groupId) {
        int gasEfId = mUsimPhoneBookManager.findEFInfo(0, mUsimPhoneBookManager.USIM_EFGAS_TAG);
        int[] gasSize = getRecordsSizeByEf(gasEfId);
        if (gasSize == null) {
            return -1;
        }

        byte[] data = gasToByte(newGas, gasSize[0]);
        if (data == null) {
            return -1;
        }
        new AdnRecordLoader(mFh).updateEFGasToUsim(gasEfId, groupId, data, null);
        mUsimPhoneBookManager.updateGasList(newGas, groupId);
        return groupId;
    }

    /**
     * Get the updated or inserted adn record index
     *
     * @return updated or inserted adn record index
     */
    public int getInsertId() {
        return mInsertId;
    }

    private boolean checkAnrLength(AdnRecord newAdn) {
        String[] ret = null;
        String anr = "";

        if (TextUtils.isEmpty(newAdn.mAnr)) {
            return true;
        }

        ret = (newAdn.mAnr + "1").split(";");
        ret[ret.length - 1] = ret[ret.length - 1].substring(0,
                ret[ret.length - 1].length() - 1);

        for (int m = 0; m < ret.length; m++) {
            anr = ret[m];
            if (anr.length() > 20) {
                return false;
            }
        }
        return true;
    }

    public UsimPhoneBookManager getUsimPhoneBookManager() {
        return mUsimPhoneBookManager;
    }

    private void log(String msg) {
        if (DBG) Rlog.d(TAG, msg);
    }

    private boolean isContainChineseChar(String alphTag) {
        Matcher m = Pattern.compile("[\u4e00-\u9fa5]").matcher(alphTag);
        if (m.find()) {
            return true;
        }
        return false;
    }
}
