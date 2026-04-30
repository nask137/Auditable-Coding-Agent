package com.nask.agent.action;

import java.time.Instant;
import java.util.UUID;

/**
 * Auditable intent created inside a step before a tool or model operation runs.
 */
public record AgentAction(
        UUID id,
        UUID stepId,
        String actionType,
        String reason,
        String riskLevel,
        String status,
        Instant createdAt) {
}
