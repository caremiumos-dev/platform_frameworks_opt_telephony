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

package com.android.internal.telephony.uicc.euicc.card;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.support.test.filters.SmallTest;
import android.telephony.IccOpenLogicalChannelResponse;
import android.telephony.TelephonyManager;

import com.android.internal.telephony.uicc.euicc.asn1.Asn1Converter;
import com.android.internal.telephony.uicc.euicc.asn1.Asn1Node;
import com.android.internal.telephony.uicc.euicc.data.CarrierPrivilegeRule;
import com.android.internal.telephony.uicc.euicc.data.EuiccNotification;
import com.android.internal.telephony.uicc.euicc.data.EuiccProfile;
import com.android.internal.telephony.uicc.euicc.data.EuiccProfile.PolicyRule;
import com.android.internal.telephony.uicc.euicc.data.EuiccProfile.ProfileClass;
import com.android.internal.telephony.uicc.euicc.data.EuiccProfile.ProfileState;
import com.android.internal.telephony.uicc.euicc.data.EuiccRat;
import com.android.internal.telephony.uicc.euicc.data.EuiccSpecVersion;
import com.android.internal.telephony.uicc.euicc.data.OperatorId;
import com.android.internal.telephony.uicc.euicc.telephony.UiccChannelException;
import com.android.internal.telephony.uicc.euicc.telephony.UiccChannelMocker;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@SmallTest
@RunWith(MockitoJUnitRunner.class)
public class EuiccCardImplV2Test {
    @Mock private TelephonyManager mMockTm;

    private EuiccCardImplV2 mEuiccCard;

    @Before
    public void setUp() {
        mEuiccCard = new EuiccCardImplV2(new EuiccSpecVersion(2, 0, 0), mMockTm);
    }

    @Test
    public void testGetAllProfiles() throws UiccChannelException, EuiccCardException {
        int channel =
                mockLogicalChannelResponses("BF2D14A012E3105A0A896700000000004523019F7001019000");

        EuiccProfile[] profiles = mEuiccCard.getAllProfiles();

        assertEquals(1, profiles.length);
        assertEquals("98760000000000543210", profiles[0].getIccid());
        assertEquals(ProfileState.ENABLED, profiles[0].getState());
        verifyStoreData(channel, "BF2D0D5C0B5A909192B79F709599BF76");
    }

    @Test
    public void testFSuffix() throws UiccChannelException, EuiccCardException {
        // iccID is 987600000000005432FF.
        int channel =
                mockLogicalChannelResponses("BF2D14A012E3105A0A896700000000004523FF9F7001019000");

        EuiccProfile[] profiles = mEuiccCard.getAllProfiles();

        assertEquals(1, profiles.length);
        assertEquals("987600000000005432", profiles[0].getIccid());
        assertEquals(ProfileState.ENABLED, profiles[0].getState());
        verifyStoreData(channel, "BF2D0D5C0B5A909192B79F709599BF76");
    }

    @Test
    public void testGetProfile() throws UiccChannelException, EuiccCardException {
        int channel =
                mockLogicalChannelResponses(
                        "BF2D8184A08181E37F"
                                + "5A0A89670000000000452301" // ICCID
                                + "90046E69636B" // Nickname
                                + "9103746D6F" // Service provider name
                                + "92027031" // Profile name
                                + "B70F800312F34581030102038203040506" // Operator id
                                + "9F700101" // Profile state
                                + "950101" // Profile class
                                + "990206C0" // Policy rules
                                + "BF7645E243E135C114ABCD92CBB156B280FA4E1429A6ECEEB6E5C1BFE4"
                                + "CA1D636F6D2E676F6F676C652E616E64726F69642E617070732E6D79617070"
                                + "E30ADB080000000000000001" // Carrier privilege rules
                                + "9000");

        EuiccProfile profile = mEuiccCard.getProfile("98760000000000543210");

        assertEquals("98760000000000543210", profile.getIccid());
        assertEquals("nick", profile.getNickname());
        assertEquals("tmo", profile.getServiceProviderName());
        assertEquals("p1", profile.getProfileName());
        assertEquals("213", profile.getOperatorId().getMcc());
        assertEquals("54", profile.getOperatorId().getMnc());
        assertArrayEquals(new byte[] {1, 2, 3}, profile.getOperatorId().getGid1());
        assertArrayEquals(new byte[] {4, 5, 6}, profile.getOperatorId().getGid2());
        assertEquals(ProfileState.ENABLED, profile.getState());
        assertEquals(ProfileClass.PROVISIONING, profile.getProfileClass());
        assertEquals(
                PolicyRule.DO_NOT_DELETE | PolicyRule.DO_NOT_DISABLE, profile.getPolicyRules());
        assertArrayEquals(
                new CarrierPrivilegeRule[] {
                    new CarrierPrivilegeRule(
                            Asn1Converter.hexToBytes("ABCD92CBB156B280FA4E1429A6ECEEB6E5C1BFE4"),
                            "com.google.android.apps.myapp",
                            1)
                },
                profile.getCarrierPrivilegeRules());
        verifyStoreData(channel, "BF2D0D5C0B5A909192B79F709599BF76");
    }

