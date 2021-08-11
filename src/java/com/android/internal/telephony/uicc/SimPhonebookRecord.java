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

package com.android.internal.telephony.uicc;

import android.hardware.radio.V1_6.PhonebookRecordInfo;
<<<<<<< HEAD   (59fd0d Snap for 7629706 from 5f8529e26eac1bffe92092c6090d64e803ae09)
=======
import android.telephony.PhoneNumberUtils;
>>>>>>> CHANGE (b80ec1 Fix CtsSimPhonebookProviderTestCases)
import android.text.TextUtils;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;

import com.android.internal.telephony.util.ArrayUtils;

import java.util.Arrays;
<<<<<<< HEAD   (59fd0d Snap for 7629706 from 5f8529e26eac1bffe92092c6090d64e803ae09)
import java.util.List;
=======
import java.util.ArrayList;
>>>>>>> CHANGE (b80ec1 Fix CtsSimPhonebookProviderTestCases)

/**
 * Represents a Phonebook entry from the SIM.
 *
 * {@hide}
 */
public class SimPhonebookRecord {
    // Instance variables
    private int mRecordId = 0;
    private String mAlphaTag;
    private String mNumber;
    private String[] mEmails;
    private String[] mAdditionalNumbers;

    // Instance methods
<<<<<<< HEAD   (59fd0d Snap for 7629706 from 5f8529e26eac1bffe92092c6090d64e803ae09)
    public SimPhonebookRecord (int recordIndex, String alphaTag, String number,
=======
    public SimPhonebookRecord (int recordId, String alphaTag, String number,
>>>>>>> CHANGE (b80ec1 Fix CtsSimPhonebookProviderTestCases)
               String[] emails, String[] adNumbers) {
        mRecordId = recordId;
        mAlphaTag = alphaTag;
        mNumber = convertRecordFormatToNumber(number);
        mEmails = emails;
        if (adNumbers != null) {
            mAdditionalNumbers = new String[adNumbers.length];
            for (int i = 0 ; i < adNumbers.length; i++) {
                mAdditionalNumbers[i] =
                        convertRecordFormatToNumber(adNumbers[i]);
            }
        }
    }

    public SimPhonebookRecord(PhonebookRecordInfo recInfo) {
<<<<<<< HEAD   (59fd0d Snap for 7629706 from 5f8529e26eac1bffe92092c6090d64e803ae09)
        mRecordIndex = recInfo.recordId;
        mAlphaTag = recInfo.name;
        mNumber = recInfo.number;
        mEmails = recInfo.emails == null ? null
                : recInfo.emails.toArray(new String[recInfo.emails.size()]);
        mAdditionalNumbers = recInfo.additionalNumbers == null ? null
                : recInfo.additionalNumbers.toArray(
                        new String[recInfo.additionalNumbers.size()]);
    }

    public SimPhonebookRecord() {}

    public PhonebookRecordInfo toPhonebookRecordInfo() {
        PhonebookRecordInfo pbRecordInfo = new PhonebookRecordInfo();
        pbRecordInfo.recordId = mRecordIndex;
        pbRecordInfo.name = convertNullToEmptyString(mAlphaTag);
        pbRecordInfo.number = convertNullToEmptyString(convertNumberToRecordFormat(mNumber));
        if (mEmails != null) {
            for (String email : mEmails) {
                pbRecordInfo.emails.add(email);
            }
        }
        if (mAdditionalNumbers != null) {
            for (String addNum : mAdditionalNumbers) {
                pbRecordInfo.additionalNumbers.add(convertNumberToRecordFormat(addNum));
            }
        }
        return pbRecordInfo;
    }
    public int getRecordIndex() {
        return mRecordIndex;
=======
        if (recInfo != null) {
            mRecordId = recInfo.recordId;
            mAlphaTag = recInfo.name;
            mNumber = recInfo.number;
            mEmails = recInfo.emails == null ? null
                    : recInfo.emails.toArray(new String[recInfo.emails.size()]);
            mAdditionalNumbers = recInfo.additionalNumbers == null ? null
                    : recInfo.additionalNumbers.toArray(
                            new String[recInfo.additionalNumbers.size()]);
        }
    }

    public SimPhonebookRecord() {}

    public PhonebookRecordInfo toPhonebookRecordInfo() {
        PhonebookRecordInfo pbRecordInfo = new PhonebookRecordInfo();
        pbRecordInfo.recordId = mRecordId;
        pbRecordInfo.name = convertNullToEmptyString(mAlphaTag);
        pbRecordInfo.number = convertNullToEmptyString(convertNumberToRecordFormat(mNumber));
        if (mEmails != null) {
            pbRecordInfo.emails = new ArrayList<>(Arrays.asList(mEmails));
        }
        if (mAdditionalNumbers != null) {
            for (String addNum : mAdditionalNumbers) {
                pbRecordInfo.additionalNumbers.add(convertNumberToRecordFormat(addNum));
            }
        }
        return pbRecordInfo;
    }

    public android.hardware.radio.sim.PhonebookRecordInfo toPhonebookRecordInfoAidl() {
        android.hardware.radio.sim.PhonebookRecordInfo pbRecordInfo =
                new android.hardware.radio.sim.PhonebookRecordInfo();
        pbRecordInfo.recordId = mRecordId;
        pbRecordInfo.name = convertNullToEmptyString(mAlphaTag);
        pbRecordInfo.number = convertNullToEmptyString(convertNumberToRecordFormat(mNumber));
        if (mEmails != null) {
            pbRecordInfo.emails = mEmails;
        }
        if (mAdditionalNumbers != null) {
            String[] additionalNumbers = new String[mAdditionalNumbers.length];
            for (int i = 0; i < additionalNumbers.length; i++) {
                additionalNumbers[i] = convertNumberToRecordFormat(mAdditionalNumbers[i]);
            }
            pbRecordInfo.additionalNumbers = additionalNumbers;
        }
        return pbRecordInfo;
    }

    public int getRecordId() {
        return mRecordId;
>>>>>>> CHANGE (b80ec1 Fix CtsSimPhonebookProviderTestCases)
    }

    public String getAlphaTag() {
        return mAlphaTag;
    }

    public String getNumber() {
        return mNumber;
    }

    public String[] getEmails() {
        return mEmails;
    }

    public String[] getAdditionalNumbers() {
        return mAdditionalNumbers;
    }

    /** Convert null to an empty String */
    private String convertNullToEmptyString(String str) {
        return str != null ? str : "";
    }

    /**
<<<<<<< HEAD   (59fd0d Snap for 7629706 from 5f8529e26eac1bffe92092c6090d64e803ae09)
     * convert the GSM pause/wild/wait character to the phone number in the SIM pb record format
     */
    private static String convertNumberToRecordFormat(String input) {
        return input == null ? null : input.replace(PhoneNumberUtils.WAIT, 'e')
                .replace(PhoneNumberUtils.PAUSE, 'T')
                .replace(PhoneNumberUtils.WILD, '?');
    }

    private static String convertNullToEmptyString(String string) {
        return string != null ? string : "";
    }
=======
     * Convert the SIM PhonebookRecordInfo number to the GSM pause/wild/wait number
     * @param input the SIM PhonebookRecordInfo number
     * @return The converted GSM pause/wild/wait number
     */
    private String convertRecordFormatToNumber(String input) {
        return input == null ? null : input.replace('e', PhoneNumberUtils.WAIT)
                .replace('T', PhoneNumberUtils.PAUSE)
                .replace('?', PhoneNumberUtils.WILD);
    }

    /**
     * Convert the GSM pause/wild/wait characters to the phone number in the SIM PhonebookRecordInfo
     * number format
     * @param input GSM pause/wild/wait character
     * @return The converted PhonebookRecordInfo number
     */
    private String convertNumberToRecordFormat(String input) {
        return input == null ? null : input.replace(PhoneNumberUtils.WAIT, 'e')
                .replace(PhoneNumberUtils.PAUSE, 'T')
                .replace(PhoneNumberUtils.WILD, '?');
    }

>>>>>>> CHANGE (b80ec1 Fix CtsSimPhonebookProviderTestCases)

    public boolean isEmpty() {
        return TextUtils.isEmpty(mAlphaTag)
                && TextUtils.isEmpty(mNumber)
                && ArrayUtils.isEmpty(mEmails)
                && ArrayUtils.isEmpty(mAdditionalNumbers);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("SimPhoneBookRecord{").append("ID =")
                .append(mRecordId).append(", name = ")
                .append(mAlphaTag == null ? "null" : mAlphaTag)
                .append(", number = ").append(mNumber == null ? "null" : mNumber)
                .append(", email count = ").append(mEmails == null ? 0 : mEmails.length)
                .append(", email = ").append(Arrays.toString(mEmails))
                .append(", ad number count = ")
                .append(mAdditionalNumbers == null ? 0 : mAdditionalNumbers.length)
                .append(", ad number = ").append(Arrays.toString(mAdditionalNumbers))
                .append("}");
        return sb.toString();
    }

    public final static class Builder {
        private int mRecordId = 0;
        private String mAlphaTag = null;
        private String mNumber = null;
        private String[] mEmails;
        private String[] mAdditionalNumbers;

        public SimPhonebookRecord build() {
            SimPhonebookRecord record = new SimPhonebookRecord();
            record.mAlphaTag = mAlphaTag;
<<<<<<< HEAD   (59fd0d Snap for 7629706 from 5f8529e26eac1bffe92092c6090d64e803ae09)
            record.mRecordIndex = mRecordIndex;
=======
            record.mRecordId = mRecordId;
>>>>>>> CHANGE (b80ec1 Fix CtsSimPhonebookProviderTestCases)
            record.mNumber = mNumber;
            if (mEmails != null) {
                record.mEmails = mEmails;
            }
            if (mAdditionalNumbers != null) {
                record.mAdditionalNumbers = mAdditionalNumbers;
            }
            return record;
        }

        public Builder setRecordId(int recordId) {
            mRecordId = recordId;
            return this;
        }

        public Builder setAlphaTag(String tag) {
            mAlphaTag = tag;
            return this;
        }

        public Builder setNumber(String number) {
            mNumber = number;
            return this;
        }

        public Builder setEmails(String[] emails) {
            mEmails = emails;
            return this;
        }

        public Builder setAdditionalNumbers(String[] anrs) {
            mAdditionalNumbers = anrs;
            return this;
        }
    }
}
