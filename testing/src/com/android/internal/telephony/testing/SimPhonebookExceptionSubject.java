/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.internal.telephony.testing;

import static com.google.common.truth.Truth.assertAbout;

import android.content.ContentValues;
import android.provider.SimPhonebookContract;
import android.provider.SimPhonebookContract.SimPhonebookException;

import androidx.annotation.NonNull;

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;

/** Truth subject for making assertions about {@link SimPhonebookException}s. */
public final class SimPhonebookExceptionSubject extends Subject {
    @NonNull
    private final SimPhonebookException mActual;

    private SimPhonebookExceptionSubject(
            FailureMetadata metadata, @NonNull SimPhonebookException actual) {
        super(metadata, actual);
        this.mActual = actual;
    }

    /** Returns the factory for this subject. */
    public static Factory<SimPhonebookExceptionSubject, SimPhonebookException> exceptions() {
        return SimPhonebookExceptionSubject::new;
    }

    /** Starts an assertion. */
    public static SimPhonebookExceptionSubject assertThat(
            SimPhonebookException exception) {
        return assertAbout(exceptions()).that(exception);
    }

    /** Asserts that the subject has the specified error code. */
    public void hasErrorCode(int errorCode) {
        check("getErrorCode()")
                .that(SimPhonebookException.getErrorName(mActual.getErrorCode()))
                .isEqualTo(SimPhonebookException.getErrorName(errorCode));
    }

    /** Asserts that {@link SimPhonebookException#getSanitizedValues()} has the specified values. */
    public void hasSanitizedValues(String name, String phoneNumber) {
        ContentValues values = new ContentValues();
        values.put(SimPhonebookContract.SimRecords.NAME, name);
        values.put(SimPhonebookContract.SimRecords.PHONE_NUMBER, phoneNumber);
        hasSanitizedValues(values);
    }

    /** Asserts that {@link SimPhonebookException#getSanitizedValues()} has the specified values. */
    public void hasSanitizedValues(ContentValues values) {
        check("getSanitizedValues()").that(mActual.getSanitizedValues()).isEqualTo(values);
    }
}