    @Test
    public void testDisableProfile() throws UiccChannelException, EuiccCardException {
        int channel = mockLogicalChannelResponses("BF32038001009000");

        mEuiccCard.disableProfile("98760000000000543210", true);

        verifyStoreData(channel, "BF3211A00C5A0A896700000000004523018101FF");
    }

    @Test
    public void testDisableProfile_SimRefresh() throws UiccChannelException, EuiccCardException {
        int channel = mockLogicalChannelResponses("6106", "6f00");

        mEuiccCard.disableProfile("98760000000000543210", true);

        verifyStoreData(channel, "BF3211A00C5A0A896700000000004523018101FF");
    }

    @Test
    public void testDisableProfile_ProfilesLoaded()
            throws UiccChannelException, EuiccCardException {
        int channel =
                mockLogicalChannelResponses(
                        "BF2D14A012E3105A0A896700000000004523019F7001019000", "BF32038001009000");

        EuiccProfile[] profiles = mEuiccCard.getAllProfiles();

        assertEquals(1, profiles.length);
        assertEquals(ProfileState.ENABLED, profiles[0].getState());

        mEuiccCard.disableProfile("98760000000000543210", true);

        profiles = mEuiccCard.getAllProfiles();

        assertEquals(1, profiles.length);
        assertEquals(ProfileState.DISABLED, profiles[0].getState());
        verifyStoreData(channel, "BF2D0D5C0B5A909192B79F709599BF76");
        verifyStoreData(channel, "BF3211A00C5A0A896700000000004523018101FF");
    }

    @Test(expected = EuiccCardErrorException.class)
    public void testDisableProfile_Error() throws UiccChannelException, EuiccCardException {
        final int channel = mockLogicalChannelResponses("BF32038001039000");
        try {
            mEuiccCard.disableProfile("98760000000000543210", true);
        } catch (EuiccCardErrorException e) {
            assertEquals(3, e.getErrorCode());
            verifyStoreData(channel, "BF3211A00C5A0A896700000000004523018101FF");
            throw e;
        }
    }

    @Test
    public void testSwitchToProfile() throws UiccChannelException, EuiccCardException {
        int channel =
                mockLogicalChannelResponses(
                        "BF2D14A012E3105A0A896700000000004523019F7001009000", "BF31038001009000");

        EuiccProfile profile = mEuiccCard.switchToProfile("98760000000000543210", true);

        assertEquals(profile.getIccid(), "98760000000000543210");
        verifyStoreData(channel, "BF3111A00C5A0A896700000000004523018101FF");
    }

    @Test
    public void testSwitchToProfile_ProfilesLoaded()
            throws UiccChannelException, EuiccCardException {
        final int channel =
                mockLogicalChannelResponses(
                        "BF2D26A024"
                                + "E3105A0A896700000000004523019F700100"
                                + "E3105A0A896700000000004523029F700101"
                                + "9000",
                        "BF31038001009000");

        EuiccProfile[] profiles = mEuiccCard.getAllProfiles();

        assertEquals(2, profiles.length);
        assertEquals(ProfileState.DISABLED, profiles[0].getState());
        assertEquals(ProfileState.ENABLED, profiles[1].getState());

        mEuiccCard.switchToProfile("98760000000000543210", true);

        profiles = mEuiccCard.getAllProfiles();

        assertEquals(2, profiles.length);
        assertEquals(ProfileState.ENABLED, profiles[0].getState());
        assertEquals(ProfileState.DISABLED, profiles[1].getState());

        verifyStoreData(channel, "BF2D0D5C0B5A909192B79F709599BF76");
        verifyStoreData(channel, "BF3111A00C5A0A896700000000004523018101FF");
    }


