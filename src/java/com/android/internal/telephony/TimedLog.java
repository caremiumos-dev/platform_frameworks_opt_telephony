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

import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.function.Consumer;

/**
 * A Logging class that prioritizes capturing logs for a certain time period over capturing only
 * a number of max log lines.
 * @hide
 */
public final class TimedLog {

    protected static class LogMessage {
        public final String message;
        public final long entryTimeMs;

        public LogMessage(String message, long entryTimeMs) {
            this.message = message;
            this.entryTimeMs = entryTimeMs;
        }
    }

    private final Deque<LogMessage> mLog;
    private final int mMaxLines;
    // number of lines above which entries older than mStalePeriodThresholdMs will be pruned.
    private final int mLinePruningThreshold;
    // time period threshold in milliseconds. Entries older than the period will be pruned if
    // we are above mLinePruningThreshold.
    private final long mStalePeriodThresholdMs;
    // An optional message logger
    private Consumer<String> mMessageLogger;


    /**
     * Create a LocalLog that will capture logs for a certain time period, rather than number of
     * lines.
     * <p>
     * Instead of removing old entries when we are above the absolute maximum, define a minimum time
     * period that the LocalLog should try to capture logs for. This LocalLog will keep entries
     * within the specified period as long as it does not exceed the absolute maximum number of
     * entries allowed. If there entries that are older than the time period specified, then do not
     * start pruning them until the number of entries hits the line pruning threshold. This removes
     * the limit that logs will ONLY be captured for the time period specified and allows the user
     * to set a reasonable number of older logs to be captured as well.
     *
     * @param maxLines The absolute maximum number of lines that can be captured. Any logs that
     *         are added that cause the size of this LocalLog to exceed this maximum will cause the
     *         oldest logs to be removed.
     * @param linePruningThreshold A threshold defined by a number of lines that will cause this
     *         LocalLog to start pruning entries older than the stale time period threshold only
     *         while over this number of lines.
     * @param stalePeriodThresholdMs A period threshold in milliseconds that will cause this
     *         LocalLog to start pruning entries older than this period if the number of entries is
     *         above the stale line threshold.
     */
    public TimedLog(int maxLines, int linePruningThreshold, long stalePeriodThresholdMs) {
        mLinePruningThreshold = Math.max(0, linePruningThreshold);
        mMaxLines = maxLines > mLinePruningThreshold ? maxLines : mLinePruningThreshold;
        mLog = new ArrayDeque<>(mMaxLines);
        mStalePeriodThresholdMs = Math.max(0, stalePeriodThresholdMs);
    }

    /**
     * Set a {@link Consumer} that will be called whenever a new message is logged by this instance.
     */
    public synchronized void setLogger(Consumer<String> messageLogger) {
        mMessageLogger = messageLogger;
    }

    /**
     * Logs a message to TimedLog, which may be pruned if the number of entries is large enough to
     * trigger pruning and this entry moves outside of the stale period threshold.
     *
     * @param msg the message to log, which will also be timestamped
     */
    public synchronized void log(String msg) {
        if (mMessageLogger != null) mMessageLogger.accept(msg);
        if (mMaxLines <= 0) {
            return;
        }
        append(String.format("%s - %s", LocalDateTime.now(), msg));
    }

    private void append(String logLine) {
        mLog.push(new LogMessage(logLine, System.currentTimeMillis()));
        pruneStaleEntries();
    }

    // Remove entries that are older than the stale period threshold if the number of entries is
    // above the lune pruning threshold.
    private void pruneStaleEntries() {
        if (mLog.size() < mLinePruningThreshold) {
            return;
        }

        while (mLog.size() > mMaxLines) {
            mLog.pop();
        }

        long currentTimeMillis = System.currentTimeMillis();
        // move from oldest entry to newest entry
        Iterator<LogMessage> itr = mLog.descendingIterator();
        while (itr.hasNext()) {
            LogMessage m = itr.next();
            if (mLog.size() >= mLinePruningThreshold
                    && (currentTimeMillis - m.entryTimeMs) >= mStalePeriodThresholdMs) {
                itr.remove();
            } else {
                break;
            }
        }
    }

    /**
     * Write logs from newest to oldest.
     * @param pw The PrintWriter that will be used to write the log entries.
     */
    public synchronized void dump(PrintWriter pw) {
        Iterator<LogMessage> itr = mLog.iterator();
        while (itr.hasNext()) {
            pw.println(itr.next().message);
        }
    }
}
