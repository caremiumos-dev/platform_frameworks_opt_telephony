/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.internal.telephony.d2d;

import android.annotation.NonNull;
import android.telecom.Log;
import android.util.ArraySet;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.BiMap;

import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Implements a DTMF-based transport for use with device-to-device communication.
 *
 * The DTMF-based transport is negotiated using a probe digit sent and confirmed by the remote side
 * of the call.
 * The {@link #DTMF_PROBE} digit is sent upon initiation of the negotiation process.  The protocol
 * is considered negotiated if a {@link #DTMF_PROBE} digit is received during the negotiation phase.
 *
 * Messages consist of 3 DTMF digits:
 * Digit 0 - message category
 * Digit 1 - message type
 * Digit 2 - message value
 *
 * Valid message categories:
 * A - call stats
 * B - device stats
 *
 * Valid call stats messages and values:
 * A - call RAT
 * A - LTE
 * B - IWLAN
 * C - NR
 * B - call Audio codec
 * A - EVS
 * B - AMR-WB
 * C - AMR-NB
 * C - Call SRVCC status
 * A - Initiated
 * B - Cancelled
 *
 * Valid device stats messages and values:
 * A - device battery level
 * A - low
 * B - good
 * C - charging
 * B - device service state
 * A - good
 * B - poor
 */
public class DtmfTransport implements TransportProtocol {
    public static final char DTMF_PROBE = 'A';

    /**
     * Indicates the number of digits which represent a message received via DTMF; 3 by default.
     * Digit 0 - message category
     * Digit 1 - message type
     * Digit 2 - message value
     */
    public static final int DTMF_MESSAGE_DIGITS = 3;

    public static final String DTMF_MESSAGE_CALL_RAT_LTE = "AAA";
    public static final String DTMF_MESSAGE_CALL_RAT_IWLAN = "AAB";
    public static final String DTMF_MESSAGE_CALL_RAT_NR = "AAC";

    public static final String DTMF_MESSAGE_CALL_AUDIO_CODEC_EVS = "ABA";
    public static final String DTMF_MESSAGE_CALL_AUDIO_CODEC_AMR_WB = "ABB";
    public static final String DTMF_MESSAGE_CALL_AUDIO_CODEC_AMR_NB = "ABC";

    public static final String DTMF_MESSAGE_DEVICE_BATTERY_LOW = "BAA";
    public static final String DTMF_MESSAGE_DEVICE_BATTERY_GOOD = "BAB";
    public static final String DTMF_MESSAGE_DEVICE_BATTERY_CHARGING = "BAC";

    public static final String DTMF_MESSAGE_DEVICE_SERVICE_GOOD = "BBA";
    public static final String DTMF_MESSAGE_DEVICE_SERVICE_POOR = "BBB";

    public static final BiMap<String, Communicator.Message> DTMF_TO_MESSAGE = new BiMap<>();

    static {
        DTMF_TO_MESSAGE.put(DTMF_MESSAGE_CALL_RAT_LTE,
                new Communicator.Message(Communicator.MESSAGE_CALL_RADIO_ACCESS_TYPE,
                        Communicator.RADIO_ACCESS_TYPE_LTE));
        DTMF_TO_MESSAGE.put(DTMF_MESSAGE_CALL_RAT_IWLAN,
                new Communicator.Message(Communicator.MESSAGE_CALL_RADIO_ACCESS_TYPE,
                        Communicator.RADIO_ACCESS_TYPE_IWLAN));
        DTMF_TO_MESSAGE.put(DTMF_MESSAGE_CALL_RAT_NR,
                new Communicator.Message(Communicator.MESSAGE_CALL_RADIO_ACCESS_TYPE,
                        Communicator.RADIO_ACCESS_TYPE_NR));

        DTMF_TO_MESSAGE.put(DTMF_MESSAGE_CALL_AUDIO_CODEC_EVS,
                new Communicator.Message(Communicator.MESSAGE_CALL_AUDIO_CODEC,
                        Communicator.AUDIO_CODEC_EVS));
        DTMF_TO_MESSAGE.put(DTMF_MESSAGE_CALL_AUDIO_CODEC_AMR_WB,
                new Communicator.Message(Communicator.MESSAGE_CALL_AUDIO_CODEC,
                        Communicator.AUDIO_CODEC_AMR_WB));
        DTMF_TO_MESSAGE.put(DTMF_MESSAGE_CALL_AUDIO_CODEC_AMR_NB,
                new Communicator.Message(Communicator.MESSAGE_CALL_AUDIO_CODEC,
                        Communicator.AUDIO_CODEC_AMR_NB));

        DTMF_TO_MESSAGE.put(DTMF_MESSAGE_DEVICE_BATTERY_LOW,
                new Communicator.Message(Communicator.MESSAGE_DEVICE_BATTERY_STATE,
                        Communicator.BATTERY_STATE_LOW));
        DTMF_TO_MESSAGE.put(DTMF_MESSAGE_DEVICE_BATTERY_GOOD,
                new Communicator.Message(Communicator.MESSAGE_DEVICE_BATTERY_STATE,
                        Communicator.BATTERY_STATE_GOOD));
        DTMF_TO_MESSAGE.put(DTMF_MESSAGE_DEVICE_BATTERY_CHARGING,
                new Communicator.Message(Communicator.MESSAGE_DEVICE_BATTERY_STATE,
                        Communicator.BATTERY_STATE_CHARGING));

        DTMF_TO_MESSAGE.put(DTMF_MESSAGE_DEVICE_SERVICE_GOOD,
                new Communicator.Message(Communicator.MESSAGE_DEVICE_NETWORK_COVERAGE,
                        Communicator.COVERAGE_GOOD));
        DTMF_TO_MESSAGE.put(DTMF_MESSAGE_DEVICE_SERVICE_POOR,
                new Communicator.Message(Communicator.MESSAGE_DEVICE_NETWORK_COVERAGE,
                        Communicator.COVERAGE_POOR));
    }

    public static final int STATE_IDLE = 0;
    public static final int STATE_NEGOTIATING = 1;
    public static final int STATE_NEGOTIATED = 2;
    public static final int STATE_NEGOTIATION_FAILED = 3;

    private final DtmfAdapter mDtmfAdapter;
    private final Timeouts.Adapter mTimeoutsAdapter;
    private final long mIntervalBetweenDigitsMillis;
    private final long mDurationOfDtmfMessageMillis;
    private final long mNegotiationTimeoutMillis;
    private final ScheduledExecutorService mScheduledExecutorService;
    private TransportProtocol.Callback mCallback;
    private int mTransportState = STATE_IDLE;
    private boolean mReceivedProbe = false;
    private char[] mReceivedDigits = new char[DTMF_MESSAGE_DIGITS];
    private final ConcurrentLinkedQueue<char[]> mPendingMessages = new ConcurrentLinkedQueue<>();
    private int mDigitNumber = 0;
    private Object mDtmfMessageTimeoutLock = new Object();
    private Object mDigitSendLock = new Object();
    private Object mNegotiationLock = new Object();
    private ScheduledFuture<?> mNegotiationFuture;
    private ScheduledFuture<?> mDigitSendScheduledFuture;
    private ScheduledFuture<?> mDtmfMessageTimeoutFuture;
    private char[] mMessageToSend;
    private int mCharToSend = 0;

    public DtmfTransport(@NonNull DtmfAdapter dtmfAdapter, Timeouts.Adapter timeoutsAdapter,
            ScheduledExecutorService executorService) {
        mDtmfAdapter = dtmfAdapter;
        mTimeoutsAdapter = timeoutsAdapter;
        mIntervalBetweenDigitsMillis = timeoutsAdapter.getDtmfMinimumIntervalMillis();
        mDurationOfDtmfMessageMillis = timeoutsAdapter.getDtmfMaxSequenceIntervalMillis();
        mNegotiationTimeoutMillis = timeoutsAdapter.getDtmfNegotiationTimeoutMillis();
        mScheduledExecutorService = executorService;
    }

    @Override
    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    @Override
    public void startNegotiation() {
        if (mTransportState != STATE_IDLE) {
            Log.w(this, "startNegotiation: can't start negotiation as not idle.");
            return;
        }
        mTransportState = STATE_NEGOTIATING;
        Log.i(this, "startNegotiation: starting negotiation.");
        if (mDtmfAdapter != null) {
            mDtmfAdapter.sendDtmf(DTMF_PROBE);
            scheduleNegotiationTimeout();
        }
    }

    /**
     * Given a set of messages to send, send them using the DTMF transport.
     *
     * @param messages the messages to send via the transport.
     */
    @Override
    public void sendMessages(Set<Communicator.Message> messages) {
        for (Communicator.Message msg : messages) {
            char[] digits = getDtmfString(msg);
            if (digits == null) continue;

            mPendingMessages.offer(digits);
        }
        if (mPendingMessages.size() > 0) {
            maybeScheduleMessageSend();
        }
    }

    /**
     * Checks for pending messages and schedules send of a pending message if one is available.
     */
    private void maybeScheduleMessageSend() {
        synchronized (mDigitSendLock) {
            if (mMessageToSend == null && mDigitSendScheduledFuture == null) {
                mMessageToSend = mPendingMessages.poll();
                mCharToSend = 0;

                if (mMessageToSend != null) {
                    Log.i(this, "maybeScheduleMessageSend: toSend=%s",
                            String.valueOf(mMessageToSend));
                    mDigitSendScheduledFuture = mScheduledExecutorService.scheduleAtFixedRate(
                            () -> {
                                handleDtmfSend();
                            }, mDurationOfDtmfMessageMillis, mIntervalBetweenDigitsMillis,
                            TimeUnit.MILLISECONDS);
                }
            }
        }
    }

    /**
     * Runs at fixed {@link #mIntervalBetweenDigitsMillis} intervals to send the individual DTMF
     * digits in {@link #mMessageToSend}.  When sending completes, the scheduled task is cancelled
     * and {@link #maybeScheduleMessageSend()} is called to schedule send of any other pending
     * message.
     */
    private void handleDtmfSend() {
        if (mCharToSend < mMessageToSend.length) {
            if (mDtmfAdapter != null) {
                Log.i(this, "handleDtmfSend: char=%c", mMessageToSend[mCharToSend]);
                mDtmfAdapter.sendDtmf(mMessageToSend[mCharToSend]);
            }
            mCharToSend++;

            if (mCharToSend == mMessageToSend.length) {
                Log.i(this, "handleDtmfSend: done");
                synchronized (mDigitSendLock) {
                    mMessageToSend = null;
                    mDigitSendScheduledFuture.cancel(false);
                    mDigitSendScheduledFuture = null;

                    maybeScheduleMessageSend();
                }
            }
        }
    }

    /**
     * @return the current state of the transport.
     */
    public int getTransportState() {
        return mTransportState;
    }

    /**
     * Called by Telephony when a DTMF digit is received from the network.
     *
     * @param digit The received DTMF digit.
     */
    public void onDtmfReceived(char digit) {
        if (mTransportState == STATE_NEGOTIATING) {
            if (digit == DTMF_PROBE) {
                Log.i(this, "onDtmfReceived: received probe; protocol ready.");
                mReceivedProbe = true;
                mTransportState = STATE_NEGOTIATED;
                cancelNegotiationTimeout();
                if (mCallback != null) {
                    mCallback.onNegotiationSuccess(this);
                }
            } else {
                cancelNegotiationTimeout();
                notifyNegotiationFailed();
            }
        } else {
            handleReceivedDigit(digit);
        }
    }

    /**
     * Upon initiation of negotiation, schedule a timeout within which we expect to receive the
     * incoming probe.
     */
    private void scheduleNegotiationTimeout() {
        synchronized (mNegotiationLock) {
            mNegotiationFuture = mScheduledExecutorService.schedule(() -> {
                        handleNegotiationTimeout();
                    },
                    mNegotiationTimeoutMillis, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Cancels a pending timeout for negotiation.
     */
    private void cancelNegotiationTimeout() {
        Log.i(this, "cancelNegotiationTimeout");
        synchronized (mNegotiationLock) {
            if (mNegotiationFuture != null) {
                mNegotiationFuture.cancel(false);
            }
            mNegotiationFuture = null;
        }
    }

    /**
     * Handle scheduled negotiation timeout.
     */
    private void handleNegotiationTimeout() {
        Log.i(this, "handleNegotiationTimeout: no probe received, negotiation timeout.");
        synchronized (mNegotiationLock) {
            mNegotiationFuture = null;
        }
        mTransportState = STATE_NEGOTIATION_FAILED;
        notifyNegotiationFailed();
    }

    /**
     * Notify listeners that negotiation has failed.
     */
    private void notifyNegotiationFailed() {
        mReceivedProbe = false;
        Log.i(this, "notifyNegotiationFailed");
        if (mCallback != null) {
            mCallback.onNegotiationFailed(this);
        }
    }

    /**
     * Handle received DTMF digits, taking into account current protocol state.
     *
     * @param digit the received digit.
     */
    private void handleReceivedDigit(char digit) {
        if (mDigitNumber == 0) {
            // First digit; start the timer
            Log.i(this, "handleReceivedDigit: digit = %c ; message timeout started.", digit);
            scheduleDtmfMessageTimeout();
        }

        mReceivedDigits[mDigitNumber] = digit;
        if (mDigitNumber == DTMF_MESSAGE_DIGITS - 1) {
            Log.i(this, "handleReceivedDigit: digit = %c, msg = %s ; full message received.",
                    digit, String.valueOf(mReceivedDigits));
            maybeCancelDtmfMessageTimeout();
            handleIncomingMessage();
        }
        Log.i(this, "handleReceivedDigit: digit = %c, msg = %s",
                digit, String.valueOf(mReceivedDigits));
        mDigitNumber = (mDigitNumber + 1) % DTMF_MESSAGE_DIGITS;
    }

    /**
     * Schedule a timeout for receiving a complete DTMF message.  A DTMF message is made up of
     * {@link #DTMF_MESSAGE_DIGITS} digits.
     */
    private void scheduleDtmfMessageTimeout() {
        synchronized (mDtmfMessageTimeoutLock) {
            maybeCancelDtmfMessageTimeout();
            // Schedule a timeout to handle cancelling the receipt of a DTMF packet.

            mDtmfMessageTimeoutFuture = mScheduledExecutorService.schedule(() -> {
                        handleDtmfMessageTimeout();
                    },
                    mDurationOfDtmfMessageMillis, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Cancels any pending DTMF message timeout scheduled with
     * {@link #scheduleDtmfMessageTimeout()}.
     */
    private void maybeCancelDtmfMessageTimeout() {
        synchronized (mDtmfMessageTimeoutLock) {
            if (mDtmfMessageTimeoutFuture != null) {
                Log.i(this, "scheduleDtmfMessageTimeout: timeout pending; cancelling");
                mDtmfMessageTimeoutFuture.cancel(false);
                mDtmfMessageTimeoutFuture = null;
            }
        }
    }

    /**
     * Called when a scheduled DTMF message timeout occurs to cleanup the incoming message and
     * prepare for receiving a new message.
     */
    private void handleDtmfMessageTimeout() {
        maybeCancelDtmfMessageTimeout();

        if (mDigitNumber != (DTMF_MESSAGE_DIGITS - 1)) {
            Log.i(this, "handleDtmfMessageTimeout: timeout receiving DTMF string; got %s so far",
                    String.valueOf(mReceivedDigits));
        }

        for (int ix = 0; ix < mReceivedDigits.length; ix++) {
            mReceivedDigits[ix] = ' ';
        }
        mDigitNumber = 0;
    }

    public ConcurrentLinkedQueue<char[]> getPendingMessages() {
        return mPendingMessages;
    }

    /**
     * Given a {@link Communicator.Message} to send, returns a string of DTMF digits to send for
     * that message.
     *
     * @param message The message to send.
     * @return The DTMF digits to send.
     */
    @VisibleForTesting
    public char[] getDtmfString(@NonNull Communicator.Message message) {
        String foundSequence = DTMF_TO_MESSAGE.getKey(message);
        if (foundSequence == null) {
            return null;
        }
        return foundSequence.toCharArray();
    }

    /**
     * Translate a string of DTMF digits into a communicator message.
     *
     * @param message The string of DTMF digits with digit 0 being the start of the string of
     *                digits.
     * @return The message received, or {@code null} if no valid message found.
     */
    @VisibleForTesting
    public Communicator.Message extractMessage(String message) {
        return DTMF_TO_MESSAGE.getValue(message);
    }

    /**
     * Handles an incoming message received via DTMF digits; notifies interested parties of the
     * message using the associated callback.
     */
    private void handleIncomingMessage() {
        String receivedDigits = String.valueOf(mReceivedDigits);
        Communicator.Message msg = extractMessage(receivedDigits);
        if (msg == null) {
            Log.w(this, "handleIncomingMessage: digits = %s; invalid msg", receivedDigits);
            return;
        }
        Log.i(this, "handleIncomingMessage: digits = %s, message = %s", receivedDigits, msg);

        Set<Communicator.Message> msgs = new ArraySet<>(1);
        msgs.add(msg);
        if (mCallback != null) {
            mCallback.onMessagesReceived(msgs);
        }
    }
}
