/*
** Copyright 2007, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

package com.android.internal.telephony;

import com.android.internal.telephony.uicc.AdnRecord;

/**
 * Interface for applications to access the ICC phone book.
 */
interface IIccPhoneBook {

    /**
     * Loads the AdnRecords in efid and returns them as a
     * List of AdnRecords
     *
     * @param efid the EF id of a ADN-like SIM
     * @return List of AdnRecord
     */
    @UnsupportedAppUsage
    List<AdnRecord> getAdnRecordsInEf(int efid);

    /**
     * Loads the AdnRecords in efid and returns them as a
     * List of AdnRecords
     *
     * @param efid the EF id of a ADN-like SIM
     * @param subId user preferred subId
     * @return List of AdnRecord
     */
    @UnsupportedAppUsage
    List<AdnRecord> getAdnRecordsInEfForSubscriber(int subId, int efid);

    /**
     * Replace oldAdn with newAdn in ADN-like record in EF
     *
     * getAdnRecordsInEf must be called at least once before this function,
     * otherwise an error will be returned
     *
     * @param efid must be one among EF_ADN, EF_FDN, and EF_SDN
     * @param oldTag adn tag to be replaced
     * @param oldPhoneNumber adn number to be replaced
     *        Set both oldTag and oldPhoneNubmer to "" means to replace an
     *        empty record, aka, insert new record
     * @param newTag adn tag to be stored
     * @param newPhoneNumber adn number ot be stored
     *        Set both newTag and newPhoneNubmer to "" means to replace the old
     *        record with empty one, aka, delete old record
     * @param pin2 required to update EF_FDN, otherwise must be null
     * @return true for success
     */
    @UnsupportedAppUsage
    boolean updateAdnRecordsInEfBySearch(int efid,
            String oldTag, String oldPhoneNumber,
            String newTag, String newPhoneNumber,
            String pin2);



    /**
     * Replace oldAdn with newAdn in ADN-like record in EF
     *
     * getAdnRecordsInEf must be called at least once before this function,
     * otherwise an error will be returned
     *
     * @param efid must be one among EF_ADN, EF_FDN, and EF_SDN
     * @param oldTag adn tag to be replaced
     * @param oldPhoneNumber adn number to be replaced
     *        Set both oldTag and oldPhoneNubmer to "" means to replace an
     *        empty record, aka, insert new record
     * @param newTag adn tag to be stored
     * @param newPhoneNumber adn number ot be stored
     *        Set both newTag and newPhoneNubmer to "" means to replace the old
     *        record with empty one, aka, delete old record
     * @param pin2 required to update EF_FDN, otherwise must be null
     * @param subId user preferred subId
     * @return true for success
     */
    boolean updateAdnRecordsInEfBySearchForSubscriber(int subId, int efid,
            String oldTag, String oldPhoneNumber,
            String newTag, String newPhoneNumber,
            String pin2);


    /**
     * Replace oldAdn with newAdn in ADN-like record in EF
     *
     * getAdnRecordsInEf must be called at least once before this function,
     * otherwise an error will be returned
     *
     * @param efid must be one among EF_ADN, EF_FDN, and EF_SDN
     * @param oldTag adn tag to be replaced
     * @param oldPhoneNumber adn number to be replaced
     *        Set both oldTag and oldPhoneNubmer to "" means to replace an
     *        empty record, aka, insert new record
     * @param oldEmailList emails to be replaced
     * @param oldAnr anr to be replaced
     * @param oldSne sne to be replaced
     * @param oldGrp grp to be replaced
     * @param newTag adn tag to be stored
     * @param newPhoneNumber adn number ot be stored
     *        Set both newTag and newPhoneNubmer to "" means to replace the old
     *        record with empty one, aka, delete old record
     * @param newEmailList emails to be stored
     * @param newAnr anr to be stored
     * @param newAas aas to be stored
     * @param newSne sne to be stored
     * @param newGrp grp to be stored
     * @param newGas gas to be stored
     * @param pin2 required to update EF_FDN, otherwise must be null
     * @param subId user preferred subId
     * @return simIndex updated
     */
    int updateUsimAdnRecordsInEfBySearchForSubscriber(int subId, int efid, String oldTag,
                String oldPhoneNumber, in String[] oldEmailList, String oldAnr,
                String oldSne, String oldGrp,
                String newTag, String newPhoneNumber, in String[] newEmailList,
                String newAnr, String newAas, String newSne, String newGrp,
                String newGas, String pin2);


    /**
     * Update an ADN-like EF record by record index
     *
     * This is useful for iteration the whole ADN file, such as write the whole
     * phone book or erase/format the whole phonebook
     *
     * @param efid must be one among EF_ADN, EF_FDN, and EF_SDN
     * @param newTag adn tag to be stored
     * @param newPhoneNumber adn number to be stored
     *        Set both newTag and newPhoneNubmer to "" means to replace the old
     *        record with empty one, aka, delete old record
     * @param index is 1-based adn record index to be updated
     * @param pin2 required to update EF_FDN, otherwise must be null
     * @return true for success
     */
    boolean updateAdnRecordsInEfByIndex(int efid, String newTag,
            String newPhoneNumber, int index,
            String pin2);

