package com.nask.agent.step;

import java.time.Instant;
import java.util.UUID;

public record AgentStep(
        UUID id,
        UUID runId,
        UUID planItemId,
        String stepType,
        String status,
        String inputSummary,
        String outputSummary,
        Instant startedAt,
        Instant finishedAt) {
}
