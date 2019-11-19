package com.android.internal.telephony.uicc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.SparseArray;
import android.telephony.Rlog;
import android.telephony.SubscriptionManager;

import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * Used to store SIM phonebook records.
 *
 * {@hide}
 */
public final class SimPhoneBookRecordCache extends Handler {
    // Instance Variables
    private static final String LOG_TAG = "SimPhoneBookRecordCache";
    private static final boolean DBG = true;
    // member variables
    protected final CommandsInterface mCi;
    protected int mPhoneId;
    protected Context mContext;
    // the maximum number of records that can be stored in SIM phonebook
    private int mMaxAdnCount = 0;
    // the actual used number of stored records
    private int mAdnUsed = 0;
    // the maximum number of emails that can be stored in SIM phonebook
    private int mMaxEmailCount = 0;
    // the actual used number of stored emails
    private int mEmailUsed = 0;
    // the maximum number of additional numbers that can be stored in SIM phonebook
    private int mMaxAddNumCount = 0;
    // the actual used number of stored additional numbers
    private int mAddNumUsed = 0;
    private int mMaxNameLen = 0;
    private int mMaxNumberLen = 0;
    private int mMaxEmailLen = 0;
    private int mMaxAnrLen = 0;
    private Object mLock = new Object();
    private ArrayList<AdnRecord> mSimPbRecords;
    // If true, clear the records in the cache and re-query from modem
    private boolean mRefreshAdnCache = false;

    // People waiting for SIM phonebook records to be loaded
    ArrayList<Message> mAdnLoadingWaiters = new ArrayList<Message>();

    // Person waiting for SIM phonebook record to be updated
    Message mAdnUpdatingWaiter = null;

    //Event Constants
    static final int EVENT_INIT_ADN_DONE = 1;
    static final int EVENT_QUERY_ADN_RECORD_DONE = 2;
    static final int EVENT_LOAD_ADN_RECORD_DONE = 3;
    static final int EVENT_LOAD_ALL_SIM_PB_RECORDS_DONE = 4;
    static final int EVENT_UPDATE_ADN_RECORD_DONE = 5;
    static final int EVENT_SIM_REFRESH = 6;

    static final String ACTION_SIM_PB_INIT_DONE = "android.intent.action.ACTION_SIM_PB_INIT_DONE";

