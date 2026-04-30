package com.nask.agent.plan;

import java.time.Instant;
import java.util.UUID;

public record Plan(
        UUID id,
        UUID taskId,
        UUID runId,
        String status,
        Instant createdAt,
        Instant updatedAt) {
}
