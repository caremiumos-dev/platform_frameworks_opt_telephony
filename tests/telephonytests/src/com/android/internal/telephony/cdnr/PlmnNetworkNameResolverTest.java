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

package com.android.internal.telephony.cdnr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.os.PersistableBundle;
import android.os.test.TestLooper;
import android.telephony.CarrierConfigManager;

import androidx.test.runner.AndroidJUnit4;

import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.IccRecords.CarrierNameDisplayConditionBitmask;
import com.android.internal.telephony.uicc.IccRecords.OperatorPlmnInfo;
import com.android.internal.telephony.uicc.IccRecords.PlmnNetworkName;
import com.android.internal.telephony.uicc.SIMRecords;
import com.android.internal.telephony.util.ArrayUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class PlmnNetworkNameResolverTest extends TelephonyTest {
    private static final List<String> SHORT_FPLMNS_LIST = Arrays.asList("12345", "123456", "09876");
    private static final List<String> LONG_FPLMNS_LIST =
            Arrays.asList("12345", "123456", "09876", "123456", "098237");
    private static final List<String> EMPTY_FPLMN_LIST = new ArrayList<>();
    private static final int EF_SIZE = 12;
    private static final int MAX_NUM_FPLMN = 4;

    private TestLooper mTestLooper;
    private SIMRecords mSIMRecords;
    private PersistableBundle mConfig;
    private PlmnNetworkNameResolver mPlmnNetworkNameResolver;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mTestLooper = new TestLooper();
        mSIMRecords = new SIMRecords(mUiccCardApplication3gpp, mContext, mSimulatedCommands);
        mConfig = mContextFixture.getCarrierConfigBundle();
        mPlmnNetworkNameResolver = new PlmnNetworkNameResolver(mContext);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testGetSpn() {
        String spn1 = "SPN 1";
        String spn2 = "SPN 2";

        mSIMRecords.setServiceProviderName(spn1);
        mPlmnNetworkNameResolver.updateSimRecords(mSIMRecords);
        assertEquals(spn1, mPlmnNetworkNameResolver.getSpn());

        mConfig.putString(CarrierConfigManager.KEY_CARRIER_NAME_STRING, spn2);
        mPlmnNetworkNameResolver.updateCarrierConfig(mConfig);
        assertEquals(spn2, mPlmnNetworkNameResolver.getSpn());

        mConfig.putBoolean(CarrierConfigManager.KEY_CARRIER_NAME_OVERRIDE_BOOL, true);
        mPlmnNetworkNameResolver.updateCarrierConfig(mConfig);
        assertEquals(spn2, mPlmnNetworkNameResolver.getSpn());

        mConfig.putString(CarrierConfigManager.KEY_CARRIER_NAME_STRING, "");
        mPlmnNetworkNameResolver.updateCarrierConfig(mConfig);
        assertEquals("", mPlmnNetworkNameResolver.getSpn());

        mConfig.putBoolean(CarrierConfigManager.KEY_CARRIER_NAME_OVERRIDE_BOOL, false);
        mConfig.putString(CarrierConfigManager.KEY_CARRIER_NAME_STRING, spn2);
        mPlmnNetworkNameResolver.updateCarrierConfig(mConfig);
        assertEquals(spn2, mPlmnNetworkNameResolver.getSpn());

        mConfig.putString(CarrierConfigManager.KEY_CARRIER_NAME_STRING, "");
        mPlmnNetworkNameResolver.updateCarrierConfig(mConfig);
        assertEquals(spn1, mPlmnNetworkNameResolver.getSpn());
    }

    @Test
    public void testGetServiceProviderNameDisplayCondition() {
        @CarrierNameDisplayConditionBitmask int displayCondition1 =
                IccRecords.CARRIER_NAME_DISPLAY_CONDITION_BITMASK_PLMN;
        @CarrierNameDisplayConditionBitmask int displayCondition2 =
                IccRecords.CARRIER_NAME_DISPLAY_CONDITION_BITMASK_SPN;

        mSIMRecords.setCarrierNameDisplayCondition(displayCondition1);
        mPlmnNetworkNameResolver.updateSimRecords(mSIMRecords);
        assertEquals(displayCondition1,
                mPlmnNetworkNameResolver.getServiceProviderNameDisplayCondition());

        mConfig.putInt(CarrierConfigManager.KEY_SPN_DISPLAY_CONDITION_OVERRIDE_INT,
                displayCondition2);
        mPlmnNetworkNameResolver.updateCarrierConfig(mConfig);
        assertEquals(displayCondition2,
                mPlmnNetworkNameResolver.getServiceProviderNameDisplayCondition());
    }

    @Test
    public void testGetSpdi() {
        String[] spdi1 = {"123456"};
        String[] spdi2 = {"654321"};

        mSIMRecords.setServiceProviderDisplayInformation(spdi1);
        mPlmnNetworkNameResolver.updateSimRecords(mSIMRecords);
        assertEquals(Arrays.asList(spdi1), mPlmnNetworkNameResolver.getSpdi());

        mConfig.putStringArray(CarrierConfigManager.KEY_SPDI_OVERRIDE_STRING_ARRAY, spdi2);
        mPlmnNetworkNameResolver.updateCarrierConfig(mConfig);
        assertEquals(Arrays.asList(spdi2), mPlmnNetworkNameResolver.getSpdi());
    }

    @Test
    public void testGetPnns() {
        String fullName1 = "Name full 1";
        String shortName1 = "Name short 1";
        String fullName2 = "Name full 2";
        String shortName2 = "Name short 2";

        String[] nameArray = {fullName2 + ',' + shortName2};

        List<PlmnNetworkName> pnns1 = new ArrayList<PlmnNetworkName>();
        List<PlmnNetworkName> pnns2 = new ArrayList<PlmnNetworkName>();

        pnns1.add(new PlmnNetworkName(fullName1, shortName1));
        pnns2.add(new PlmnNetworkName(fullName2, shortName2));

        mSIMRecords.setPnns(pnns1.toArray(new PlmnNetworkName[0]));
        mPlmnNetworkNameResolver.updateSimRecords(mSIMRecords);
        assertEquals(pnns1, mPlmnNetworkNameResolver.getPnns());

        mConfig.putStringArray(CarrierConfigManager.KEY_PNN_OVERRIDE_STRING_ARRAY, nameArray);
        mPlmnNetworkNameResolver.updateCarrierConfig(mConfig);
        assertEquals(pnns2, mPlmnNetworkNameResolver.getPnns());
    }

    @Test
    public void testGetOpl() {
        String plmn1 = "345678";
        String plmn2 = "345678";
        int lacTacStart = 0x0000;
        int lacTacEnd = 0xFFFE;
        int pnnIndex = 1;

        String[] nameArray = {String.format("%s,%d,%d,%d",
                plmn2, lacTacStart, lacTacEnd, pnnIndex)};

        List<OperatorPlmnInfo> opl1 = new ArrayList<OperatorPlmnInfo>();
        List<OperatorPlmnInfo> opl2 = new ArrayList<OperatorPlmnInfo>();

        opl1.add(new OperatorPlmnInfo(plmn1, lacTacStart, lacTacEnd, pnnIndex));
        opl2.add(new OperatorPlmnInfo(plmn2, lacTacStart, lacTacEnd, pnnIndex));

        mSIMRecords.setOpl(opl1.toArray(new OperatorPlmnInfo[0]));
        mPlmnNetworkNameResolver.updateSimRecords(mSIMRecords);
        assertEquals(opl1, mPlmnNetworkNameResolver.getOpl());

        mConfig.putStringArray(CarrierConfigManager.KEY_OPL_OVERRIDE_STRING_ARRAY, nameArray);
        mPlmnNetworkNameResolver.updateCarrierConfig(mConfig);
        assertEquals(opl2, mPlmnNetworkNameResolver.getOpl());
    }

    @Test
    public void testGetEhplmnList() {
        String[] ehplmns1 = {"234567"};
        String[] ehplmns2 = {"765432"};

        mSIMRecords.setEhplmns(ehplmns1);
        mPlmnNetworkNameResolver.updateSimRecords(mSIMRecords);
        assertEquals(Arrays.asList(ehplmns1), mPlmnNetworkNameResolver.getEhplmnList());

        mConfig.putStringArray(CarrierConfigManager.KEY_EHPLMN_OVERRIDE_STRING_ARRAY, ehplmns2);
        mPlmnNetworkNameResolver.updateCarrierConfig(mConfig);
        assertEquals(Arrays.asList(ehplmns2), mPlmnNetworkNameResolver.getEhplmnList());
    }

    @Test
    public void testGetHomePlmns() {
        String[] ehplmns1 = {"234567"};
        String[] spdi1 = {"123456"};
        String[] homePlmns = ArrayUtils.concatElements(String.class, ehplmns1, spdi1);

        mSIMRecords.setImsi("");
        mSIMRecords.setEhplmns(ehplmns1);
        mSIMRecords.setServiceProviderDisplayInformation(spdi1);
        mPlmnNetworkNameResolver.updateSimRecords(mSIMRecords);
        assertEquals(Arrays.asList(homePlmns), mPlmnNetworkNameResolver.getHomePlmns());
    }

    @Test
    public void testGetNetworkNameForPlmn() {
        // Set SPN
        mConfig.putBoolean(CarrierConfigManager.KEY_CARRIER_NAME_OVERRIDE_BOOL, true);
        mConfig.putString(CarrierConfigManager.KEY_CARRIER_NAME_STRING, "");

        // Set up SPDI
        String plmn1 = "123456";
        String[] spdi = {plmn1};
        mConfig.putStringArray(CarrierConfigManager.KEY_SPDI_OVERRIDE_STRING_ARRAY, spdi);

        // Set up EHPLMN
        String plmn2 = "234567";
        String[] ehplmns = {plmn2};
        mConfig.putStringArray(CarrierConfigManager.KEY_EHPLMN_OVERRIDE_STRING_ARRAY, ehplmns);

        // Set up PNN
        String fullName1 = "Name full 1";
        String shortName1 = "Name short 1";
        String fullName2 = "Name full 2";
        String shortName2 = "Name short 2";
        List<PlmnNetworkName> pnns = new ArrayList<PlmnNetworkName>();
        pnns.add(new PlmnNetworkName(fullName1, shortName1));
        pnns.add(new PlmnNetworkName(fullName2, shortName2));
        pnns.add(new PlmnNetworkName(null, shortName2));
        mConfig.putStringArray(CarrierConfigManager.KEY_PNN_OVERRIDE_STRING_ARRAY, null);
        mSIMRecords.setPnns(pnns.toArray(new PlmnNetworkName[0]));
        mPlmnNetworkNameResolver.updateSimRecords(mSIMRecords);
        mPlmnNetworkNameResolver.updateCarrierConfig(mConfig);

        // Set up OPL
        String plmn3 = "345678";
        String plmn4 = "456789";
        String plmn5 = "567890";
        int lacTacStart = 0x0000;
        int lacTacEnd = 0xFFFE;
        int pnnIndex = 1;
        List<OperatorPlmnInfo> opl = new ArrayList<OperatorPlmnInfo>();
        opl.add(new OperatorPlmnInfo(plmn3, lacTacStart, lacTacEnd, pnnIndex));
        opl.add(new OperatorPlmnInfo(plmn4, lacTacStart, lacTacEnd, pnnIndex + 1));
        opl.add(new OperatorPlmnInfo(plmn5, lacTacStart, lacTacEnd, pnnIndex + 2));
        mConfig.putStringArray(CarrierConfigManager.KEY_OPL_OVERRIDE_STRING_ARRAY, null);
        mSIMRecords.setOpl(opl.toArray(new OperatorPlmnInfo[0]));

        // Test
        mPlmnNetworkNameResolver.updateSimRecords(mSIMRecords);
        mPlmnNetworkNameResolver.updateCarrierConfig(mConfig);
        assertNull(mPlmnNetworkNameResolver.getNetworkNameForPlmn(null, 0));
        assertEquals(fullName1, mPlmnNetworkNameResolver.getNetworkNameForPlmn(plmn1, 0));
        assertEquals(fullName1, mPlmnNetworkNameResolver.getNetworkNameForPlmn(plmn2, 0));
        assertEquals(fullName1, mPlmnNetworkNameResolver.getNetworkNameForPlmn(plmn3, 0));
        assertEquals(fullName2, mPlmnNetworkNameResolver.getNetworkNameForPlmn(plmn4, 0));
        assertEquals(shortName2, mPlmnNetworkNameResolver.getNetworkNameForPlmn(plmn5, 0));

        String spn = "SPN 1";
        mSIMRecords.setServiceProviderName(spn);
        mPlmnNetworkNameResolver.updateSimRecords(mSIMRecords);
        assertEquals(fullName1, mPlmnNetworkNameResolver.getNetworkNameForPlmn(plmn1, 0));
        assertEquals(fullName1, mPlmnNetworkNameResolver.getNetworkNameForPlmn(plmn2, 0));

        mConfig.putString(CarrierConfigManager.KEY_CARRIER_NAME_STRING, spn);
        mPlmnNetworkNameResolver.updateCarrierConfig(mConfig);
        assertEquals(spn, mPlmnNetworkNameResolver.getNetworkNameForPlmn(plmn1, 0));
        assertEquals(spn, mPlmnNetworkNameResolver.getNetworkNameForPlmn(plmn2, 0));
    }
}