    @Test
    public void testswitchToProfile_SimRefresh() throws UiccChannelException, EuiccCardException {
        int channel =
                mockLogicalChannelResponses(
                        "BF2D14A012E3105A0A896700000000004523019F7001009000", "6106", "6f00");

        mEuiccCard.switchToProfile("98760000000000543210", true);

        verifyStoreData(channel, "BF3111A00C5A0A896700000000004523018101FF");
    }

    @Test(expected = EuiccCardErrorException.class)
    public void testSwitchToProfile_Error() throws UiccChannelException, EuiccCardException {
        int channel =
                mockLogicalChannelResponses(
                        "BF2D14A012E3105A0A896700000000004523019F7001009000", "BF31038001039000");
        try {
            mEuiccCard.switchToProfile("98760000000000543210", true);
        } catch (EuiccCardErrorException e) {
            assertEquals(3, e.getErrorCode());
            verifyStoreData(channel, "BF3111A00C5A0A896700000000004523018101FF");
            throw e;
        }
    }

    @Test
    public void testGetEid() throws UiccChannelException, EuiccCardException {
        int channel = mockLogicalChannelResponses("BF3E065A041A2B3C4D9000");

        assertEquals("1A2B3C4D", mEuiccCard.getEid());
        verifyStoreData(channel, "BF3E035C015A");
    }

    @Test
    public void testSetNickname() throws UiccChannelException, EuiccCardException {
        int channel = mockLogicalChannelResponses("BF29038001009000");

        mEuiccCard.setNickname("98760000000000543210", "new nickname");

        verifyStoreData(channel, "BF291A5A0A89670000000000452301900C6E6577206E69636B6E616D65");
    }

    @Test
    public void testSetNickname_ProfilesLoaded() throws UiccChannelException, EuiccCardException {
        int channel =
                mockLogicalChannelResponses(
                        "BF2D26A024"
                                + "E3105A0A896700000000004523019F700100"
                                + "E3105A0A896700000000004523029F700101"
                                + "9000",
                        "BF29038001009000");

        EuiccProfile[] profiles = mEuiccCard.getAllProfiles();
        assertEquals(2, profiles.length);

        mEuiccCard.setNickname("98760000000000543210", "new nickname");

        profiles = mEuiccCard.getAllProfiles();
        assertEquals(2, profiles.length);
        assertEquals("new nickname", profiles[0].getNickname());

        verifyStoreData(channel, "BF2D0D5C0B5A909192B79F709599BF76");
        verifyStoreData(channel, "BF291A5A0A89670000000000452301900C6E6577206E69636B6E616D65");
    }

    @Test
    public void testDeleteProfile() throws UiccChannelException, EuiccCardException {
        int channel =
                mockLogicalChannelResponses(
                        "BF2D26A024"
                                + "E3105A0A896700000000004523019F700100"
                                + "E3105A0A896700000000004523029F700101"
                                + "9000",
                        "BF33038001009000");

        mEuiccCard.deleteProfile("98760000000000543210");

        verifyStoreData(channel, "BF330C5A0A89670000000000452301");
    }

    @Test
    public void testDeleteProfile_ProfilesLoaded() throws UiccChannelException, EuiccCardException {
        int channel =
                mockLogicalChannelResponses(
                        "BF2D26A024"
                                + "E3105A0A896700000000004523019F700100"
                                + "E3105A0A896700000000004523029F700101"
                                + "9000",
                        "BF33038001009000");

        EuiccProfile[] profiles = mEuiccCard.getAllProfiles();
        assertEquals(2, profiles.length);
        assertEquals("98760000000000543210", profiles[0].getIccid());
        assertEquals("98760000000000543220", profiles[1].getIccid());

        mEuiccCard.deleteProfile("98760000000000543210");

        profiles = mEuiccCard.getAllProfiles();
        assertEquals(1, profiles.length);
        assertEquals("98760000000000543220", profiles[0].getIccid());

        verifyStoreData(channel, "BF2D0D5C0B5A909192B79F709599BF76");
        verifyStoreData(channel, "BF330C5A0A89670000000000452301");
    }

