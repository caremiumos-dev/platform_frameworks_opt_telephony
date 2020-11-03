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
import android.database.Cursor;
import android.database.DatabaseUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.IntegerSubject;
import com.google.common.truth.StringSubject;
import com.google.common.truth.Subject;

/** Truth subject for assertions on Cursors. */
public final class CursorSubject extends Subject {

    private final Cursor actual;

    public static Factory<CursorSubject, Cursor> cursors() {
        return CursorSubject::new;
    }

    public static CursorSubject assertThat(Cursor cursor) {
        return assertAbout(cursors()).that(cursor);
    }

    private CursorSubject(FailureMetadata metadata, @Nullable Cursor actual) {
        super(metadata, actual);
        this.actual = actual;
    }

    public void hasCount(int count) {
        check("getCount()").that(actual.getCount()).isEqualTo(count);
    }

    public void hasColumnNames(String... columnNames) {
        check("getColumnNames()").that(actual.getColumnNames()).asList()
                .containsExactlyElementsIn(columnNames).inOrder();
    }

    public CursorSubject atRow(int position) {
        check("moveToPosition").that(actual.moveToPosition(position)).isTrue();
        return this;
    }

    public CursorSubject hasRowValues(Object... values) {
        check("getColumnCount()").that(actual.getColumnCount()).isEqualTo(values.length);
        ContentValues expectedValues = new ContentValues();
        for (int i = 0; i < values.length; i++) {
            expectedValues.putObject(actual.getColumnName(i), values[i].toString());
        }

        ContentValues actualValues = new ContentValues();
        DatabaseUtils.cursorRowToContentValues(actual, actualValues);

        check("Row: %s", actual.getPosition()).that(actualValues).isEqualTo(expectedValues);
        return this;
    }

    public CursorSubject hasRowValue(String columnName, Object value) {
        int index = actual.getColumnIndex(columnName);
        check("getColumnIndex()").that(index).isNotEqualTo(-1);

        check("Row[%s]: %s", columnName, index).that(actual.getString(index))
                .isEqualTo(value.toString());
        return this;
    }

    public IntegerSubject intField(String columnName) {
        int index = actual.getColumnIndex(columnName);
        check("getColumnIndex()").that(index).isNotEqualTo(-1);
        check("getType()").that(actual.getType(index)).isEqualTo(Cursor.FIELD_TYPE_INTEGER);

        return check("getInt()").that(actual.getInt(index));
    }

    public StringSubject stringField(String columnName) {
        int index = actual.getColumnIndex(columnName);
        check("getColumnIndex()").that(index).isNotEqualTo(-1);
        check("getType()").that(actual.getType(index)).isEqualTo(Cursor.FIELD_TYPE_STRING);

        return check("getString()").that(actual.getString(index));
    }

    public void hasSameData(@NonNull Cursor expected) {
        // If it's expected to be null just use a standard subject.
        check("Not null").that(actual).isNotNull();

        hasColumnNames(expected.getColumnNames());
        hasCount(expected.getCount());

        actual.moveToPosition(-1);
        expected.moveToPosition(-1);

        ContentValues actualValues = new ContentValues();
        ContentValues expectedValues = new ContentValues();
        while (actual.moveToNext() && expected.moveToNext()) {
            DatabaseUtils.cursorRowToContentValues(actual, actualValues);
            DatabaseUtils.cursorRowToContentValues(expected, expectedValues);

            check("row: %d", actual.getPosition()).that(actualValues).isEqualTo(
                    expectedValues);
        }
    }
}
