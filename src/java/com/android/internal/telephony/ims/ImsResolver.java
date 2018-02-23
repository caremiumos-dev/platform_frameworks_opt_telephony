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

package com.android.internal.telephony.ims;

import android.Manifest;
import android.annotation.Nullable;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.UserHandle;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.ims.ImsService;
import android.telephony.ims.aidl.IImsConfig;
import android.telephony.ims.aidl.IImsMmTelFeature;
import android.telephony.ims.aidl.IImsRcsFeature;
import android.telephony.ims.aidl.IImsRegistration;
import android.telephony.ims.feature.ImsFeature;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;

import com.android.ims.internal.IImsServiceFeatureCallback;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.os.SomeArgs;
import com.android.internal.telephony.PhoneConstants;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Creates a list of ImsServices that are available to bind to based on the Device configuration
 * overlay value "config_ims_package" and Carrier Configuration value
 * "config_ims_package_override_string".
 * These ImsServices are then bound to in the following order:
 *
 * 1. Carrier Config defined override value per SIM.
 * 2. Device overlay default value (including no SIM case).
 *
 * ImsManager can then retrieve the binding to the correct ImsService using
 * {@link #getImsServiceControllerAndListen} on a per-slot and per feature basis.
 */

public class ImsResolver implements ImsServiceController.ImsServiceControllerCallbacks {

    private static final String TAG = "ImsResolver";

    public static final String METADATA_EMERGENCY_MMTEL_FEATURE =
            "android.telephony.ims.EMERGENCY_MMTEL_FEATURE";
    public static final String METADATA_MMTEL_FEATURE = "android.telephony.ims.MMTEL_FEATURE";
    public static final String METADATA_RCS_FEATURE = "android.telephony.ims.RCS_FEATURE";

    // Based on updates from PackageManager
    private static final int HANDLER_ADD_PACKAGE = 0;
    // Based on updates from PackageManager
    private static final int HANDLER_REMOVE_PACKAGE = 1;
    // Based on updates from CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED
    private static final int HANDLER_CONFIG_CHANGED = 2;
    // A query has been started for an ImsService to relay the features they support.
    private static final int HANDLER_START_DYNAMIC_FEATURE_QUERY = 3;
    // A query to request ImsService features has completed.
    private static final int HANDLER_DYNAMIC_FEATURE_QUERY_COMPLETE = 4;

    // Delay between dynamic ImsService queries.
    private static final int DELAY_DYNAMIC_QUERY_MS = 5000;


    /**
     * Stores information about an ImsService, including the package name, class name, and features
     * that the service supports.
     */
    @VisibleForTesting
    public static class ImsServiceInfo {
        public ComponentName name;
        public boolean supportsEmergencyMmTel = false;
        // Determines if features were created from metadata in the manifest or through dynamic
        // query.
        public boolean featureFromMetadata = true;
        public ImsServiceControllerFactory controllerFactory;

        // Map slotId->Feature
        private final HashSet<Pair<Integer, Integer>> mSupportedFeatures;
        private final int mNumSlots;

        public ImsServiceInfo(int numSlots) {
            mNumSlots = numSlots;
            mSupportedFeatures = new HashSet<>();
        }

        void addFeatureForAllSlots(int feature) {
            for(int i = 0; i < mNumSlots; i++) {
                mSupportedFeatures.add(new Pair<>(i, feature));
            }
        }

        void replaceFeatures(Set<Pair<Integer, Integer>> mNewFeatures) {
            // Emergency is a special case, filter out and if any slot supports it, set it to true.
            // TODO: support per-slot emergency call filtering
            List<Pair<Integer, Integer>> emergencyFeatures = mNewFeatures.stream()
                    .filter(f -> f.second == ImsFeature.FEATURE_EMERGENCY_MMTEL)
                    .collect(Collectors.toList());
            if (!emergencyFeatures.isEmpty()) {
                Log.i(TAG, "replaceFeatures: emergency calls enabled.");
                supportsEmergencyMmTel = true;
                mNewFeatures.removeAll(emergencyFeatures);
            } else {
                Log.i(TAG, "replaceFeatures: emergency calls disabled.");
                supportsEmergencyMmTel = false;
            }
            mSupportedFeatures.clear();
            mSupportedFeatures.addAll(mNewFeatures);
        }

        @VisibleForTesting
        public HashSet<Pair<Integer, Integer>> getSupportedFeatures() {
            return mSupportedFeatures;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ImsServiceInfo that = (ImsServiceInfo) o;

            if (supportsEmergencyMmTel != that.supportsEmergencyMmTel) return false;
            if (name != null ? !name.equals(that.name) : that.name != null) return false;
            if (!mSupportedFeatures.equals(that.mSupportedFeatures)) {
                return false;
            }
            return controllerFactory != null ? controllerFactory.equals(that.controllerFactory)
                    : that.controllerFactory == null;
        }

        @Override
        public int hashCode() {
            int result = name != null ? name.hashCode() : 0;
            result = 31 * result + (supportsEmergencyMmTel ? 1 : 0);
            result = 31 * result + mSupportedFeatures.hashCode();
            result = 31 * result + (controllerFactory != null ? controllerFactory.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            StringBuilder res = new StringBuilder();
            res.append("[ImsServiceInfo] name=");
            res.append(name);
            res.append(", supportedFeatures=[ ");
            for (Pair<Integer,Integer> feature : mSupportedFeatures) {
                res.append("(");
                res.append(feature.first);
                res.append(",");
                res.append(feature.second);
                res.append(") ");
            }
            res.append("], supportsEmergency=");
            res.append(supportsEmergencyMmTel);
            return res.toString();
        }
    }

    // Receives broadcasts from the system involving changes to the installed applications. If
    // an ImsService that we are configured to use is installed, we must bind to it.
    private BroadcastReceiver mAppChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            final String packageName = intent.getData().getSchemeSpecificPart();
            switch (action) {
                case Intent.ACTION_PACKAGE_ADDED:
                    // intentional fall-through
                case Intent.ACTION_PACKAGE_REPLACED:
                    // intentional fall-through
                case Intent.ACTION_PACKAGE_CHANGED:
                    mHandler.obtainMessage(HANDLER_ADD_PACKAGE, packageName).sendToTarget();
                    break;
                case Intent.ACTION_PACKAGE_REMOVED:
                    mHandler.obtainMessage(HANDLER_REMOVE_PACKAGE, packageName).sendToTarget();
                    break;
                default:
                    return;
            }
        }
    };

    // Receives the broadcast that a new Carrier Config has been loaded in order to possibly
    // unbind from one service and bind to another.
    private BroadcastReceiver mConfigChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            int slotId = intent.getIntExtra(CarrierConfigManager.EXTRA_SLOT_INDEX,
                    SubscriptionManager.INVALID_SIM_SLOT_INDEX);

            if (slotId == SubscriptionManager.INVALID_SIM_SLOT_INDEX) {
                Log.i(TAG, "Received SIM change for invalid slot id.");
                return;
            }

            Log.i(TAG, "Received Carrier Config Changed for SlotId: " + slotId);

            mHandler.obtainMessage(HANDLER_CONFIG_CHANGED, slotId).sendToTarget();
        }
    };

    /**
     * Testing interface used to mock SubscriptionManager in testing
     */
    @VisibleForTesting
    public interface SubscriptionManagerProxy {
        /**
         * Mock-able interface for {@link SubscriptionManager#getSubId(int)} used for testing.
         */
        int getSubId(int slotId);
        /**
         * Mock-able interface for {@link SubscriptionManager#getSlotIndex(int)} used for testing.
         */
        int getSlotIndex(int subId);
    }

    private SubscriptionManagerProxy mSubscriptionManagerProxy = new SubscriptionManagerProxy() {
        @Override
        public int getSubId(int slotId) {
            int[] subIds = SubscriptionManager.getSubId(slotId);
            if (subIds != null) {
                // This is done in all other places getSubId is used.
                return subIds[0];
            }
            return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        }

        @Override
        public int getSlotIndex(int subId) {
            return SubscriptionManager.getSlotIndex(subId);
        }
    };

    /**
     * Testing interface for injecting mock ImsServiceControllers.
     */
    @VisibleForTesting
    public interface ImsServiceControllerFactory {
        /**
         * @return the Service Interface String used for binding the ImsService.
         */
        String getServiceInterface();
        /**
         * @return the ImsServiceController created using the context and componentName supplied.
         */
        ImsServiceController create(Context context, ComponentName componentName,
                ImsServiceController.ImsServiceControllerCallbacks callbacks);
    }

    private ImsServiceControllerFactory mImsServiceControllerFactory =
            new ImsServiceControllerFactory() {

        @Override
        public String getServiceInterface() {
            return ImsService.SERVICE_INTERFACE;
        }

        @Override
        public ImsServiceController create(Context context, ComponentName componentName,
                ImsServiceController.ImsServiceControllerCallbacks callbacks) {
            return new ImsServiceController(context, componentName, callbacks);
        }
    };

    @VisibleForTesting
    public interface ImsDynamicQueryManagerFactory {
        ImsServiceFeatureQueryManager create(Context context,
                ImsServiceFeatureQueryManager.Listener listener);
    }

    private ImsServiceControllerFactory mImsServiceControllerFactoryCompat =
            new ImsServiceControllerFactory() {
                @Override
                public String getServiceInterface() {
                    return android.telephony.ims.compat.ImsService.SERVICE_INTERFACE;
                }

                @Override
                public ImsServiceController create(Context context, ComponentName componentName,
                        ImsServiceController.ImsServiceControllerCallbacks callbacks) {
                    return new ImsServiceControllerCompat(context, componentName, callbacks);
                }
            };

    private ImsServiceControllerFactory mImsServiceControllerFactoryStaticBindingCompat =
            new ImsServiceControllerFactory() {
                @Override
                public String getServiceInterface() {
                    // The static method of binding does not use service interfaces.
                    return null;
                }

                @Override
                public ImsServiceController create(Context context, ComponentName componentName,
                        ImsServiceController.ImsServiceControllerCallbacks callbacks) {
                    return new ImsServiceControllerStaticCompat(context, componentName, callbacks);
                }
            };

    private ImsDynamicQueryManagerFactory mDynamicQueryManagerFactory =
            ImsServiceFeatureQueryManager::new;

    private final CarrierConfigManager mCarrierConfigManager;
    private final Context mContext;
    // Locks mBoundImsServicesByFeature only. Be careful to avoid deadlocks from
    // ImsServiceController callbacks.
    private final Object mBoundServicesLock = new Object();
    private final int mNumSlots;
    private final boolean mIsDynamicBinding;

    // Synchronize all messages on a handler to ensure that the cache includes the most recent
    // version of the installed ImsServices.
    private Handler mHandler = new Handler(Looper.getMainLooper(), (msg) -> {
        switch (msg.what) {
            case HANDLER_ADD_PACKAGE: {
                String packageName = (String) msg.obj;
                maybeAddedImsService(packageName);
                break;
            }
            case HANDLER_REMOVE_PACKAGE: {
                String packageName = (String) msg.obj;
                maybeRemovedImsService(packageName);
                break;
            }
            case HANDLER_CONFIG_CHANGED: {
                int slotId = (Integer) msg.obj;
                maybeRebindService(slotId);
                break;
            }
            case HANDLER_START_DYNAMIC_FEATURE_QUERY: {
                ImsServiceInfo info = (ImsServiceInfo) msg.obj;
                startDynamicQuery(info);
                break;
            }
            case HANDLER_DYNAMIC_FEATURE_QUERY_COMPLETE: {
                SomeArgs args = (SomeArgs) msg.obj;
                ComponentName name = (ComponentName) args.arg1;
                Set<Pair<Integer, Integer>> features = (Set<Pair<Integer, Integer>>) args.arg2;
                args.recycle();
                dynamicQueryComplete(name, features);
                break;
            }
            default:
                return false;
        }
        return true;
    });

    // Results from dynamic queries to ImsService regarding the features they support.
    private ImsServiceFeatureQueryManager.Listener mDynamicQueryListener =
            new ImsServiceFeatureQueryManager.Listener() {

                @Override
                public void onComplete(ComponentName name, Set<Pair<Integer, Integer>> features) {
                    Log.d(TAG, "onComplete called for name: " + name + "features:"
                            + printFeatures(features));
                    scheduleFeatureQueryComplete(name, features);
                }

                @Override
                public void onError(ComponentName name) {
                    Log.w(TAG, "onError: " + name + "returned with an error result");
                    scheduleQueryForFeatures(name, DELAY_DYNAMIC_QUERY_MS);
                }
            };

    // Package name of the default device service.
    private String mDeviceService;
    // Array index corresponds to slot Id associated with the service package name.
    private String[] mCarrierServices;
    // List index corresponds to Slot Id, Maps ImsFeature.FEATURE->bound ImsServiceController
    // Locked on mBoundServicesLock
    private List<SparseArray<ImsServiceController>> mBoundImsServicesByFeature;
    // not locked, only accessed on a handler thread.
    private Set<ImsServiceInfo> mInstalledServicesCache = new ArraySet<>();
    // not locked, only accessed on a handler thread.
    private Set<ImsServiceController> mActiveControllers = new ArraySet<>();
    // Only used as the Component name for legacy ImsServices that did not use dynamic binding.
    private final ComponentName mStaticComponent;
    private ImsServiceFeatureQueryManager mFeatureQueryManager;

    public ImsResolver(Context context, String defaultImsPackageName, int numSlots,
            boolean isDynamicBinding) {
        mContext = context;
        mDeviceService = defaultImsPackageName;
        mNumSlots = numSlots;
        mIsDynamicBinding = isDynamicBinding;
        mStaticComponent = new ComponentName(mContext, ImsResolver.class);
        if (!mIsDynamicBinding) {
            Log.i(TAG, "ImsResolver initialized with static binding.");
            mDeviceService = mStaticComponent.getPackageName();
        }
        mCarrierConfigManager = (CarrierConfigManager) mContext.getSystemService(
                Context.CARRIER_CONFIG_SERVICE);
        mCarrierServices = new String[numSlots];
        mBoundImsServicesByFeature = Stream.generate(SparseArray<ImsServiceController>::new)
                .limit(mNumSlots).collect(Collectors.toList());

        // Only register for Package/CarrierConfig updates if dynamic binding.
        if(mIsDynamicBinding) {
            IntentFilter appChangedFilter = new IntentFilter();
            appChangedFilter.addAction(Intent.ACTION_PACKAGE_CHANGED);
            appChangedFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
            appChangedFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
            appChangedFilter.addDataScheme("package");
            context.registerReceiverAsUser(mAppChangedReceiver, UserHandle.ALL, appChangedFilter,
                    null,
                    null);

            context.registerReceiver(mConfigChangedReceiver, new IntentFilter(
                    CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED));
        }
    }

    @VisibleForTesting
    public void setSubscriptionManagerProxy(SubscriptionManagerProxy proxy) {
        mSubscriptionManagerProxy = proxy;
    }

    @VisibleForTesting
    public void setImsServiceControllerFactory(ImsServiceControllerFactory factory) {
        mImsServiceControllerFactory = factory;
    }

    @VisibleForTesting
    public Handler getHandler() {
        return mHandler;
    }

    @VisibleForTesting
    public void setImsDynamicQueryManagerFactory(ImsDynamicQueryManagerFactory m) {
        mDynamicQueryManagerFactory = m;
    }

    /**
     * Needs to be called after the constructor to first populate the cache and possibly bind to
     * ImsServices.
     */
    public void initPopulateCacheAndStartBind() {
        Log.i(TAG, "Initializing cache and binding.");
        mFeatureQueryManager = mDynamicQueryManagerFactory.create(mContext, mDynamicQueryListener);
        // Populates the CarrierConfig override package names for each slot
        mHandler.obtainMessage(HANDLER_CONFIG_CHANGED,
                SubscriptionManager.INVALID_SIM_SLOT_INDEX).sendToTarget();
        // Starts first bind to the system.
        mHandler.obtainMessage(HANDLER_ADD_PACKAGE, null).sendToTarget();
    }

    /**
     * Notify ImsService to enable IMS for the framework. This will trigger IMS registration and
     * trigger ImsFeature status updates.
     */
    public void enableIms(int slotId) {
        SparseArray<ImsServiceController> controllers = getImsServiceControllers(slotId);
        if (controllers != null) {
            for (int i = 0; i < controllers.size(); i++) {
                int key = controllers.keyAt(i);
                controllers.get(key).enableIms(slotId);
            }
        }
    }

    /**
     * Notify ImsService to disable IMS for the framework. This will trigger IMS de-registration and
     * trigger ImsFeature capability status to become false.
     */
    public void disableIms(int slotId) {
        SparseArray<ImsServiceController> controllers = getImsServiceControllers(slotId);
        if (controllers != null) {
            for (int i = 0; i < controllers.size(); i++) {
                int key = controllers.keyAt(i);
                controllers.get(key).disableIms(slotId);
            }
        }
    }

    /**
     * Returns the {@link IImsMmTelFeature} that corresponds to the given slot Id or {@link null} if
     * the service is not available. If an IImsMMTelFeature is available, the
     * {@link IImsServiceFeatureCallback} callback is registered as a listener for feature updates.
     * @param slotId The SIM slot that we are requesting the {@link IImsMmTelFeature} for.
     * @param callback Listener that will send updates to ImsManager when there are updates to
     * the feature.
     * @return {@link IImsMmTelFeature} interface or {@link null} if it is unavailable.
     */
    public IImsMmTelFeature getMmTelFeatureAndListen(int slotId,
            IImsServiceFeatureCallback callback) {
        ImsServiceController controller = getImsServiceControllerAndListen(slotId,
                ImsFeature.FEATURE_MMTEL, callback);
        return (controller != null) ? controller.getMmTelFeature(slotId) : null;
    }

    /**
     * Returns the {@link IImsRcsFeature} that corresponds to the given slot Id for emergency
     * calling or {@link null} if the service is not available. If an IImsMMTelFeature is
     * available, the {@link IImsServiceFeatureCallback} callback is registered as a listener for
     * feature updates.
     * @param slotId The SIM slot that we are requesting the {@link IImsRcsFeature} for.
     * @param callback listener that will send updates to ImsManager when there are updates to
     * the feature.
     * @return {@link IImsRcsFeature} interface or {@link null} if it is unavailable.
     */
    public IImsRcsFeature getRcsFeatureAndListen(int slotId, IImsServiceFeatureCallback callback) {
        ImsServiceController controller = getImsServiceControllerAndListen(slotId,
                ImsFeature.FEATURE_RCS, callback);
        return (controller != null) ? controller.getRcsFeature(slotId) : null;
    }

    /**
     * Returns the ImsRegistration structure associated with the slotId and feature specified.
     */
    public @Nullable IImsRegistration getImsRegistration(int slotId, int feature)
            throws RemoteException {
        ImsServiceController controller = getImsServiceController(slotId, feature);
        if (controller != null) {
            return controller.getRegistration(slotId);
        }
        return null;
    }

    /**
     * Returns the ImsConfig structure associated with the slotId and feature specified.
     */
    public @Nullable IImsConfig getImsConfig(int slotId, int feature)
            throws RemoteException {
        ImsServiceController controller = getImsServiceController(slotId, feature);
        if (controller != null) {
            return controller.getConfig(slotId);
        }
        return null;
    }

    /**
     * @return true if the ImsService associated with this slot supports emergency calling over IMS,
     * false if the call should be placed over circuit switch instead.
     */
    public boolean isEmergencyMmTelAvailable(int slotId) {
        ImsServiceController controller = getImsServiceController(slotId, ImsFeature.FEATURE_MMTEL);
        if (controller != null) {
            return controller.canPlaceEmergencyCalls();
        }
        Log.w(TAG, "isEmergencyMmTelAvailable: No controller found for slot " + slotId);
        return false;
    }

    @VisibleForTesting
    public ImsServiceController getImsServiceController(int slotId, int feature) {
        if (slotId < 0 || slotId >= mNumSlots) {
            return null;
        }
        ImsServiceController controller;
        synchronized (mBoundServicesLock) {
            SparseArray<ImsServiceController> services = mBoundImsServicesByFeature.get(slotId);
            if (services == null) {
                return null;
            }
            controller = services.get(feature);
        }
        return controller;
    }

    private  SparseArray<ImsServiceController> getImsServiceControllers(int slotId) {
        if (slotId < 0 || slotId >= mNumSlots) {
            return null;
        }
        synchronized (mBoundServicesLock) {
            SparseArray<ImsServiceController> services = mBoundImsServicesByFeature.get(slotId);
            if (services == null) {
                return null;
            }
            return services;
        }
    }

    @VisibleForTesting
    public ImsServiceController getImsServiceControllerAndListen(int slotId, int feature,
            IImsServiceFeatureCallback callback) {
        ImsServiceController controller = getImsServiceController(slotId, feature);

        if (controller != null) {
            controller.addImsServiceFeatureListener(callback);
            return controller;
        }
        return null;
    }

    private void putImsController(int slotId, int feature, ImsServiceController controller) {
        if (slotId < 0 || slotId >= mNumSlots || feature <= ImsFeature.FEATURE_INVALID
                || feature >= ImsFeature.FEATURE_MAX) {
            Log.w(TAG, "putImsController received invalid parameters - slot: " + slotId
                    + ", feature: " + feature);
            return;
        }
        synchronized (mBoundServicesLock) {
            SparseArray<ImsServiceController> services = mBoundImsServicesByFeature.get(slotId);
            if (services == null) {
                services = new SparseArray<>();
                mBoundImsServicesByFeature.add(slotId, services);
            }
            Log.i(TAG, "ImsServiceController added on slot: " + slotId + " with feature: "
                    + feature + " using package: " + controller.getComponentName());
            services.put(feature, controller);
        }
    }

    private ImsServiceController removeImsController(int slotId, int feature) {
        if (slotId < 0 || slotId >= mNumSlots || feature <= ImsFeature.FEATURE_INVALID
                || feature >= ImsFeature.FEATURE_MAX) {
            Log.w(TAG, "removeImsController received invalid parameters - slot: " + slotId
                    + ", feature: " + feature);
            return null;
        }
        synchronized (mBoundServicesLock) {
            SparseArray<ImsServiceController> services = mBoundImsServicesByFeature.get(slotId);
            if (services == null) {
                return null;
            }
            ImsServiceController c = services.get(feature, null);
            if (c != null) {
                Log.i(TAG, "ImsServiceController removed on slot: " + slotId + " with feature: "
                        + feature + " using package: " + c.getComponentName());
                services.remove(feature);
            }
            return c;
        }
    }

    // Update the current cache with the new ImsService(s) if it has been added or update the
    // supported IMS features if they have changed.
    // Called from the handler ONLY
    private void maybeAddedImsService(String packageName) {
        Log.d(TAG, "maybeAddedImsService, packageName: " + packageName);
        List<ImsServiceInfo> infos = getImsServiceInfo(packageName);
        List<ImsServiceInfo> newlyAddedInfos = new ArrayList<>();
        for (ImsServiceInfo info : infos) {
            // Checking to see if the ComponentName is the same, so we can update the supported
            // features. Will only be one (if it exists), since it is a set.
            Optional<ImsServiceInfo> match = getInfoByComponentName(mInstalledServicesCache,
                    info.name);
            if (match.isPresent()) {
                // for dynamic query the new "info" will have no supported features yet. Don't wipe
                // out the cache for the existing features or update yet. Instead start a query
                // for features dynamically.
                if (info.featureFromMetadata) {
                    // update features in the cache
                    Log.i(TAG, "Updating features in cached ImsService: " + info.name);
                    Log.d(TAG, "Updating features - Old features: " + match.get()
                            + " new features: " + info);
                    match.get().replaceFeatures(info.getSupportedFeatures());
                    updateImsServiceFeatures(info);
                } else {
                    // start a query to get ImsService features
                    scheduleQueryForFeatures(info);
                }
            } else {
                Log.i(TAG, "Adding newly added ImsService to cache: " + info.name);
                mInstalledServicesCache.add(info);
                if (info.featureFromMetadata) {
                    newlyAddedInfos.add(info);
                } else {
                    // newly added ImsServiceInfo that has not had features queried yet. Start async
                    // bind and query features.
                    scheduleQueryForFeatures(info);
                }
            }
        }
        // Loop through the newly created ServiceInfos in a separate loop to make sure the cache
        // is fully updated.
        for (ImsServiceInfo info : newlyAddedInfos) {
            if (isActiveCarrierService(info)) {
                // New ImsService is registered to active carrier services and must be newly
                // bound.
                bindImsService(info);
                // Update existing device service features
                updateImsServiceFeatures(getImsServiceInfoFromCache(mDeviceService));
            } else if (isDeviceService(info)) {
                // New ImsService is registered as device default and must be newly bound.
                bindImsService(info);
            }
        }
    }

    // Remove the ImsService from the cache. At this point, the ImsService will have already been
    // killed.
    // Called from the handler ONLY
    private boolean maybeRemovedImsService(String packageName) {
        Optional<ImsServiceInfo> match = getInfoByPackageName(mInstalledServicesCache, packageName);
        if (match.isPresent()) {
            mInstalledServicesCache.remove(match.get());
            Log.i(TAG, "Removing ImsService: " + match.get().name);
            unbindImsService(match.get());
            updateImsServiceFeatures(getImsServiceInfoFromCache(mDeviceService));
            return true;
        }
        return false;
    }

    // Returns true if the CarrierConfig that has been loaded includes this ImsServiceInfo
    // package name.
    // Called from Handler ONLY
    private boolean isActiveCarrierService(ImsServiceInfo info) {
        for (int i = 0; i < mNumSlots; i++) {
            if (TextUtils.equals(mCarrierServices[i], info.name.getPackageName())) {
                return true;
            }
        }
        return false;
    }

    private boolean isDeviceService(ImsServiceInfo info) {
        return TextUtils.equals(mDeviceService, info.name.getPackageName());
    }

    private int getSlotForActiveCarrierService(ImsServiceInfo info) {
        for (int i = 0; i < mNumSlots; i++) {
            if (TextUtils.equals(mCarrierServices[i], info.name.getPackageName())) {
                return i;
            }
        }
        return SubscriptionManager.INVALID_PHONE_INDEX;
    }

    private Optional<ImsServiceController> getControllerByServiceInfo(
            Set<ImsServiceController> searchSet, ImsServiceInfo matchValue) {
        return searchSet.stream()
                .filter(c -> Objects.equals(c.getComponentName(), matchValue.name)).findFirst();
    }

    private Optional<ImsServiceInfo> getInfoByPackageName(Set<ImsServiceInfo> searchSet,
            String matchValue) {
        return searchSet.stream()
                .filter((i) -> Objects.equals(i.name.getPackageName(), matchValue)).findFirst();
    }

    private Optional<ImsServiceInfo> getInfoByComponentName(Set<ImsServiceInfo> searchSet,
            ComponentName matchValue) {
        return searchSet.stream()
                .filter((i) -> Objects.equals(i.name, matchValue)).findFirst();
    }

    // Creates new features in active ImsServices and removes obsolete cached features. If
    // cachedInfo == null, then newInfo is assumed to be a new ImsService and will have all features
    // created.
    private void updateImsServiceFeatures(ImsServiceInfo newInfo) {
        if (newInfo == null) {
            return;
        }
        Optional<ImsServiceController> o = getControllerByServiceInfo(mActiveControllers, newInfo);
        // Will return zero if these features are overridden or it should not currently have any
        // features because it is not carrier/device.
        HashSet<Pair<Integer, Integer>> features = calculateFeaturesToCreate(newInfo);
        if (features.size() > 0) {
            try {
                if(o.isPresent()) {
                    Log.i(TAG, "Updating features for ImsService: " + o.get().getComponentName());
                    Log.d(TAG, "Updating canPlaceEmergencyCalls: "
                            + newInfo.supportsEmergencyMmTel);
                    o.get().setCanPlaceEmergencyCalls(newInfo.supportsEmergencyMmTel);
                    Log.d(TAG, "Updating Features - New Features: " + features);
                    o.get().changeImsServiceFeatures(features);
                } else {
                    Log.i(TAG, "updateImsServiceFeatures: unbound with active features, rebinding");
                    bindImsServiceWithFeatures(newInfo, features);
                }
                // If the carrier service features have changed, the device features will also
                // need to be recalculated.
                if (isActiveCarrierService(newInfo)
                        // Prevent infinite recursion from bad behavior
                        && !TextUtils.equals(newInfo.name.getPackageName(), mDeviceService)) {
                    Log.i(TAG, "Updating device default");
                    updateImsServiceFeatures(getImsServiceInfoFromCache(mDeviceService));
                }
            } catch (RemoteException e) {
                Log.e(TAG, "updateImsServiceFeatures: Remote Exception: " + e.getMessage());
            }
        // Don't stay bound if the ImsService is providing no features.
        } else if (o.isPresent()) {
            Log.i(TAG, "Unbinding: features = 0 for ImsService: " + o.get().getComponentName());
            unbindImsService(newInfo);
        }
    }

    // Bind to an ImsService and wait for the service to be connected to create ImsFeatures.
    private void bindImsService(ImsServiceInfo info) {
        if (info == null) {
            return;
        }
        HashSet<Pair<Integer, Integer>> features = calculateFeaturesToCreate(info);
        bindImsServiceWithFeatures(info, features);
    }

    private void bindImsServiceWithFeatures(ImsServiceInfo info,
            HashSet<Pair<Integer, Integer>> features) {
        // Only bind if there are features that will be created by the service.
        if (features.size() > 0) {
            // Check to see if an active controller already exists
            ImsServiceController controller;
            Optional<ImsServiceController> o = getControllerByServiceInfo(mActiveControllers, info);
            if(o.isPresent()) {
                controller = o.get();
                Log.i(TAG, "ImsService connection exists, updating features " + features
                        + ", updating supports emergency calling: " + info.supportsEmergencyMmTel);
                try {
                    controller.changeImsServiceFeatures(features);
                    // Features have been set, there was an error adding/removing. When the
                    // controller recovers, it will add/remove again.
                } catch (RemoteException e) {
                    Log.w(TAG, "bindImsService: error=" + e.getMessage());
                }
            } else {
                controller = info.controllerFactory.create(mContext, info.name, this);
                Log.i(TAG,"Binding ImsService: " + controller.getComponentName()
                        + " with features: " + features + ", supports emergency calling: "
                        + info.supportsEmergencyMmTel);
                controller.bind(features);
            }
            controller.setCanPlaceEmergencyCalls(info.supportsEmergencyMmTel);
            mActiveControllers.add(controller);
        }
    }

    // Clean up and unbind from an ImsService
    private void unbindImsService(ImsServiceInfo info) {
        if (info == null) {
            return;
        }
        Optional<ImsServiceController> o = getControllerByServiceInfo(mActiveControllers, info);
        if (o.isPresent()) {
            // Calls imsServiceFeatureRemoved on all features in the controller
            try {
                Log.i(TAG, "Unbinding ImsService: " + o.get().getComponentName());
                o.get().unbind();
            } catch (RemoteException e) {
                Log.e(TAG, "unbindImsService: Remote Exception: " + e.getMessage());
            }
            mActiveControllers.remove(o.get());
        }
    }

    // Calculate which features an ImsServiceController will need. If it is the carrier specific
    // ImsServiceController, it will be granted all of the features it requests on the associated
    // slot. If it is the device ImsService, it will get all of the features not covered by the
    // carrier implementation.
    private HashSet<Pair<Integer, Integer>> calculateFeaturesToCreate(ImsServiceInfo info) {
        HashSet<Pair<Integer, Integer>> imsFeaturesBySlot = new HashSet<>();
        // Check if the info is a carrier service
        int slotId = getSlotForActiveCarrierService(info);
        if (slotId != SubscriptionManager.INVALID_PHONE_INDEX) {
            imsFeaturesBySlot.addAll(info.getSupportedFeatures().stream()
                    // Match slotId with feature slotId.
                    .filter(feature -> slotId == feature.first)
                    .collect(Collectors.toList()));
        } else if (isDeviceService(info)) {
            // For all slots that are not currently using a carrier ImsService, enable all features
            // for the device default.
            for (int i = 0; i < mNumSlots; i++) {
                final int currSlotId = i;
                ImsServiceInfo carrierImsInfo = getImsServiceInfoFromCache(mCarrierServices[i]);
                if (carrierImsInfo == null) {
                    // No Carrier override, add all features for this slot
                    imsFeaturesBySlot.addAll(info.getSupportedFeatures().stream()
                            .filter(feature -> currSlotId == feature.first)
                            .collect(Collectors.toList()));
                } else {
                    // Add all features to the device service that are not currently covered by
                    // the carrier ImsService.
                    HashSet<Pair<Integer, Integer>> deviceFeatures =
                            new HashSet<>(info.getSupportedFeatures());
                    deviceFeatures.removeAll(carrierImsInfo.getSupportedFeatures());
                    // only add features for current slot
                    imsFeaturesBySlot.addAll(deviceFeatures.stream()
                            .filter(feature -> currSlotId == feature.first).collect(
                            Collectors.toList()));
                }
            }
        }
        return imsFeaturesBySlot;
    }

    /**
     * Implementation of
     * {@link ImsServiceController.ImsServiceControllerCallbacks#imsServiceFeatureCreated}, which
     * removes the ImsServiceController from the mBoundImsServicesByFeature structure.
     */
    public void imsServiceFeatureCreated(int slotId, int feature, ImsServiceController controller) {
        putImsController(slotId, feature, controller);
    }

    /**
     * Implementation of
     * {@link ImsServiceController.ImsServiceControllerCallbacks#imsServiceFeatureRemoved}, which
     * removes the ImsServiceController from the mBoundImsServicesByFeature structure.
     */
    public void imsServiceFeatureRemoved(int slotId, int feature, ImsServiceController controller) {
        removeImsController(slotId, feature);
    }

    // Possibly rebind to another ImsService if currently installed ImsServices were changed or if
    // the SIM card has changed.
    // Called from the handler ONLY
    private void maybeRebindService(int slotId) {
        if (slotId <= SubscriptionManager.INVALID_SIM_SLOT_INDEX) {
            // not specified, replace package on all slots.
            for (int i = 0; i < mNumSlots; i++) {
                updateBoundCarrierServices(i);
            }
        } else {
            updateBoundCarrierServices(slotId);
        }

    }

    private void updateBoundCarrierServices(int slotId) {
        int subId = mSubscriptionManagerProxy.getSubId(slotId);
        String newPackageName = mCarrierConfigManager.getConfigForSubId(subId).getString(
                CarrierConfigManager.KEY_CONFIG_IMS_PACKAGE_OVERRIDE_STRING, null);
        if (slotId > SubscriptionManager.INVALID_SIM_SLOT_INDEX && slotId < mNumSlots) {
            String oldPackageName = mCarrierServices[slotId];
            mCarrierServices[slotId] = newPackageName;
            if (!TextUtils.equals(newPackageName, oldPackageName)) {
                Log.i(TAG, "Carrier Config updated, binding new ImsService");
                // Unbind old ImsService, not needed anymore
                // ImsService is retrieved from the cache. If the cache hasn't been populated yet,
                // the calls to unbind/bind will fail (intended during initial start up).
                unbindImsService(getImsServiceInfoFromCache(oldPackageName));
                ImsServiceInfo newInfo = getImsServiceInfoFromCache(newPackageName);
                // if there is no carrier ImsService, newInfo is null. This we still want to update
                // bindings for device ImsService to pick up the missing features.
                if(newInfo == null || newInfo.featureFromMetadata) {
                    bindImsService(newInfo);
                    // Recalculate the device ImsService features to reflect changes.
                    updateImsServiceFeatures(getImsServiceInfoFromCache(mDeviceService));
                } else {
                    // ImsServiceInfo that has not had features queried yet. Start async
                    // bind and query features.
                    scheduleQueryForFeatures(newInfo);
                }
            }
        }
    }

    /**
     * Schedules a query for dynamic ImsService features.
     */
    private void scheduleQueryForFeatures(ImsServiceInfo service, int delayMs) {
        // if not current device/carrier service, don't perform query. If this changes, this method
        // will be called again.
        if (!isDeviceService(service) && getSlotForActiveCarrierService(service)
                == SubscriptionManager.INVALID_PHONE_INDEX) {
            Log.i(TAG, "scheduleQueryForFeatures: skipping query for ImsService that is not"
                    + " set as carrier/device ImsService.");
            return;
        }
        Message msg = Message.obtain(mHandler, HANDLER_START_DYNAMIC_FEATURE_QUERY, service);
        if (mHandler.hasMessages(HANDLER_START_DYNAMIC_FEATURE_QUERY, service)) {
            Log.d(TAG, "scheduleQueryForFeatures: dynamic query for " + service.name
                    + " already scheduled");
            return;
        }
        Log.d(TAG, "scheduleQueryForFeatures: starting dynamic query for " + service.name
                + " in " + delayMs + "ms.");
        mHandler.sendMessageDelayed(msg, delayMs);
    }

    private void scheduleQueryForFeatures(ComponentName name, int delayMs) {
        ImsServiceInfo service = getImsServiceInfoFromCache(name.getPackageName());
        if (service == null) {
            Log.w(TAG, "scheduleQueryForFeatures: Couldn't find cached info for name: " + name);
            return;
        }
        scheduleQueryForFeatures(service, delayMs);
    }

    private void scheduleQueryForFeatures(ImsServiceInfo service) {
        scheduleQueryForFeatures(service, 0);
    }

    /**
     * Schedules the processing of a completed query.
     */
    private void scheduleFeatureQueryComplete(ComponentName name,
            Set<Pair<Integer, Integer>> features) {
        SomeArgs args = SomeArgs.obtain();
        args.arg1 = name;
        args.arg2 = features;
        mHandler.obtainMessage(HANDLER_DYNAMIC_FEATURE_QUERY_COMPLETE, args).sendToTarget();
    }

    // Starts a dynamic query. Called from handler ONLY.
    private void startDynamicQuery(ImsServiceInfo service) {
        boolean queryStarted = mFeatureQueryManager.startQuery(service.name,
                service.controllerFactory.getServiceInterface());
        if (!queryStarted) {
            Log.w(TAG, "startDynamicQuery: service could not connect. Retrying after delay.");
            scheduleQueryForFeatures(service, DELAY_DYNAMIC_QUERY_MS);
        } else {
            Log.d(TAG, "startDynamicQuery: Service queried, waiting for response.");
        }
    }

    // process complete dynamic query. Called from handler ONLY.
    private void dynamicQueryComplete(ComponentName name, Set<Pair<Integer, Integer>> features) {
        ImsServiceInfo service = getImsServiceInfoFromCache(name.getPackageName());
        if (service == null) {
            Log.w(TAG, "scheduleFeatureQueryComplete: Couldn't find cached info for name: "
                    + name);
            return;
        }
        // Add features to service
        service.replaceFeatures(features);
        if (isActiveCarrierService(service)) {
            // New ImsService is registered to active carrier services and must be newly
            // bound.
            bindImsService(service);
            // Update existing device service features
            updateImsServiceFeatures(getImsServiceInfoFromCache(mDeviceService));
        } else if (isDeviceService(service)) {
            // New ImsService is registered as device default and must be newly bound.
            bindImsService(service);
        }
    }

    /**
     * @return true if the ImsResolver is in the process of resolving a dynamic query and should not
     * be considered available, false if the ImsResolver is idle.
     */
    public boolean isResolvingBinding() {
        return mHandler.hasMessages(HANDLER_START_DYNAMIC_FEATURE_QUERY)
                // We haven't processed this message yet, so it is still resolving.
                || mHandler.hasMessages(HANDLER_DYNAMIC_FEATURE_QUERY_COMPLETE)
                || mFeatureQueryManager.isQueryInProgress();
    }

    private String printFeatures(Set<Pair<Integer, Integer>> features) {
        StringBuilder featureString = new StringBuilder();
        featureString.append("features: [");
        if(features != null) {
            for (Pair<Integer, Integer> feature: features) {
                featureString.append("{");
                featureString.append(feature.first);
                featureString.append(",");
                featureString.append(feature.second);
                featureString.append("} ");
            }
            featureString.append("]");
        }
        return featureString.toString();
    }

    /**
     * Returns the ImsServiceInfo that matches the provided packageName. Visible for testing
     * the ImsService caching functionality.
     */
    @VisibleForTesting
    public ImsServiceInfo getImsServiceInfoFromCache(String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return null;
        }
        Optional<ImsServiceInfo> infoFilter = getInfoByPackageName(mInstalledServicesCache,
                packageName);
        if (infoFilter.isPresent()) {
            return infoFilter.get();
        } else {
            return null;
        }
    }

    // Return the ImsServiceInfo specified for the package name. If the package name is null,
    // get all packages that support ImsServices.
    private List<ImsServiceInfo> getImsServiceInfo(String packageName) {
        List<ImsServiceInfo> infos = new ArrayList<>();
        if (!mIsDynamicBinding) {
            // always return the same ImsService info.
            infos.addAll(getStaticImsService());
        } else {
            // Search for Current ImsService implementations
            infos.addAll(searchForImsServices(packageName, mImsServiceControllerFactory));
            // Search for compat ImsService Implementations
            infos.addAll(searchForImsServices(packageName, mImsServiceControllerFactoryCompat));
        }
        return infos;
    }

    private List<ImsServiceInfo> getStaticImsService() {
        List<ImsServiceInfo> infos = new ArrayList<>();

        ImsServiceInfo info = new ImsServiceInfo(mNumSlots);
        info.name = mStaticComponent;
        info.controllerFactory = mImsServiceControllerFactoryStaticBindingCompat;
        info.supportsEmergencyMmTel = true;
        info.addFeatureForAllSlots(ImsFeature.FEATURE_MMTEL);
        infos.add(info);
        return infos;
    }

    private List<ImsServiceInfo> searchForImsServices(String packageName,
            ImsServiceControllerFactory controllerFactory) {
        List<ImsServiceInfo> infos = new ArrayList<>();

        Intent serviceIntent = new Intent(controllerFactory.getServiceInterface());
        serviceIntent.setPackage(packageName);

        PackageManager packageManager = mContext.getPackageManager();
        for (ResolveInfo entry : packageManager.queryIntentServicesAsUser(
                serviceIntent,
                PackageManager.GET_META_DATA,
                mContext.getUserId())) {
            ServiceInfo serviceInfo = entry.serviceInfo;

            if (serviceInfo != null) {
                ImsServiceInfo info = new ImsServiceInfo(mNumSlots);
                info.name = new ComponentName(serviceInfo.packageName, serviceInfo.name);
                info.controllerFactory = controllerFactory;

                // we will allow the manifest method of declaring manifest features in two cases:
                // 1) it is the device overlay "default" ImsService, where the features do not
                // change (the new method can still be used if the default does not define manifest
                // entries).
                // 2) using the "compat" ImsService, which only supports manifest query.
                if (isDeviceService(info)
                        || mImsServiceControllerFactoryCompat == controllerFactory) {
                    if (serviceInfo.metaData != null) {
                        if (serviceInfo.metaData.getBoolean(METADATA_EMERGENCY_MMTEL_FEATURE,
                                false)) {
                            info.supportsEmergencyMmTel = true;
                        }
                        if (serviceInfo.metaData.getBoolean(METADATA_MMTEL_FEATURE, false)) {
                            info.addFeatureForAllSlots(ImsFeature.FEATURE_MMTEL);
                        }
                        if (serviceInfo.metaData.getBoolean(METADATA_RCS_FEATURE, false)) {
                            info.addFeatureForAllSlots(ImsFeature.FEATURE_RCS);
                        }
                    }
                    // Only dynamic query if we are not a compat version of ImsService and the
                    // default service.
                    if (mImsServiceControllerFactoryCompat != controllerFactory
                            && info.getSupportedFeatures().isEmpty()) {
                        // metadata empty, try dynamic query instead
                        info.featureFromMetadata = false;
                    }
                } else {
                    // We are a carrier service and not using the compat version of ImsService.
                    info.featureFromMetadata = false;
                }
                Log.i(TAG, "service name: " + info.name + ", manifest query: "
                        + info.featureFromMetadata);
                // Check manifest permission to be sure that the service declares the correct
                // permissions.
                if (TextUtils.equals(serviceInfo.permission,
                        Manifest.permission.BIND_IMS_SERVICE)) {
                    infos.add(info);
                } else {
                    Log.w(TAG, "ImsService is not protected with BIND_IMS_SERVICE permission: "
                            + info.name);
                }
            }
        }
        return infos;
    }
}
