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

package com.android.internal.telephony;

import android.annotation.UnsupportedAppUsage;
import android.content.pm.PackageManager;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.telephony.Rlog;

import com.android.internal.telephony.gsm.UsimPhoneBookManager;
import com.android.internal.telephony.uicc.AdnRecord;
import com.android.internal.telephony.uicc.AdnRecordCache;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppType;
import com.android.internal.telephony.uicc.IccConstants;
import com.android.internal.telephony.uicc.IccFileHandler;
import com.android.internal.telephony.uicc.IccRecords;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SimPhoneBookInterfaceManager to provide an inter-process communication to
 * access ADN-like SIM records.
 */
public class IccPhoneBookInterfaceManager {
    static final String LOG_TAG = "IccPhoneBookIM";
    @UnsupportedAppUsage
    protected static final boolean DBG = true;

    @UnsupportedAppUsage
    protected Phone mPhone;
    @UnsupportedAppUsage
    protected AdnRecordCache mAdnCache;

    protected static final int EVENT_GET_SIZE_DONE = 1;
    protected static final int EVENT_LOAD_DONE = 2;
    protected static final int EVENT_UPDATE_DONE = 3;

    private static final class Request {
        AtomicBoolean mStatus = new AtomicBoolean(false);
        Object mResult = null;
    }

    @UnsupportedAppUsage
    protected Handler mBaseHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            AsyncResult ar = (AsyncResult) msg.obj;
            Request request = (Request) ar.userObj;

