/*
 * Copyright (C) 2018 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.internal.telephony.ims;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.IBinder;
import android.util.Pair;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Manages the querying of multiple ImsServices asynchronously in order to retrieve the ImsFeatures
 * they support.
 */

public class ImsServiceFeatureQueryManager {

    private static final class ImsServiceFeatureQuery implements ServiceConnection {

        private final Context mContext;
        private final ComponentName mName;
        private final String mIntentFilter;

        public ImsServiceFeatureQuery(Context context, ComponentName name, String intentFilter) {
            mContext = context;
            mName = name;
            mIntentFilter = intentFilter;
        }

        /**
         * Starts the bind to the ImsService specified ComponentName.
         * @return true if binding started, false if it failed and will not recover.
         */
        public boolean start() {
            Intent imsServiceIntent = new Intent(mIntentFilter).setComponent(mName);
            int serviceFlags = Context.BIND_FOREGROUND_SERVICE | Context.BIND_IMPORTANT;
            boolean bindStarted = mContext.bindService(imsServiceIntent, this, serviceFlags);
            if (!bindStarted) {
                // the docs say to do this if
                mContext.unbindService(this);
            }
            return bindStarted;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {

        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    }

    public interface Listener {
        /**
         * Called when a query has completed.
         * @param name The Package Name of the query
         * @param features A Set of slotid->feature pairs that the ImsService supports.
         */
        void onImsServiceQueryResult(String name, Set<Pair<Integer, Integer>> features);
    }

    // Maps an active ImsService query (by Package Name String) its query.
    private final Map<ComponentName, ImsServiceFeatureQuery> mActiveQueries;
    private final Context mContext;
    private final Listener mListener;

    public ImsServiceFeatureQueryManager(Context context, Listener listener) {
        mActiveQueries = new HashMap<>();
        mContext = context;
        mListener = listener;
    }

    public void startQuery(ComponentName name, String intentFilter) {
        if (mActiveQueries.containsKey(name)) {
            // We already have an active query, wait for it to return.
            return;
        }
        ImsServiceFeatureQuery query = new ImsServiceFeatureQuery(mContext, name, intentFilter);
        mActiveQueries.put(name, query);
        query.execute();
    }
}
