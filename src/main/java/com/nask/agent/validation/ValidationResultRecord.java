package com.nask.agent.validation;

import java.time.Instant;
import java.util.UUID;

public record ValidationResultRecord(
        UUID id,
        UUID taskId,
        UUID runId,
        UUID stepId,
        UUID commandId,
        String validationType,
        boolean success,
        String summary,
        Instant createdAt) {
}
