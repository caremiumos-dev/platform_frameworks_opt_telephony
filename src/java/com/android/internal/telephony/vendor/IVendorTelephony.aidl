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

package com.android.internal.telephony.vendor;

/**
 * Interface used to interact with the telephony framework for
 * Telephony value adds.
 * {@hide}
 */
interface IVendorTelephony {

    /**
     * Returns the current SIM Manual provision status.
     * @param slotId user preferred slotId.
     * @return Card provision status as integer, below are
     * possible return values.
     *   '0' - returned if Uicc Card is not provisioned.
     *   '1' - returned if Uicc Card provisioned.
     *  '-1'-  returned if there is an error @ below layers OR
     *         if framework does not received info from Modem yet.
     *  '-2'  returned when SIM card is not present in slot.
     * Requires Permission: android.Manifest.permission.READ_PHONE_STATE
     */
    int getCurrentUiccCardProvisioningStatus(int slotId);

    /**
    * Check if Sms Prompt is Enabled or Not.
    * @return
    *        true - Sms Prompt is Enabled
    *        false - Sms prompt is Disabled
    * Requires Permission: android.Manifest.permission.READ_PHONE_STATE
    */
    boolean isSmsPromptEnabled();

    /**
    * Enable/Disable Sms prompt option.
    * @param - enabled
    *        true - to enable Sms prompt
    *        false - to disable Sms prompt
    * Requires Permission: android.Manifest.permission.MODIFY_PHONE_STATE
    */
    void setSmsPromptEnabled(boolean enabled);

    /**
    * Get value assigned to vendor property
    * @param - property name
    * @param - default value of property
    * @return - integer value assigned
    */
    int getPropertyValueInt(String property, int def);
}
