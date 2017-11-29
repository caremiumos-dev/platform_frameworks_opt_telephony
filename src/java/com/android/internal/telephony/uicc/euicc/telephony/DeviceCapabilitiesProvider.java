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

package com.android.euicc.telephony;

import android.os.Handler;
import android.support.annotation.Nullable;
import com.android.euicc.asn1.Asn1Node;
import com.android.euicc.async.AsyncResultCallback;
import com.android.euicc.card.EuiccCard;

/**
 * Class to provide device capabilities. Device capabilities are required by the implementation of
 * {@link EuiccCard#authenticateServer(String, Asn1Node, Asn1Node, Asn1Node, Asn1Node,
 * AsyncResultCallback, Handler)}. This class doesn't return any particular release versions for any
 * device capacities. OEM LPA should extend this class to provide device-specific implementation to
 * retrieve device capabilities.
 */
public class DeviceCapabilitiesProvider {

    /**
     * Retrieves supported release version of GSM. A version is a 3-byte array representing the
     * major, the minor, and the revision bits. If the device doesn't support this capability,
     * {@code null} can be returned.
     */
    @Nullable
    public byte[] getGsmSupportedRelease() {
        return null;
    }

    /**
     * Retrieves supported release version of UTRAN. A version is a 3-byte array representing the
     * major, the minor, and the revision bits. If the device doesn't support this capability,
     * {@code null} can be returned.
     */
    @Nullable
    public byte[] getUtranSupportedRelease() {
        return null;
    }

    /**
     * Retrieves supported release version of CDMA2000 1x. A version is a 3-byte array representing
     * the major, the minor, and the revision bits. If the device doesn't support this capability,
     * {@code null} can be returned.
     */
    @Nullable
    public byte[] getCdma2000OneXSupportedRelease() {
        return null;
    }

    /**
     * Retrieves supported release version of CDMA2000 HRPD. A version is a 3-byte array
     * representing the major, the minor, and the revision bits. If the device doesn't support this
     * capability, {@code null} can be returned.
     */
    @Nullable
    public byte[] getCdma2000HrpdSupportedRelease() {
        return null;
    }

    /**
     * Retrieves supported release version of CDMA2000 eHRPD. A version is a 3-byte array
     * representing the major, the minor, and the revision bits. If the device doesn't support this
     * capability, {@code null} can be returned.
     */
    @Nullable
    public byte[] getCdma2000EhrpdSupportedRelease() {
        return null;
    }

    /**
     * Retrieves supported release version of E-UTRAN. A version is a 3-byte array representing the
     * major, the minor, and the revision bits. If the device doesn't support this capability,
     * {@code null} can be returned.
     */
    @Nullable
    public byte[] getEutranSupportedRelease() {
        return null;
    }

    /**
     * Retrieves supported release version of NFC. A version is a 3-byte array representing the
     * major, the minor, and the revision bits. If the device doesn't support this capability,
     * {@code null} can be returned.
     */
    @Nullable
    public byte[] getContactlessSupportedRelease() {
        return null;
    }

    /**
     * Retrieves supported release version of RSP CRL. A version is a 3-byte array representing the
     * major, the minor, and the revision bits. If the device doesn't support this capability,
     * {@code null} can be returned.
     */
    @Nullable
    public byte[] getRspCrlSupportedVersion() {
        return null;
    }
}
