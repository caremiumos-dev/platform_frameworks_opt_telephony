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

/**
 * Manages the Binding lifecycle of one ImsService as well as the relevant ImsFeatures that the
 * ImsService will support.
 *
 * Compatibility interface for interacting with older implementations of ImsService. The older
 * ImsService implementation is contained within the android.telephony.ims.compat.* namspace.
 * Newer implementations of ImsService should use the current APIs contained in
 * android.telephony.ims.compat.*.
 */
public class ImsServiceControllerCompat extends ImsServiceController {

    public static final String SERVICE_INTERFACE = "android.telephony.ims.compat.ImsService";

    public ImsServiceControllerCompat(Context context, ComponentName componentName,
            ImsServiceController.ImsServiceControllerCallbacks callbacks) {
        super(context, componentName, callbacks);
    }

    @Override
    protected String getServiceInterface() {
        return SERVICE_INTERFACE;
    }
}
