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

package com.android.internal.telephony;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Tests for {@link TimedLog}
 */
@RunWith(AndroidJUnit4.class)
public class TimedLogTest {

    private static final String LOG_LINE = "test";
    private static final String LOG_LINE2 = "test2";

    /**
     * Ensure the logger doesn't keep logs above the number of max lines defined in the constructor.
     */
    @Test
    @SmallTest
    public void testDoesntExceedMaxLines() {
        TimedLog testLog = new TimedLog(5, 5, Long.MAX_VALUE);
        for (int i = 0; i < 10; i++) {
            testLog.log(LOG_LINE);
        }
        assertEquals(5, countLines(dumpToString(testLog)));
    }

    /**
     * Ensure all logs are kept below the line pruning threshold.
     */
    @Test
    @SmallTest
    public void testKeepLogsBelowLineThresholdBelowPeriodThreshold() {
        // Set the threshold to 11 lines, make sure ten lines are logged.
        TimedLog testLog = new TimedLog(15, 11, Long.MAX_VALUE);
        for (int i = 0; i < 10; i++) {
            testLog.log(LOG_LINE);
        }
        assertEquals(10, countLines(dumpToString(testLog)));
    }

    /**
     * Ensure all logs are kept below the line pruning threshold, even if log is older than period
     * threshold.
     */
    @Test
    @SmallTest
    public void testKeepLogsBelowLineThresholdAbovePeriodThreshold() {
        // Set the threshold to 11 lines, make sure ten lines are logged.
        TimedLog testLog = new TimedLog(15, 11, Long.MIN_VALUE);
        for (int i = 0; i < 10; i++) {
            testLog.log(LOG_LINE);
        }
        assertEquals(10, countLines(dumpToString(testLog)));
    }

    /**
     * Ensure all entries are kept that are newer than the time period threshold, even if over the
     * line threshold.
     */
    @Test
    @SmallTest
    public void testKeepLogsAboveLineThresholdBelowPeriodThreshold() {
        TimedLog testLog = new TimedLog(15, 10, Long.MAX_VALUE);
        for (int i = 0; i < 10; i++) {
            testLog.log(LOG_LINE);
        }
        assertEquals(10, countLines(dumpToString(testLog)));
    }

    /**
     * Logs entries that are above the line pruning threshold and older than the period threshold
     * should be pruned.
     */
    @Test
    @SmallTest
    public void testKeepLogsAboveLineThresholdAbovePeriodThreshold() {
        TimedLog testLog = new TimedLog(15, 6, Long.MIN_VALUE);
        for (int i = 0; i < 10; i++) {
            testLog.log(LOG_LINE);
        }
        assertEquals(5, countLines(dumpToString(testLog)));
    }

    /**
     * Only keep the logs that are within the logging period threshold if the number of entries
     * grows above the line threshold.
     */
    @Test
    @SmallTest
    public void testKeepOnlyLogsWithinPeriodThresholdWhenAboveLineThreshold() throws Exception {
        TimedLog testLog = new TimedLog(20, 6, 50);
        for (int i = 0; i < 10; i++) {
            testLog.log(LOG_LINE);
        }
        Thread.sleep(100);
        for (int i = 0; i < 5; i++) {
            testLog.log(LOG_LINE2);
        }
        String logMessage = dumpToString(testLog);
        // Split by log lines
        String[] logMessageSegments = logMessage.split("\\r?\\n");
        // Only logs with LOG_LINE2 should be in the dump.
        for (String seg : logMessageSegments) {
            assertTrue("segment (" + seg + ") did not contain " + LOG_LINE2,
                    seg.contains(LOG_LINE2));
        }
        assertEquals(5, countLines(logMessage));
    }

    /**
     * Ensure the Log message consumer works properly and triggers after every log(), even if the
     * message is not stored in the TimedLog.
     */
    @Test
    @SmallTest
    public void testMessageLoggerConsumer() {
        int[] logMessageCounter = new int[1];
        TimedLog testLog = new TimedLog(15, 6, Long.MIN_VALUE);
        testLog.setLogger((message) -> {
            assertEquals(LOG_LINE, message);
            logMessageCounter[0]++;
        });
        for (int i = 0; i < 10; i++) {
            testLog.log(LOG_LINE);
        }
        assertEquals(10, logMessageCounter[0]);
        assertEquals(5, countLines(dumpToString(testLog)));
    }

    private String dumpToString(TimedLog log) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        log.dump(pw);
        return sw.toString();
    }

    private int countLines(String s) {
        int numLines = 0;
        for (char c : s.toCharArray()) {
            if (c == '\n') numLines++;
        }
        return numLines;
    }
}
