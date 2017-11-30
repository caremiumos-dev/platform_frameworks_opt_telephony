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

import android.os.RemoteException;
import android.telephony.ims.internal.feature.ImsFeature;
import android.telephony.ims.internal.feature.MmTelFeature;
import android.telephony.ims.stub.ImsEcbmImplBase;
import android.telephony.ims.stub.ImsMultiEndpointImplBase;
import android.telephony.ims.stub.ImsUtImplBase;

import com.android.ims.ImsCallProfile;
import com.android.ims.internal.ImsCallSession;

public class TestMmTelFeature extends MmTelFeature {

    public int setCapabilitiesResult = ImsFeature.CAPABILITY_SUCCESS;
    public CapabilityConfiguration lastConfig;

    public void incomingCall(ImsCallSession c) throws RemoteException {
        notifyIncomingCall(c);
    }

    @Override
    public boolean isConnected(int callSessionType, int callType) {
        return super.isConnected(callSessionType, callType);
    }

    @Override
    public ImsCallProfile createCallProfile(int callSessionType, int callType) {
        return super.createCallProfile(callSessionType, callType);
    }

    @Override
    public ImsCallSession createCallSession(ImsCallProfile profile,
            ImsCallSessionListener listener) {
        return super.createCallSession(profile, listener);
    }

    @Override
    public ImsUtImplBase getUt() {
        return super.getUt();
    }

    @Override
    public ImsEcbmImplBase getEcbm() {
        return super.getEcbm();
    }

    @Override
    public ImsMultiEndpointImplBase getMultiEndpoint() {
        return super.getMultiEndpoint();
    }

    @Override
    public int onSetCapabilityValues(MmTelCapabilityConfiguration capabilities) {
        lastConfig = capabilities;
        return setCapabilitiesResult;
    }

    @Override
    public void onFeatureRemoved() {
    }

    @Override
    public void onFeatureReady() {
    }
}
