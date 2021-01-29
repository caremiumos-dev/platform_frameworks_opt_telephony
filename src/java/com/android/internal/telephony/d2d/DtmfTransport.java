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
import android.util.Pair;

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
 * Note: Per RFC2833, transmission of DTMF digits provides a level of guarantee that where DTMF
 * digits can be transmitted between two devices that they will be transmitted successfully.  Thus
 * the probe only needs to be sent once to see if the other party can potentially use DTMF as a
 * transport mechanism.
 *
 * The {@link #DTMF_PROBE} is used to negotiate D2D communication using DTMF.  It is important to
 * note that the protocol includes no specific concept of versioning.  The message format assumes
 * new message types and values can be added in the future; there is an assumption that if one side
 * of the D2D communication pathway is unable to handle messages, it will ignore them.
 *
 * Protocol format:
 * The DTMF protocol follows the following regular expression:
 * ^A[ABC]+D[ABC]+D$
 * A - start of message indicator, {@link #DTMF_MESSAGE_START}
 * [ABC]+ - one or more message type digits [A-C]
 * D - message delimiter {@link #DTMF_MESSAGE_DELIMITER} separating the type and value.
 * [ABC]+ - one or more message value digits [A-C]
 * D - end message delimiter {@link #DTMF_MESSAGE_DELIMITER}
 *
 * Valid message types and values are:
 * A - call RAT
 * ....A - LTE
 * ....B - IWLAN
 * ....C - NR
 * B - call Audio codec
 * ....A - EVS
 * ....B - AMR-WB
 * ....C - AMR-NB
 * C - device battery level
 * ....A - low
 * ....B - good
 * ....C - charging
 * AA - device service state
 * ....A - good
 * ....B - poor
 *
 * Example message:
 * Call RAT - NR --> A AD CD
 * Service State - poor --> A AAD BD
 */
public class DtmfTransport implements TransportProtocol {
    /**
     * The DTMF probe digit is the one which is sent first when protocol negotiation takes place.
     * We use the probe as a version specifier.
     */
    public static final char DTMF_PROBE = 'A';

    /**
     * All DTMF messages start with this digit.
     */
    public static final char DTMF_MESSAGE_START = 'A';

    /**
     * Delimits components of a DTMF message and also terminates a message.
     */
    public static final char DTMF_MESSAGE_DELIMITER = 'D';

    public static final String DTMF_MESSAGE_RAT = "A";
    public static final String DTMF_MESSAGE_RAT_LTE = "A";
    public static final String DTMF_MESSAGE_RAT_IWLAN = "B";
    public static final String DTMF_MESSAGE_RAT_NR = "C";

    public static final String DTMF_MESSAGE_CODEC = "B";
    public static final String DTMF_MESSAGE_CODEC_EVS = "A";
    public static final String DTMF_MESSAGE_CODEC_AMR_WB = "B";
    public static final String DTMF_MESSAGE_CODEC_AMR_NB = "C";

    public static final String DTMF_MESSAGE_BATERY = "C";
    public static final String DTMF_MESSAGE_BATTERY_LOW = "A";
    public static final String DTMF_MESSAGE_BATTERY_GOOD = "B";
    public static final String DTMF_MESSAGE_BATTERY_CHARGING = "C";

    public static final String DTMF_MESSAGE_SERVICE = "AA";
    public static final String DTMF_MESSAGE_SERVICE_GOOD = "A";
    public static final String DTMF_MESSAGE_SERVICE_POOR = "B";

    public static final BiMap<Pair<String, String>, Communicator.Message> DTMF_TO_MESSAGE =
            new BiMap<>();

    static {
        DTMF_TO_MESSAGE.put(new Pair<>(DTMF_MESSAGE_RAT, DTMF_MESSAGE_RAT_LTE),
                new Communicator.Message(Communicator.MESSAGE_CALL_RADIO_ACCESS_TYPE,
                        Communicator.RADIO_ACCESS_TYPE_LTE));
        DTMF_TO_MESSAGE.put(new Pair<>(DTMF_MESSAGE_RAT, DTMF_MESSAGE_RAT_IWLAN),
                new Communicator.Message(Communicator.MESSAGE_CALL_RADIO_ACCESS_TYPE,
                        Communicator.RADIO_ACCESS_TYPE_IWLAN));
        DTMF_TO_MESSAGE.put(new Pair<>(DTMF_MESSAGE_RAT, DTMF_MESSAGE_RAT_NR),
                new Communicator.Message(Communicator.MESSAGE_CALL_RADIO_ACCESS_TYPE,
                        Communicator.RADIO_ACCESS_TYPE_NR));

        DTMF_TO_MESSAGE.put(new Pair<>(DTMF_MESSAGE_CODEC, DTMF_MESSAGE_CODEC_EVS),
                new Communicator.Message(Communicator.MESSAGE_CALL_AUDIO_CODEC,
                        Communicator.AUDIO_CODEC_EVS));
        DTMF_TO_MESSAGE.put(new Pair<>(DTMF_MESSAGE_CODEC, DTMF_MESSAGE_CODEC_AMR_WB),
                new Communicator.Message(Communicator.MESSAGE_CALL_AUDIO_CODEC,
                        Communicator.AUDIO_CODEC_AMR_WB));
        DTMF_TO_MESSAGE.put(new Pair<>(DTMF_MESSAGE_CODEC, DTMF_MESSAGE_CODEC_AMR_NB),
                new Communicator.Message(Communicator.MESSAGE_CALL_AUDIO_CODEC,
                        Communicator.AUDIO_CODEC_AMR_NB));

        DTMF_TO_MESSAGE.put(new Pair<>(DTMF_MESSAGE_BATERY, DTMF_MESSAGE_BATTERY_LOW),
                new Communicator.Message(Communicator.MESSAGE_DEVICE_BATTERY_STATE,
                        Communicator.BATTERY_STATE_LOW));
        DTMF_TO_MESSAGE.put(new Pair<>(DTMF_MESSAGE_BATERY, DTMF_MESSAGE_BATTERY_GOOD),
                new Communicator.Message(Communicator.MESSAGE_DEVICE_BATTERY_STATE,
                        Communicator.BATTERY_STATE_GOOD));
        DTMF_TO_MESSAGE.put(new Pair<>(DTMF_MESSAGE_BATERY, DTMF_MESSAGE_BATTERY_CHARGING),
                new Communicator.Message(Communicator.MESSAGE_DEVICE_BATTERY_STATE,
                        Communicator.BATTERY_STATE_CHARGING));

        DTMF_TO_MESSAGE.put(new Pair<>(DTMF_MESSAGE_SERVICE, DTMF_MESSAGE_SERVICE_GOOD),
                new Communicator.Message(Communicator.MESSAGE_DEVICE_NETWORK_COVERAGE,
                        Communicator.COVERAGE_GOOD));
        DTMF_TO_MESSAGE.put(new Pair<>(DTMF_MESSAGE_SERVICE, DTMF_MESSAGE_SERVICE_POOR),
                new Communicator.Message(Communicator.MESSAGE_DEVICE_NETWORK_COVERAGE,
                        Communicator.COVERAGE_POOR));
    }

    public static final int STATE_IDLE = 0;
    public static final int STATE_NEGOTIATING = 1;
    public static final int STATE_NEGOTIATED = 2;
    public static final int STATE_NEGOTIATION_FAILED = 3;

    /**
     * Indicates no message is being received yet.
     */
    public static final int RECEIVE_STATE_IDLE = 0;
    /**
     * Indicates receive of the message type is underway.
     */
    public static final int RECEIVE_STATE_MESSAGE_TYPE = 1;
    /**
     * Inidicates receive of the message value is underway.
     */
    public static final int RECEIVE_STATE_MESSAGE_VALUE = 2;

    private final DtmfAdapter mDtmfAdapter;
    private final Timeouts.Adapter mTimeoutsAdapter;
    private final long mIntervalBetweenDigitsMillis;
    private final long mDurationOfDtmfMessageMillis;
    private final long mNegotiationTimeoutMillis;
    private final ScheduledExecutorService mScheduledExecutorService;
    private TransportProtocol.Callback mCallback;
    private int mTransportState = STATE_IDLE;
    private boolean mReceivedProbe = false;

    private int mReceiveState = RECEIVE_STATE_IDLE;
    private StringBuffer mMessageTypeDigits = new StringBuffer();
    private StringBuffer mMessageValueDigits = new StringBuffer();
    private final ConcurrentLinkedQueue<char[]> mPendingMessages = new ConcurrentLinkedQueue<>();
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
            // Start negotiation by sending the probe; schedule timeout so we know if negotiation
            // fails.
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
            char[] digits = getMessageDigits(msg);
            if (digits == null) continue;
            Log.i(this, "sendMessages: queing message: %s", String.valueOf(digits));

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
                    // Schedule the message to send; the inital delay will be
                    // mDurationOfDtmfMessageMillis to ensure we separate messages with an
                    // adequate padding of space, and mIntervalBetweenDigitsMillis will be used to
                    // ensure there is enough time between each digit.
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
                Log.i(this, "onDtmfReceived: received probe %c", digit);
                mReceivedProbe = true;
                mTransportState = STATE_NEGOTIATED;
                cancelNegotiationTimeout();
                if (mCallback != null) {
                    mCallback.onNegotiationSuccess(this);
                }
            } else {
                // We are still negotiating and got something other than a probe, so we can assume
                // negotiation failed.
                Log.w(this, "onDtmfReceived: received digit %c - not probe", digit);
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
        if (mReceiveState == RECEIVE_STATE_IDLE) {
            if (digit == DTMF_MESSAGE_START) {
                // First digit; start the timer
                Log.i(this, "handleReceivedDigit: digit = %c ; message timeout started.", digit);
                mReceiveState = RECEIVE_STATE_MESSAGE_TYPE;
                scheduleDtmfMessageTimeout();
            } else {
                Log.w(this, "handleReceivedDigit: digit = %c ; unexpected start digit, ignoring.",
                        digit);
            }
        } else if (digit == DTMF_MESSAGE_DELIMITER) {
            if (mReceiveState == RECEIVE_STATE_MESSAGE_TYPE) {
                Log.i(this, "handleReceivedDigit: digit = %c ; msg = %s ; awaiting value.", digit,
                        mMessageTypeDigits.toString());
                mReceiveState = RECEIVE_STATE_MESSAGE_VALUE;
            } else if (mReceiveState == RECEIVE_STATE_MESSAGE_VALUE) {
                Log.i(this, "handleReceivedDigit: digit = %c ; msg = %s ; value = %s ; full msg",
                        digit, mMessageTypeDigits.toString(), mMessageValueDigits.toString());
                maybeCancelDtmfMessageTimeout();
                handleIncomingMessage(mMessageTypeDigits.toString(),
                        mMessageValueDigits.toString());
                resetIncomingMessage();
            }
        } else {
            if (mReceiveState == RECEIVE_STATE_MESSAGE_TYPE) {
                mMessageTypeDigits.append(digit);
                Log.i(this, "handleReceivedDigit: typeDigit = %c ; msg = %s",
                        digit, mMessageTypeDigits.toString());
            } else if (mReceiveState == RECEIVE_STATE_MESSAGE_VALUE) {
                mMessageValueDigits.append(digit);
                Log.i(this, "handleReceivedDigit: valueDigit = %c ; value = %s",
                        digit, mMessageValueDigits.toString());
            }
        }
    }

    /**
     * Schedule a timeout for receiving a complete DTMF message.
     */
    private void scheduleDtmfMessageTimeout() {
        synchronized (mDtmfMessageTimeoutLock) {
            maybeCancelDtmfMessageTimeout();

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

        Log.i(this, "handleDtmfMessageTimeout: timeout receiving DTMF string; got %s/%s so far",
                    mMessageTypeDigits.toString(), mMessageValueDigits.toString());

        resetIncomingMessage();
    }

    /**
     * Given a {@link Communicator.Message} to send, returns a string of DTMF digits to send for
     * that message.  This is the complete message, including the message start, and all delimiters.
     *
     * @param message The message to send.
     * @return The DTMF digits to send, including all delimiters.
     */
    @VisibleForTesting
    public char[] getMessageDigits(@NonNull Communicator.Message message) {
        Pair<String, String> foundSequence = DTMF_TO_MESSAGE.getKey(message);
        if (foundSequence == null) {
            return null;
        }
        StringBuilder theMessage = new StringBuilder();
        theMessage.append(DTMF_MESSAGE_START);
        theMessage.append(foundSequence.first);
        theMessage.append(DTMF_MESSAGE_DELIMITER);
        theMessage.append(foundSequence.second);
        theMessage.append(DTMF_MESSAGE_DELIMITER);
        return theMessage.toString().toCharArray();
    }

    /**
     * Translate a string of DTMF digits into a communicator message.
     *
     * @param message The string of DTMF digits with digit 0 being the start of the string of
     *                digits.
     * @return The message received, or {@code null} if no valid message found.
     */
    @VisibleForTesting
    public Communicator.Message extractMessage(String message, String value) {
        return DTMF_TO_MESSAGE.getValue(new Pair<>(message, value));
    }

    /**
     * Handles an incoming message received via DTMF digits; notifies interested parties of the
     * message using the associated callback.
     */
    private void handleIncomingMessage(String message, String value) {

        Communicator.Message msg = extractMessage(message, value);
        if (msg == null) {
            Log.w(this, "handleIncomingMessage: msgDigits = %s, msgValueDigits = %s; invalid msg",
                    message, value);
            return;
        }
        Log.i(this, "handleIncomingMessage: msgDigits = %s, msgValueDigits = %s", message, value);

        Set<Communicator.Message> msgs = new ArraySet<>(1);
        msgs.add(msg);
        if (mCallback != null) {
            mCallback.onMessagesReceived(msgs);
        }
    }

    /**
     * Moves message receive state back to idle and clears received digits.
     */
    private void resetIncomingMessage() {
        mReceiveState = RECEIVE_STATE_IDLE;
        if (mMessageTypeDigits.length() != 0) {
            mMessageTypeDigits.delete(0, mMessageTypeDigits.length());
        }
        if (mMessageValueDigits.length() != 0) {
            mMessageValueDigits.delete(0, mMessageValueDigits.length());
        }
    }
}
