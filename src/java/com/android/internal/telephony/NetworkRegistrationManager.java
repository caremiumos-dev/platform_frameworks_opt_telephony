
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
import android.telephony.CarrierConfigManager;
import android.telephony.INetworkService;
import android.telephony.INetworkServiceCallback;
import android.telephony.NetworkRegistrationState;
import android.telephony.NetworkService;
import android.telephony.SubscriptionManager;
import android.telephony.AccessNetworkConstants.TransportType;
import android.util.Log;

/**
 * Class that serves as the layer between NetworkService and ServiceStateTracker. It helps binding,
 * sending request and registering for state change to NetworkService.
 */
public class NetworkRegistrationManager {
//    static public String TAG = NetworkRegistrationManager.class.getSimpleName();

    static public String TAG = "mal debug";

    private final int mTransportType;

    private final Phone mPhone;

    private final CarrierConfigManager mCarrierConfigManager;

    private RegistrantList mRegStateChangeRegistrants = new RegistrantList();

    INetworkService.Stub mServiceBinder;

//    static public NetworkRegistrationManager createInstance(int transportType, Phone phone) {
//        if (phone == null || SubscriptionManager.isValidPhoneId(phone.getPhoneId())
//                || (transportType != TransportType.WLAN && transportType != TransportType.WWAN)) {
//            Log.e(TAG, "createInstance with invalid parameter.");
//            return null;
//        }
//
//        return new NetworkRegistrationManager(transportType, phone);
//    }

    public NetworkRegistrationManager(int transportType, Phone phone) {
        mTransportType = transportType;
        mPhone = phone;
        mCarrierConfigManager = (CarrierConfigManager) phone.getContext().getSystemService(
                Context.CARRIER_CONFIG_SERVICE);

        bindService();
    }

    public boolean isServiceConnected() {
        Log.d(TAG, " isServiceConnected? " + ((mServiceBinder != null) && (mServiceBinder.isBinderAlive())));
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

    /**
     * Returns a boolean indicating whether the reque
     */
    public void getNetworkRegistrationState(int domain, Message onCompleteMessage) {
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
            onCompleteMessage.obj = new AsyncResult(onCompleteMessage.obj, null, e);
            onCompleteMessage.sendToTarget();
        }
    }

    private class NetworkServiceConnection implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "service connected ComponentName" + name);
            mServiceBinder = (INetworkService.Stub) service;
            try {
                Log.d(TAG, "createNetworkServiceProvider phone id " + mPhone.getPhoneId());
                mServiceBinder.createNetworkServiceProvider(mPhone.getPhoneId());
                Log.d(TAG, "registerForNetworkRegistrationStateChanged phone id " + mPhone.getPhoneId());
                mServiceBinder.registerForNetworkRegistrationStateChanged(mPhone.getPhoneId(),
                        new NetworkRegStateCallback(null));
            } catch (RemoteException exception) {

                Log.d(TAG, "RemoteException " + exception);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "onServiceDisconnected " + name);
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

            Log.d(TAG, "onGetNetworkRegistrationStateComplete result " + result + " state " + state);
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
        Log.d(TAG, "binding service to package " + intent.getPackage());
        // We bind this as a foreground service because it is operating directly on the SIM, and we
        // do not want it subjected to power-savings restrictions while doing so.
        return mPhone.getContext().bindService(intent, new NetworkServiceConnection(),
                Context.BIND_AUTO_CREATE);
    }

    private String getPackageName() {
        String packageName;
        int resourceId;

        Log.d(TAG, "getPackageName: mTransportType" + mTransportType);

        switch (mTransportType) {
            case TransportType.WWAN:
                resourceId = com.android.internal.R.string.config_wwan_network_service_package;
                break;
            case TransportType.WLAN:
                resourceId = com.android.internal.R.string.config_wlan_network_service_package;
                break;
            default:
                throw new IllegalStateException("Transport type not WWAN or WLAN. type="
                        + mTransportType);
        }

        // Read package name from resource overlay
        packageName = mPhone.getContext().getResources().getString(resourceId);

        Log.d(TAG, "getPackageName: packageName" + packageName);

        PersistableBundle b = mCarrierConfigManager.getConfigForSubId(mPhone.getSubId());

        if (b != null) {
            // If carrier config overrides it, use the one from carrier config
            packageName = b.getString(CarrierConfigManager
                    .KEY_CARRIER_NETWORK_SERVICE_PACKAGE_OVERRIDE_STRING, packageName);
        }

        Log.d(TAG, "getPackageName: final packageName" + packageName);

        return packageName;
    }
}
