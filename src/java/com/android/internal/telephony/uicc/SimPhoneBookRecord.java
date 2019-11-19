package com.android.internal.telephony.uicc;

import android.text.TextUtils;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;

import com.android.internal.telephony.util.ArrayUtils;

import java.util.Arrays;
import java.util.List;

/**
 * Used to store SIM phonebook records.
 *
 * {@hide}
 */
public class SimPhoneBookRecord {
    static final String LOG_TAG = "SimPhoneBookRecord";

    // Instance Variables
    int mRecordIndex = 0;
    String mAlphaTag = null;
    String mNumber = null;
    String[] mEmails = new String[]{};
    String[] mAdditionalNumbers = new String[]{};

    // Instance Methods
    public SimPhoneBookRecord (int recordIndex, String alphaTag, String number,
                 String[] emails, String[] adNumbers) {
         this.mRecordIndex = recordIndex;
         this.mAlphaTag = alphaTag;
         this.mNumber = convertRecordFormatToNumber(number);
         this.mEmails = emails;
         for (int i = 0 ; i < adNumbers.length; i++) {
            this.mAdditionalNumbers[i] =
                convertRecordFormatToNumber(adNumbers[i]);
         }
     }

    public SimPhoneBookRecord() {}

    public int getRecordIndex() {
        return mRecordIndex;
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

    /**
     * convert phone number in the SIM phonebook record format to GSM pause/wild/wait character
     */
    public static String convertRecordFormatToNumber(String input) {
        return input == null ? null : input.replace( 'e', PhoneNumberUtils.WAIT )
                                            .replace( 'T', PhoneNumberUtils.PAUSE )
                                            .replace( '?', PhoneNumberUtils.WILD );
    }

    /**
     * convert the GSM pause/wild/wait character to the phone number in the SIM phonebook record format
     */
    public static String convertNumberToRecordFormat(String input) {
        return input == null ? null : input.replace(PhoneNumberUtils.WAIT, 'e')
                                            .replace(PhoneNumberUtils.PAUSE, 'T')
                                            .replace(PhoneNumberUtils.WILD, '?');
    }

    public boolean isEmpty() {
        return TextUtils.isEmpty(mAlphaTag)
                && TextUtils.isEmpty(mNumber)
                && ArrayUtils.isEmpty(mEmails)
                && ArrayUtils.isEmpty(mAdditionalNumbers);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("SimPhoneBookRecord{").append("index =").append(mRecordIndex);
        sb.append(", name = ").append(mAlphaTag == null ? "null" : mAlphaTag);
        sb.append(", number = ").append(mNumber == null ? "null" : mNumber);
        sb.append(", email count = ").append(mEmails.length);
        sb.append(", email = ").append(Arrays.toString(mEmails));
        sb.append(", ad number count = ").append(mAdditionalNumbers.length);
        sb.append(", ad number = ").append(Arrays.toString(mAdditionalNumbers));
        sb.append("}");
        return sb.toString();
    }
}
