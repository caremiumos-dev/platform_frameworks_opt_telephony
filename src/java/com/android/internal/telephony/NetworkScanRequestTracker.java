/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static android.telephony.RadioNetworkConstants.RadioAccessNetworks.EUTRAN;
import static android.telephony.RadioNetworkConstants.RadioAccessNetworks.GERAN;
import static android.telephony.RadioNetworkConstants.RadioAccessNetworks.UTRAN;

import android.os.AsyncResult;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.Process;
import android.os.RemoteException;
import android.telephony.NetworkScan;
import android.telephony.NetworkScanRequest;
import android.telephony.RadioAccessSpecifier;
import android.telephony.TelephonyScanManager;
import android.util.Log;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages radio access network scan requests.
 *
 * Provides methods to start and stop network scan requests, and keeps track of all the live scans.
 *
 * {@hide}
 */
public final class NetworkScanRequestTracker {

    private static final String TAG = "ScanRequestTracker";

    private static final int CMD_START_NETWORK_SCAN = 1;
    private static final int EVENT_START_NETWORK_SCAN_DONE = 2;
    private static final int EVENT_RECEIVE_NETWORK_SCAN_RESULT = 3;
    private static final int CMD_STOP_NETWORK_SCAN = 4;
    private static final int EVENT_STOP_NETWORK_SCAN_DONE = 5;
    private static final int CMD_INTERRUPT_NETWORK_SCAN = 6;
    private static final int EVENT_INTERRUPT_NETWORK_SCAN_DONE = 7;

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case CMD_START_NETWORK_SCAN:
                    mScheduler.doStartScan((NetworkScanRequestInfo) msg.obj);
                    break;

                case EVENT_START_NETWORK_SCAN_DONE:
                    mScheduler.startScanDone((AsyncResult) msg.obj);
                    break;

                case EVENT_RECEIVE_NETWORK_SCAN_RESULT:
                    mScheduler.receiveResult((AsyncResult) msg.obj);
                    break;

                case CMD_STOP_NETWORK_SCAN:
                    mScheduler.doStopScan(msg.arg1);
                    break;

                case EVENT_STOP_NETWORK_SCAN_DONE:
                    mScheduler.stopScanDone((AsyncResult) msg.obj);
                    break;

                case CMD_INTERRUPT_NETWORK_SCAN:
                    mScheduler.doInterruptScan(msg.arg1);
                    break;

