/*
 * Copyright (C) 2006, 2012 The Android Open Source Project
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

import android.os.AsyncResult;
import android.os.Message;
import android.telephony.Rlog;

import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.uicc.UiccCardApplication;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * {@hide}
 * This class should be used to access files in USIM ADF
 */
public final class UsimFileHandler extends IccFileHandler implements IccConstants {
    static final String LOG_TAG = "UsimFH";

    private static final int EVENT_READ_EF_LINEAR_RECORD_DONE = 12;
    private final int[] mDualMapFile = {EF_SMS, EF_PBR, EF_SDN, EF_EXT3};

    private CommandsInterface mCi;
    private Map<Integer, String> mDualMapFileList;

    public UsimFileHandler(UiccCardApplication app, String aid, CommandsInterface ci) {
        super(app, aid, ci);
        mCi = ci;
        initDualMapFileSet();
    }

    @Override
    protected String getEFPath(int efid) {
        String path = getEfPathFromList(efid);
        if (path != null) {
            return path;
        }

        switch(efid) {
        case EF_SMS:
        case EF_EXT5:
        case EF_EXT6:
        case EF_MWIS:
        case EF_MBI:
        case EF_SPN:
        case EF_AD:
        case EF_MBDN:
        case EF_PNN:
        case EF_OPL:
        case EF_SPDI:
        case EF_SST:
        case EF_CFIS:
        case EF_MAILBOX_CPHS:
        case EF_VOICE_MAIL_INDICATOR_CPHS:
        case EF_CFF_CPHS:
        case EF_SPN_CPHS:
        case EF_SPN_SHORT_CPHS:
        case EF_FDN:
        case EF_SDN:
        case EF_EXT3:
        case EF_MSISDN:
        case EF_EXT2:
        case EF_INFO_CPHS:
        case EF_CSP_CPHS:
        case EF_GID1:
        case EF_GID2:
        case EF_LI:
        case EF_PLMN_W_ACT:
        case EF_OPLMN_W_ACT:
        case EF_HPLMN_W_ACT:
        case EF_EHPLMN:
        case EF_FPLMN:
        case EF_LRPLMNSI:
        case EF_HPPLMN:
            return MF_SIM + DF_ADF;

        case EF_PBR:
                // EF_PBR file may be under TELECOM("3F007F10") or ADF("3F007FFF").
                if (mDualMapFileList != null) {
                    return mDualMapFileList.get(EF_PBR);
                } else {
                    return MF_SIM + DF_TELECOM + DF_PHONEBOOK;
                }
        }
        path = getCommonIccEFPath(efid);
        if (path == null) {
            // EF_PBR file may be under TELECOM("3F007F10") or ADF("3F007FFF").
            if (mDualMapFileList != null) {
                return mDualMapFileList.get(EF_PBR);
            } else {
                return MF_SIM + DF_TELECOM + DF_PHONEBOOK;
            }
        }
        return path;
    }


    private void initDualMapFileSet() {
        mDualMapFileList = new HashMap<Integer, String>();

        mDualMapFileList.put(mDualMapFile[0], MF_SIM + DF_ADF);
        mDualMapFileList.put(mDualMapFile[1], MF_SIM + DF_TELECOM
                + DF_PHONEBOOK);
        mDualMapFileList.put(mDualMapFile[2], MF_SIM + DF_ADF);
        mDualMapFileList.put(mDualMapFile[3], MF_SIM + DF_ADF);
    }

    private void clearDualMapFileSet() {
        if (mDualMapFileList != null) {
            mDualMapFileList.clear();
            mDualMapFileList = null;
        }
    }

    private void updatePathOfDualMapFile(int efid, String path) {
        if (mDualMapFileList != null) {
            mDualMapFileList.put(efid, path);
        }

    }

    /**
     * Dispose the UsimFileHandler.
     */
    public void dispose() {
        super.dispose();
        clearDualMapFileSet();
    }

