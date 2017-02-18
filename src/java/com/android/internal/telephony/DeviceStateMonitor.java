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

import static com.android.internal.telephony.DeviceStateMonitor.DeviceStateType.CHARGING_STATE;
import static com.android.internal.telephony.DeviceStateMonitor.DeviceStateType.LOW_DATA_EXPECTED;
import static com.android.internal.telephony.DeviceStateMonitor.DeviceStateType.POWER_SAVE_MODE;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.telephony.Rlog;
import android.util.LocalLog;

import com.android.internal.util.IndentingPrintWriter;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;

/**
 * The device state monitor monitors the device state such as charging state, power saving sate,
 * and then passes down the information to the radio modem for the modem to perform its own
 * proprietary power saving strategy. Device state monitor also turns off the unsolicited
 * response from the modem when the device does not need to receive it, for example, device's
 * screen is off and does not have activities like tethering, remote display, etc...This effectively
 * prevents the CPU from waking up by those unnecessary unsolicited responses such as signal
 * strength update.
 */
public class DeviceStateMonitor extends Handler {
    protected static final boolean DBG = false;      /* STOPSHIP if true */
    protected static final String TAG = DeviceStateMonitor.class.getSimpleName();

    private static final int EVENT_RIL_CONNECTED                = 0;
    private static final int EVENT_SCREEN_STATE_CHANGED         = 1;
    private static final int EVENT_POWER_SAVE_MODE_CHANGED      = 2;
    private static final int EVENT_CHARGING_STATE_CHANGED       = 3;
    private static final int EVENT_TETHERING_STATE_CHANGED      = 4;

    /**
     * The device state type. Used for sending device state to the modem.
     * The definition should match DeviceStateType defined in types.hal.
     */
    public enum DeviceStateType {
        /**
         * Device power save mode (provided by PowerManager). True if the device is in power
         * save mode.
         */
        POWER_SAVE_MODE,
        /**
         * Device charging state (provided by BatteryManager). True if the device is charging.
         */
        CHARGING_STATE,
        /**
         * Low data expected mode. True indicates low data traffic is expected, for example, when
         * the device is idle (e.g. not doing tethering in the background). Note this doesn't mean
         * no data is expected.
         */
        LOW_DATA_EXPECTED
    }

    /**
     * The filter used for turning on/off unsolicited response from the modem. The definition should
     * match IndicationFilter defined in types.hal.
     */
    private enum UnsolicitedResponseFilter {
        NONE(0),
        /**
         * When this bit is set, RIL_UNSOL_SIGNAL_STRENGTH will always be sent if there is any
         * update from the modem. When this bit is not set, RIL_UNSOL_SIGNAL_STRENGTH will be
         * completely suppressed.
         */
        SIGNAL_STRENGTH(0x01),
        /**
         * When this bit is set, RIL_UNSOL_RESPONSE_NETWORK_STATE_CHANGED will always be sent
         * when any field in voice registration state or data registration state changes.
         * When this bit is not set, RIL_UNSOL_RESPONSE_NETWORK_STATE_CHANGED will be suppressed
         * when only insignificant fields are changed (e.g. cell info). If the significant fields
         * such as registration status or RAT change, RIL_UNSOL_RESPONSE_NETWORK_STATE_CHANGED
         * will still be sent regardless this bit is set or not.
         */
        FULL_NETWORK_STATE(0x02),
        /**
         * When this bit is set, RIL_UNSOL_DATA_CALL_LIST_CHANGED will always be sent if there
         * is any update of the data calls. When this bit is not set, the dormancy state update
         * will be suppressed.
         */
        DATA_CALL_DORMANCY_CHANGED(0x04),
        ALL(0x07);

        private final int mValue;

        UnsolicitedResponseFilter(int v) {
            mValue = v;
        }

        int getValue() {
            return mValue;
        }
    }

    private final Phone mPhone;

    private final LocalLog mLocalLog = new LocalLog(100);

    /**
     * Flag for wifi/usb/bluetooth tethering turned on or not
     */
    private boolean mIsTetheringOn;

    /**
     * True if the device is in an interactive state.
     */
    // TODO: Support remote display and Android Auto in the future.
    private boolean mIsInteractive;

    /**
     * Indicating the device is plugged in and is supplying sufficient power that the battery level
     * is going up (or the battery is fully charged). See BatteryManager.isCharging() for the
     * details
     */
    private boolean mIsCharging;

