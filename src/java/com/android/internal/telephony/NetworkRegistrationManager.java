/*
 * Copyright 2018 The Android Open Source Project
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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PersistableBundle;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.RemoteException;
import android.telephony.AccessNetworkConstants.TransportType;
import android.telephony.CarrierConfigManager;
import android.telephony.INetworkService;
import android.telephony.INetworkServiceCallback;
import android.telephony.NetworkRegistrationState;
import android.telephony.NetworkService;
import android.util.Log;

/**
 * Class that serves as the layer between NetworkService and ServiceStateTracker. It helps binding,
 * sending request and registering for state change to NetworkService.
 */
public class NetworkRegistrationManager {
    public static final String TAG = NetworkRegistrationManager.class.getSimpleName();

    private final int mTransportType;

    private final Phone mPhone;

    private final CarrierConfigManager mCarrierConfigManager;

    // Registrants who listens registration state change callback from this class.
    private final RegistrantList mRegStateChangeRegistrants = new RegistrantList();

    INetworkService.Stub mServiceBinder;

    private RegManagerDeathRecipient mDeathRecipient;

    public NetworkRegistrationManager(int transportType, Phone phone) {
        mTransportType = transportType;
        mPhone = phone;
        mCarrierConfigManager = (CarrierConfigManager) phone.getContext().getSystemService(
                Context.CARRIER_CONFIG_SERVICE);

        bindService();
    }

    public boolean isServiceConnected() {
        return (mServiceBinder != null) && (mServiceBinder.isBinderAlive());
    }

    public void unregisterForNetworkRegistrationStateChanged(Handler h) {
        mRegStateChangeRegistrants.remove(h);
    }

    public void registerForNetworkRegistrationStateChanged(Handler h, int what, Object obj) {
        Log.d(TAG, "registerForNetworkRegistrationStateChanged");
        Registrant r = new Registrant(h, what, obj);
        mRegStateChangeRegistrants.addUnique(h, what, obj);
    }

    public void getNetworkRegistrationState(int domain, Message onCompleteMessage) {
        if (onCompleteMessage == null) return;

        Log.d(TAG, "getNetworkRegistrationState domain " + domain);
        if (!isServiceConnected()) {
            onCompleteMessage.obj = new AsyncResult(onCompleteMessage.obj, null,
                    new IllegalStateException("Service not connected."));
            onCompleteMessage.sendToTarget();
            return;
        }

        try {
            mServiceBinder.getNetworkRegistrationState(mPhone.getPhoneId(), domain,
                    new NetworkRegStateCallback(onCompleteMessage));
        } catch (RemoteException e) {
            Log.d(TAG, "getNetworkRegistrationState RemoteException " + e);
            onCompleteMessage.obj = new AsyncResult(onCompleteMessage.obj, null, e);
            onCompleteMessage.sendToTarget();
        }
    }

    private class RegManagerDeathRecipient implements IBinder.DeathRecipient {

        private ComponentName mComponentName;

        RegManagerDeathRecipient(ComponentName name) {
            mComponentName = name;
        }

        @Override
        public void binderDied() {
            // TODO: try to restart the service.
            Log.d(TAG, "NetworkService(" + mComponentName +  " transport type "
                    + mTransportType + ") died.");
        }
    }

    private class NetworkServiceConnection implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mServiceBinder = (INetworkService.Stub) service;
            mDeathRecipient = new RegManagerDeathRecipient(name);
            try {
                mServiceBinder.linkToDeath(mDeathRecipient);
                mServiceBinder.createNetworkServiceProvider(mPhone.getPhoneId());
                mServiceBinder.registerForNetworkRegistrationStateChanged(mPhone.getPhoneId(),
                        new NetworkRegStateCallback(null));
            } catch (RemoteException exception) {
                // Remote exception means that the binder already died.
                mDeathRecipient.binderDied();
                Log.d(TAG, "RemoteException " + exception);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "onServiceDisconnected " + name);
            if (mServiceBinder != null) {
                mServiceBinder.unlinkToDeath(mDeathRecipient, 0);
            }
        }
    }

    private class NetworkRegStateCallback extends INetworkServiceCallback.Stub {
        // Message only used upon onGetNetworkRegistrationStateComplete.
        // If the callback is passed to listen to network state change,
        // this message is null.
        Message mOnCompleteMessage;

        NetworkRegStateCallback(Message onCompleteMessage) {
            mOnCompleteMessage = onCompleteMessage;
        }

        @Override
        public void onGetNetworkRegistrationStateComplete(
                int result, NetworkRegistrationState state) {
            Log.d(TAG, "onGetNetworkRegistrationStateComplete result "
                    + result + " state " + state);
            mOnCompleteMessage.arg1 = result;
            mOnCompleteMessage.obj = new AsyncResult(mOnCompleteMessage.obj, state, null);
            mOnCompleteMessage.sendToTarget();
        }

        @Override
        public void onNetworkStateChanged() {
            Log.d(TAG, "onNetworkStateChanged");
            mRegStateChangeRegistrants.notifyRegistrants();
        }
    }

    private boolean bindService() {
        Intent intent = new Intent(NetworkService.NETWORK_SERVICE_INTERFACE);
        intent.setPackage(getPackageName());
        // We bind this as a foreground service because it is operating directly on the SIM, and we
        // do not want it subjected to power-savings restrictions while doing so.
        return mPhone.getContext().bindService(intent, new NetworkServiceConnection(),
                Context.BIND_AUTO_CREATE);
    }

    private String getPackageName() {
        String packageName;
        int resourceId;
        String carrierConfig;

        switch (mTransportType) {
            case TransportType.WWAN:
                resourceId = com.android.internal.R.string.config_wwan_network_service_package;
                carrierConfig = CarrierConfigManager
                        .KEY_CARRIER_NETWORK_SERVICE_WWAN_PACKAGE_OVERRIDE_STRING;
                break;
            case TransportType.WLAN:
                resourceId = com.android.internal.R.string.config_wlan_network_service_package;
                carrierConfig = CarrierConfigManager
                        .KEY_CARRIER_NETWORK_SERVICE_WLAN_PACKAGE_OVERRIDE_STRING;
                break;
            default:
                throw new IllegalStateException("Transport type not WWAN or WLAN. type="
                        + mTransportType);
        }

        // Read package name from resource overlay
        packageName = mPhone.getContext().getResources().getString(resourceId);

        PersistableBundle b = mCarrierConfigManager.getConfigForSubId(mPhone.getSubId());

        if (b != null) {
            // If carrier config overrides it, use the one from carrier config
            packageName = b.getString(carrierConfig, packageName);
        }

        Log.d(TAG, "Binding to packageName " + packageName + " for transport type"
                + mTransportType);

        return packageName;
    }
}
