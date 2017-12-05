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

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.res.Resources;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Binder;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.Registrant;
import android.os.RegistrantList;
import android.telephony.Rlog;
import android.telephony.TelephonyManager;
import android.util.LocalLog;
import android.view.WindowManager;

import com.android.internal.R;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.CommandsInterface.RadioState;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppType;
import com.android.internal.telephony.uicc.IccCardStatus.CardState;
import com.android.internal.telephony.uicc.IccCardStatus.PinState;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.List;

/**
 * {@hide}
 */
public class UiccCard {
    protected static final String LOG_TAG = "UiccCard";
    protected static final boolean DBG = true;

    public static final String EXTRA_ICC_CARD_ADDED =
            "com.android.internal.telephony.uicc.ICC_CARD_ADDED";

    private static final String OPERATOR_BRAND_OVERRIDE_PREFIX = "operator_branding_";

    private final Object mLock = new Object();
    private CardState mCardState;
    // TODO(yinxu): Get mIccid from active profile once all the sim records are loaded.
    private String mIccid;
    private UiccProfile mUiccProfile;
    private Context mContext;
    private CommandsInterface mCi;
    private RadioState mLastRadioState = RadioState.RADIO_UNAVAILABLE;

    private RegistrantList mAbsentRegistrants = new RegistrantList();

    private static final int EVENT_CARD_REMOVED = 13;
    private static final int EVENT_CARD_ADDED = 14;
    private static final int EVENT_OPEN_LOGICAL_CHANNEL_DONE = 15;
    private static final int EVENT_CLOSE_LOGICAL_CHANNEL_DONE = 16;
    private static final int EVENT_TRANSMIT_APDU_LOGICAL_CHANNEL_DONE = 17;
    private static final int EVENT_TRANSMIT_APDU_BASIC_CHANNEL_DONE = 18;
    private static final int EVENT_SIM_IO_DONE = 19;

    private static final LocalLog mLocalLog = new LocalLog(100);

    private final int mPhoneId;

    public UiccCard(Context c, CommandsInterface ci, IccCardStatus ics, int phoneId) {
        if (DBG) log("Creating");
        mCardState = ics.mCardState;
        mPhoneId = phoneId;
        update(c, ci, ics);
    }

    public void dispose() {
        synchronized (mLock) {
            if (DBG) log("Disposing card");
            if (mUiccProfile != null) {
                mUiccProfile.dispose();
            }
            mUiccProfile = null;
        }
    }

    public void update(Context c, CommandsInterface ci, IccCardStatus ics) {
        synchronized (mLock) {
            CardState oldState = mCardState;
            mCardState = ics.mCardState;
            mContext = c;
            mCi = ci;

            // TODO(yinxu): Currently UiccCard will not be disposed when card is removed, so we
            // have to dispose the UiccProfile when card is removed. Once UiccSlot is enabled,
            // the below logic can be removed since the UiccCard will be disposed then.
            if (mCardState != CardState.CARDSTATE_ABSENT) {
                if (mUiccProfile == null) {
                    mUiccProfile = new UiccProfile(mContext, mCi, ics, mPhoneId, this);
                }
                mUiccProfile.update(mContext, mCi, ics);
            } else {
                if (mUiccProfile != null) {
                    mUiccProfile.dispose();
                }
                mUiccProfile = null;
            }

            RadioState radioState = mCi.getRadioState();
            if (DBG) log("update: radioState=" + radioState + " mLastRadioState="
                    + mLastRadioState);
            // No notifications while radio is off or we just powering up
            if (radioState == RadioState.RADIO_ON && mLastRadioState == RadioState.RADIO_ON) {
                if (oldState != CardState.CARDSTATE_ABSENT &&
                        mCardState == CardState.CARDSTATE_ABSENT) {
                    if (DBG) log("update: notify card removed");
                    mAbsentRegistrants.notifyRegistrants();
                    mHandler.sendMessage(mHandler.obtainMessage(EVENT_CARD_REMOVED, null));
                } else if (oldState == CardState.CARDSTATE_ABSENT &&
                        mCardState != CardState.CARDSTATE_ABSENT) {
                    if (DBG) log("update: notify card added");
                    mHandler.sendMessage(mHandler.obtainMessage(EVENT_CARD_ADDED, null));
                }
            }
            mLastRadioState = radioState;
        }
    }

