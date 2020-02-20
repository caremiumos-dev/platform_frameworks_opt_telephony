/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static android.content.pm.PackageParser.SigningDetails;
import static android.telephony.CarrierConfigManager.KEY_CARRIER_CERTIFICATE_STRING_ARRAY;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.NonNull;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.os.UserHandle;
import android.os.test.TestLooper;
import android.telephony.CarrierConfigManager;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class CarrierPrivilegesTrackerTest {
    private static final int ADMINISTRATOR_UID = 10;
    private static final int UID_A = 100;
    private static final int UID_B = 101;
    private static final int SLOT_INDEX = 1;
    private static final int SUB_ID = 2;

    private static final String PACKAGE_NAME_A = "android.test.a";
    private static final String PACKAGE_NAME_B = "android.test.b";
    private static final String CERTIFICATE_A = "61ed377e85d386a8dfee6b864bd85b0bfaa5af81";
    private static final String CERTIFICATE_B = "112233445566778899AABBCCDDEEFF0011223344";
    private static final String CERTIFICATE_C = "FFDDEECCBBAA99887766554433221100FFEEDDCC";

    private Phone mPhone;
    private Context mContext;
    private CarrierConfigManager mCarrierConfigManager;
    private PackageManager mPackageManager;

    private TestLooper mTestLooper;
    private CarrierPrivilegesTracker mTracker;
    private TestListener mListener;

    @Before
    public void setUp() {
        mPhone = mock(Phone.class);
        mContext = mock(Context.class);
        mCarrierConfigManager = mock(CarrierConfigManager.class);
        mPackageManager = mock(PackageManager.class);

        when(mPhone.getContext()).thenReturn(mContext);

        mTestLooper = new TestLooper();
        mTracker =
                new CarrierPrivilegesTracker(
                        mPhone, mTestLooper.getLooper(), mCarrierConfigManager, mPackageManager);
        mListener = new TestListener();
    }

    @Test
    public void testRegisterListener() {
        mTracker.mAdministratorUids.add(ADMINISTRATOR_UID);

        mTracker.registerCarrierPrivilegeListener(mListener);
        mTestLooper.dispatchAll();
        assertTrue(mTracker.mListeners.contains(mListener));
        assertTrue(mListener.mUids.contains(ADMINISTRATOR_UID));
        assertEquals(1, mListener.mTimesInvoked);
    }

    @Test
    public void testUnregisterListener() {
        mTracker.registerCarrierPrivilegeListener(mListener);
        mTestLooper.dispatchAll();

        mTracker.unregisterCallback(mListener);
        mTestLooper.dispatchAll();
        assertTrue(mTracker.mListeners.isEmpty());
    }

    @Test
    public void testCarrierConfigsChanged() throws Exception {
        String[] certs = {CERTIFICATE_A, CERTIFICATE_B, CERTIFICATE_C};
        Bundle bundle = prepareCarrierConfigCerts(certs);

        mTracker.mPackageInfos.add(
                createPackageInfoWithCertificates(PACKAGE_NAME_A, UID_A, CERTIFICATE_A));
        mTracker.mPackageInfos.add(
                createPackageInfoWithCertificates(PACKAGE_NAME_B, UID_B, CERTIFICATE_B));

        when(mPhone.getPhoneId()).thenReturn(SLOT_INDEX);
        when(mPhone.getSubId()).thenReturn(SUB_ID);

        mTracker.mListeners.add(mListener);
        mTracker.handleCarrierConfigCertsUpdated(bundle);

        assertTrue(mTracker.mCarrierConfigCerts.containsAll(Arrays.asList(certs)));
        List<Integer> expectedUids = Arrays.asList(UID_A, UID_B);
        assertTrue(mTracker.mAdministratorUids.containsAll(expectedUids));
        assertTrue(mListener.mUids.containsAll(expectedUids));
        assertEquals(1, mListener.mTimesInvoked);
        verify(mPhone).getPhoneId();
        verify(mPhone).getSubId();
        verify(mCarrierConfigManager).getConfigForSubId(eq(SUB_ID));
        verify(mPackageManager).getPackageUidAsUser(eq(PACKAGE_NAME_A), eq(UserHandle.USER_SYSTEM));
        verify(mPackageManager).getPackageUidAsUser(eq(PACKAGE_NAME_B), eq(UserHandle.USER_SYSTEM));
    }

    @Test
    public void testCarrierConfigsChangedWithoutUpdates() throws Exception {
        Bundle bundle = prepareCarrierConfigCerts(CERTIFICATE_A);

        mTracker.mCarrierConfigCerts.add(CERTIFICATE_A.toUpperCase());
        mTracker.mCarrierConfigCerts.add(CERTIFICATE_A.toLowerCase());

        when(mPhone.getPhoneId()).thenReturn(SLOT_INDEX);
        when(mPhone.getSubId()).thenReturn(SUB_ID);

        mTracker.mListeners.add(mListener);
        mTracker.handleCarrierConfigCertsUpdated(bundle);

        assertTrue(mTracker.mCarrierConfigCerts.contains(CERTIFICATE_A));
        assertTrue(mTracker.mAdministratorUids.isEmpty());
        assertEquals(0, mListener.mTimesInvoked);
        verify(mPhone).getPhoneId();
        verify(mPhone).getSubId();
        verify(mCarrierConfigManager).getConfigForSubId(eq(SUB_ID));
    }

    private Bundle prepareCarrierConfigCerts(String... certs) {
        PersistableBundle carrierConfigs = new PersistableBundle();
        carrierConfigs.putStringArray(KEY_CARRIER_CERTIFICATE_STRING_ARRAY, certs);
        when(mCarrierConfigManager.getConfigForSubId(eq(SUB_ID))).thenReturn(carrierConfigs);

        Bundle bundle = new Bundle();
        bundle.putInt(CarrierConfigManager.EXTRA_SLOT_INDEX, SLOT_INDEX);
        return bundle;
    }

    private PackageInfo createPackageInfoWithCertificates(
            String packageName, int uid, String... certificates) throws Exception {
        Signature[] signatures = new Signature[certificates.length];
        for (int i = 0; i < signatures.length; i++) {
            signatures[i] = new Signature(certificates[i]);
        }
        SigningDetails signingDetails =
                new SigningDetails(
                        signatures, SigningDetails.SignatureSchemeVersion.UNKNOWN, null, null);
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.signingInfo = new SigningInfo(signingDetails);
        packageInfo.packageName = packageName;

        when(mPackageManager.getPackageUidAsUser(eq(packageName), eq(UserHandle.USER_SYSTEM)))
                .thenReturn(uid);

        return packageInfo;
    }

    private class TestListener extends CarrierPrivilegesTracker.CarrierPrivilegesListener {
        private Set<Integer> mUids;
        private int mTimesInvoked;

        @Override
        public void onCarrierPrivilegesChanged(@NonNull Set<Integer> uids) {
            mUids = uids;
            mTimesInvoked++;
        }
    }
}