    protected void finalize() throws Throwable {
        super.finalize();
    }

    private boolean isDualMapFile(int fileId) {
        if (mDualMapFileList == null) {
            return false;
        }

        if (mDualMapFileList.containsKey(fileId)) {
            return true;
        }
        return false;
    }

    private boolean isFinishLoadFile(int fileId, int pathNum) {
        if (isDualMapFile(fileId)) {
            if (pathNum == 1) {
                return true;
            }
            if (pathNum == 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Get new root path of the file.
     *
     * @param efid of path to retrieve
     * @return new root path of the file.
     */
    protected String getEFPathofUsim(int efid) {
        String oldPath = getEFPath(efid);

        if (oldPath.length() < 8) {
            return null;
        }

        String pathFirst = oldPath.substring(0, 4);
        String pathSecond = oldPath.substring(4, 8);

        String pathLast = "";
        if (oldPath.length() > 8) {
            pathLast = oldPath.substring(8, oldPath.length());
        }

        if (pathSecond.equals(DF_ADF)) {
            pathSecond = DF_TELECOM;
        } else if (pathSecond.equals(DF_TELECOM)) {
            pathSecond = DF_ADF;
        } else {
            return null;
        }

        String newPath = pathFirst + pathSecond + pathLast;
        updatePathOfDualMapFile(efid, newPath);
        return newPath;
    }

    /**
     * If load file fail, try to load the file under the new path.
     * Ex: EF_PBR file may be under TELECOM("3F007F10") or ADF("3F007FFF").
     *
     * @param fileId of path to retrieve
     * @param pathNum file path number
     * @param event message.what
     * @return true for load file again.
     */
    private boolean loadFileAgain(int fileId, int pathNum, int event, Object obj) {
        if (isFinishLoadFile(fileId, pathNum)) {
            return false;
        } else {
            String newPath = getEFPathofUsim(fileId);
            if (newPath == null) {
                return false;
            }

            ((LoadLinearFixedContext) obj).mPath = newPath;
            Message response = obtainMessage(event, fileId, 1, obj);

            logd("loadFileAgain, try again newPath: " + newPath);
            mCi.iccIO(COMMAND_GET_RESPONSE, fileId, newPath, 0, 0,
                    GET_RESPONSE_EF_SIZE_BYTES, null, null, response);
        }
        return true;
    }

    @Override
    public void handleMessage(Message msg) {
        AsyncResult ar;
        IccIoResult result;
        Message response = null;
        IccFileHandler.LoadLinearFixedContext lc;

        IccException iccException;
        byte[] data;
        int size, fcp_size;
        int fileid;
        int[] recordSize;
        int pathNum = msg.arg2;
        String path = null;

        try {
            switch (msg.what) {
                case EVENT_GET_EF_LINEAR_RECORD_SIZE_DONE:
                    ar = (AsyncResult) msg.obj;
                    lc = (IccFileHandler.LoadLinearFixedContext) ar.userObj;
                    result = (IccIoResult) ar.result;
                    response = lc.mOnLoaded;
                    if (ar.exception != null) {
                        sendResult(response, null, ar.exception);
                        break;
                    }

                    data = result.payload;
                    fileid = lc.mEfid;
                    iccException = result.getException();
                    if ((iccException != null || (fileid == EF_PBR && !isDataValid(data)))
                            && loadFileAgain(fileid, pathNum, msg.what, lc)) {
                        logd("load file again");
                        return;
                    }

                    if (iccException != null) {
                        sendResult(response, null, iccException);
                        break;
                    }

                    if (TYPE_EF != data[RESPONSE_DATA_FILE_TYPE]
                            || (EF_TYPE_LINEAR_FIXED != data[RESPONSE_DATA_STRUCTURE]
                            && EF_TYPE_CYCLIC != data[RESPONSE_DATA_STRUCTURE])) {
                        throw new IccFileTypeMismatch();
                    }

                    recordSize = new int[3];
                    recordSize[0] = data[RESPONSE_DATA_RECORD_LENGTH] & 0xFF;
                    recordSize[1] = ((data[RESPONSE_DATA_FILE_SIZE_1] & 0xff) << 8)
                            + (data[RESPONSE_DATA_FILE_SIZE_2] & 0xff);
                    recordSize[2] = recordSize[1] / recordSize[0];

                    sendResult(response, recordSize, null);
                    break;

                case EVENT_GET_RECORD_SIZE_IMG_DONE:
                case EVENT_GET_RECORD_SIZE_DONE:
                    logd("msg.what = " + msg.what);
                    ar = (AsyncResult) msg.obj;
                    lc = (IccFileHandler.LoadLinearFixedContext) ar.userObj;
                    result = (IccIoResult) ar.result;
                    response = lc.mOnLoaded;
                    if (ar.exception != null) {
                        sendResult(response, null, ar.exception);
                        break;
                    }

                    data = result.payload;
                    path = lc.mPath;
                    fileid = lc.mEfid;
                    iccException = result.getException();
                    if ((iccException != null || (fileid == EF_PBR && !isDataValid(data)))
                            && loadFileAgain(fileid, pathNum, msg.what, lc)) {
                        logd("load file again");
                        return;
                    }

                    if (iccException != null) {
                        sendResult(response, null, iccException);
                        break;
                    }

                    if (TYPE_EF != data[RESPONSE_DATA_FILE_TYPE]) {
                        throw new IccFileTypeMismatch();
                    }

                    if (EF_TYPE_LINEAR_FIXED != data[RESPONSE_DATA_STRUCTURE]
                            && EF_TYPE_CYCLIC != data[RESPONSE_DATA_STRUCTURE]) {
                        throw new IccFileTypeMismatch();
                    }

                    lc.mRecordSize = data[RESPONSE_DATA_RECORD_LENGTH] & 0xFF;

                    size = ((data[RESPONSE_DATA_FILE_SIZE_1] & 0xff) << 8)
                            + (data[RESPONSE_DATA_FILE_SIZE_2] & 0xff);

                    lc.mCountRecords = size / lc.mRecordSize;

                    if (lc.mLoadAll) {
                        lc.results = new ArrayList<byte[]>(lc.mCountRecords);
                    }

                    if (path == null) {
                        path = getEFPath(lc.mEfid);
                    }
                    mCi.iccIOForApp(COMMAND_READ_RECORD, lc.mEfid, path,
                            lc.mRecordNum, READ_RECORD_MODE_ABSOLUTE,
                            lc.mRecordSize, null, null, mAid,
                            obtainMessage(EVENT_READ_RECORD_DONE, lc));
                    break;

                case EVENT_GET_BINARY_SIZE_DONE:
                    ar = (AsyncResult) msg.obj;
                    response = (Message) ar.userObj;
                    result = (IccIoResult) ar.result;
                    if (ar.exception != null) {
                        sendResult(response, null, ar.exception);
                        break;
                    }

                    iccException = result.getException();
                    fileid = msg.arg1;
                    if (iccException != null
                            && loadFileAgain(fileid, pathNum, msg.what, response)) {
                        logd("load file again");
                        return;
                    }
                    super.handleMessage(msg);
                    break;

                case EVENT_READ_RECORD_DONE:
                    ar = (AsyncResult) msg.obj;
                    lc = (IccFileHandler.LoadLinearFixedContext) ar.userObj;
                    result = (IccIoResult) ar.result;
                    response = lc.mOnLoaded;

                    if (ar.exception != null) {
                        sendResult(response, null, ar.exception);
                        break;
                    }

                    iccException = result.getException();
                    if (iccException != null) {
                        if (lc.mLoadAll && lc.results != null && lc.results.size() > 0) {
                            sendResult(response, lc.results, null);
                        } else {
                            sendResult(response, null, iccException);
                        }
                        break;
                    }

                    if (!lc.mLoadAll) {
                        sendResult(response, result.payload, null);
                    } else {
                        lc.results.add(result.payload);
                        lc.mRecordNum++;

                        if (lc.mRecordNum > lc.mCountRecords) {
                            sendResult(response, lc.results, null);
                        } else {
                            if (path == null) {
                                path = getEFPath(lc.mEfid);
                            }

                            mCi.iccIO(
                                    COMMAND_READ_RECORD,
                                    lc.mEfid,
                                    path,
                                    lc.mRecordNum,
                                    READ_RECORD_MODE_ABSOLUTE,
                                    lc.mRecordSize,
                                    null,
                                    null,
                                    obtainMessage(EVENT_READ_RECORD_DONE, 0,
                                            pathNum, lc));
                        }
                    }
                    break;

                case EVENT_READ_BINARY_DONE:
                    ar = (AsyncResult) msg.obj;
                    response = (Message) ar.userObj;
                    result = (IccIoResult) ar.result;

                    if (ar.exception != null) {
                        sendResult(response, null, ar.exception);
                        break;
                    }

                    iccException = result.getException();
                    if (iccException != null) {
                        sendResult(response, null, iccException);
                        break;
                    }
                    sendResult(response, result.payload, null);
                    break;

                case EVENT_READ_EF_LINEAR_RECORD_DONE:
                    ar = (AsyncResult) msg.obj;
                    result = (IccIoResult) ar.result;
                    response = (Message) ar.userObj;
                    if (processException(response, (AsyncResult) msg.obj)) {
                        break;
                    }
                    sendResult(response, result.payload, null);
                    break;

                default:
                    super.handleMessage(msg);

            }
        } catch (Exception exc) {
            exc.printStackTrace();
            if (response != null && response.getTarget() != null) {
                sendResult(response, null, exc);
            } else {
                loge("uncaught exception" + exc);
            }
        }
    }

    /**
     * Returns the root path of the EF file.
     *
     * @param efid of path to retrieve
     * @return root path of the file.
     */
    private String getEfPathFromList(int efid) {
        String path = null;
        if (mDualMapFileList == null) {
            return null;
        }

        if (mDualMapFileList.containsKey(efid)) {
            path = mDualMapFileList.get(efid);
            if (path != null) {
                return path;
            }
        }

        return null;
    }

    /**
     * Returns true if data is valid
     *
     * @param data record data in file
     * @return true for valid data
     */
    protected boolean isDataValid(byte[] data) {
        boolean isValid = false;
        for (int i = 0; i < data.length; i++) {
            if (data[i] != (byte) 0xFF) {
                isValid = true;
                break;
            }
        }
        return isValid;
    }

    @Override
    public void loadEFLinearFixed(int fileid, int recordNum, int recordSize,
                                  Message onLoaded) {
        Message response = obtainMessage(EVENT_READ_EF_LINEAR_RECORD_DONE,
                fileid, recordNum, onLoaded);

        mCi.iccIOForApp(COMMAND_READ_RECORD, fileid, getEFPath(fileid),
                recordNum, READ_RECORD_MODE_ABSOLUTE, recordSize, null, null,
                mAid, response);
    }

    private void sendResult(Message response, Object result, Throwable ex) {
        if (response == null) {
            return;
        }
        AsyncResult.forMessage(response, result, ex);
        response.sendToTarget();
    }

    private boolean processException(Message response, AsyncResult ar) {
        IccException iccException;
        boolean flag = false;
        IccIoResult result = (IccIoResult) ar.result;
        if (ar.exception != null) {
            sendResult(response, null, ar.exception);
            flag = true;
        } else {
            iccException = result.getException();
            if (iccException != null) {
                sendResult(response, null, iccException);
                flag = true;
            }
        }
        return flag;
    }

    @Override
    protected void logd(String msg) {
        Rlog.d(LOG_TAG, msg);
    }

    @Override
    protected void loge(String msg) {
        Rlog.e(LOG_TAG, msg);
    }
}
