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

package com.android.internal.telephony.uicc;

import android.text.TextUtils;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;

import java.util.Arrays;


/**
 *
 * Used to store ADNs (Abbreviated Dialing Numbers).
 *
 * {@hide}
 *
 */
public class SimPhoneBookAdnRecord {
    static final String LOG_TAG = "SimPhoneBookAdnRecord";

    //***** Instance Variables
    public int mRecordIndex = 0;

    private String mAlphaTag = null;
    private String mNumber = null;

    private int mEmailCount = 0;
    private String[] mEmails = null;

    private int mAdNumCount = 0;
    private String[] mAdNumbers = null;
    //***** Constants


    //***** Instance Methods
    public SimPhoneBookAdnRecord (int recordIndex, String alphaTag, String number, int emailCount,
                 String[] emails, int adNumCount, String[] adNumbers) {
         this.mRecordIndex = recordIndex;
         this.mAlphaTag = alphaTag;
         this.mNumber = number;
         this.mEmailCount = emailCount;
         this.mEmails = emails;
         this.mAdNumCount = adNumCount;
         this.mAdNumbers = adNumbers;
     }

    public int getRecordIndex() {
        return mRecordIndex;
    }

    public String getAlphaTag() {
        return mAlphaTag;
    }

    public String getNumber() {
        return mNumber;
    }

    public int getNumEmails() {
        return mEmailCount;
    }

    public String[] getEmails() {
        return mEmails;
    }

    public int getNumAdNumbers() {
        return mAdNumCount;
    }

    public String[] getAdNumbers() {
        return mAdNumbers;
    }

    public static String ConvertToPhoneNumber(String input) {
        return input == null ? null : input.replace( 'e', PhoneNumberUtils.WAIT )
                                            .replace( 'T', PhoneNumberUtils.PAUSE )
                                            .replace( '?', PhoneNumberUtils.WILD );
    }

    public static String ConvertToRecordNumber(String input) {
        return input == null ? null : input.replace( PhoneNumberUtils.WAIT, 'e' )
                                            .replace( PhoneNumberUtils.PAUSE, 'T' )
                                            .replace( PhoneNumberUtils.WILD, '?' );
    }

    public boolean isEmpty() {
        return TextUtils.isEmpty(mAlphaTag)
                && TextUtils.isEmpty(mNumber)
                && mEmails == null
                && mAdNumbers == null;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("SimPhoneBookAdnRecord{").append("index =").append(mRecordIndex);

        sb.append(", name = ").append(mAlphaTag == null ? "null" : mAlphaTag);
        sb.append(", number = ").append(mNumber == null ? "null" : mNumber);

        sb.append(", email count = ").append(mEmailCount);
        sb.append(", email = ").append(Arrays.toString(mEmails));

        sb.append(", ad number count = ").append(mAdNumCount);
        sb.append(", ad number = ").append(Arrays.toString(mAdNumbers));

        sb.append("}");
        return sb.toString();
    }
}
