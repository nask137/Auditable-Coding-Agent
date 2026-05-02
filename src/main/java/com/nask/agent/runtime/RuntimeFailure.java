package com.nask.agent.runtime;

import java.time.Instant;
import java.util.UUID;

/**
 * Persisted structured failure used by recovery policy and API readers.
 */
public record RuntimeFailure(
        UUID id,
        UUID taskId,
        UUID runId,
        UUID stepId,
        UUID planItemId,
        String failureType,
        boolean recoverable,
        String strategy,
        String summary,
        String details,
        UUID relatedEventId,
        UUID relatedToolCallId,
        UUID relatedCommandId,
        UUID relatedFileChangeId,
        int attempt,
        Instant createdAt) {
}