    /**
     * Update an ADN-like EF record by record index
     *
     * This is useful for iteration the whole ADN file, such as write the whole
     * phone book or erase/format the whole phonebook
     *
     * @param efid must be one among EF_ADN, EF_FDN, and EF_SDN
     * @param newTag adn tag to be stored
     * @param newPhoneNumber adn number to be stored
     *        Set both newTag and newPhoneNubmer to "" means to replace the old
     *        record with empty one, aka, delete old record
     * @param index is 1-based adn record index to be updated
     * @param pin2 required to update EF_FDN, otherwise must be null
     * @param subId user preferred subId
     * @return true for success
     */
    boolean updateAdnRecordsInEfByIndexForSubscriber(int subId, int efid, String newTag,
            String newPhoneNumber, int index,
            String pin2);


   /**
     * Update an ADN-like EF record by record index
     *
     * This is useful for iteration the whole ADN file, such as write the whole
     * phone book or erase/format the whole phonebook
     *
     * @param efid must be one among EF_ADN, EF_FDN, and EF_SDN
     * @param newTag adn tag to be stored
     * @param newPhoneNumber adn number to be stored
     *        Set both newTag and newPhoneNubmer to "" means to replace the old
     *        record with empty one, aka, delete old record
     * @param newEmailList emails to be stored
     * @param newAnr anr to be stored
     * @param newAas aas to be stored
     * @param newSne sne tag to be stored
     * @param newGrp grp tag to be stored
     * @param newGas gas tag to be stored
     * @param index is 1-based adn record index to be updated
     * @param pin2 required to update EF_FDN, otherwise must be null
     * @param subId user preferred subId
     * @return simIndex updated
     */
    int updateUsimAdnRecordsInEfByIndexForSubscriber(int subId, int efid, String newTag,
            String newPhoneNumber, in String[] newEmailList, String newAnr,
            String newAas, String newSne, String newGrp, String newGas,
            int index, String pin2);


    /**
     * Replace oldName with newName in GAS-like record in EF
     *
     * @param oldName group name to be replaced
     * @param newName group name to be stored
     * @param subId user preferred subId
     * @return gasIndex updated
     */
    int updateUsimGroupBySearchForSubscriber(int subId, String oldName,String newName);

    /**
     * Update an GAS-like EF record by record index
     *
     * @param newName group name to be stored
     * @param groupId is 1-based gas record index to be updated
     * @param subId user preferred subId
     * @return gasIndex updated
     */
    int updateUsimGroupByIndexForSubscriber(int subId, String newName,int groupId);

    /**
     * Replace oldName with newName in AAS-like record in EF
     *
     * @param oldName aas to be replaced
     * @param newName aas to be stored
     * @param subId user preferred subId
     * @return aasIndex updated
     */
    int updateUsimAasBySearchForSubscriber(String oldName,String newName, int subId);

    /**
     * Update an AAS-like EF record by record index
     *
     * @param newName aas to be stored
     * @param aasIndex is 1-based aas record index to be updated
     * @param subId user preferred subId
     * @return aasIndex updated
     */
    int updateUsimAasByIndexForSubscriber(String newName,int aasIndex, int subId);

    /**
     * Loads the gas in efid and returns them as a list of gas
     *
     * @param subId user preferred subId
     * @return list of gas string
     */
     List<String> getGasInEfForSubscriber(int subId);

    /**
     * Loads the aas in efid and returns them as a list of aas
     *
     * @param subId user preferred subId
     * @return list of aas string
     */
    List<String> getAasInEfForSubscriber(int subId);

    /**
     * Get the max munber of records in efid
     *
     * @param efid the EF id of a ADN-like SIM
     * @return  int[3] array
     *            recordSizes[0]  is the single record length
     *            recordSizes[1]  is the total length of the EF file
     *            recordSizes[2]  is the number of records in the EF file
     */
    @UnsupportedAppUsage
    int[] getAdnRecordsSize(int efid);

    /**
     * Get inserted sim index
     *
     * @param subId user preferred subId
     * @return inserted sim index
     */
    int getInsertIndex(int subId);

    /**
     * Get the max munber of records in efid
     *
     * @param efid the EF id of a ADN-like SIM
     * @param subId user preferred subId
     * @return  int[3] array
     *            recordSizes[0]  is the single record length
     *            recordSizes[1]  is the total length of the EF file
     *            recordSizes[2]  is the number of records in the EF file
     */
    @UnsupportedAppUsage
    int[] getAdnRecordsSizeForSubscriber(int subId, int efid);

}