    @Test(expected = EuiccCardErrorException.class)
    public void testDeleteProfile_Error() throws UiccChannelException, EuiccCardException {
        int channel =
                mockLogicalChannelResponses(
                        "BF2D26A024"
                                + "E3105A0A896700000000004523019F700100"
                                + "E3105A0A896700000000004523029F700101"
                                + "9000",
                        "BF33038001039000");
        try {
            mEuiccCard.deleteProfile("98760000000000543210");
        } catch (EuiccCardErrorException e) {
            assertEquals(3, e.getErrorCode());
            verifyStoreData(channel, "BF330C5A0A89670000000000452301");
            throw e;
        }
    }

    @Test
    public void testGetDefaultSmdpAddress() throws UiccChannelException, EuiccCardException {
        int channel =
                mockLogicalChannelResponses("BF3C148008534D44502E434F4D8108736D64732E636F6D9000");

        assertEquals("SMDP.COM", mEuiccCard.getDefaultSmdpAddress());
        verifyStoreData(channel, "BF3C00");
    }

    @Test
    public void testGetSmdsAddress() throws UiccChannelException, EuiccCardException {
        int channel =
                mockLogicalChannelResponses("BF3C148008534D44502E434F4D8108736D64732E636F6D9000");

        assertEquals("smds.com", mEuiccCard.getSmdsAddress());
        verifyStoreData(channel, "BF3C00");
    }

    @Test
    public void testSetDefaultSmdpAddress() throws UiccChannelException, EuiccCardException {
        int channel =
                mockLogicalChannelResponses(
                        "BF3C148008534D44502E434F4D8108736D64732E636F6D9000", "BF3F038001009000");

        assertEquals("SMDP.COM", mEuiccCard.getDefaultSmdpAddress());
        verifyStoreData(channel, "BF3C00");

        mEuiccCard.setDefaultSmdpAddress("smdp.gsma.com");

        assertEquals("smdp.gsma.com", mEuiccCard.getDefaultSmdpAddress());
        verifyStoreData(channel, "BF3F0F800D736D64702E67736D612E636F6D");
    }

    @Test
    public void testGetRat() throws UiccChannelException, EuiccCardException {
        int channel =
                mockLogicalChannelResponses(
                        "BF4347"
                                + "A021" // Rule #1
                                + "800206C0" // Policy rules: DO_NOT_DELETE | DO_NOT_DISABLE
                                + "A118" // Operator IDs
                                + "B70A800312F3458103010203" // ID #1: 213, 54, [1,2,3], null
                                + "B70A800312F3458203040506" // ID #2: 213, 54, null, [4,5,6]
                                + "820108" // Flag (no user consent)
                                + "A022" // Rule #2
                                + "80020780" // Policy rules: DO_NOT_DISABLE
                                + "A118" // Operator IDs
                                + "B70A800312E3458103010203" // ID #1: 213, 54E, [1,2,3], null
                                + "B70A8003EEEE458203040506" // ID #2: EEE, 54E, null, [4,5,6]
                                + "82020780" // Flag (user consent)
                                + "9000");

        EuiccRat rat = mEuiccCard.getRat();
        assertEquals(
                -1,
                rat.findIndex(
                        PolicyRule.DO_NOT_DELETE,
                        new OperatorId(new byte[] {0x12, (byte) 0xF3, 0x45}, null, null)));
        assertEquals(
                1,
                rat.findIndex(
                        PolicyRule.DO_NOT_DISABLE,
                        new OperatorId(new byte[] {0x23, 0x67, 0x45}, null, new byte[] {4, 5, 6})));
        assertFalse(rat.hasPolicyRuleFlag(0, EuiccRat.PolicyRuleFlag.CONSENT_REQUIRED));
        assertTrue(rat.hasPolicyRuleFlag(1, EuiccRat.PolicyRuleFlag.CONSENT_REQUIRED));
        verifyStoreData(channel, "BF4300");
    }

    @Test
    public void testResetMemory() throws UiccChannelException, EuiccCardException {
        int channel = mockLogicalChannelResponses("BF34038001009000");

        mEuiccCard.resetMemory(EuiccCard.ResetOption.DELETE_FIELD_LOADED_TEST_PROFILES);

        verifyStoreData(channel, "BF340482020640");
    }