                case EVENT_INTERRUPT_NETWORK_SCAN_DONE:
                    mScheduler.interruptScanDone((AsyncResult) msg.obj);
                    break;
            }
        }
    };

    // The sequence number of NetworkScanRequests
    private final AtomicInteger mNextNetworkScanRequestId = new AtomicInteger(1);
    private final NetworkScanRequestScheduler mScheduler = new NetworkScanRequestScheduler();

    private void logEmptyResultOrException(AsyncResult ar) {
        if (ar.result == null) {
            Log.e(TAG, "NetworkScanResult: Empty result");
        } else {
            Log.e(TAG, "NetworkScanResult: Exception: " + ar.exception);
        }
    }

    private boolean isValidScan(NetworkScanRequestInfo nsri) {
        if (nsri.mRequest.specifiers.length > NetworkScanRequest.MAX_RADIO_ACCESS_NETWORKS) {
            return false;
        }
        for (RadioAccessSpecifier ras : nsri.mRequest.specifiers) {
            if (ras.radioAccessNetwork != GERAN && ras.radioAccessNetwork != UTRAN
                    && ras.radioAccessNetwork != EUTRAN) {
                return false;
            }
            if (ras.bands.length > NetworkScanRequest.MAX_BANDS
                    || ras.channels.length > NetworkScanRequest.MAX_CHANNELS) {
                return false;
            }
        }
        return true;
    }

    /** Sends a message back to the application via its callback. */
    private void notifyMessenger(NetworkScanRequestInfo nsri, int what, int err, Object result) {
        Messenger messenger = nsri.mMessenger;
        Message message = Message.obtain();
        message.what = what;
        message.arg1 = err;
        message.arg2 = nsri.mScanId;
        message.obj = result;
        try {
            messenger.send(message);
        } catch (RemoteException e) {
            Log.e(TAG, "Exception in notifyMessenger: " + e);
        }
    }

    /**
    * Tracks info about the radio network scan.
     *
    * Also used to notice when the calling process dies so we can self-expire.
    */
    private class NetworkScanRequestInfo implements IBinder.DeathRecipient {
        private final NetworkScanRequest mRequest;
        private final Messenger mMessenger;
        private final IBinder mBinder;
        private final Phone mPhone;
        private final int mScanId;
        private final int mUid;
        private final int mPid;

        NetworkScanRequestInfo(NetworkScanRequest r, Messenger m, IBinder b, int id, Phone phone) {
            super();
            mRequest = r;
            mMessenger = m;
            mBinder = b;
            mScanId = id;
            mPhone = phone;
            mUid = Binder.getCallingUid();
            mPid = Binder.getCallingPid();

            try {
                mBinder.linkToDeath(this, 0);
            } catch (RemoteException e) {
                binderDied();
            }
        }

        void unlinkDeathRecipient() {
            if (mBinder != null) {
                mBinder.unlinkToDeath(this, 0);
            }
        }

        @Override
        public void binderDied() {
            Log.e(TAG, "PhoneInterfaceManager NetworkScanRequestInfo binderDied("
                    + mRequest + ", " + mBinder + ")");
            interruptNetworkScan(mScanId);
        }
    }

    /**
     * Handles multiplexing and scheduling for multiple requests.
     */
    private class NetworkScanRequestScheduler {

        private NetworkScanRequestInfo mLiveRequestInfo;
        private NetworkScanRequestInfo mPendingRequestInfo;

        private void doStartScan(NetworkScanRequestInfo nsri) {
            if (nsri == null) {
                Log.e(TAG, "CMD_START_NETWORK_SCAN: nsri is null");
                return;
            }
            if (!isValidScan(nsri)) {
                notifyMessenger(nsri, TelephonyScanManager.CALLBACK_SCAN_ERROR,
                        NetworkScan.ERROR_INVALID_SCAN, null);
                return;
            }
            if (!startNewScan(nsri)) {
                if (!interruptLiveScan(nsri)) {
                    if (!cacheScan(nsri)) {
                        notifyMessenger(nsri, TelephonyScanManager.CALLBACK_SCAN_ERROR,
                                NetworkScan.ERROR_DEVICE_BUSY, null);
                    }
                }
            }
        }

        private void startScanDone(AsyncResult ar) {
            NetworkScanRequestInfo nsri = (NetworkScanRequestInfo) ar.userObj;
            if (nsri == null) {
                Log.e(TAG, "EVENT_START_NETWORK_SCAN_DONE: nsri is null");
                return;
            }
            if (ar.exception == null && ar.result != null) {
                NetworkScanResult nsr = (NetworkScanResult) ar.result;
                if (nsr.scanError == NetworkScan.SUCCESS) {
                    // Register for the scan results if the scan started successfully.
                    nsri.mPhone.mCi.registerForNetworkScanResult(mHandler,
                            EVENT_RECEIVE_NETWORK_SCAN_RESULT, nsr);
                } else {
                    deleteScanAndNotify(nsri, nsr.scanError);
                }
            } else {
                logEmptyResultOrException(ar);
                deleteScanAndNotify(nsri, NetworkScan.ERROR_RIL_ERROR);
            }
        }

        private void receiveResult(AsyncResult ar) {
            NetworkScanRequestInfo nsri = (NetworkScanRequestInfo) ar.userObj;
            if (nsri == null) {
                Log.e(TAG, "EVENT_RECEIVE_NETWORK_SCAN_RESULT: nsri is null");
                return;
            }
            if (ar.exception == null && ar.result != null) {
                NetworkScanResult nsr = (NetworkScanResult) ar.result;
                if (nsr.scanError == NetworkScan.SUCCESS) {
                    notifyMessenger(nsri, TelephonyScanManager.CALLBACK_SCAN_RESULTS,
                            NetworkScan.SUCCESS, nsr.networkInfos);
                    if (nsr.scanStatus == NetworkScanResult.SCAN_STATUS_COMPLETE) {
                        deleteScanAndNotify(nsri, NetworkScan.SUCCESS);
                        nsri.mPhone.mCi.unregisterForNetworkScanResult(mHandler);
                    }
                } else {
                    if (nsr.networkInfos != null) {
                        notifyMessenger(nsri, TelephonyScanManager.CALLBACK_SCAN_RESULTS,
                                NetworkScan.SUCCESS, nsr.networkInfos);
                    }
                    deleteScanAndNotify(nsri, nsr.scanError);
                    nsri.mPhone.mCi.unregisterForNetworkScanResult(mHandler);
                }
            } else {
                logEmptyResultOrException(ar);
                deleteScanAndNotify(nsri, NetworkScan.ERROR_RIL_ERROR);
                nsri.mPhone.mCi.unregisterForNetworkScanResult(mHandler);
            }
        }


        // Stops the scan if the scanId and uid match the mScanId and mUid.
        // If the scan to be stopped is the live scan, we only send the request to RIL, while the
        // mLiveRequestInfo will not be cleared and the user will not be notified either.
        // If the scan to be stopped is the pending scan, we will clear mPendingRequestInfo and
        // notify the user.
        private synchronized void doStopScan(int scanId) {
            if (scanId == mLiveRequestInfo.mScanId) {
                mLiveRequestInfo.mPhone.stopNetworkScan(
                        mHandler.obtainMessage(EVENT_STOP_NETWORK_SCAN_DONE, mLiveRequestInfo));
            } else if (scanId == mPendingRequestInfo.mScanId) {
                notifyMessenger(mPendingRequestInfo,
                        TelephonyScanManager.CALLBACK_SCAN_COMPLETE, NetworkScan.SUCCESS, null);
                mPendingRequestInfo = null;
            } else {
                Log.e(TAG, "stopScan: scan " + scanId + " does not exist!");
            }
        }

        private void stopScanDone(AsyncResult ar) {
            NetworkScanRequestInfo nsri = (NetworkScanRequestInfo) ar.userObj;
            if (nsri == null) {
                Log.e(TAG, "EVENT_STOP_NETWORK_SCAN_DONE: nsri is null");
                return;
            }
            if (ar.exception == null && ar.result != null) {
                NetworkScanResult nsr = (NetworkScanResult) ar.result;
                deleteScanAndNotify(nsri, nsr.scanError);
            } else {
                logEmptyResultOrException(ar);
                deleteScanAndNotify(nsri, NetworkScan.ERROR_RIL_ERROR);
            }
            nsri.mPhone.mCi.unregisterForNetworkScanResult(mHandler);
        }

        // Interrupts the live scan is the scanId matches the mScanId of the mLiveRequestInfo.
        private synchronized void doInterruptScan(int scanId) {
            if (scanId == mLiveRequestInfo.mScanId) {
                mLiveRequestInfo.mPhone.stopNetworkScan(mHandler.obtainMessage(
                        EVENT_INTERRUPT_NETWORK_SCAN_DONE, mLiveRequestInfo));
            } else {
                Log.e(TAG, "doInterruptScan: scan " + scanId + " does not exist!");
            }
        }

        private void interruptScanDone(AsyncResult ar) {
            NetworkScanRequestInfo nsri = (NetworkScanRequestInfo) ar.userObj;
            if (nsri == null) {
                Log.e(TAG, "EVENT_INTERRUPT_NETWORK_SCAN_DONE: nsri is null");
                return;
            }
            nsri.mPhone.mCi.unregisterForNetworkScanResult(mHandler);
            deleteScan(nsri);
        }

        // Interrupts the live scan and caches nsri in mPendingRequestInfo. Once the live scan is
        // stopped, a new scan will automatically start with nsri.
        // The new scan can interrupt the live scan only when all the below requirements are met:
        //   1. There is 1 live scan and no other pending scan
        //   2. The new scan is requested by system process
        //   3. The live scan is not requested by system process
        private synchronized boolean interruptLiveScan(NetworkScanRequestInfo nsri) {
            if (mLiveRequestInfo != null && mPendingRequestInfo == null
                    && nsri.mUid == Process.SYSTEM_UID
                            && mLiveRequestInfo.mUid != Process.SYSTEM_UID) {
                doInterruptScan(mLiveRequestInfo.mScanId);
                mPendingRequestInfo = nsri;
                notifyMessenger(mLiveRequestInfo, TelephonyScanManager.CALLBACK_SCAN_ERROR,
                        NetworkScan.ERROR_INTERRUPTED, null);
                return true;
            }
            return false;
        }

        private boolean cacheScan(NetworkScanRequestInfo nsri) {
            // TODO(30954762): Cache periodic scan for OC-MR1.
            return false;
        }

        // Starts a new scan with nsri if there is no live scan running.
        private synchronized boolean startNewScan(NetworkScanRequestInfo nsri) {
            if (mLiveRequestInfo == null) {
                mLiveRequestInfo = nsri;
                nsri.mPhone.startNetworkScan(
                        mHandler.obtainMessage(EVENT_START_NETWORK_SCAN_DONE, nsri));
                return true;
            }
            return false;
        }


        // Deletes the mLiveRequestInfo and notify the user if it matches nsri.
        private synchronized void deleteScanAndNotify(NetworkScanRequestInfo nsri, int error) {
            if (nsri.mScanId == mLiveRequestInfo.mScanId) {
                if (error == NetworkScan.SUCCESS) {
                    notifyMessenger(nsri, TelephonyScanManager.CALLBACK_SCAN_COMPLETE, error,
                            null);
                } else {
                    notifyMessenger(nsri, TelephonyScanManager.CALLBACK_SCAN_ERROR, error,
                            null);
                }
                mLiveRequestInfo = null;
                if (mPendingRequestInfo != null) {
                    startNewScan(mPendingRequestInfo);
                    mPendingRequestInfo = null;
                }
            }
        }

        // Deletes the mLiveRequestInfo if it matches nsri.
        private synchronized void deleteScan(NetworkScanRequestInfo nsri) {
            // Delete the scan only if this scan has not been stopped or interrupted.
            if (nsri.mScanId == mLiveRequestInfo.mScanId) {
                mLiveRequestInfo = null;
                if (mPendingRequestInfo != null) {
                    if (startNewScan(mPendingRequestInfo)) {
                        mPendingRequestInfo = null;
                    }
                }
            }
        }
    }

    /**
     * Interrupts an ongoing network scan
     *
     * This method is similar to stopNetworkScan, since they both stops an ongoing scan. The
     * difference is that stopNetworkScan is only used by the callers to stop their own scans, so
     * sanity check will be done to make sure the request is valid; while this method is only
     * internally used by NetworkScanRequestTracker so sanity check is not needed.
     */
    private void interruptNetworkScan(int scanId) {
        // scanId will be stored at Message.arg1
        mHandler.obtainMessage(CMD_INTERRUPT_NETWORK_SCAN, scanId).sendToTarget();
    }

    /**
     * Starts a new network scan
     *
     * This function only wraps all the incoming information and delegate then to the handler thread
     * which will actually handles the scan request. So a new scanId will always be generated and
     * returned to the user, no matter how this scan will be actually handled.
     */
    public int startNetworkScan(
            NetworkScanRequest request, Messenger messenger, IBinder binder, Phone phone) {
        int scanId = mNextNetworkScanRequestId.getAndIncrement();
        NetworkScanRequestInfo nsri =
                new NetworkScanRequestInfo(request, messenger, binder, scanId, phone);
        // nsri will be stored as Message.obj
        mHandler.obtainMessage(CMD_START_NETWORK_SCAN, nsri).sendToTarget();
        return scanId;
    }

    /**
     * Stops an ongoing network scan
     *
     * The ongoing scan will be stopped only when the input scanId and caller's uid matches the
     * corresponding information associated with it.
     */
    public void stopNetworkScan(int scanId) {
        synchronized (mScheduler) {
            if ((scanId == mScheduler.mLiveRequestInfo.mScanId
                    && Binder.getCallingUid() == mScheduler.mLiveRequestInfo.mUid)
                    || (scanId == mScheduler.mPendingRequestInfo.mScanId
                    && Binder.getCallingUid() == mScheduler.mPendingRequestInfo.mUid)) {
                // scanId will be stored at Message.arg1
                mHandler.obtainMessage(CMD_STOP_NETWORK_SCAN, scanId).sendToTarget();
            } else {
                throw new IllegalArgumentException("Scan with id: " + scanId + " does not exist!");
            }
        }
    }
}
