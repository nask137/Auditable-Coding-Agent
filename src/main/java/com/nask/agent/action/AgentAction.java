package com.nask.agent.action;

import java.time.Instant;
import java.util.UUID;

public record AgentAction(
        UUID id,
        UUID stepId,
        String actionType,
        String reason,
        String riskLevel,
        String status,
        Instant createdAt) {
}
