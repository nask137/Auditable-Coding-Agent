package com.nask.agent.common;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public final class DbValues {
    private DbValues() {
    }

    public static OffsetDateTime ts(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