    @Override
    protected void finalize() {
        if (DBG) log("UiccCard finalized");
    }

    /**
     * Notifies handler of any transition into State.ABSENT
     */
    public void registerForAbsent(Handler h, int what, Object obj) {
        synchronized (mLock) {
            Registrant r = new Registrant (h, what, obj);

            mAbsentRegistrants.add(r);

            if (mCardState == CardState.CARDSTATE_ABSENT) {
                r.notifyRegistrant();
            }
        }
    }

    public void unregisterForAbsent(Handler h) {
        synchronized (mLock) {
            mAbsentRegistrants.remove(h);
        }
    }

    /**
     * Notifies handler when carrier privilege rules are loaded.
     * @deprecated Please use
     * {@link UiccProfile#registerForCarrierPrivilegeRulesLoaded(Handler, int, Object)} instead.
     */
    @Deprecated
    public void registerForCarrierPrivilegeRulesLoaded(Handler h, int what, Object obj) {
        synchronized (mLock) {
            if (mUiccProfile != null) {
                mUiccProfile.registerForCarrierPrivilegeRulesLoaded(h, what, obj);
            }
        }
    }

    /**
     * @deprecated Please use
     * {@link UiccProfile#unregisterForCarrierPrivilegeRulesLoaded(Handler)} instead.
     */
    @Deprecated
    public void unregisterForCarrierPrivilegeRulesLoaded(Handler h) {
        synchronized (mLock) {
            if (mUiccProfile != null) {
                mUiccProfile.unregisterForCarrierPrivilegeRulesLoaded(h);
            }
        }
    }

    private void onIccSwap(boolean isAdded) {

        boolean isHotSwapSupported = mContext.getResources().getBoolean(
                R.bool.config_hotswapCapable);

        if (isHotSwapSupported) {
            log("onIccSwap: isHotSwapSupported is true, don't prompt for rebooting");
            return;
        }
        log("onIccSwap: isHotSwapSupported is false, prompt for rebooting");

        promptForRestart(isAdded);
    }

