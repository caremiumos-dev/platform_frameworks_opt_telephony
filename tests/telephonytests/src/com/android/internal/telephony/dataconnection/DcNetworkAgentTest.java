/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.internal.telephony.dataconnection;

import static android.telephony.AccessNetworkConstants.TRANSPORT_TYPE_WWAN;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.NetworkAgentConfig;
import android.net.NetworkCapabilities;
import android.net.vcn.VcnUnderlyingNetworkPolicy;
import android.os.Handler;
import android.os.test.TestLooper;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.TelephonyTest;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public class DcNetworkAgentTest extends TelephonyTest {
    private static final int SCORE = 45;
    private static final NetworkAgentConfig NETWORK_AGENT_CONFIG =
            new NetworkAgentConfig.Builder()
                    .setLegacyType(ConnectivityManager.TYPE_MOBILE)
                    .setLegacyTypeName("MOBILE")
                    .build();

    private static final LinkProperties DATA_CONNECTION_LINK_PROPERTIES = new LinkProperties();

    static {
        DATA_CONNECTION_LINK_PROPERTIES.setInterfaceName("test interface" /* iface */);
    }

    private static final NetworkCapabilities DATA_CONNECTION_NETWORK_CAPABILITIES =
            new NetworkCapabilities.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                    .build();

    private static final NetworkCapabilities VCN_MERGED_NETWORK_CAPABILITIES =
            new NetworkCapabilities.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                    .setRequestorPackageName("android.net.vcn")
                    .build();

    @Mock private DataConnection mDataConnection;
    @Mock private Phone mPhone;
    @Mock private Handler mHandler;

    private TestLooper mTestLooper;
    private DcNetworkAgent mDcNetworkAgent;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());

        when(mPhone.getContext()).thenReturn(mContext);

        // TODO: can't mock getHandler() -- need to create spy(DataConnection) instead
        mTestLooper = new TestLooper();
        when(mDataConnection.getHandler()).thenReturn(new Handler(mTestLooper.getLooper()));

        when(mVcnManager.getUnderlyingNetworkPolicy(
                        eq(DATA_CONNECTION_NETWORK_CAPABILITIES),
                        eq(DATA_CONNECTION_LINK_PROPERTIES)))
                .thenReturn(
                        new VcnUnderlyingNetworkPolicy(
                                false /* isTeardownRequested */, VCN_MERGED_NETWORK_CAPABILITIES));

        mDcNetworkAgent =
                new DcNetworkAgent(
                        mDataConnection,
                        mPhone,
                        SCORE,
                        NETWORK_AGENT_CONFIG,
                        null /* networkProvider */,
                        TRANSPORT_TYPE_WWAN);
    }

    @Test
    public void testVcnPolicyListenerAddedOnRegister() {
        // DcNetworkAgent has already been created, so verify it was registered to
        // ConnectivityService when constructed (using merged VCN NetworkCapabilities)

        verifyDcNetworkAgentStart();
    }

    private void verifyDcNetworkAgentStart() {
        verify(mConnectivityManager)
                .registerNetworkAgent(
                        any(),
                        any(),
                        eq(DATA_CONNECTION_LINK_PROPERTIES),
                        eq(VCN_MERGED_NETWORK_CAPABILITIES),
                        eq(SCORE),
                        any(),
                        anyInt());
        verify(mVcnManager, times(2))
                .getUnderlyingNetworkPolicy(
                        eq(DATA_CONNECTION_NETWORK_CAPABILITIES),
                        eq(DATA_CONNECTION_LINK_PROPERTIES));
    }

    @Test
    public void testDcNetworkAgentTornDownOnStart() {
        when(mVcnManager.getUnderlyingNetworkPolicy(
                        eq(DATA_CONNECTION_NETWORK_CAPABILITIES),
                        eq(DATA_CONNECTION_LINK_PROPERTIES)))
                .thenReturn(
                        new VcnUnderlyingNetworkPolicy(
                                true /* isTeardownRequested */, VCN_MERGED_NETWORK_CAPABILITIES));

        mDcNetworkAgent =
                new DcNetworkAgent(
                        mDataConnection,
                        mPhone,
                        SCORE,
                        NETWORK_AGENT_CONFIG,
                        null /* networkProvider */,
                        TRANSPORT_TYPE_WWAN);

        verifyDcNetworkAgentStart();
        verify(mDataConnection)
                .tearDownAll(
                        eq(Phone.REASON_VCN_REQUIRED_TEARDOWN),
                        eq(DcTracker.RELEASE_TYPE_DETACH),
                        any());
    }

    @Test
    public void testVcnPolicyChangeUpdatesNetworkCapabilities() {
        when(mVcnManager.getUnderlyingNetworkPolicy(
                        eq(VCN_MERGED_NETWORK_CAPABILITIES), eq(DATA_CONNECTION_LINK_PROPERTIES)))
                .thenReturn(
                        new VcnUnderlyingNetworkPolicy(
                                false /* isTeardownRequested */, VCN_MERGED_NETWORK_CAPABILITIES));
    }
}
