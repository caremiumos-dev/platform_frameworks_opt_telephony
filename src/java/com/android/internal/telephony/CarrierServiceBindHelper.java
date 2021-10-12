/*
* Copyright (C) 2015 The Android Open Source Project
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

import android.compat.annotation.UnsupportedAppUsage;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Process;
import android.os.SystemClock;
import android.os.UserHandle;
import android.service.carrier.CarrierService;
import android.telephony.AnomalyReporter;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.LocalLog;
import android.util.Log;
import android.util.SparseArray;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.util.TelephonyUtils;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.List;
import java.util.UUID;

/**
 * Manages long-lived bindings to carrier services
 * @hide
 */
public class CarrierServiceBindHelper {
    private static final String LOG_TAG = "CarrierSvcBindHelper";

    /**
     * How long to linger a binding after an app loses carrier privileges, as long as no new
     * binding comes in to take its place.
     */
    private static final int UNBIND_DELAY_MILLIS = 30 * 1000; // 30 seconds

    @UnsupportedAppUsage(maxTargetSdk = Build.VERSION_CODES.R, trackingBug = 170729553)
    private Context mContext;
    @VisibleForTesting
    public SparseArray<AppBinding> mBindings = new SparseArray();
    @VisibleForTesting
    public SparseArray<String> mLastSimState = new SparseArray<>();
    private final PackageChangeReceiver mPackageMonitor = new CarrierServicePackageMonitor();
    private final LocalLog mLocalLog = new LocalLog(100);

    // whether we have successfully bound to the service
    private boolean mServiceBound = false;

    // UUID for the anomaly to report carrier service binding lost (unbound or disconnected)
    private static final UUID UUID_ANOMALY_CARRIER_SERVICE_BINDING_LOST =
            UUID.fromString("4fad323a-82bf-4329-8cfb-771eac7adbcc");
    // The minimum interval between two anomalies allowed to report here
    private static final int ANOMALIES_MINIMUM_INTERVAL_IN_MILLIS = 24 * 60 * 60 * 1000; // 24 hours
    private long mLastAnomalyReportingTimestamp = -1;

    // The initial delay to proactively rebind when binding get lost
    private static final int PROACTIVE_REBIND_INITIAL_DELAY_MILLIS = 30 * 1000; // 30 seconds
    // The maximum delay to proactively rebind before giving up. The number is picked so at most
    // two anomalies were reported
    private static final int PROACTIVE_REBIND_MAXIMUM_DELAY_MILLIS = 47 * 60 * 60 * 100; //47 hours
    // Delay start from initial value, doubled if binding does not re-establish,
    // until it reaches maximum and set to -1.
    private long mLastProactiveRebindDelay = PROACTIVE_REBIND_INITIAL_DELAY_MILLIS;

    private BroadcastReceiver mUserUnlockedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            logdWithLocalLog("Received " + action);