    private final BroadcastReceiver sReceiver = new  BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (TelephonyIntents.ACTION_SIM_STATE_CHANGED.equals(intent.getAction())) {
                int phoneId = intent.getIntExtra(PhoneConstants.PHONE_KEY,
                                            SubscriptionManager.INVALID_SIM_SLOT_INDEX);
                String simStatus = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
                if (IccCardConstants.INTENT_VALUE_ICC_ABSENT.equals(simStatus)
                        && mPhoneId == phoneId) {
                    log("ACTION_SIM_STATE_CHANGED intent received simStatus: "
                            + simStatus + "phoneId: " + phoneId);
                    invalidateAdnCache();
                }
            }
        }
    };

    // Constructor
    public SimPhoneBookRecordCache(Context context, int phoneId, CommandsInterface ci) {
        mCi = ci;
        mPhoneId = phoneId;
        mContext = context;
        mSimPbRecords = new ArrayList<AdnRecord>();
        mCi.registerForPhonebookInitDone(this, EVENT_INIT_ADN_DONE, null);
        mCi.registerForIccRefresh(this, EVENT_SIM_REFRESH, null);

        IntentFilter intentFilter = new IntentFilter(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        context.registerReceiver(sReceiver, intentFilter);
    }

    private void clear() {
        mMaxAdnCount = 0;
        mAdnUsed = 0;
        mMaxEmailCount = 0;
        mEmailUsed = 0;
        mMaxAddNumCount = 0;
        mAddNumUsed = 0;

        mSimPbRecords.clear();

        mRefreshAdnCache = false;
    }

    public void reset() {
        mAdnLoadingWaiters.clear();
        clearUpdatingWriter();

        clear();
    }

    private void clearUpdatingWriter() {
        sendErrorResponse(mAdnUpdatingWaiter, "SimPhoneBookRecordCache reset");
        mAdnUpdatingWaiter = null;
    }

    private void sendErrorResponse(Message response, String errString) {
        if (response != null) {
            Exception e = new RuntimeException(errString);
            AsyncResult.forMessage(response).exception = e;
            response.sendToTarget();
        }
    }

    private void notifyAndClearWaiters() {
        if (mAdnLoadingWaiters == null) {
            return;
        }

        for (Message response : mAdnLoadingWaiters){
            if ((response != null)) {
                AsyncResult.forMessage(response).result = mSimPbRecords;
                response.sendToTarget();
            }
        }

        mAdnLoadingWaiters.clear();
    }

    public void queryAdnRecord () {
        clear();

        log("start to queryAdnRecord");

        mCi.registerForPhonebookRecordInfo(this, EVENT_LOAD_ADN_RECORD_DONE, null);
        mCi.getPhonebookRecords(obtainMessage(EVENT_QUERY_ADN_RECORD_DONE));

        try {
            mLock.wait();
        } catch (InterruptedException e) {
            Rlog.e(LOG_TAG, "Interrupted Exception in queryAdnRecord");
        }

        mCi.unregisterForPhonebookRecordInfo(this);
    }


    public void requestLoadAllPbRecords(Message response) {
        if (!mAdnLoadingWaiters.isEmpty()) {
            mAdnLoadingWaiters.add(response);
            return;
        }

        mAdnLoadingWaiters.add(response);

        synchronized (mLock) {
            if (!mSimPbRecords.isEmpty()) {
                log("ADN cache has already filled in");
                if (mRefreshAdnCache) {
                    refreshAdnCache();
                } else {
                    notifyAndClearWaiters();
                }

                return;
            }

            queryAdnRecord();
        }
    }

    public void updateSimPbAdnBySearch(AdnRecord oldAdn, AdnRecord newAdn, Message response) {
        ArrayList<AdnRecord> oldAdnList = mSimPbRecords;

        if (mAdnUpdatingWaiter != null) {
            sendErrorResponse(response, "Have pending update for SIM Phonebook record");
            return;
        }

        synchronized (mLock) {
            if (!mSimPbRecords.isEmpty()) {
                log("ADN cache has already filled in");
                if (mRefreshAdnCache) {
                    refreshAdnCache();
                }
            } else {
                queryAdnRecord();
            }
        }

        if (oldAdnList == null) {
            sendErrorResponse(response, "SIM Phonebook record list not exist");
            return;
        }

        int index = -1;
        if (oldAdn.isEmpty() && !newAdn.isEmpty()) {
            // add contact
            index = 0;
        } else {
            int count = 1;
            // delete or update contact
            for (Iterator<AdnRecord> it = oldAdnList.iterator(); it.hasNext();) {
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

        if (index == 0 && mAdnUsed == mMaxAdnCount) {
            sendErrorResponse(response, "SIM Phonebook record is full");
            return;
        }

        int recordIndex = (index == 0) ? 0 : oldAdnList.get(index-1).getRecordNumber();

        SimPhoneBookRecord updateAdn = new SimPhoneBookRecord();
        updateAdn.mRecordIndex = recordIndex;
        updateAdn.mAlphaTag = newAdn.getAlphaTag();
        updateAdn.mNumber = newAdn.getNumber();
        if (newAdn.getEmails() != null) {
            updateAdn.mEmails = newAdn.getEmails();
        }
        if (newAdn.getAdditionalNumbers() != null) {
            updateAdn.mAdditionalNumbers = newAdn.getAdditionalNumbers();
        }

        mAdnUpdatingWaiter = response;

        mCi.updatePhonebookRecord(updateAdn,
                obtainMessage(EVENT_UPDATE_ADN_RECORD_DONE, index, 0, newAdn));
    }

    // Overridden from Handler
    @Override
    public void handleMessage(Message msg) {
        AsyncResult ar = (AsyncResult)msg.obj;

        switch(msg.what) {
            case EVENT_INIT_ADN_DONE:
                ar = (AsyncResult)msg.obj;
                if (ar.exception == null) {
                    invalidateAdnCache();

                    Intent intent = new Intent(ACTION_SIM_PB_INIT_DONE);
                    intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
                    SubscriptionManager.putPhoneIdAndSubIdExtra(intent, mPhoneId);
                    log("broadcast intent ACTION_SIM_PB_INIT_DONE for mPhoneId=" + mPhoneId);
                    mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
                } else {
                    log("Init ADN done Exception: " + ar.exception);
                }
                break;

            case EVENT_QUERY_ADN_RECORD_DONE:
                log("Querying ADN record done");
                if (ar.exception != null) {
                    synchronized (mLock) {
                        mLock.notify();
                    }

                    for (Message response : mAdnLoadingWaiters) {
                        sendErrorResponse(response, "Query adn record failed" + ar.exception);
                    }
                    mAdnLoadingWaiters.clear();
                    break;
                }
                mMaxAdnCount = ((int[]) (ar.result))[0];
                mAdnUsed = ((int[]) (ar.result))[1];
                mMaxEmailCount = ((int[]) (ar.result))[2];
                mEmailUsed = ((int[]) (ar.result))[3];
                mMaxAddNumCount = ((int[]) (ar.result))[4];
                mAddNumUsed = ((int[]) (ar.result))[5];
                mMaxNameLen = ((int[]) (ar.result))[6];
                mMaxNumberLen = ((int[]) (ar.result))[7];
                mMaxEmailLen = ((int[]) (ar.result))[8];
                mMaxAnrLen = ((int[]) (ar.result))[9];
                log("Max ADN count is: " + mMaxAdnCount
                    + ", Valid ADN count is: " + mAdnUsed
                    + ", Email count is: " + mMaxEmailCount
                    + ", Valid Email count is: " + mEmailUsed
                    + ", Add number count is: " + mMaxAddNumCount
                    + ", Valid Add number count is: " + mAddNumUsed
                    + ", Max name length is: " + mMaxNameLen
                    + ", Max number length is: " + mMaxNumberLen
                    + ", Max email length is: " + mMaxEmailLen
                    + ", Valid anr length is: " + mMaxAnrLen);

                if (mAdnUsed == 0 || mSimPbRecords.size() == mAdnUsed) {
                    sendMessage(obtainMessage(EVENT_LOAD_ALL_SIM_PB_RECORDS_DONE));
                }
                break;

            case EVENT_LOAD_ADN_RECORD_DONE:
                log("Loading ADN record done");
                if (ar.exception != null) {
                    break;
                }

                SimPhoneBookRecord[] PhonebookRecordInfoGroup = (SimPhoneBookRecord[])(ar.result);
                if (PhonebookRecordInfoGroup == null) {
                    break;
                }
                for (int i = 0 ; i < PhonebookRecordInfoGroup.length ; i++) {
                    if (PhonebookRecordInfoGroup[i] != null) {
                        mSimPbRecords.add(new AdnRecord(0,
                                                PhonebookRecordInfoGroup[i].getRecordIndex(),
                                                PhonebookRecordInfoGroup[i].getAlphaTag(),
                                                PhonebookRecordInfoGroup[i].getNumber(),
                                                PhonebookRecordInfoGroup[i].getEmails(),
                                                PhonebookRecordInfoGroup[i].getAdditionalNumbers()));
                    }
                }

                if (mSimPbRecords.size() == mAdnUsed) {
                    sendMessage(obtainMessage(EVENT_LOAD_ALL_SIM_PB_RECORDS_DONE));
                }
                break;

            case EVENT_LOAD_ALL_SIM_PB_RECORDS_DONE:
                log("Loading all SIM phonebook records done");
                synchronized (mLock) {
                    mLock.notify();
                }

                notifyAndClearWaiters();
                break;

            case EVENT_UPDATE_ADN_RECORD_DONE:
                log("Update ADN record done");
                Exception e = null;

                if (ar.exception == null) {
                    int index = msg.arg1;
                    AdnRecord adn = (AdnRecord) (ar.userObj);
                    int recordIndex = ((int[]) (ar.result))[0];

                    if (index == 0) {
                        // add contact
                        log("Record number for added ADN is " + recordIndex);
                        //updated the used email count in cache
                        if(adn.getEmails() != null) {
                            int usedEmailCount = mEmailUsed + adn.getEmails().length;
                            mEmailUsed = Math.min(usedEmailCount, mMaxEmailCount);
                        }
                        //updated the used additional number count in cache
                        if(adn.getAdditionalNumbers() != null) {
                            int usedAnrCount =
                                mAddNumUsed + adn.getAdditionalNumbers().length;
                            mAddNumUsed = Math.min(usedAnrCount, mMaxAddNumCount);
                        }

                        adn.setRecordNumber(recordIndex);
                        mSimPbRecords.add(adn);
                        mAdnUsed ++;
                    } else if (adn.isEmpty()){
                        //delete contact
                        AdnRecord deletedRecord = mSimPbRecords.get(index - 1);
                        int adnRecordIndex = deletedRecord.getRecordNumber();
                        log("Record number for deleted ADN is " + adnRecordIndex);
                        if(recordIndex == adnRecordIndex) {
                            //updated the used email count in cache
                            if(deletedRecord.getEmails() != null) {
                                int usedEmailCount = mEmailUsed
                                    - deletedRecord.getEmails().length;
                                mEmailUsed = Math.max(usedEmailCount, 0);
                            }
                            //updated the used additional number count in cache
                            if(deletedRecord.getAdditionalNumbers() != null) {
                                int usedAnrCount = mAddNumUsed
                                    - deletedRecord.getAdditionalNumbers().length;
                                mAddNumUsed = Math.max(usedAnrCount, 0);
                            }

                            mSimPbRecords.remove(index - 1);
                            mAdnUsed --;
                        } else {
                            e = new RuntimeException(
                                "The index for deleted ADN record did not match");
                        }
                    } else {
                        //Change contact
                        AdnRecord changedRecord = mSimPbRecords.get(index - 1);
                        int adnRecordIndex = changedRecord.getRecordNumber();
                        log("Record number for changed ADN is " + adnRecordIndex);
                        if(recordIndex == adnRecordIndex) {
                            //updated the used email count in cache
                            int oldEmailCount = changedRecord.getEmails() == null
                                ? 0 : changedRecord.getEmails().length;
                            int newEmailCount =
                                adn.getEmails() == null ? 0 : adn.getEmails().length;
                            int usedEmailCount = mEmailUsed - oldEmailCount + newEmailCount;
                            mEmailUsed = usedEmailCount > 0 && usedEmailCount <= mMaxEmailCount
                                ? usedEmailCount : mEmailUsed;
                            //updated the used additional number count in cache
                            int oldAnrCount = changedRecord.getAdditionalNumbers() == null
                                ? 0 : changedRecord.getAdditionalNumbers().length;
                            int newAnrCount = adn.getAdditionalNumbers() == null
                                ? 0 : adn.getAdditionalNumbers().length;
                            int usedAnrCount = mAddNumUsed - oldAnrCount + newAnrCount;
                            mAddNumUsed = usedAnrCount > 0 && usedAnrCount <= mMaxAddNumCount
                                ? usedAnrCount : mAddNumUsed;

                            adn.setRecordNumber(recordIndex);
                            mSimPbRecords.set(index - 1, adn);
                        } else {
                            e = new RuntimeException(
                                "The index for changed ADN record did not match");
                        }
                    }
                } else {
                   e = new RuntimeException("Update adn record failed",
                                ar.exception);
                }

                if (mAdnUpdatingWaiter != null) {
                    AsyncResult.forMessage(mAdnUpdatingWaiter, null, e);
                    mAdnUpdatingWaiter.sendToTarget();
                    mAdnUpdatingWaiter = null;
                }
                break;

            case EVENT_SIM_REFRESH:
                ar = (AsyncResult)msg.obj;
                log("SIM REFRESH occurred");
                if (ar.exception == null) {
                    IccRefreshResponse refreshRsp = (IccRefreshResponse)ar.result;
                    if (refreshRsp == null) {
                        log("IccRefreshResponse received is null");
                        break;
                    }

                    if (refreshRsp.refreshResult == IccRefreshResponse.REFRESH_RESULT_FILE_UPDATE ||
                        refreshRsp.refreshResult == IccRefreshResponse.REFRESH_RESULT_INIT) {
                          invalidateAdnCache();
                    }
                } else {
                    log("SIM refresh Exception: " + ar.exception);
                }
                break;
        }

    }

    public int getAdnCount() {
        return mMaxAdnCount;
    }

    public int getUsedAdnCount() {
        return mAdnUsed;
    }

    public int getEmailCount() {
        return mMaxEmailCount;
    }

    public int getUsedEmailCount() {
        return mEmailUsed;
    }

    public int getAnrCount() {
        return mMaxAddNumCount;
    }

    public int getUsedAnrCount() {
        return mAddNumUsed;
    }

    public int getMaxNameLen() {
        return mMaxNameLen;
    }

    public int getMaxNumberLen() {
        return mMaxNumberLen;
    }

    public int getMaxEmailLen() {
        return mMaxEmailLen;
    }

    public int getMaxAnrLen() {
        return mMaxAnrLen;
    }

    private void log(String msg) {
        if (DBG) {
            Rlog.d(LOG_TAG, msg);
        }
    }

    public void invalidateAdnCache() {
        log("invalidateAdnCache");
        mRefreshAdnCache = true;
    }

    private void refreshAdnCache() {
        log("refreshAdnCache");
        mSimPbRecords.clear();
        queryAdnRecord();
    }
}
