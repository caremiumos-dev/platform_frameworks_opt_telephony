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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.telecom.TelecomManager;
import android.telephony.CarrierConfigManager;

import com.android.internal.telephony.uicc.IccUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

/**
 * Unit test for {@link MissedIncomingCallSmsFilter}
 */
public class MissedIncomingCallSmsFilterTest extends TelephonyTest {

    private static final String FAKE_CARRIER_SMS_ORIGINATOR = "+18584121234";

    private static final String FAKE_CALLER_ID = "6501234567";

    private MissedIncomingCallSmsFilter mFilterUT;

    private PersistableBundle mBundle;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mBundle = mContextFixture.getCarrierConfigBundle();
        mFilterUT = new MissedIncomingCallSmsFilter(mPhone);
    }

    @After
    public void tearDown() throws Exception {
        mFilterUT = null;
        mBundle = null;
        super.tearDown();
    }

    private void putCarrierConfigSettings() {
        mBundle.putStringArray(
                CarrierConfigManager.KEY_MISSED_INCOMING_CALL_SMS_ORIGINATOR_STRING_ARRAY,
                new String[]{FAKE_CARRIER_SMS_ORIGINATOR});
        String callerIdPattern = "^(?<month>0[1-9]|1[012])\\/(?<day>0[1-9]|1[0-9]|2[0-9]|3[0-1])\\s"
                + "(?<hour>[0-1][0-9]|2[0-3]):(?<minute>[0-5][0-9])\\s(\\n|\\s\\n)"
                + "(?<callerId>[0-9]+)\\b\\s*";
        String restrictedPattern = "^(?<month>0[1-9]|1[012])\\/(?<day>;0[1-9]|1[0-9]|2[0-9]|3[0-1])"
                + "\\s(?<hour>[0-1][0-9]|2[0-3]):(?<minute>[0-5][0-9])\\s\\s\\n"
                + "((?<restricted>非通知設定)|(?<payphone>公衆電話)|(?<unknown>通知不可能)|"
                + "(?<unavailable>利用不可能))\\b\\s*";
        mBundle.putStringArray(
                CarrierConfigManager.KEY_MISSED_INCOMING_CALL_SMS_PATTERN_STRING_ARRAY,
                new String[]{callerIdPattern, restrictedPattern});
    }

    @Test
    public void testMissedIncomingCallwithCallerId() {
        putCarrierConfigSettings();

        // SMS originator:"+18584121234"
        // message body:"01/14 08:15 \n6501234567"
        // Data Coding Scheme:GSM 7bit default alphabet
        String smsPduString = "07919107739667F9040B918185141232F400000210413141114A17B0D82B4603C170"
                + "BA580DA4B0D56031D98C56B3DD1A";
        byte[][] pdus = {IccUtils.hexStringToBytes(smsPduString)};
        assertTrue(mFilterUT.filter(pdus, SmsConstants.FORMAT_3GPP));

        TelecomManager telecomManager = (TelecomManager) mContext.getSystemService(
                Context.TELECOM_SERVICE);

        ArgumentCaptor<Bundle> captor = ArgumentCaptor.forClass(Bundle.class);
        verify(telecomManager).addNewIncomingCall(any(), captor.capture());

        Bundle bundle = captor.getValue();
        Uri uri = bundle.getParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS);

        assertEquals(FAKE_CALLER_ID, uri.getSchemeSpecificPart());
    }

    @Test
    public void testMissedIncomingCallNotMatchSMSOriginator() {
        putCarrierConfigSettings();

        // SMS originator:"+11114121234"
        // message body:"01/14 08:15 \n6501234567"
        // Data Coding Scheme:GSM 7bit default alphabet
        String smsPduString = "07919107739667F9040B911111141232F400000210413141114A17B0D82B4603C170"
                + "BA580DA4B0D56031D98C56B3DD1A";
        byte[][] pdus = {IccUtils.hexStringToBytes(smsPduString)};
        assertFalse(mFilterUT.filter(pdus, SmsConstants.FORMAT_3GPP));
    }

    @Test
    public void testMissedIncomingCallwithRestrictedNumber() {
        putCarrierConfigSettings();

        // SMS originator:"+18584121234"
        // message body:"04/10 14:18 \s\n非通知設定"
        // Data Coding Scheme:UCS2 (16 bit)
        String smsPduString = "07919107739667F9040B918185141232F400080210413141114A2600300034002F00"
                + "310030002000310034003A003100380020000D000A975E901A77E58A2D5B9A";
        byte[][] pdus = {IccUtils.hexStringToBytes(smsPduString)};
        assertTrue(mFilterUT.filter(pdus, SmsConstants.FORMAT_3GPP));

        TelecomManager telecomManager = (TelecomManager) mContext.getSystemService(
                Context.TELECOM_SERVICE);

        ArgumentCaptor<Bundle> captor = ArgumentCaptor.forClass(Bundle.class);
        verify(telecomManager).addNewIncomingCall(any(), captor.capture());

        Bundle bundle = captor.getValue();

        assertEquals(TelecomManager.PRESENTATION_RESTRICTED,
                bundle.getInt(TelecomManager.EXTRA_CALL_PRESENTATION));
    }

    @Test
    public void testMissedIncomingCallwithPayphone() {
        putCarrierConfigSettings();

        // SMS originator:"+18584121234"
        // message body:"04/10 14:18 \s\n公衆電話"
        // Data Coding Scheme:UCS2 (16 bit)
        String smsPduString = "07919107739667F9040B918185141232F400080210413141114A2400300034002F00"
                + "310030002000310034003A003100380020000D000A516C884696FB8A71";
        byte[][] pdus = {IccUtils.hexStringToBytes(smsPduString)};
        assertTrue(mFilterUT.filter(pdus, SmsConstants.FORMAT_3GPP));

        TelecomManager telecomManager = (TelecomManager) mContext.getSystemService(
                Context.TELECOM_SERVICE);

        ArgumentCaptor<Bundle> captor = ArgumentCaptor.forClass(Bundle.class);
        verify(telecomManager).addNewIncomingCall(any(), captor.capture());

        Bundle bundle = captor.getValue();

        assertEquals(TelecomManager.PRESENTATION_PAYPHONE,
                bundle.getInt(TelecomManager.EXTRA_CALL_PRESENTATION));
    }

    @Test
    public void testMissedIncomingCallwithUnknown() {
        putCarrierConfigSettings();

        // SMS originator:"+18584121234"
        // message body:"04/10 14:18 \s\n通知不可能"
        // Data Coding Scheme:UCS2 (16 bit)
        String smsPduString = "07919107739667F9040B918185141232F400080210413141114A2600300034002F00"
                + "310030002000310034003A003100380020000D000A901A77E54E0D53EF80FD";
        byte[][] pdus = {IccUtils.hexStringToBytes(smsPduString)};
        assertTrue(mFilterUT.filter(pdus, SmsConstants.FORMAT_3GPP));

        TelecomManager telecomManager = (TelecomManager) mContext.getSystemService(
                Context.TELECOM_SERVICE);

        ArgumentCaptor<Bundle> captor = ArgumentCaptor.forClass(Bundle.class);
        verify(telecomManager).addNewIncomingCall(any(), captor.capture());

        Bundle bundle = captor.getValue();

        assertEquals(TelecomManager.PRESENTATION_UNKNOWN,
                bundle.getInt(TelecomManager.EXTRA_CALL_PRESENTATION));
    }

    @Test
    public void testMissedIncomingCallwithUnavailabe() {
        putCarrierConfigSettings();

        // SMS originator:"+18584121234"
        // message body:"04/10 14:18 \s\n利用不可能"
        // Data Coding Scheme:UCS2 (16 bit)
        String smsPduString = "07919107739667F9040B918185141232F400080210413141114A2600300034002F00"
                + "310030002000310034003A003100380020000D000A522975284E0D53EF80FD";
        byte[][] pdus = {IccUtils.hexStringToBytes(smsPduString)};
        assertTrue(mFilterUT.filter(pdus, SmsConstants.FORMAT_3GPP));

        TelecomManager telecomManager = (TelecomManager) mContext.getSystemService(
                Context.TELECOM_SERVICE);

        ArgumentCaptor<Bundle> captor = ArgumentCaptor.forClass(Bundle.class);
        verify(telecomManager).addNewIncomingCall(any(), captor.capture());

        Bundle bundle = captor.getValue();

        assertEquals(TelecomManager.PRESENTATION_UNAVAILABLE,
                bundle.getInt(TelecomManager.EXTRA_CALL_PRESENTATION));
    }

    @Test
    public void testMissedIncomingCallNotMatchSMSpattern() {
        putCarrierConfigSettings();

        // SMS originator:"+18584121234"
        // message body:"01/14 08:15 \n123-456-789\n\n01/14 08:16 \nerror"
        // Data Coding Scheme:GSM 7bit default alphabet
        String smsPduString = "07914400000000F001000B811000000000F000002CB0D82B4603C170BA580DA488C9"
                + "662D5ACDD6BAE1720A052CF68AD140309C2E660329CA72F95B0E";
        byte[][] pdus = {IccUtils.hexStringToBytes(smsPduString)};
        assertFalse(mFilterUT.filter(pdus, SmsConstants.FORMAT_3GPP));
    }

    @Test
    public void testMultiMissedIncomingCallwithCallerId() {
        putCarrierConfigSettings();

        // SMS originator:"+18584121234"
        // message body:"01/14 08:15 \n6501234567\n\n01/14 08:16 \n6501234567"
        // Data Coding Scheme:GSM 7bit default alphabet
        String smsPduString = "07919107739667F9040B918185141232F400000210413141114A30B0D82B4603C170"
                + "BA580DA4B0D56031D98C56B3DD140A58EC15A38160385DCC0652D86AB0986C46ABD96E";
        byte[][] pdus = {IccUtils.hexStringToBytes(smsPduString)};
        assertTrue(mFilterUT.filter(pdus, SmsConstants.FORMAT_3GPP));

        TelecomManager telecomManager = (TelecomManager) mContext.getSystemService(
                Context.TELECOM_SERVICE);

        ArgumentCaptor<Bundle> captor = ArgumentCaptor.forClass(Bundle.class);
        verify(telecomManager, times(2)).addNewIncomingCall(any(), captor.capture());

        for (Bundle bundle : captor.getAllValues()) {
            Uri uri = bundle.getParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS);
            assertEquals(FAKE_CALLER_ID, uri.getSchemeSpecificPart());
        }
    }

    @Test
    public void testMultiMissedIncomingCallwithRestrictedReason() {
        putCarrierConfigSettings();

        /*
         SMS originator:"+18584121234"
         message body:
         01/14 08:15 \s\n非通知設定\s\n\s\n01/14 08:16 \s\n公衆電話\s\n\s\n01/14 08:17 \s\n通知不可能
         Data Coding Scheme:UCS2 (16 bit)
        */
        String smsPduString = "07919107739667F9040B918185141232F400080210413141114A8000300031002F00"
                + "310034002000300038003A003100350020000D000A975E901A77E58A2D5B9A000D000A000D000A00"
                + "300031002F00310034002000300038003A003100360020000D000A516C884696FB8A71000D000A00"
                + "0D000A00300031002F00310034002000300038003A003100370020000D000A901A77E54E0D53EF80"
                + "FD";
        byte[][] pdus = {IccUtils.hexStringToBytes(smsPduString)};
        assertTrue(mFilterUT.filter(pdus, SmsConstants.FORMAT_3GPP));

        TelecomManager telecomManager = (TelecomManager) mContext.getSystemService(
                Context.TELECOM_SERVICE);

        ArgumentCaptor<Bundle> captor = ArgumentCaptor.forClass(Bundle.class);
        verify(telecomManager, times(3)).addNewIncomingCall(any(), captor.capture());

        List<Bundle> bundleList = captor.getAllValues();
        assertEquals(TelecomManager.PRESENTATION_RESTRICTED,
                bundleList.get(0).getInt(TelecomManager.EXTRA_CALL_PRESENTATION));
        assertEquals(TelecomManager.PRESENTATION_PAYPHONE,
                bundleList.get(1).getInt(TelecomManager.EXTRA_CALL_PRESENTATION));
        assertEquals(TelecomManager.PRESENTATION_UNKNOWN,
                bundleList.get(2).getInt(TelecomManager.EXTRA_CALL_PRESENTATION));
    }

    @Test
    public void testMultiMissedIncomingCallwithCallerIdAndRestrictedReason() {
        putCarrierConfigSettings();

        /*
         SMS originator:"+18584121234"
         message body:"01/14 08:15 \s\n6501234567\s\n\s\n01/14 08:16 \s\n非通知設定"
         Data Coding Scheme:UCS2 (16 bit)
        */
        String smsPduString = "07919107739667F9040B918185141232F400080210413141114A5E00300031002F00"
                + "310034002000300038003A003100350020000D000A00360035003000310032003300340035003600"
                + "37000D000A000D000A00300031002F00310034002000300038003A003100360020000D000A975E90"
                + "1A77E58A2D5B9A";
        byte[][] pdus = {IccUtils.hexStringToBytes(smsPduString)};
        assertTrue(mFilterUT.filter(pdus, SmsConstants.FORMAT_3GPP));

        TelecomManager telecomManager = (TelecomManager) mContext.getSystemService(
                Context.TELECOM_SERVICE);

        ArgumentCaptor<Bundle> captor = ArgumentCaptor.forClass(Bundle.class);
        verify(telecomManager, times(2)).addNewIncomingCall(any(), captor.capture());

        List<Bundle> bundleList = captor.getAllValues();
        Uri uri = bundleList.get(0).getParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS);
        assertEquals(FAKE_CALLER_ID, uri.getSchemeSpecificPart());
        assertEquals(TelecomManager.PRESENTATION_RESTRICTED,
                bundleList.get(1).getInt(TelecomManager.EXTRA_CALL_PRESENTATION));
    }
}
