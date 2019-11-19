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

package com.android.internal.telephony.uicc;

import android.text.TextUtils;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;

import java.util.Arrays;
import java.util.List;

/**
 *
 * Used to store SIM phonebook records.
 *
 * {@hide}
 *
 */
public class SimPhoneBookRecord {
    static final String LOG_TAG = "SimPhoneBookRecord";

    //***** Instance Variables
    int mRecordIndex = 0;

    String mAlphaTag = null;
    String mNumber = null;

    int mEmailCount = 0;
    String[] mEmails = null;

    int mAdNumCount = 0;
    String[] mAdditionalNumbers = null;

    //***** Instance Methods
    /*public SimPhoneBookRecord (int recordIndex, String alphaTag, String number, int emailCount,
                 List<String> emails, int adNumCount, List<String> adNumbers) {
         this.mRecordIndex = recordIndex;
         this.mAlphaTag = alphaTag;
         this.mNumber = number;
         this.mEmailCount = emailCount;
         this.mEmails = emails;
         this.mAdNumCount = adNumCount;
         this.mAdditionalNumbers = adNumbers;
     }*/

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

    public String[] getAdditionalNumbers() {
        return mAdditionalNumbers;
    }

    //covert phone number in the SIM phonebook record format to GSM pause/wild/wait character
    public static String convertRecordFormatToNumber(String input) {
        return input == null ? null : input.replace( 'e', PhoneNumberUtils.WAIT )
                                            .replace( 'T', PhoneNumberUtils.PAUSE )
                                            .replace( '?', PhoneNumberUtils.WILD );
    }

    //covert the GSM pause/wild/wait character to the phone number in the SIM phonebook record format
    public static String convertNumberToRecordFormat(String input) {
        return input == null ? null : input.replace( PhoneNumberUtils.WAIT, 'e' )
                                            .replace( PhoneNumberUtils.PAUSE, 'T' )
                                            .replace( PhoneNumberUtils.WILD, '?' );
    }

    public boolean isEmpty() {
        return TextUtils.isEmpty(mAlphaTag)
                && TextUtils.isEmpty(mNumber)
                && mEmails == null
                && mAdditionalNumbers == null;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("SimPhoneBookRecord{").append("index =").append(mRecordIndex);

        sb.append(", name = ").append(mAlphaTag == null ? "null" : mAlphaTag);
        sb.append(", number = ").append(mNumber == null ? "null" : mNumber);

        sb.append(", email count = ").append(mEmailCount);
        sb.append(", email = ").append(Arrays.toString(mEmails));

        sb.append(", ad number count = ").append(mAdNumCount);
        sb.append(", ad number = ").append(Arrays.toString(mAdditionalNumbers));

        sb.append("}");
        return sb.toString();
    }
}