    @Test
    public void testResetMemory_SimRefresh() throws UiccChannelException, EuiccCardException {
        int channel = mockLogicalChannelResponses("6106", "6f00");

        mEuiccCard.resetMemory(EuiccCard.ResetOption.DELETE_FIELD_LOADED_TEST_PROFILES);

        verifyStoreData(channel, "BF340482020640");
    }

    @Test
    public void testGetEuiccChallenge() throws UiccChannelException, EuiccCardException {
        int channel = mockLogicalChannelResponses("BF2E0580030102039000");

        assertArrayEquals(new byte[] {1, 2, 3}, mEuiccCard.getEuiccChallenge());
        verifyStoreData(channel, "BF2E00");
    }

    @Test
    public void testGetEuiccInfo1() throws UiccChannelException, EuiccCardException {
        int channel = mockLogicalChannelResponses("BF20030102039000");

        assertEquals("BF2003010203", mEuiccCard.getEuiccInfo1().toHex());
        verifyStoreData(channel, "BF2000");
    }

    @Test
    public void testGetEuiccInfo2() throws UiccChannelException, EuiccCardException {
        int channel = mockLogicalChannelResponses("BF22030102039000");

        assertEquals("BF2203010203", mEuiccCard.getEuiccInfo2().toHex());
        verifyStoreData(channel, "BF2200");
    }

    @Test
    public void testAuthenticateServer() throws UiccChannelException, EuiccCardException {
        int channel = mockLogicalChannelResponses("BF3802A0009000");
        when(mMockTm.getDeviceId()).thenReturn("987654321012345");

        Asn1Node result =
                mEuiccCard.authenticateServer(
                        "A1B2C3-X4Y5Z6", // Matching id
                        Asn1Node.newBuilder(0xA0).build(),
                        Asn1Node.newBuilder(0xA1).build(),
                        Asn1Node.newBuilder(0xA2).build(),
                        Asn1Node.newBuilder(0xA3).build());

        assertEquals("BF3802A000", result.toHex());
        verifyStoreData(
                channel,
                "BF382D"
                        + "A000"
                        + "A100"
                        + "A200"
                        + "A300"
                        + "A023"
                        + "800D4131423243332D583459355A36" // Matching id
                        + "A112800489674523" // TAC
                        + "A100" // Device capabilities
                        + "82088967452301214305"); // IMEI
    }

    @Test(expected = EuiccCardErrorException.class)
    public void testAuthenticateServer_Error() throws UiccChannelException, EuiccCardException {
        int channel = mockLogicalChannelResponses("BF38038101039000");
        when(mMockTm.getDeviceId()).thenReturn("987654321012345");
        try {
            mEuiccCard.authenticateServer(
                    "A1B2C3-X4Y5Z6", // Matching id
                    Asn1Node.newBuilder(0xA0).build(),
                    Asn1Node.newBuilder(0xA1).build(),
                    Asn1Node.newBuilder(0xA2).build(),
                    Asn1Node.newBuilder(0xA3).build());
        } catch (EuiccCardErrorException e) {
            assertEquals(3, e.getErrorCode());
            verifyStoreData(
                    channel,
                    "BF382D"
                            + "A000"
                            + "A100"
                            + "A200"
                            + "A300"
                            + "A023"
                            + "800D4131423243332D583459355A36" // Matching id
                            + "A112800489674523" // TAC
                            + "A100" // Device capabilities
                            + "82088967452301214305"); // IMEI
            throw e;
        }
    }

    @Test
    public void testPrepareDownload() throws UiccChannelException, EuiccCardException {
        int channel = mockLogicalChannelResponses("BF2102A0009000");

        Asn1Node result =
                mEuiccCard.prepareDownload(
                        Asn1Converter.hexToBytes("4131423243332D583459355A36"), // hashCc
                        Asn1Node.newBuilder(0xA0).build(),
                        Asn1Node.newBuilder(0xA1).build(),
                        Asn1Node.newBuilder(0xA2).build());

        assertEquals("BF2102A000", result.toHex());
        verifyStoreData(
                channel,
                "BF2115"
                        + "A000"
                        + "A100"
                        + "040D4131423243332D583459355A36" // hashCc
                        + "A200");
    }