    private void promptForRestart(boolean isAdded) {
        synchronized (mLock) {
            final Resources res = mContext.getResources();
            final String dialogComponent = res.getString(
                    R.string.config_iccHotswapPromptForRestartDialogComponent);
            if (dialogComponent != null) {
                Intent intent = new Intent().setComponent(ComponentName.unflattenFromString(
                        dialogComponent)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra(EXTRA_ICC_CARD_ADDED, isAdded);
                try {
                    mContext.startActivity(intent);
                    return;
                } catch (ActivityNotFoundException e) {
                    loge("Unable to find ICC hotswap prompt for restart activity: " + e);
                }
            }

            // TODO: Here we assume the device can't handle SIM hot-swap
            //      and has to reboot. We may want to add a property,
            //      e.g. REBOOT_ON_SIM_SWAP, to indicate if modem support
            //      hot-swap.
            DialogInterface.OnClickListener listener = null;


            // TODO: SimRecords is not reset while SIM ABSENT (only reset while
            //       Radio_off_or_not_available). Have to reset in both both
            //       added or removed situation.
            listener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    synchronized (mLock) {
                        if (which == DialogInterface.BUTTON_POSITIVE) {
                            if (DBG) log("Reboot due to SIM swap");
                            PowerManager pm = (PowerManager) mContext
                                    .getSystemService(Context.POWER_SERVICE);
                            pm.reboot("SIM is added.");
                        }
                    }
                }

            };

            Resources r = Resources.getSystem();

            String title = (isAdded) ? r.getString(R.string.sim_added_title) :
                r.getString(R.string.sim_removed_title);
            String message = (isAdded) ? r.getString(R.string.sim_added_message) :
                r.getString(R.string.sim_removed_message);
            String buttonTxt = r.getString(R.string.sim_restart_button);

            AlertDialog dialog = new AlertDialog.Builder(mContext)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(buttonTxt, listener)
            .create();
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
            dialog.show();
        }
    }

    protected Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg){
            switch (msg.what) {
                case EVENT_CARD_REMOVED:
                    onIccSwap(false);
                    break;
                case EVENT_CARD_ADDED:
                    onIccSwap(true);
                    break;
                case EVENT_OPEN_LOGICAL_CHANNEL_DONE:
                case EVENT_CLOSE_LOGICAL_CHANNEL_DONE:
                case EVENT_TRANSMIT_APDU_LOGICAL_CHANNEL_DONE:
                case EVENT_TRANSMIT_APDU_BASIC_CHANNEL_DONE:
                case EVENT_SIM_IO_DONE:
                    AsyncResult ar = (AsyncResult)msg.obj;
                    if (ar.exception != null) {
                        loglocal("Exception: " + ar.exception);
                        log("Error in SIM access with exception" + ar.exception);
                    }
                    AsyncResult.forMessage((Message)ar.userObj, ar.result, ar.exception);
                    ((Message)ar.userObj).sendToTarget();
                    break;
                default:
                    loge("Unknown Event " + msg.what);
            }
        }
    };

    private class ClickListener implements DialogInterface.OnClickListener {
        String pkgName;
        public ClickListener(String pkgName) {
            this.pkgName = pkgName;
        }
        @Override
        public void onClick(DialogInterface dialog, int which) {
            synchronized (mLock) {
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    Intent market = new Intent(Intent.ACTION_VIEW);
                    market.setData(Uri.parse("market://details?id=" + pkgName));
                    market.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    mContext.startActivity(market);
                } else if (which == DialogInterface.BUTTON_NEGATIVE) {
                    if (DBG) log("Not now clicked for carrier app dialog.");
                }
            }
        }
    }

    /**
     * @deprecated Please use {@link UiccProfile#isApplicationOnIcc(AppType)} instead.
     */
    @Deprecated
    public boolean isApplicationOnIcc(IccCardApplicationStatus.AppType type) {
        synchronized (mLock) {
            if (mUiccProfile != null) {
                return mUiccProfile.isApplicationOnIcc(type);
            } else {
                return false;
            }
        }
    }

    public CardState getCardState() {
        synchronized (mLock) {
            return mCardState;
        }
    }

    /**
     * @deprecated Please use {@link UiccProfile#getUniversalPinState()} instead.
     */
    @Deprecated
    public PinState getUniversalPinState() {
        synchronized (mLock) {
            if (mUiccProfile != null) {
                return mUiccProfile.getUniversalPinState();
            } else {
                return PinState.PINSTATE_UNKNOWN;
            }
        }
    }

    /**
     * @deprecated Please use {@link UiccProfile#getApplication(int)} instead.
     */
    @Deprecated
    public UiccCardApplication getApplication(int family) {
        synchronized (mLock) {
            if (mUiccProfile != null) {
                return mUiccProfile.getApplication(family);
            } else {
                return null;
            }
        }
    }

    /**
     * @deprecated Please use {@link UiccProfile#getApplicationIndex(int)} instead.
     */
    @Deprecated
    public UiccCardApplication getApplicationIndex(int index) {
        synchronized (mLock) {
            if (mUiccProfile != null) {
                return mUiccProfile.getApplicationIndex(index);
            } else {
                return null;
            }
        }
    }

    /**
     * Returns the SIM application of the specified type.
     *
     * @param type ICC application type (@see com.android.internal.telephony.PhoneConstants#APPTYPE_xxx)
     * @return application corresponding to type or a null if no match found
     *
     * @deprecated Please use {@link UiccProfile#getApplicationByType(int)} instead.
     */
    @Deprecated
    public UiccCardApplication getApplicationByType(int type) {
        synchronized (mLock) {
            if (mUiccProfile != null) {
                return mUiccProfile.getApplicationByType(type);
            } else {
                return null;
            }
        }
    }

    /**
     * Resets the application with the input AID. Returns true if any changes were made.
     *
     * A null aid implies a card level reset - all applications must be reset.
     *
     * @deprecated Please use {@link UiccProfile#resetAppWithAid(String)} instead.
     */
    @Deprecated
    public boolean resetAppWithAid(String aid) {
        synchronized (mLock) {
            if (mUiccProfile != null) {
                return mUiccProfile.resetAppWithAid(aid);
            } else {
                return false;
            }
        }
    }

    // TODO(yinxu): Below methods which expose CommandsInterface should be marked as @Deprecated
    // once UiccSlot is enabled because they actually belong to UiccSlot.
    /**
     * Exposes {@link CommandsInterface#iccOpenLogicalChannel}
     */
    public void iccOpenLogicalChannel(String AID, int p2, Message response) {
        loglocal("Open Logical Channel: " + AID + " , " + p2 + " by pid:" + Binder.getCallingPid()
                + " uid:" + Binder.getCallingUid());
        mCi.iccOpenLogicalChannel(AID, p2,
                mHandler.obtainMessage(EVENT_OPEN_LOGICAL_CHANNEL_DONE, response));
    }

    /**
     * Exposes {@link CommandsInterface#iccCloseLogicalChannel}
     */
    public void iccCloseLogicalChannel(int channel, Message response) {
        loglocal("Close Logical Channel: " + channel);
        mCi.iccCloseLogicalChannel(channel,
                mHandler.obtainMessage(EVENT_CLOSE_LOGICAL_CHANNEL_DONE, response));
    }

    /**
     * Exposes {@link CommandsInterface#iccTransmitApduLogicalChannel}
     */
    public void iccTransmitApduLogicalChannel(int channel, int cla, int command,
            int p1, int p2, int p3, String data, Message response) {
        mCi.iccTransmitApduLogicalChannel(channel, cla, command, p1, p2, p3,
                data, mHandler.obtainMessage(EVENT_TRANSMIT_APDU_LOGICAL_CHANNEL_DONE, response));
    }

    /**
     * Exposes {@link CommandsInterface#iccTransmitApduBasicChannel}
     */
    public void iccTransmitApduBasicChannel(int cla, int command,
            int p1, int p2, int p3, String data, Message response) {
        mCi.iccTransmitApduBasicChannel(cla, command, p1, p2, p3,
                data, mHandler.obtainMessage(EVENT_TRANSMIT_APDU_BASIC_CHANNEL_DONE, response));
    }

    /**
     * Exposes {@link CommandsInterface#iccIO}
     */
    public void iccExchangeSimIO(int fileID, int command, int p1, int p2, int p3,
            String pathID, Message response) {
        mCi.iccIO(command, fileID, pathID, p1, p2, p3, null, null,
                mHandler.obtainMessage(EVENT_SIM_IO_DONE, response));
    }

    /**
     * Exposes {@link CommandsInterface#sendEnvelopeWithStatus}
     */
    public void sendEnvelopeWithStatus(String contents, Message response) {
        mCi.sendEnvelopeWithStatus(contents, response);
    }

    /**
     * Returns number of applications on this card
     * @deprecated Please use {@link UiccProfile#getNumApplications()} instead.
     */
    @Deprecated
    public int getNumApplications() {
        if (mUiccProfile != null) {
            return mUiccProfile.getNumApplications();
        } else {
            return 0;
        }
    }

    public int getPhoneId() {
        return mPhoneId;
    }

    /**
     * Returns true iff carrier privileges rules are null (dont need to be loaded) or loaded.
     * @deprecated Please use {@link UiccProfile#areCarrierPriviligeRulesLoaded()} instead.
     */
    @Deprecated
    public boolean areCarrierPriviligeRulesLoaded() {
        if (mUiccProfile != null) {
            return mUiccProfile.areCarrierPriviligeRulesLoaded();
        } else {
            return false;
        }
    }

    /**
     * Returns true if there are some carrier privilege rules loaded and specified.
     * @deprecated Please use {@link UiccProfile#hasCarrierPrivilegeRules()} instead.
     */
    @Deprecated
    public boolean hasCarrierPrivilegeRules() {
        if (mUiccProfile != null) {
            return mUiccProfile.hasCarrierPrivilegeRules();
        } else {
            return false;
        }
    }

    /**
     * Exposes {@link UiccCarrierPrivilegeRules#getCarrierPrivilegeStatus}.
     * @deprecated Please use
     * {@link UiccProfile#getCarrierPrivilegeStatus(Signature, String)} instead.
     */
    @Deprecated
    public int getCarrierPrivilegeStatus(Signature signature, String packageName) {
        if (mUiccProfile != null) {
            return mUiccProfile.getCarrierPrivilegeStatus(signature, packageName);
        } else {
            return TelephonyManager.CARRIER_PRIVILEGE_STATUS_RULES_NOT_LOADED;
        }
    }

    /**
     * Exposes {@link UiccCarrierPrivilegeRules#getCarrierPrivilegeStatus}.
     * @deprecated Please use
     * {@link UiccProfile#getCarrierPrivilegeStatus(PackageManager, String)} instead.
     */
    @Deprecated
    public int getCarrierPrivilegeStatus(PackageManager packageManager, String packageName) {
        if (mUiccProfile != null) {
            return mUiccProfile.getCarrierPrivilegeStatus(packageManager, packageName);
        } else {
            return TelephonyManager.CARRIER_PRIVILEGE_STATUS_RULES_NOT_LOADED;
        }
    }

    /**
     * Exposes {@link UiccCarrierPrivilegeRules#getCarrierPrivilegeStatus}.
     * @deprecated Please use {@link UiccProfile#getCarrierPrivilegeStatus(PackageInfo)} instead.
     */
    @Deprecated
    public int getCarrierPrivilegeStatus(PackageInfo packageInfo) {
        if (mUiccProfile != null) {
            return mUiccProfile.getCarrierPrivilegeStatus(packageInfo);
        } else {
            return TelephonyManager.CARRIER_PRIVILEGE_STATUS_RULES_NOT_LOADED;
        }
    }

    /**
     * Exposes {@link UiccCarrierPrivilegeRules#getCarrierPrivilegeStatusForCurrentTransaction}.
     * @deprecated Please use
     * {@link UiccProfile#getCarrierPrivilegeStatusForCurrentTransaction(PackageManager)} instead.
     */
    @Deprecated
    public int getCarrierPrivilegeStatusForCurrentTransaction(PackageManager packageManager) {
        if (mUiccProfile != null) {
            return mUiccProfile.getCarrierPrivilegeStatusForCurrentTransaction(packageManager);
        } else {
            return TelephonyManager.CARRIER_PRIVILEGE_STATUS_RULES_NOT_LOADED;
        }
    }

    /**
     * Exposes {@link UiccCarrierPrivilegeRules#getCarrierPackageNamesForIntent}.
     * @deprecated Please use
     * {@link UiccProfile#getCarrierPackageNamesForIntent(PackageManager, Intent)} instead.
     */
    @Deprecated
    public List<String> getCarrierPackageNamesForIntent(
            PackageManager packageManager, Intent intent) {
        if (mUiccProfile != null) {
            return mUiccProfile.getCarrierPackageNamesForIntent(packageManager, intent);
        } else {
            return null;
        }
    }

    /**
     * @deprecated Please use {@link UiccProfile#setOperatorBrandOverride(String)} instead.
     */
    @Deprecated
    public boolean setOperatorBrandOverride(String brand) {
        if (mUiccProfile != null) {
            return mUiccProfile.setOperatorBrandOverride(brand);
        } else {
            return false;
        }
    }

    /**
     * @deprecated Please use {@link UiccProfile#getOperatorBrandOverride()} instead.
     */
    @Deprecated
    public String getOperatorBrandOverride() {
        if (mUiccProfile != null) {
            return mUiccProfile.getOperatorBrandOverride();
        } else {
            return null;
        }
    }

    public String getIccId() {
        if (mIccid != null) {
            return mIccid;
        } else if (mUiccProfile != null) {
            return mUiccProfile.getIccId();
        } else {
            return null;
        }
    }

    private void log(String msg) {
        Rlog.d(LOG_TAG, msg);
    }

    private void loge(String msg) {
        Rlog.e(LOG_TAG, msg);
    }

    private void loglocal(String msg) {
        if (DBG) mLocalLog.log(msg);
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("UiccCard:");
        pw.println(" mCi=" + mCi);
        pw.println(" mLastRadioState=" + mLastRadioState);
        pw.println(" mAbsentRegistrants: size=" + mAbsentRegistrants.size());
        for (int i = 0; i < mAbsentRegistrants.size(); i++) {
            pw.println("  mAbsentRegistrants[" + i + "]="
                    + ((Registrant) mAbsentRegistrants.get(i)).getHandler());
        }
        pw.println(" mCardState=" + mCardState);
        pw.println();
        if (mUiccProfile != null) {
            mUiccProfile.dump(fd, pw, args);
        }
    }
}
