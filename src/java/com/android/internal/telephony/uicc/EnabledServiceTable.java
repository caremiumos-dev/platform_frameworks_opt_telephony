/*
 * Copyright (C) 2011 The Android Open Source Project
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


/**
 * Wrapper class for the Enabled Service Table EF.
 * See 3GPP TS 31.102 Release 10 section 4.2.27
 */
public final class EnabledServiceTable extends IccServiceTable {
    public enum EnabledService {
        FDN,
        BDN,
        APN_CONTROL_LIST
    }

    public EnabledServiceTable(byte[] table) {
        super(table);
    }

    public boolean isAvailable(EnabledService service) {
        return super.isAvailable(service.ordinal());
    }

    @Override
    protected String getTag() {
        return "EnabledServiceTable";
    }

    @Override
    protected Object[] getValues() {
        return EnabledService.values();
    }
}
