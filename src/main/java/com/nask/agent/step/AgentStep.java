package com.nask.agent.step;

import java.time.Instant;
import java.util.UUID;

/**
 * Timeline entry for a run phase or plan-item execution.
 */
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