    /**
     * Flag for device power save mode. See PowerManager.isPowerSaveMode() for the details.
     * Note that it is not possible both mIsCharging and mIsPowerSaveOn are true at the same time.
     * The system will automatically end power save mode when the device starts charging.
     */
    private boolean mIsPowerSaveOn;

    /**
     * The legacy screen state. True if screen 'on' has been sent to the modem through
     * RIL_REQUEST_SCREEN_STATE. False if screen 'off' has been sent.
     */
    private boolean mLegacyScreenState;

    /**
     * Low data expected mode. True indicates low data traffic is expected, for example, when the
     * device is idle (e.g. screen is off and not doing tethering in the background). Note this
     * doesn't mean no data is expected.
     */
    private boolean mIsLowDataExpected;

    /**
     * The unsolicited response filter. See UnsolicitedResponseFilter for the definition of each
     * bit.
     */
    private int mUnsolicitedResponseFilter = UnsolicitedResponseFilter.ALL.getValue();

    /**
     * Device state broadcast receiver
     */
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DBG) log("received: " + intent);
            mLocalLog.log("received: " + intent);

            Message msg;
            switch (intent.getAction()) {
                case Intent.ACTION_SCREEN_ON:
                    msg = obtainMessage(EVENT_SCREEN_STATE_CHANGED);
                    msg.arg1 = 1;   // screen on
                    break;
                case Intent.ACTION_SCREEN_OFF:
                    msg = obtainMessage(EVENT_SCREEN_STATE_CHANGED);
                    msg.arg1 = 0;   // screen off
                    break;
                case PowerManager.ACTION_POWER_SAVE_MODE_CHANGED:
                    msg = obtainMessage(EVENT_POWER_SAVE_MODE_CHANGED);
                    msg.arg1 = isPowerSaveModeOn() ? 1 : 0;
                    if (DBG) log("Power Save mode " + ((msg.arg1 == 1) ? "on" : "off"));
                    break;
                case BatteryManager.ACTION_CHARGING:
                    msg = obtainMessage(EVENT_CHARGING_STATE_CHANGED);
                    msg.arg1 = 1;   // charging
                    break;
                case BatteryManager.ACTION_DISCHARGING:
                    msg = obtainMessage(EVENT_CHARGING_STATE_CHANGED);
                    msg.arg1 = 0;   // not charging
                    break;
                case ConnectivityManager.ACTION_TETHER_STATE_CHANGED:
                    ArrayList<String> activeTetherIfaces = intent.getStringArrayListExtra(
                            ConnectivityManager.EXTRA_ACTIVE_TETHER);

                    boolean isTetheringOn = activeTetherIfaces != null
                            && activeTetherIfaces.size() > 0;
                    if (DBG) log("Tethering " + (isTetheringOn ? "on" : "off"));
                    msg = obtainMessage(EVENT_TETHERING_STATE_CHANGED);
                    msg.arg1 = isTetheringOn ? 1 : 0;
                    break;
                default:
                    log("Unexpected broadcast intent: " + intent);
                    return;
            }
            sendMessage(msg);
        }
    };

    /**
     * Device state monitor constructor. Note that each phone object should have its own device
     * state monitor, meaning there will be two device monitors on the multi-sim device.
     *
     * @param phone Phone object
     */
    public DeviceStateMonitor(Phone phone) {
        mPhone = phone;

        // Initializing the flags, assuming tethering is always off after boot up.
        mIsPowerSaveOn = isPowerSaveModeOn();
        mIsInteractive = isInteractive();
        mIsCharging = isDeviceCharging();
        mIsTetheringOn = false;
        mLegacyScreenState = true;
        mIsLowDataExpected = false;

        if (DBG) {
            log("DeviceStateMonitor mIsPowerSaveOn=" + mIsPowerSaveOn + ",mIsInteractive="
                    + mIsInteractive + ",mIsCharging=" + mIsCharging);
        }

        final IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
        filter.addAction(BatteryManager.ACTION_CHARGING);
        filter.addAction(BatteryManager.ACTION_DISCHARGING);
        filter.addAction(ConnectivityManager.ACTION_TETHER_STATE_CHANGED);
        mPhone.getContext().registerReceiver(mBroadcastReceiver, filter, null, mPhone);

        mPhone.mCi.registerForRilConnected(this, EVENT_RIL_CONNECTED, null);
    }

    /**
     * @return True if framework should invoke the legacy screen state on API.
     */
    private boolean shouldInvokeLegacyScreenOn() {
        return !isLowDataExpected();
    }

    /**
     * @return True if low data is expected
     */
    private boolean isLowDataExpected() {
        return mIsPowerSaveOn || (!mIsCharging && !mIsTetheringOn && !mIsInteractive);
    }

    /**
     * @return True if signal strength update should be turned off.
     */
    private boolean shouldTurnOffSignalStrength() {
        return mIsPowerSaveOn || (!mIsCharging && !mIsInteractive);
    }

    /**
     * @return True if full network update should be turned off. Only significant changes will
     * trigger the network update unsolicited response.
     */
    private boolean shouldTurnOffFullNetworkUpdate() {
        return mIsPowerSaveOn || (!mIsCharging && !mIsInteractive && !mIsTetheringOn);
    }

    /**
     * @return True if data dormancy status update should be turned off.
     */
    private boolean shouldTurnOffDormancyUpdate() {
        return mIsPowerSaveOn || (!mIsCharging && !mIsTetheringOn && !mIsInteractive);
    }

    /**
     * Message handler
     *
     * @param msg The message
     */
    @Override
    public void handleMessage(Message msg) {
        if (DBG) log("handleMessage msg=" + msg);
        switch (msg.what) {
            case EVENT_RIL_CONNECTED:
                onRilConnected();
                break;
            default:
                updateDeviceState(msg.what, msg.arg1 != 0);
        }
    }

    /**
     * Update the device and send the information to the modem.
     *
     * @param eventType Device state event type
     * @param state True if enabled/on, otherwise disabled/off.
     */
    private void updateDeviceState(int eventType, boolean state) {
        switch (eventType) {
            case EVENT_SCREEN_STATE_CHANGED:
                if (mIsInteractive == state) return;
                mIsInteractive = state;
                break;
            case EVENT_CHARGING_STATE_CHANGED:
                if (mIsCharging == state) return;
                mIsCharging = state;
                sendDeviceState(CHARGING_STATE, mIsCharging);
                break;
            case EVENT_TETHERING_STATE_CHANGED:
                if (mIsTetheringOn == state) return;
                mIsTetheringOn = state;
                break;
            case EVENT_POWER_SAVE_MODE_CHANGED:
                if (mIsPowerSaveOn == state) return;
                mIsPowerSaveOn = state;
                sendDeviceState(POWER_SAVE_MODE, mIsPowerSaveOn);
                break;
            default:
                return;
        }

        if (mIsLowDataExpected != isLowDataExpected()) {
            mIsLowDataExpected = !mIsLowDataExpected;
            sendDeviceState(LOW_DATA_EXPECTED, mIsLowDataExpected);
        }

        if (mLegacyScreenState != shouldInvokeLegacyScreenOn()) {
            mLegacyScreenState = !mLegacyScreenState;
            // TODO: Once all vendor RILs support the new API RIL_REQUEST_SEND_DEVICE_STATE and
            // RIL_REQUEST_SET_UNSOLICITED_RESPONSE_FILTER, we should not invoke this legacy
            // screen state API anymore.
            updateLegacyScreenState(mLegacyScreenState);
        }

        int newFilter = 0;
        if (!shouldTurnOffSignalStrength()) {
            newFilter |= UnsolicitedResponseFilter.SIGNAL_STRENGTH.getValue();
        }

        if (!shouldTurnOffFullNetworkUpdate()) {
            newFilter |= UnsolicitedResponseFilter.FULL_NETWORK_STATE.getValue();
        }

        if (!shouldTurnOffDormancyUpdate()) {
            newFilter |= UnsolicitedResponseFilter.DATA_CALL_DORMANCY_CHANGED.getValue();
        }

        if (newFilter != mUnsolicitedResponseFilter) {
            mLocalLog.log("old filter: " + mUnsolicitedResponseFilter + ", new filter: "
                    + newFilter);
            setUnsolResponseFilter(newFilter);
            mUnsolicitedResponseFilter = newFilter;
        }
    }

    /**
     * Called when RIL is connected during boot up or reconnected after modem restart.
     *
     * When modem crashes, if the user turns the screen off before RIL reconnects, device
     * state and filter cannot be sent to modem. Resend the state here so that modem
     * has the correct state (to stop signal strength reporting, etc).
     */
    private void onRilConnected() {
        if (DBG) log("RIL connected.");
        mLocalLog.log("RIL connected.");
        updateLegacyScreenState(mLegacyScreenState);
        sendDeviceState(CHARGING_STATE, mIsCharging);
        sendDeviceState(LOW_DATA_EXPECTED, mIsLowDataExpected);
        sendDeviceState(POWER_SAVE_MODE, mIsPowerSaveOn);
        setUnsolResponseFilter(mUnsolicitedResponseFilter);
    }

    /**
     * Update the legacy screen state. Note this should be removed once all the vendor RILs support
     * the new RIL_REQUEST_SEND_DEVICE_STATE and RIL_REQUEST_SET_UNSOLICITED_RESPONSE_FILTER.
     *
     * @param screenOn True if screen is on
     */
    private void updateLegacyScreenState(boolean screenOn) {
        mLocalLog.log("legacy screen " + (screenOn ? "on" : "off"));
        mPhone.mCi.sendLegacyScreenState(screenOn);
    }

    /**
     * Send the device state to the modem.
     *
     * @param type Device state type. See {@link #DeviceStateType DeviceStateType} for the details.
     * @param state True if enabled/on, otherwise disabled/off
     */
    private void sendDeviceState(DeviceStateType type, boolean state) {
        mLocalLog.log("send type: " + type + ", state=" + state);
        mPhone.mCi.sendDeviceState(type, state, null);
    }

    /**
     * Turn on/off the unsolicited response from the modem.
     *
     * @param filter See {@link #UnsolicitedResponseFilter UnsolicitedResponseFilter} for the
     *               definition of each bit.
     */
    private void setUnsolResponseFilter(int filter) {
        mLocalLog.log("set filter: " + filter);
        mPhone.mCi.setUnsolResponseFilter(filter, null);
    }

    /**
     * @return True if the device is currently in power save mode.
     * See {@link android.os.BatteryManager#isPowerSaveMode BatteryManager.isPowerSaveMode()}.
     */
    private boolean isPowerSaveModeOn() {
        final PowerManager pm = (PowerManager) mPhone.getContext().getSystemService(
                Context.POWER_SERVICE);
        return pm.isPowerSaveMode();
    }

    /**
     * @return Return true if the battery is currently considered to be charging. This means that
     * the device is plugged in and is supplying sufficient power that the battery level is
     * going up (or the battery is fully charged).
     * See {@link android.os.BatteryManager#isCharging BatteryManager.isCharging()}.
     */
    private boolean isDeviceCharging() {
        final BatteryManager bm = (BatteryManager) mPhone.getContext().getSystemService(
                Context.BATTERY_SERVICE);
        return bm.isCharging();
    }

    /**
     * @return True if the device is in an interactive state.
     * See {@link android.os.PowerManager#isInteractive PowerManager.isInteractive()}.
     */
    private boolean isInteractive() {
        final PowerManager pm = (PowerManager) mPhone.getContext().getSystemService(
                Context.POWER_SERVICE);
        return pm.isInteractive();
    }

    private void log(String msg) {
        Rlog.d(TAG, msg);
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        final IndentingPrintWriter ipw = new IndentingPrintWriter(pw, "  ");
        ipw.println("DeviceStateMonitor:");
        ipw.increaseIndent();
        ipw.println("mIsTetheringOn=" + mIsTetheringOn);
        ipw.println("mIsInteractive=" + mIsInteractive);
        ipw.println("mIsCharging=" + mIsCharging);
        ipw.println("mIsPowerSaveOn=" + mIsPowerSaveOn);
        ipw.println("mLegacyScreenState=" + mLegacyScreenState);
        ipw.println("mIsLowDataExpected=" + mIsLowDataExpected);
        ipw.println("mUnsolicitedResponseFilter=" + mUnsolicitedResponseFilter);
        ipw.println("Local logs:");
        ipw.increaseIndent();
        mLocalLog.dump(fd, ipw, args);
        ipw.decreaseIndent();
        ipw.decreaseIndent();
        ipw.println("++++++++++++++++++++++++++++++++");
        ipw.flush();
    }
}
