/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.internal.telephony.imsphone;

import static org.junit.Assert.assertEquals;

import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.ims.ImsCallProfile;
import android.telephony.ims.ImsExternalCallState;
import android.test.suitebuilder.annotation.SmallTest;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.internal.telephony.TelephonyTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class ImsExternalConnectionTest extends TelephonyTest {
    private static final int CALL_ID = 1;

    ImsExternalCallTracker mTracker;

    @Mock
    ImsPullCall mImsPullCall;
    @Mock
    ImsExternalCallTracker.ImsCallNotify mCallNotifier;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mTracker = new ImsExternalCallTracker(mImsPhone, mImsPullCall, mCallNotifier);
    }

    /**
     * Test converts to address for incoming calls.
     */
    @Test
    @SmallTest
    public void testAddressConvert() {
        String[] testAddressMappingSet = {
                /* {"inputAddress", "ExpectResult"} */
                {"tel:+11234567890", "01234567890"},
                {"tel:+201234567890", "01234567890"},
                {"tel:+811234567890", "01234567890"},
                {"tel:+3541234567890", "01234567890"},
                {"tel:01234567890", "01234567890"}};
        String[] testConvertAddressMappingSet = {"+1:0", "+20:0", "+81:0", "+354:0"};
        for (String[] testAddress : testAddressMappingSet) {
            PersistableBundle bundle = mContextFixture.getCarrierConfigBundle();
            mContextFixture.addSystemFeature(PackageManager.FEATURE_TELEPHONY_IMS);
            bundle.putStringArray(CarrierConfigManager
                            .KEY_CONVERT_INCOMING_NUMBER_TO_NATIONAL_FORMAT_STRING_ARRAY,
                    testConvertAddressMappingSet);

            List<ImsExternalCallState> dep = new ArrayList<>();
            dep.add(
                    new ImsExternalCallState(CALL_ID,
                            Uri.parse(testAddress[0]),
                            false /* isPullable */,
                            ImsExternalCallState.CALL_STATE_CONFIRMED,
                            ImsCallProfile.CALL_TYPE_VOICE,
                            false /* isHeld */));
            mTracker.refreshExternalCallState(dep);
            ImsExternalConnection connection = (ImsExternalConnection)
                    mTracker.getConnectionById(CALL_ID);
            connection.setIsIncoming(true);
            assertEquals(testAddress[1], connection.getAddress());
        }
    }
}
