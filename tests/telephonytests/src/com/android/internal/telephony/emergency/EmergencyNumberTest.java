/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.internal.telephony.emergency;

import android.telephony.emergency.EmergencyNumber;

import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EmergencyNumberTest extends TestCase {
    public void testEmergencyNumberGeneric() throws Exception {
        EmergencyNumber number = new EmergencyNumber(
                "911",
                "310",
                "us",
                // EMERGENCY_SERVICE_CATEGORY_GENERIC
                0);
        assertEquals(number.getNumber(), "911");
        assertEquals(number.getMcc(), "310");
        assertEquals(number.getCountryIso(), "us");
        assertTrue(number.isGenericService());
        assertTrue(number.inServiceCategory(EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_GENERIC));
        assertFalse(number.inServiceCategory(EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_POLICE));
        assertFalse(number.inServiceCategory(EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_AMBULANCE));
        assertFalse(number.inServiceCategory(EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_BRIGADE));
        assertFalse(number.inServiceCategory(
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_MARINE_GUARD));
        assertFalse(number.inServiceCategory(
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_MOUNTAIN_RESCUE));
        assertFalse(number.inServiceCategory(EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_MIEC));
        assertFalse(number.inServiceCategory(EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_AIEC));
        assertEquals(0, number.getEmergencyServiceCategoryBitmask());

        List<Integer> categories = number.getEmergencyServiceCategories();
        assertEquals(1, categories.size());
        assertEquals(EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_GENERIC, (int) categories.get(0));
    }

    public void testEmergencyNumberSpecificService() throws Exception {
        EmergencyNumber number = new EmergencyNumber(
                "911",
                "310",
                "us",
                // EMERGENCY_SERVICE_CATEGORY_MARINE_GUARD
                8);
        assertEquals(number.getNumber(), "911");
        assertEquals(number.getMcc(), "310");
        assertEquals(number.getCountryIso(), "us");
        assertFalse(number.isGenericService());
        assertFalse(number.inServiceCategory(EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_GENERIC));
        assertFalse(number.inServiceCategory(EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_POLICE));
        assertFalse(number.inServiceCategory(EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_AMBULANCE));
        assertFalse(number.inServiceCategory(EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_BRIGADE));
        assertTrue(number.inServiceCategory(
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_MARINE_GUARD));
        assertFalse(number.inServiceCategory(
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_MOUNTAIN_RESCUE));
        assertFalse(number.inServiceCategory(EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_MIEC));
        assertFalse(number.inServiceCategory(EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_AIEC));
        assertEquals(8, number.getEmergencyServiceCategoryBitmask());

        List<Integer> categories = number.getEmergencyServiceCategories();
        assertEquals(1, categories.size());
        assertEquals(EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_MARINE_GUARD,
                (int) categories.get(0));
    }

    public void testEmergencyNumberMultipleServices() throws Exception {
        EmergencyNumber number = new EmergencyNumber(
                "110",
                "440",
                "jp",
                // EMERGENCY_SERVICE_CATEGORY_POLICE
                // EMERGENCY_SERVICE_CATEGORY_AMBULANCE
                // EMERGENCY_SERVICE_CATEGORY_MIEC
                35);
        assertEquals(number.getNumber(), "110");
        assertEquals(number.getMcc(), "440");
        assertEquals(number.getCountryIso(), "jp");
        assertFalse(number.isGenericService());
        assertFalse(number.inServiceCategory(EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_GENERIC));
        assertTrue(number.inServiceCategory(EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_POLICE));
        assertTrue(number.inServiceCategory(EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_AMBULANCE));
        assertFalse(number.inServiceCategory(EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_BRIGADE));
        assertFalse(number.inServiceCategory(
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_MARINE_GUARD));
        assertFalse(number.inServiceCategory(
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_MOUNTAIN_RESCUE));
        assertTrue(number.inServiceCategory(EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_MIEC));
        assertFalse(number.inServiceCategory(EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_AIEC));
        assertEquals(35, number.getEmergencyServiceCategoryBitmask());

        List<Integer> categories = number.getEmergencyServiceCategories();
        assertEquals(3, categories.size());
        Collections.sort(categories);
        List<Integer> categoriesToVerify = new ArrayList<Integer>();
        categoriesToVerify.add(EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_POLICE);
        categoriesToVerify.add(EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_AMBULANCE);
        categoriesToVerify.add(EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_MIEC);
        Collections.sort(categoriesToVerify);
        assertTrue(categoriesToVerify.equals(categories));
    }
}