    @Test(expected = EuiccCardErrorException.class)
    public void testPrepareDownload_Error() throws UiccChannelException, EuiccCardException {
        int channel = mockLogicalChannelResponses("BF2105A1030201039000");
        try {
            mEuiccCard.prepareDownload(
                    Asn1Converter.hexToBytes("4131423243332D583459355A36"), // hashCc
                    Asn1Node.newBuilder(0xA0).build(),
                    Asn1Node.newBuilder(0xA1).build(),
                    Asn1Node.newBuilder(0xA2).build());
        } catch (EuiccCardErrorException e) {
            assertEquals(3, e.getErrorCode());
            verifyStoreData(
                    channel,
                    "BF2115"
                            + "A000"
                            + "A100"
                            + "040D4131423243332D583459355A36" // hashCc
                            + "A200");
            throw e;
        }
    }

    @Test
    public void testLoadBoundProfilePackage() throws UiccChannelException, EuiccCardException {
        int channel =
                mockLogicalChannelResponses(
                        // For boundProfilePackage head + initialiseSecureChannelRequest
                        // (ES8+.InitialiseSecureChannel)
                        "9000",
                        // For firstSequenceOf87 (ES8+.ConfigureISDP)
                        "9000",
                        // For head of sequenceOf88 (ES8+.StoreMetadata)
                        "9000",
                        // For body (element 1) of sequenceOf88 (ES8+.StoreMetadata)
                        "9000",
                        "9000",
                        // For head of sequenceOf86 (ES8+.LoadProfileElements)
                        "9000",
                        // For body (element 1) of sequenceOf86 (ES8+.LoadProfileElements)
                        "9000",
                        // Profile installation result (element 2 of sequenceOf86)
                        "BF37009000");

        Asn1Node result =
                mEuiccCard.loadBoundProfilePackage(
                        Asn1Node.newBuilder(0xBF36)
                                .addChild(Asn1Node.newBuilder(0xBF23))
                                .addChild(
                                        Asn1Node.newBuilder(0xA0)
                                                .addChildAsBytes(0x87, new byte[] {1, 2, 3}))
                                .addChild(
                                        Asn1Node.newBuilder(0xA1)
                                                .addChildAsBytes(0x88, new byte[] {4, 5, 6}))
                                .addChild(Asn1Node.newBuilder(0xA2))
                                .addChild(
                                        Asn1Node.newBuilder(0xA3)
                                                .addChildAsBytes(0x86, new byte[] {7, 8, 9})
                                                .addChildAsBytes(0x86, new byte[] {0xA, 0xB, 0xC}))
                                .build());

        assertEquals("BF3700", result.toHex());
        verifyStoreData(channel, "BF361FBF2300"); // ES8+.InitialiseSecureChannel
        verifyStoreData(channel, "A0058703010203"); // ES8+.ConfigureISDP
        verifyStoreData(channel, "A105"); // ES8+.StoreMetadata
        verifyStoreData(channel, "8803040506"); // ES8+.StoreMetadata
        verifyStoreData(channel, "A200");
        verifyStoreData(channel, "A30A"); // ES8+.LoadProfileElements
        verifyStoreData(channel, "8603070809"); // ES8+.LoadProfileElements
        verifyStoreData(channel, "86030A0B0C"); // ES8+.LoadProfileElements
    }

