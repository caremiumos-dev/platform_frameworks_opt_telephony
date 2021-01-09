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

package com.android.internal.telephony;

import static org.junit.Assert.assertEquals;

import android.telephony.ims.SipMessage;
import android.test.suitebuilder.annotation.SmallTest;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test SIP Message parsing utilities in frameworks/base. see {@link SipMessageParsingUtils}.
 */
@RunWith(AndroidJUnit4.class)
public class SipMessageParsingUtilsTest {

    // Sample messages from RFC 3261 modified for parsing use cases.
    public static final SipMessage SIP_MESSAGE_1 = new SipMessage(
            "INVITE sip:bob@biloxi.com SIP/2.0",
            // Typical Via
            "Via: SIP/2.0/UDP pc33.atlanta.com;branch=z9hG4bK776asdhds\n"
                    + "Max-Forwards: 70\n"
                    + "To: Bob <sip:bob@biloxi.com>\n"
                    + "From: Alice <sip:alice@atlanta.com>;tag=1928301774\n"
                    + "Call-ID: a84b4c76e66710@pc33.atlanta.com\n"
                    + "CSeq: 314159 INVITE\n"
                    + "Contact: <sip:alice@pc33.atlanta.com>\n"
                    + "Content-Type: application/sdp\n"
                    + "Content-Length: 142",
            new byte[0]);
    public static final String SIP_MESSAGE_1_TRANSACTION_ID = "z9hG4bK776asdhds";
    public static final SipMessage SIP_MESSAGE_2 = new SipMessage(
            "INVITE sip:bob@biloxi.com SIP/2.0",
            "Max-Forwards: 70\n"
                    + "To: Bob <sip:bob@biloxi.com>\n"
                    + "From: Alice <sip:alice@atlanta.com>;tag=1928301774\n"
                    + "Call-ID: a84b4c76e66710@pc33.atlanta.com\n"
                    + "CSeq: 314159 INVITE\n"
                    + "Contact: <sip:alice@pc33.atlanta.com>\n"
                    + "Content-Type: application/sdp\n"
                    + "Content-Length: 142\n"
                    // transaction ID should be returned for the first "Via"
                    + "Via: SIP/2.0/UDP pc33.atlanta.com;branch=z9hG4bKabcdefghi\n"
                    + "Via: SIP/2.0/UDP pc33.atlanta.com;branch=z9hG4bK776asdhds\n",

            new byte[0]);
    public static final String SIP_MESSAGE_2_TRANSACTION_ID = "z9hG4bKabcdefghi";
    public static final SipMessage SIP_MESSAGE_3 = new SipMessage(
            "INVITE sip:bob@biloxi.com SIP/2.0",
            "Max-Forwards: 70\n"
                    + "To: Bob <sip:bob@biloxi.com>\n"
                    + "From: Alice <sip:alice@atlanta.com>;tag=1928301774\n"
                    // Subject line is split into multiple lines via space and tab.
                    + "Subject: I know you're there,\n"
                    + " pick up the phone\n"
                    + "\tand talk to me!\n"
                    // transaction ID should be returned for the first "Via"
                    + "Via: SIP/2.0/UDP pc33.atlanta.com;branch=z9hG4bKzyxwvutsr\n"
                    + "Call-ID: a84b4c76e66710@pc33.atlanta.com\n"
                    + "CSeq: 314159 INVITE\n"
                    + "Contact: <sip:alice@pc33.atlanta.com>\n"
                    + "Content-Type: application/sdp\n"
                    + "Content-Length: 142\n"
                    + "Via: SIP/2.0/UDP pc33.atlanta.com;branch=z9hG4bKabcdefghi\n",
            new byte[0]);
    public static final String SIP_MESSAGE_3_TRANSACTION_ID = "z9hG4bKzyxwvutsr";
    // compact form
    public static final SipMessage SIP_MESSAGE_4 = new SipMessage(
            "INVITE sip:bob@biloxi.com SIP/2.0",
            "Max-Forwards: 70\n"
                    + "t: Bob <sip:bob@biloxi.com>\n"
                    + "f: Alice <sip:alice@atlanta.com>;tag=1928301774\n"
                    // compat form of via
                    + "v: SIP/2.0/UDP pc33.atlanta.com;branch=z9hG4bKAbCdEfGiJ\n"
                    + "v: SIP/2.0/UDP pc33.atlanta.com;branch=z9hG4bKabcdefghi\n"
                    + "i: a84b4c76e66710@pc33.atlanta.com\n"
                    + "CSeq: 314159 INVITE\n"
                    + "m: <sip:alice@pc33.atlanta.com>\n"
                    + "c: application/sdp\n"
                    + "l: 142\n",
            new byte[0]);
    public static final String SIP_MESSAGE_4_TRANSACTION_ID = "z9hG4bKAbCdEfGiJ";
    public static final SipMessage SIP_MESSAGE_5 = new SipMessage(
            "INVITE sip:bob@biloxi.com SIP/2.0",
            "Max-Forwards: 70\n"
                    + "To: Bob <sip:bob@biloxi.com>\n"
                    + "From: Alice <sip:alice@atlanta.com>;tag=1928301774\n"
                    // Malformed lines
                    + "Subject: I know you're there,\n"
                    + "pick up the phone\n"
                    + "and talk to me!\n"
                    // transaction ID should be returned for the first "Via"
                    + "Via: SIP/2.0/UDP pc33.atlanta.com;branch=z9hG4bKzyxwvutsr\n"
                    + "Call-ID: a84b4c76e66710@pc33.atlanta.com\n"
                    + "CSeq: 314159 INVITE\n"
                    + "Contact: <sip:alice@pc33.atlanta.com>\n"
                    + "Content-Type: application/sdp\n"
                    + "Content-Length: 142\n"
                    + "Via: SIP/2.0/UDP pc33.atlanta.com;branch=z9hG4bKabcdefghi\n",
            new byte[0]);
    public static final String SIP_MESSAGE_5_TRANSACTION_ID = "z9hG4bKzyxwvutsr";

    @Test
    @SmallTest
    public void testGetTransactionId() {
        assertEquals(SIP_MESSAGE_1_TRANSACTION_ID, SipMessageParsingUtils.getTransactionId(
                SIP_MESSAGE_1.getHeaderSection()));
        assertEquals(SIP_MESSAGE_2_TRANSACTION_ID, SipMessageParsingUtils.getTransactionId(
                SIP_MESSAGE_2.getHeaderSection()));
        assertEquals(SIP_MESSAGE_3_TRANSACTION_ID, SipMessageParsingUtils.getTransactionId(
                SIP_MESSAGE_3.getHeaderSection()));
        assertEquals(SIP_MESSAGE_4_TRANSACTION_ID, SipMessageParsingUtils.getTransactionId(
                SIP_MESSAGE_4.getHeaderSection()));
        assertEquals(SIP_MESSAGE_5_TRANSACTION_ID, SipMessageParsingUtils.getTransactionId(
                SIP_MESSAGE_5.getHeaderSection()));
    }
}
