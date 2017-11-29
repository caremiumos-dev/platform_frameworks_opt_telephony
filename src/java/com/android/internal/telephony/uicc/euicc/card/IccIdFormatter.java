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
package com.android.euicc.card;

import android.text.TextUtils;
import com.android.euicc.asn1.Asn1Converter;

/** Format the IccId with or without trailing Fs. */
public final class IccIdFormatter {
    private IccIdFormatter() {}

    /** Strip all the trailing Fs of an iccId. */
    public static String stripTrailingFs(byte[] iccId) {
        String iccIdString = Asn1Converter.bcdBytesToString(iccId);
        if (iccIdString.endsWith("F")) {
            iccIdString = iccIdString.substring(0, iccIdString.indexOf('F'));
        }
        return iccIdString;
    }

    /** Pad an iccId with trailing Fs until the length is 20. */
    public static String padTrailingFs(String iccId) {
        if (!TextUtils.isEmpty(iccId) && iccId.length() < 20) {
            iccId += new String(new char[20 - iccId.length()]).replace('\0', 'F');
        }
        return iccId;
    }
}