    @Test(expected = EuiccCardErrorException.class)
    public void testLoadBoundProfilePackage_Error()
            throws UiccChannelException, EuiccCardException {
        int channel =
                mockLogicalChannelResponses(
                        // For boundProfilePackage head + initialiseSecureChannelRequest
                        // (ES8+.InitialiseSecureChannel)
                        "9000",
                        // For firstSequenceOf87 (ES8+.ConfigureISDP)
                        "9000",
                        // For head of sequenceOf88 (ES8+.StoreMetadata)
                        "9000",
                        // For body (element 1) of sequenceOf88 (ES8+.StoreMetadata)
                        "9000",
                        "9000",
                        // For head of sequenceOf86 (ES8+.LoadProfileElements)
                        "9000",
                        // For body (element 1) of sequenceOf86 (ES8+.LoadProfileElements)
                        "9000",
                        // Profile installation result (element 2 of sequenceOf86)
                        "BF370ABF2707A205A1038101039000");
        try {
            mEuiccCard.loadBoundProfilePackage(
                    Asn1Node.newBuilder(0xBF36)
                            .addChild(Asn1Node.newBuilder(0xBF23))
                            .addChild(
                                    Asn1Node.newBuilder(0xA0)
                                            .addChildAsBytes(0x87, new byte[] {1, 2, 3}))
                            .addChild(
                                    Asn1Node.newBuilder(0xA1)
                                            .addChildAsBytes(0x88, new byte[] {4, 5, 6}))
                            .addChild(Asn1Node.newBuilder(0xA2))
                            .addChild(
                                    Asn1Node.newBuilder(0xA3)
                                            .addChildAsBytes(0x86, new byte[] {7, 8, 9})
                                            .addChildAsBytes(0x86, new byte[] {0xA, 0xB, 0xC}))
                            .build());
        } catch (EuiccCardErrorException e) {
            assertEquals(3, e.getErrorCode());
            verifyStoreData(channel, "BF361FBF2300"); // ES8+.InitialiseSecureChannel
            verifyStoreData(channel, "A0058703010203"); // ES8+.ConfigureISDP
            verifyStoreData(channel, "A105"); // ES8+.StoreMetadata
            verifyStoreData(channel, "8803040506"); // ES8+.StoreMetadata
            verifyStoreData(channel, "A200");
            verifyStoreData(channel, "A30A"); // ES8+.LoadProfileElements
            verifyStoreData(channel, "8603070809"); // ES8+.LoadProfileElements
            verifyStoreData(channel, "86030A0B0C"); // ES8+.LoadProfileElements
            throw e;
        }
    }

    @Test
    public void testCancelSession() throws UiccChannelException, EuiccCardException {
        int channel = mockLogicalChannelResponses("BF41009000");

        Asn1Node result =
                mEuiccCard.cancelSession(
                        Asn1Converter.hexToBytes("A1B2C3"), EuiccCard.CancelReason.POSTPONED);

        assertEquals("BF4100", result.toHex());
        verifyStoreData(channel, "BF41088003A1B2C3810101");
    }

    @Test(expected = EuiccCardErrorException.class)
    public void testCancelSession_Error() throws UiccChannelException, EuiccCardException {
        int channel = mockLogicalChannelResponses("BF41038101039000");
        try {
            mEuiccCard.cancelSession(
                    Asn1Converter.hexToBytes("A1B2C3"), EuiccCard.CancelReason.POSTPONED);
        } catch (EuiccCardErrorException e) {
            assertEquals(3, e.getErrorCode());
            verifyStoreData(channel, "BF41088003A1B2C3810101");
            throw e;
        }
    }

    @Test
    public void testListNotifications() throws UiccChannelException, EuiccCardException {
        int channel =
                mockLogicalChannelResponses(
                        "BF282BA029"
                                + "BF2F118001010C08736D64702E636F6D81020410" // Notification #1
                                + "BF2F128001020C09736D6470322E636F6D81020420" // Notification #2
                                + "9000");

        EuiccNotification[] result =
                mEuiccCard.listNotifications(
                        EuiccNotification.Event.DELETE | EuiccNotification.Event.DISABLE);

        assertArrayEquals(
                new EuiccNotification[] {
                    new EuiccNotification(1, "smdp.com", EuiccNotification.Event.DELETE, null),
                    new EuiccNotification(2, "smdp2.com", EuiccNotification.Event.DISABLE, null)
                },
                result);
        verifyStoreData(channel, "BF280481020430");
    }

    @Test(expected = EuiccCardErrorException.class)
    public void testListNotifications_Error() throws UiccChannelException, EuiccCardException {
        int channel = mockLogicalChannelResponses("BF28038101039000");
        try {
            mEuiccCard.listNotifications(
                    EuiccNotification.Event.DELETE | EuiccNotification.Event.DISABLE);
        } catch (EuiccCardErrorException e) {
            assertEquals(3, e.getErrorCode());
            verifyStoreData(channel, "BF280481020430");
            throw e;
        }
    }

