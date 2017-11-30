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

package android.telephony.ims.internal;

import android.telephony.ims.internal.stub.ImsRegistrationConfiguration;
import android.telephony.ims.internal.stub.ImsRegistrationImplBase;


/**
 * Created by breadley on 11/29/17.
 */

public class TestImsRegistration  extends ImsRegistrationImplBase {

    private ImsRegistrationConfiguration mTestConfig;
    private int mImsRegistrationCalledCount = 0;
    private ImsRegistrationImplBase.Callback mCallback = new ImsRegistrationImplBase.Callback() {

    };

    public void setConfig(ImsRegistrationConfiguration c) {
        mTestConfig = c;
    }

    public int getRegistrationFeaturesChangedCount() {
        return mImsRegistrationCalledCount;
    }

    @Override
    public ImsRegistrationConfiguration onQuerySupportedImsFeatures() {
        return mTestConfig;
    }

    @Override
    public void onSupportedImsFeaturesChanged(ImsRegistrationConfiguration config) {
        mImsRegistrationCalledCount++;
    }
}
