package com.nask.agent.runtime;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Persisted request for user guidance when runtime recovery needs input.
 */
public record UserInputRequestRecord(
        UUID id,
        UUID taskId,
        UUID runId,
        UUID stepId,
        UUID planItemId,
        String status,
        String question,
        String contextSummary,
        List<String> suggestedOptions,
        String answer,
        Instant createdAt,
        Instant answeredAt) {
}
