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

package com.android.internal.telephony.uicc.euicc.logging;

import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * A helper to obfuscate PII in logs. Obfuscated strings are wrapped with "[" and "]".
 *
 * @hide
 */
public final class Pii {
    // TODO: make this as a flag to turn PII obfuscation off for debug or custom builds.
    private static final boolean ENABLED = true;

    /** Obfuscates a string using SHA1. */
    public static String s(String s) {
        return ENABLED ? "[" + obfuscate(s) + "]" : s;
    }

    /**
     * Obfuscate an ICCID. The first 9 digits of the total 20 digits are retained, as they
     * represents an industry prefix, country code, and issuer id.
     */
    public static String iccid(String iccid) {
        return ENABLED
                ? "[" + iccid.substring(0, 9) + "-" + obfuscate(iccid.substring(9)) + "]"
                : iccid;
    }

    private static String obfuscate(String s) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        byte[] r = digest.digest(s.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(r, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
    }

    private Pii() {}
}