            switch (msg.what) {
                case EVENT_GET_SIZE_DONE:
                    int[] recordSize = null;
                    if (ar.exception == null) {
                        recordSize = (int[]) ar.result;
                        // recordSize[0]  is the record length
                        // recordSize[1]  is the total length of the EF file
                        // recordSize[2]  is the number of records in the EF file
                        logd("GET_RECORD_SIZE Size " + recordSize[0]
                                + " total " + recordSize[1]
                                + " #record " + recordSize[2]);
                    } else {
                        loge("EVENT_GET_SIZE_DONE: failed; ex=" + ar.exception);
                    }
                    notifyPending(request, recordSize);
                    break;
                case EVENT_UPDATE_DONE:
                    boolean success = (ar.exception == null);
                    int simIndex = -1;
                    if (success) {
                        simIndex = getInsertIndex();
                    } else {
                        loge("EVENT_UPDATE_DONE failed; ex = " + ar.exception);
                    }
                    notifyPending(request, simIndex);
                    break;
                case EVENT_LOAD_DONE:
                    List<AdnRecord> records = null;
                    if (ar.exception == null) {
                        records = (List<AdnRecord>) ar.result;
                    } else {
                        loge("EVENT_LOAD_DONE: Cannot load ADN records; ex="
                                + ar.exception);
                    }
                    notifyPending(request, records);
                    break;
            }
        }

        private void notifyPending(Request request, Object result) {
            if (request != null) {
                synchronized (request) {
                    request.mResult = result;
                    request.mStatus.set(true);
                    request.notifyAll();
                }
            }
        }
    };

    public IccPhoneBookInterfaceManager(Phone phone) {
        this.mPhone = phone;
        IccRecords r = phone.getIccRecords();
        if (r != null) {
            mAdnCache = r.getAdnCache();
        }
    }

    public void dispose() {
    }

    public void updateIccRecords(IccRecords iccRecords) {
        if (iccRecords != null) {
            mAdnCache = iccRecords.getAdnCache();
        } else {
            mAdnCache = null;
        }
    }

    @UnsupportedAppUsage
    protected void logd(String msg) {
        Rlog.d(LOG_TAG, "[IccPbInterfaceManager] " + msg);
    }

    @UnsupportedAppUsage
    protected void loge(String msg) {
        Rlog.e(LOG_TAG, "[IccPbInterfaceManager] " + msg);
    }

    /**
     * Replace oldAdn with newAdn in ADN-like record in EF
     *
     * getAdnRecordsInEf must be called at least once before this function,
     * otherwise an error will be returned. Currently the email field
     * if set in the ADN record is ignored.
     * throws SecurityException if no WRITE_CONTACTS permission
     *
     * @param efid must be one among EF_ADN, EF_FDN, and EF_SDN
     * @param oldTag adn tag to be replaced
     * @param oldPhoneNumber adn number to be replaced
     *        Set both oldTag and oldPhoneNubmer to "" means to replace an
     *        empty record, aka, insert new record
     * @param newTag adn tag to be stored
     * @param newPhoneNumber adn number ot be stored
     *        Set both newTag and newPhoneNubmer to "" means to replace the old
     *        record with empty one, aka, delete old record
     * @param pin2 required to update EF_FDN, otherwise must be null
     * @return true for success
     */
    public boolean
    updateAdnRecordsInEfBySearch (int efid,
            String oldTag, String oldPhoneNumber,
            String newTag, String newPhoneNumber, String pin2) {


        if (mPhone.getContext().checkCallingOrSelfPermission(
                android.Manifest.permission.WRITE_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException(
                    "Requires android.permission.WRITE_CONTACTS permission");
        }


        if (DBG) logd("updateAdnRecordsInEfBySearch: efid=0x" +
                Integer.toHexString(efid).toUpperCase() + " ("+ Rlog.pii(LOG_TAG, oldTag) + "," +
                Rlog.pii(LOG_TAG, oldPhoneNumber) + ")" + "==>" + " ("+ Rlog.pii(LOG_TAG, newTag) +
                "," + Rlog.pii(LOG_TAG, newPhoneNumber) + ")"+ " pin2=" + Rlog.pii(LOG_TAG, pin2));

        efid = updateEfForIccType(efid);

        checkThread();
        Request updateRequest = new Request();
        synchronized (updateRequest) {
            Message response = mBaseHandler.obtainMessage(EVENT_UPDATE_DONE, updateRequest);
            AdnRecord oldAdn = new AdnRecord(oldTag, oldPhoneNumber);
            AdnRecord newAdn = new AdnRecord(newTag, newPhoneNumber);
            if (mAdnCache != null) {
                mAdnCache.updateAdnBySearch(efid, oldAdn, newAdn, pin2, response);
                waitForResult(updateRequest);
            } else {
                loge("Failure while trying to update by search due to uninitialised adncache");
            }
        }
        return (boolean) updateRequest.mResult;
    }

    /**
     * Replace oldAdn with newAdn in ADN-like record in EF
     *
     * getAdnRecordsInEf must be called at least once before this function,
     * otherwise an error will be returned. Currently the email field
     * if set in the ADN record is ignored.
     * throws SecurityException if no WRITE_CONTACTS permission
     *
     * @param efid must be one among EF_ADN, EF_FDN, and EF_SDN
     * @param oldTag adn tag to be replaced
     * @param oldPhoneNumber adn number to be replaced
     *        Set both oldTag and oldPhoneNubmer to "" means to replace an
     *        empty record, aka, insert new record
     * @param oldEmailList emails to be replaced
     * @param oldAnr anr to be replaced
     * @param oldSne sne to be replaced
     * @param oldGrp grp to be replaced
     * @param newTag adn tag to be stored
     * @param newPhoneNumber adn number ot be stored
     *        Set both newTag and newPhoneNubmer to "" means to replace the old
     *        record with empty one, aka, delete old record
     * @param newEmailList emails to be stored
     * @param newAnr anr to be stored
     * @param newAas aas to be stored
     * @param newSne sne to be stored
     * @param newGrp grp to be stored
     * @param newGas gas to be stored
     * @param pin2 required to update EF_FDN, otherwise must be null
     * @return simIndex updated
     */
    public int updateAdnRecordsInEfBySearch(int efid, String oldTag,
            String oldPhoneNumber, String[] oldEmailList, String oldAnr,
            String oldSne, String oldGrp, String newTag, String newPhoneNumber,
            String[] newEmailList, String newAnr, String newAas, String newSne,
            String newGrp, String newGas, String pin2) {

        if (mPhone.getContext().checkCallingOrSelfPermission(
                android.Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException(
                    "Requires android.permission.WRITE_CONTACTS permission");
        }
        if (DBG) {
            logd("updateAdnRecordsInEfBySearch: efid = 0x"
                    + Integer.toHexString(efid).toUpperCase()
                    + " (" + newTag + "," + newPhoneNumber + ")" + ", pin2=" + pin2);
        }

        int newid = updateEfForIccType(efid);

        checkThread();
        Request updateRequest = new Request();
        synchronized (updateRequest) {
            Message response = mBaseHandler.obtainMessage(EVENT_UPDATE_DONE, updateRequest);
            AdnRecord oldAdn = null;
            AdnRecord newAdn = null;
            if (mAdnCache == null) {
                loge("Failure while trying to update by search due to uninitialised adncache");
                return -1;
            }
            if (newid == IccConstants.EF_PBR) {
                oldAdn = new AdnRecord(oldTag, oldPhoneNumber, oldEmailList,
                        oldAnr, "", oldSne, oldGrp, "");
                newAdn = new AdnRecord(newTag, newPhoneNumber, newEmailList,
                        newAnr, newAas, newSne, newGrp, newGas);

                mAdnCache.updateUSIMAdnBySearch(newid, oldAdn, newAdn, pin2,
                        response);

            } else {
                oldAdn = new AdnRecord(oldTag, oldPhoneNumber);
                newAdn = new AdnRecord(newTag, newPhoneNumber);
                mAdnCache.updateAdnBySearch(newid, oldAdn, newAdn, pin2,
                        response);
            }
            waitForResult(updateRequest);
        }
        return (int) updateRequest.mResult;
    }

    /**
     * Update an ADN-like EF record by record index
     *
     * This is useful for iteration the whole ADN file, such as write the whole
     * phone book or erase/format the whole phonebook. Currently the email field
     * if set in the ADN record is ignored.
     * throws SecurityException if no WRITE_CONTACTS permission
     *
     * @param efid must be one among EF_ADN, EF_FDN, and EF_SDN
     * @param newTag adn tag to be stored
     * @param newPhoneNumber adn number to be stored
     *        Set both newTag and newPhoneNubmer to "" means to replace the old
     *        record with empty one, aka, delete old record
     * @param index is 1-based adn record index to be updated
     * @param pin2 required to update EF_FDN, otherwise must be null
     * @return true for success
     */
    public boolean
    updateAdnRecordsInEfByIndex(int efid, String newTag,
            String newPhoneNumber, int index, String pin2) {

        if (mPhone.getContext().checkCallingOrSelfPermission(
                android.Manifest.permission.WRITE_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException(
                    "Requires android.permission.WRITE_CONTACTS permission");
        }

        if (DBG) logd("updateAdnRecordsInEfByIndex: efid=0x" +
                Integer.toHexString(efid).toUpperCase() + " Index=" + index + " ==> " + "(" +
                Rlog.pii(LOG_TAG, newTag) + "," + Rlog.pii(LOG_TAG, newPhoneNumber) + ")" +
                " pin2=" + Rlog.pii(LOG_TAG, pin2));


        checkThread();
        Request updateRequest = new Request();
        synchronized (updateRequest) {
            Message response = mBaseHandler.obtainMessage(EVENT_UPDATE_DONE, updateRequest);
            AdnRecord newAdn = new AdnRecord(newTag, newPhoneNumber);
            if (mAdnCache != null) {
                mAdnCache.updateAdnByIndex(efid, newAdn, index, pin2, response);
                waitForResult(updateRequest);
            } else {
                loge("Failure while trying to update by index due to uninitialised adncache");
            }
        }
        return (boolean) updateRequest.mResult;
    }

    /**
     * Update an ADN-like EF record by record index
     *
     * This is useful for iteration the whole ADN file, such as write the whole
     * phone book or erase/format the whole phonebook. Currently the email field
     * if set in the ADN record is ignored.
     * throws SecurityException if no WRITE_CONTACTS permission
     *
     * @param efid must be one among EF_ADN, EF_FDN, and EF_SDN
     * @param newTag adn tag to be stored
     * @param newPhoneNumber adn number to be stored
     *        Set both newTag and newPhoneNubmer to "" means to replace the old
     *        record with empty one, aka, delete old record
     * @param newEmailList email list to be stored
     * @param newAnr anr to be stored
     * @param newAas aas to be stored
     * @param newSne sne to be stored
     * @param newGrp grp to be stored
     * @param newGas gas to be stored
     * @param index is 1-based adn record index to be updated
     * @param pin2 required to update EF_FDN, otherwise must be null
     * @return simIndex updated
     */
    public int updateAdnRecordsInEfByIndex(int efid, String newTag,
             String newPhoneNumber, String[] newEmailList, String newAnr,
             String newAas, String newSne, String newGrp, String newGas,
             int index, String pin2) {

        if (mPhone.getContext().checkCallingOrSelfPermission(
                android.Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException(
                    "Requires android.permission.WRITE_CONTACTS permission");
        }

        if (DBG) {
            logd("updateAdnRecordsInEfByIndex: efid=0x"
                    + Integer.toHexString(efid).toUpperCase() + " Index=" + index + " ==> " + "("
                    + Rlog.pii(LOG_TAG, newTag) + "," + Rlog.pii(LOG_TAG, newPhoneNumber) + ","
                    + Rlog.pii(LOG_TAG, Arrays.toString(newEmailList)) + ","
                    + Rlog.pii(LOG_TAG, newAnr) + "," + Rlog.pii(LOG_TAG, newAas) + ","
                    + Rlog.pii(LOG_TAG, newSne) + "," + Rlog.pii(LOG_TAG, newGrp) + ","
                    + Rlog.pii(LOG_TAG, newGas) + "," + ")"
                    + " pin2=" + Rlog.pii(LOG_TAG, pin2));
        }

        int newid = updateEfForIccType(efid);

        checkThread();
        Request updateRequest = new Request();
        synchronized (updateRequest) {
            Message response = mBaseHandler.obtainMessage(EVENT_UPDATE_DONE, updateRequest);
            AdnRecord newAdn = null;
            if (mAdnCache == null) {
                loge("Failure while trying to update by search due to uninitialised adncache");
                return -1;
            }
            if (newid == IccConstants.EF_PBR) {
                newAdn = new AdnRecord(newTag, newPhoneNumber, newEmailList,
                        newAnr, newAas, newSne, newGrp, newGas);
                mAdnCache.updateUSIMAdnByIndex(newid, index, newAdn, pin2,
                        response);
            } else {
                newAdn = new AdnRecord(newTag, newPhoneNumber);
                mAdnCache.updateAdnByIndex(newid, newAdn, index, pin2,
                        response);
            }
            waitForResult(updateRequest);
        }
        return (int) updateRequest.mResult;
    }

    /**
     * Get the capacity of records in efid
     *
     * @param efid the EF id of a ADN-like ICC
     * @return  int[3] array
     *            recordSizes[0]  is the single record length
     *            recordSizes[1]  is the total length of the EF file
     *            recordSizes[2]  is the number of records in the EF file
     */
    public int[] getAdnRecordsSize(int efid) {
        if (DBG) logd("getAdnRecordsSize: efid=" + efid);
        if (efid == IccConstants.EF_ADN
                && updateEfForIccType(efid) == IccConstants.EF_PBR) {
            int[] size = getUsimAdnRecordsSize();
            if (null == size || size[0] == 0) {
                size = getRecordsSize(efid);
            }
            return size;
        } else {
            return getRecordsSize(efid);
        }
    }

    /**
     * Get the capacity of records in efid
     *
     * @param efid the EF id
     * @return  int[3] array
     *            recordSizes[0]  is the single record length
     *            recordSizes[1]  is the total length of the EF file
     *            recordSizes[2]  is the number of records in the EF file
     */
    public int[] getRecordsSize(int efid) {
        if (DBG) logd("getRecordsSize: efid = 0x" + Integer.toHexString(efid).toUpperCase());
        if (efid <= 0) {
            loge("the efid is invalid");
            return null;
        }

        checkThread();
        Request getSizeRequest = new Request();
        synchronized (getSizeRequest) {
            //Using mBaseHandler, no difference in EVENT_GET_SIZE_DONE handling
            Message response = mBaseHandler.obtainMessage(EVENT_GET_SIZE_DONE, getSizeRequest);
            IccFileHandler fh = mPhone.getIccFileHandler();
            if (fh != null) {
                fh.getEFLinearRecordSize(efid, response);
                waitForResult(getSizeRequest);
            }
        }

        return getSizeRequest.mResult == null ? new int[3] : (int[]) getSizeRequest.mResult;
    }

    private int[] getUsimAdnRecordsSize() {
        UsimPhoneBookManager mUsimPhoneBookManager = getUsimPhoneBookManager();
        if (mUsimPhoneBookManager == null) {
            return null;
        }
        return mUsimPhoneBookManager.getAdnRecordsSize();
    }

    private UsimPhoneBookManager getUsimPhoneBookManager() {
        if (mAdnCache != null && isHasUsimApp()) {
            return mAdnCache.getUsimPhoneBookManager();
        }

        return null;
    }


    /**
     * Loads the AdnRecords in efid and returns them as a
     * List of AdnRecords
     *
     * throws SecurityException if no READ_CONTACTS permission
     *
     * @param efid the EF id of a ADN-like ICC
     * @return List of AdnRecord
     */
    public List<AdnRecord> getAdnRecordsInEf(int efid) {

        if (mPhone.getContext().checkCallingOrSelfPermission(
                android.Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException(
                    "Requires android.permission.READ_CONTACTS permission");
        }

        efid = updateEfForIccType(efid);
        if (DBG) logd("getAdnRecordsInEF: efid=0x" + Integer.toHexString(efid).toUpperCase());

        checkThread();
        Request loadRequest = new Request();
        synchronized (loadRequest) {
            Message response = mBaseHandler.obtainMessage(EVENT_LOAD_DONE, efid, 0, loadRequest);
            if (mAdnCache != null) {
                mAdnCache.requestLoadAllAdnLike(efid, mAdnCache.extensionEfForEf(efid), response);
                waitForResult(loadRequest);
            } else {
                loge("Failure while trying to load from SIM due to uninitialised adncache");
            }
        }
        return (List<AdnRecord>) loadRequest.mResult;
    }

    @UnsupportedAppUsage
    protected void checkThread() {
        // Make sure this isn't the UI thread, since it will block
        if (mBaseHandler.getLooper().equals(Looper.myLooper())) {
            loge("query() called on the main UI thread!");
            throw new IllegalStateException(
                    "You cannot call query on this provder from the main UI thread.");
        }
    }

    protected void waitForResult(Request request) {
        synchronized (request) {
            while (!request.mStatus.get()) {
                try {
                    request.wait();
                } catch (InterruptedException e) {
                    logd("interrupted while trying to update by search");
                }
            }
        }
    }

    @UnsupportedAppUsage
    private int updateEfForIccType(int efid) {
        // Check if PBR file exist or if PBR file contain adn
        boolean isPbrFileExisting = false;
        boolean isContainAdnInPbr = false;
        if (mAdnCache != null && mAdnCache.getUsimPhoneBookManager() != null) {
            isPbrFileExisting = mAdnCache.getUsimPhoneBookManager().isPbrFileExisting();
            isContainAdnInPbr = mAdnCache.getUsimPhoneBookManager().isContainAdnInPbr();
        }

        // Check if we are trying to read ADN records
        if (efid == IccConstants.EF_ADN) {
            if (isHasUsimApp() && isPbrFileExisting && isContainAdnInPbr) {
                return IccConstants.EF_PBR;
            }
        }
        return efid;
    }

    /**
     * Loads the gas in efid and returns them as a list of gas
     *
     * throws SecurityException if no READ_CONTACTS permission
     *
     * @return list of gas string
     */
    public synchronized List<String> getGasInEf() {
        if (DBG) logd("getGasInEf");
        if (mPhone.getContext().checkCallingOrSelfPermission(
                android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException(
                    "Requires android.permission.READ_CONTACTS permission");
        }

        if (!isHasUsimApp()) {
            loge("Can not get gas from a sim card");
            return null;
        }

        if (mAdnCache == null) {
            loge("Failure while trying to update by search due to uninitialised adncache");
            return new ArrayList<String>();
        }

        return mAdnCache.loadGasFromUsim();
    }

    /**
     * Replace oldName with newName in GAS-like record in EF
     *
     * throws SecurityException if no READ_CONTACTS permission
     *
     * @param oldName group name to be replaced
     * @param newName group name to be stored
     * @return gasIndex updated
     */
    public int updateUsimGroupBySearch(String oldName, String newName) {
        if (mPhone.getContext().checkCallingOrSelfPermission(
                android.Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException(
                    "Requires android.permission.WRITE_CONTACTS permission");
        }

        if (mAdnCache == null) {
            loge("Failure while trying to update by search due to uninitialised adncache");
            return -1;
        }

        return mAdnCache.updateGasBySearch(oldName, newName);
    }

    /**
     * Update an GAS-like EF record by record index
     *
     * throws SecurityException if no READ_CONTACTS permission
     *
     * @param newName group name to be stored
     * @param groupId is 1-based gas record index to be updated
     * @return gasIndex updated
     */
    public int updateUsimGroupByIndex(String newName, int groupId) {
        if (mPhone.getContext().checkCallingOrSelfPermission(
                android.Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException(
                    "Requires android.permission.WRITE_CONTACTS permission");
        }

        if (mAdnCache == null) {
            loge("Failure while trying to update by search due to uninitialised adncache");
            return -1;
        }

        return mAdnCache.updateGasByIndex(newName, groupId);
    }

    /**
     * Loads the aas in efid and returns them as a list of aas
     *
     * throws SecurityException if no READ_CONTACTS permission
     *
     * @return list of aas string
     */
    public synchronized List<String> getAasInEf() {
        if (mPhone.getContext().checkCallingOrSelfPermission(
                android.Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException(
                    "Requires android.permission.READ_CONTACTS permission");
        }

        if (mAdnCache == null) {
            loge("Failure while trying to update by search due to uninitialised adncache");
            return new ArrayList<String>();
        }

        return mAdnCache.loadAasFromUsim();
    }

    /**
     * Replace oldName with newName in AAS-like record in EF
     *
     * throws SecurityException if no READ_CONTACTS permission
     *
     * @param oldName aas to be replaced
     * @param newName aas to be stored
     * @return aasIndex updated
     */
    public int updateUsimAasBySearch(String oldName, String newName) {
        if (mPhone.getContext().checkCallingOrSelfPermission(
                android.Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException(
                    "Requires android.permission.WRITE_CONTACTS permission");
        }

        if (mAdnCache == null) {
            loge("Failure while trying to update by search due to uninitialised adncache");
            return -1;
        }

        return mAdnCache.updateAasBySearch(oldName, newName);
    }

    /**
     * Update an AAS-like EF record by record index
     *
     * throws SecurityException if no READ_CONTACTS permission
     *
     * @param newName aas to be stored
     * @param aasIndex is 1-based aas record index to be updated
     * @return aasIndex updated
     */
    public int updateUsimAasByIndex(String newName, int aasIndex) {
        if (mPhone.getContext().checkCallingOrSelfPermission(
                android.Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException(
                    "Requires android.permission.WRITE_CONTACTS permission");
        }

        if (mAdnCache == null) {
            loge("Failure while trying to update by search due to uninitialised adncache");
            return -1;
        }

        return mAdnCache.updateAasByIndex(newName, aasIndex);
    }

    /**
     * Get inserted sim index
     *
     * @return inserted sim index
     */
    public int getInsertIndex() {
        if (mAdnCache == null) {
            loge("Failure while trying to update by search due to uninitialised adncache");
            return -1;
        }
        return mAdnCache.getInsertId();
    }

    private boolean isHasUsimApp() {
        return mPhone.getCurrentUiccAppType() == AppType.APPTYPE_USIM;
    }
}

