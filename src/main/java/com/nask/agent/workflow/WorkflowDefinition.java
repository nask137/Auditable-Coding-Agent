package com.nask.agent.workflow;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Persisted workflow template used by the phase 3 runtime.
 */
public record WorkflowDefinition(
        UUID id,
        String name,
        int version,
        String description,
        String mode,
        boolean enabled,
        Map<String, Object> definition,
        Instant createdAt,
        Instant updatedAt) {
}