            if (Intent.ACTION_USER_UNLOCKED.equals(action)) {
                // On user unlock, new components might become available, so reevaluate all
                // bindings.
                for (int phoneId = 0; phoneId < mBindings.size(); phoneId++) {
                    mBindings.get(phoneId).rebind();
                }
            }
        }
    };

    // msg.obj is the phoneId
    private static final int EVENT_REBIND = 0;
    // msg.obj is the phoneId
    @VisibleForTesting
    public static final int EVENT_PERFORM_IMMEDIATE_UNBIND = 1;
    @VisibleForTesting
    public static final int EVENT_MULTI_SIM_CONFIG_CHANGED = 2;
    // msg.obj is the message string
    private static final int EVENT_BINDING_LOST = 3;

    @UnsupportedAppUsage(maxTargetSdk = Build.VERSION_CODES.R, trackingBug = 170729553)
    @VisibleForTesting
    public Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            int phoneId;
            AppBinding binding;
            logdWithLocalLog("mHandler: " + msg.what);

            switch (msg.what) {
                case EVENT_REBIND:
                    phoneId = (int) msg.obj;
                    binding = mBindings.get(phoneId);
                    if (binding == null) return;
                    logdWithLocalLog("Rebinding if necessary for phoneId: " + binding.getPhoneId());
                    binding.rebind();
                    break;
                case EVENT_PERFORM_IMMEDIATE_UNBIND:
                    phoneId = (int) msg.obj;
                    binding = mBindings.get(phoneId);
                    if (binding == null) return;
                    logdWithLocalLog("Unbind immediate with phoneId: " + binding.getPhoneId());
                    binding.performImmediateUnbind();
                    break;
                case EVENT_MULTI_SIM_CONFIG_CHANGED:
                    updateBindingsAndSimStates();
                    break;
                case EVENT_BINDING_LOST:
                    String message = (String) msg.obj;
                    maybeReportAnomaly(message);
                    maybeProactivelyRebind();
                    break;
            }
        }
    };

    public CarrierServiceBindHelper(Context context) {
        mContext = context.createContextAsUser(Process.myUserHandle(), 0);

        updateBindingsAndSimStates();

        PhoneConfigurationManager.registerForMultiSimConfigChange(
                mHandler, EVENT_MULTI_SIM_CONFIG_CHANGED, null);

        mPackageMonitor.register(
                context, mHandler.getLooper(), UserHandle.ALL);
        try {
            Context contextAsUser = mContext.createPackageContextAsUser(mContext.getPackageName(),
                0, UserHandle.SYSTEM);
            contextAsUser.registerReceiver(mUserUnlockedReceiver,
                new IntentFilter(Intent.ACTION_USER_UNLOCKED), null /* broadcastPermission */,
                mHandler);
        } catch (PackageManager.NameNotFoundException e) {
            logeWithLocalLog("Package name not found: " + e.getMessage());
        }
    }

    // Create or dispose mBindings and mLastSimState objects.
    private void updateBindingsAndSimStates() {
        int prevLen = mBindings.size();
        int newLen = ((TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE))
                .getActiveModemCount();

        // If prevLen < newLen, allocate AppBinding and simState objects.
        for (int phoneId = prevLen; phoneId < newLen; phoneId++) {
            mBindings.put(phoneId, new AppBinding(phoneId));
            mLastSimState.put(phoneId, new String());
        }

        // If prevLen > newLen, dispose AppBinding and simState objects.
        for (int phoneId = newLen; phoneId < prevLen; phoneId++) {
            mBindings.get(phoneId).unbind(true);
            mBindings.delete(phoneId);
            mLastSimState.delete(phoneId);
        }
    }

    void updateForPhoneId(int phoneId, String simState) {
        logdWithLocalLog("update binding for phoneId: " + phoneId + " simState: " + simState);
        if (!SubscriptionManager.isValidPhoneId(phoneId)) {
            return;
        }
        if (TextUtils.isEmpty(simState) || phoneId >= mLastSimState.size()) return;
        if (simState.equals(mLastSimState.get(phoneId))) {
            // ignore consecutive duplicated events
            return;
        } else {
            mLastSimState.put(phoneId, simState);
        }
        mHandler.sendMessage(mHandler.obtainMessage(EVENT_REBIND, phoneId));
    }

    private class AppBinding {
        private int phoneId;
        private CarrierServiceConnection connection;
        private int bindCount;
        private long lastBindStartMillis;
        private int unbindCount;
        private long lastUnbindMillis;
        private String carrierPackage;
        private String carrierServiceClass;
        private long mUnbindScheduledUptimeMillis = -1;

        public AppBinding(int phoneId) {
            this.phoneId = phoneId;
        }

        public int getPhoneId() {
            return phoneId;
        }

        /** Return the package that is currently being bound to, or null if there is no binding. */
        public String getPackage() {
            return carrierPackage;
        }

        /**
         * Update the bindings for the current carrier app for this phone.
         *
         * <p>Safe to call even if a binding already exists. If the current binding is invalid, it
         * will be dropped. If it is valid, it will be left untouched.
         */
        void rebind() {
            // Get the package name for the carrier app
            List<String> carrierPackageNames =
                TelephonyManager.from(mContext).getCarrierPackageNamesForIntentAndPhone(
                    new Intent(CarrierService.CARRIER_SERVICE_INTERFACE), phoneId
                );

            if (carrierPackageNames == null || carrierPackageNames.size() <= 0) {
                logdWithLocalLog("No carrier app for: " + phoneId);
                // Unbind after a delay in case this is a temporary blip in carrier privileges.
                unbind(false /* immediate */);
                return;
            }

            logdWithLocalLog("Found carrier app: " + carrierPackageNames);
            String candidateCarrierPackage = carrierPackageNames.get(0);
            // If we are binding to a different package, unbind immediately from the current one.
            if (!TextUtils.equals(carrierPackage, candidateCarrierPackage)) {
                unbind(true /* immediate */);
            }

            // Look up the carrier service
            Intent carrierService = new Intent(CarrierService.CARRIER_SERVICE_INTERFACE);
            carrierService.setPackage(candidateCarrierPackage);

            ResolveInfo carrierResolveInfo = mContext.getPackageManager().resolveService(
                carrierService, PackageManager.GET_META_DATA);
            Bundle metadata = null;
            String candidateServiceClass = null;
            if (carrierResolveInfo != null) {
                metadata = carrierResolveInfo.serviceInfo.metaData;
                ComponentInfo componentInfo = TelephonyUtils.getComponentInfo(carrierResolveInfo);
                candidateServiceClass = new ComponentName(componentInfo.packageName,
                    componentInfo.name).getClassName();
            }

            // Only bind if the service wants it
            if (metadata == null ||
                !metadata.getBoolean("android.service.carrier.LONG_LIVED_BINDING", false)) {
                logdWithLocalLog("Carrier app does not want a long lived binding");
                unbind(true /* immediate */);
                return;
            }

            if (!TextUtils.equals(carrierServiceClass, candidateServiceClass)) {
                logdWithLocalLog("CarrierService class changed, unbind immediately.");
                // Unbind immediately if the carrier service component has changed.
                unbind(true /* immediate */);
            } else if (connection != null && connection.connected) {
                logdWithLocalLog(
                        "CarrierService class unchanged with connection up, cancelScheduledUnbind");
                // Component is unchanged and connection is up - do nothing, but cancel any
                // scheduled unbinds.
                cancelScheduledUnbind();
                return;
            }

            carrierPackage = candidateCarrierPackage;
            carrierServiceClass = candidateServiceClass;

            logdWithLocalLog("Binding to " + carrierPackage + " for phone " + phoneId);

            // Log debug information
            bindCount++;
            lastBindStartMillis = System.currentTimeMillis();

            connection = new CarrierServiceConnection();

            String error;
            try {
                if (mContext.bindService(
                        carrierService,
                        Context.BIND_AUTO_CREATE
                                | Context.BIND_FOREGROUND_SERVICE
                                | Context.BIND_INCLUDE_CAPABILITIES,
                        (r) -> mHandler.post(r),
                        connection)) {
                    logdWithLocalLog("service bound");
                    mServiceBound = true;
                    return;
                }

                error = "bindService returned false";
            } catch (SecurityException ex) {
                error = ex.getMessage();
            }

            logdWithLocalLog("Unable to bind to " + carrierPackage + " for phone " + phoneId
                    + ". Error: " + error);
            unbind(true /* immediate */);
        }

        /**
         * Release the binding.
         *
         * @param immediate whether the binding should be released immediately or after a short
         *                  delay. This should be true unless the reason for the unbind is that no
         *                  app has carrier privileges, in which case it is useful to delay
         *                  unbinding in case this is a temporary SIM blip.
         */
        void unbind(boolean immediate) {
            if (connection == null) {
                // Already fully unbound.
                return;
            }

            // Only let the binding linger if a delayed unbind is requested *and* the connection is
            // currently active. If the connection is down, unbind immediately as the app is likely
            // not running anyway and it may be a permanent disconnection (e.g. the app was
            // disabled).
            if (immediate || !connection.connected) {
                logdWithLocalLog("unbind immediately or with disconnected connection");
                cancelScheduledUnbind();
                performImmediateUnbind();
            } else if (mUnbindScheduledUptimeMillis == -1) {
                long currentUptimeMillis = SystemClock.uptimeMillis();
                mUnbindScheduledUptimeMillis = currentUptimeMillis + UNBIND_DELAY_MILLIS;
                logdWithLocalLog("Scheduling unbind in " + UNBIND_DELAY_MILLIS + " millis");
                mHandler.sendMessageAtTime(
                        mHandler.obtainMessage(EVENT_PERFORM_IMMEDIATE_UNBIND, phoneId),
                        mUnbindScheduledUptimeMillis);
            }
        }

        private void performImmediateUnbind() {
            // Log debug information
            unbindCount++;
            lastUnbindMillis = System.currentTimeMillis();

            // Clear package state now that no binding is desired.
            carrierPackage = null;
            carrierServiceClass = null;

            // Actually unbind
            if (mServiceBound) {
                String message = "Unbinding from carrier app";
                logdWithLocalLog(message);
                mServiceBound = false;
                mContext.unbindService(connection);
                maybeSendEventBindingLostWithDelay(message);
            } else {
                logdWithLocalLog("Not bound, skipping unbindService call");
            }
            connection = null;
            mUnbindScheduledUptimeMillis = -1;
        }

        private void cancelScheduledUnbind() {
            logdWithLocalLog("cancelScheduledUnbind");
            mHandler.removeMessages(EVENT_PERFORM_IMMEDIATE_UNBIND);
            mUnbindScheduledUptimeMillis = -1;
        }

        public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
            pw.println("Carrier app binding for phone " + phoneId);
            pw.println("  connection: " + connection);
            pw.println("  bindCount: " + bindCount);
            pw.println("  lastBindStartMillis: " + lastBindStartMillis);
            pw.println("  unbindCount: " + unbindCount);
            pw.println("  lastUnbindMillis: " + lastUnbindMillis);
            pw.println("  mUnbindScheduledUptimeMillis: " + mUnbindScheduledUptimeMillis);
            pw.println();
        }
    }

    private class CarrierServiceConnection implements ServiceConnection {
        private boolean connected;

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            logdWithLocalLog("Connected to carrier app: " + name.flattenToString());
            connected = true;
            cancelEventBindingLost();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            String message = "Disconnected from carrier app: " + name.flattenToString();
            logdWithLocalLog(message);
            connected = false;
            maybeSendEventBindingLostWithDelay(message);
        }

        @Override
        public void onBindingDied(ComponentName name) {
            String message = "Binding from carrier app died: " + name.flattenToString();
            logdWithLocalLog(message);
            connected = false;
            maybeSendEventBindingLostWithDelay(message);
        }

        @Override
        public void onNullBinding(ComponentName name) {
            String message = "Null binding from carrier app: " + name.flattenToString();
            logdWithLocalLog(message);
            connected = false;
            maybeSendEventBindingLostWithDelay(message);
        }

        @Override
        public String toString() {
            return "CarrierServiceConnection[connected=" + connected + "]";
        }
    }

    private class CarrierServicePackageMonitor extends PackageChangeReceiver {
        @Override
        public void onPackageAdded(String packageName) {
            evaluateBinding(packageName, true /* forceUnbind */);
        }

        @Override
        public void onPackageRemoved(String packageName) {
            evaluateBinding(packageName, true /* forceUnbind */);
        }

        @Override
        public void onPackageUpdateFinished(String packageName) {
            evaluateBinding(packageName, true /* forceUnbind */);
        }

        @Override
        public void onPackageModified(String packageName) {
            evaluateBinding(packageName, false /* forceUnbind */);
        }

        @Override
        public void onHandleForceStop(String[] packages, boolean doit) {
            if (doit) {
                for (String packageName : packages) {
                    evaluateBinding(packageName, true /* forceUnbind */);
                }
            }
        }

        private void evaluateBinding(String carrierPackageName, boolean forceUnbind) {
            for (int i = 0; i < mBindings.size(); i++) {
                AppBinding appBinding = mBindings.get(i);
                String appBindingPackage = appBinding.getPackage();
                boolean isBindingForPackage = carrierPackageName.equals(appBindingPackage);
                // Only log if this package was a carrier package to avoid log spam in the common
                // case that there are no carrier packages, but evaluate the binding if the package
                // is unset, in case this package change resulted in a new carrier package becoming
                // available for binding.
                if (isBindingForPackage) {
                    logdWithLocalLog(
                            carrierPackageName + " changed and corresponds to a phone. Rebinding.");
                }
                if (appBindingPackage == null || isBindingForPackage) {
                    if (forceUnbind) {
                        appBinding.unbind(true /* immediate */);
                    }
                    appBinding.rebind();
                }
            }
        }
    }

    private void maybeSendEventBindingLostWithDelay(String message) {
        if (mLastProactiveRebindDelay != -1) {
            mHandler.sendMessageAtTime(
                    mHandler.obtainMessage(EVENT_BINDING_LOST, message),
                    SystemClock.uptimeMillis() + mLastProactiveRebindDelay);

            mLastProactiveRebindDelay *= 2; // double the next rebind delay
            if (mLastProactiveRebindDelay >= PROACTIVE_REBIND_MAXIMUM_DELAY_MILLIS) {
                mLastProactiveRebindDelay = -1;
                logdWithLocalLog("Proactive rebind reach maximum delay, give up!");
            }
        }
    }

    private void cancelEventBindingLost() {
        mHandler.removeMessages(EVENT_BINDING_LOST);
    }

    // Proactively rebind if service is not bound or connection is lost
    private void maybeProactivelyRebind() {
        for (int phoneId = 0; phoneId < mBindings.size(); phoneId++) {
            AppBinding binding = mBindings.get(phoneId);
            if (!mServiceBound || !binding.connection.connected) {
                binding.rebind();
                logdWithLocalLog("Proactive rebind for phoneId: " + phoneId);
            }
        }
    }

    // Report anomaly if it was never reported or reported long time ago
    private void maybeReportAnomaly(String message) {
        if (mLastAnomalyReportingTimestamp == -1
                || SystemClock.elapsedRealtime() - mLastAnomalyReportingTimestamp
                > ANOMALIES_MINIMUM_INTERVAL_IN_MILLIS) {
            AnomalyReporter.reportAnomaly(UUID_ANOMALY_CARRIER_SERVICE_BINDING_LOST, message);
            mLastAnomalyReportingTimestamp = SystemClock.elapsedRealtime();
            logdWithLocalLog("Report anomaly with message: " + message);
        }
    }

    private void logdWithLocalLog(String msg) {
        Log.d(LOG_TAG, msg);
        mLocalLog.log(msg);
    }

    private void logeWithLocalLog(String msg) {
        Log.e(LOG_TAG, msg);
        mLocalLog.log(msg);
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("CarrierServiceBindHelper:");
        for (int i = 0; i < mBindings.size(); i++) {
            mBindings.get(i).dump(fd, pw, args);
        }
        pw.println("CarrierServiceBindHelperLogs=");
        mLocalLog.dump(fd, pw, args);
    }
}
