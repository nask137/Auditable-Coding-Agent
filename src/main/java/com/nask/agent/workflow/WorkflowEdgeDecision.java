package com.nask.agent.workflow;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Records why a workflow transition was selected.
 */
public record WorkflowEdgeDecision(
        UUID id,
        UUID taskId,
        UUID runId,
        UUID workflowDefinitionId,
        String fromNodeId,
        String toNodeId,
        String edgeType,
        String conditionSummary,
        String decisionReason,
        boolean selected,
        Instant createdAt,
        Map<String, Object> metadata) {
}