    @Test
    public void testRetrieveNotificationList() throws UiccChannelException, EuiccCardException {
        int channel =
                mockLogicalChannelResponses(
                        "BF2B2FA02D"
                                // Notification #1
                                + "3014BF2F118001010C08736D64702E636F6D81020410"
                                // Notification #2
                                + "3015BF2F128001020C09736D6470322E636F6D81020420"
                                + "9000");

        EuiccNotification[] result =
                mEuiccCard.retrieveNotificationList(
                        EuiccNotification.Event.DELETE | EuiccNotification.Event.DISABLE);

        assertArrayEquals(
                new EuiccNotification[] {
                    new EuiccNotification(
                            1,
                            "smdp.com",
                            EuiccNotification.Event.DELETE,
                            Asn1Converter.hexToBytes(
                                    "3014BF2F118001010C08736D64702E636F6D81020410")),
                    new EuiccNotification(
                            2,
                            "smdp2.com",
                            EuiccNotification.Event.DISABLE,
                            Asn1Converter.hexToBytes(
                                    "3015BF2F128001020C09736D6470322E636F6D81020420"))
                },
                result);
        verifyStoreData(channel, "BF2B06A00481020430");
    }

    @Test
    public void testRetrieveNotificationList_Empty()
            throws UiccChannelException, EuiccCardException {
        int channel = mockLogicalChannelResponses("BF2B038101019000");

        EuiccNotification[] result =
                mEuiccCard.retrieveNotificationList(
                        EuiccNotification.Event.DELETE | EuiccNotification.Event.DISABLE);

        assertArrayEquals(new EuiccNotification[0], result);
        verifyStoreData(channel, "BF2B06A00481020430");
    }

    @Test(expected = EuiccCardErrorException.class)
    public void testRetrieveNotificationList_Error()
            throws UiccChannelException, EuiccCardException {
        int channel = mockLogicalChannelResponses("BF2B038101039000");
        try {
            mEuiccCard.retrieveNotificationList(
                    EuiccNotification.Event.DELETE | EuiccNotification.Event.DISABLE);
        } catch (EuiccCardErrorException e) {
            assertEquals(3, e.getErrorCode());
            verifyStoreData(channel, "BF2B06A00481020430");
            throw e;
        }
    }

    @Test
    public void testRetrieveNotification() throws UiccChannelException, EuiccCardException {
        int channel =
                mockLogicalChannelResponses(
                        "BF2B18A016"
                                + "3014BF2F118001010C08736D64702E636F6D81020410" // Notification
                                + "9000");

        EuiccNotification result = mEuiccCard.retrieveNotification(5);

        assertEquals(
                new EuiccNotification(
                        1,
                        "smdp.com",
                        EuiccNotification.Event.DELETE,
                        Asn1Converter.hexToBytes("3014BF2F118001010C08736D64702E636F6D81020410")),
                result);
        verifyStoreData(channel, "BF2B05A003800105");
    }

    @Test(expected = EuiccCardErrorException.class)
    public void testRetrieveNotification_Error() throws UiccChannelException, EuiccCardException {
        int channel = mockLogicalChannelResponses("BF2B038101019000");
        try {
            mEuiccCard.retrieveNotification(5);
        } catch (EuiccCardErrorException e) {
            assertEquals(1, e.getErrorCode());
            verifyStoreData(channel, "BF2B05A003800105");
            throw e;
        }
    }

    @Test
    public void testRemoveNotificationFromList() throws UiccChannelException, EuiccCardException {
        int channel = mockLogicalChannelResponses("BF30038001009000");

        mEuiccCard.removeNotificationFromList(5);

        verifyStoreData(channel, "BF3003800105");
    }

    private void verifyStoreData(int channel, String command) {
        verify(mMockTm, times(1))
                .iccTransmitApduLogicalChannel(
                        channel, 0x80 | channel, 0xE2, 0x91, 0, command.length() / 2, command);
    }

    private int mockLogicalChannelResponses(String response, String... extraResponses) {
        int channel =
                UiccChannelMocker.mockOpenLogicalChannelResponse(
                        mMockTm, IccOpenLogicalChannelResponse.STATUS_NO_ERROR, "9000");
        UiccChannelMocker.mockSendToLogicalChannel(mMockTm, channel, response, extraResponses);
        UiccChannelMocker.mockCloseLogicalChannel(mMockTm, channel);
        return channel;
    }
}
