package com.nask.agent.common;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Small conversion helpers for values written through JDBC.
 */
public final class DbValues {
    private DbValues() {
    }

    /**
     * Converts an {@link Instant} to a UTC {@link OffsetDateTime} accepted by
     * PostgreSQL timestamp columns.
     */
    public static OffsetDateTime ts(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
