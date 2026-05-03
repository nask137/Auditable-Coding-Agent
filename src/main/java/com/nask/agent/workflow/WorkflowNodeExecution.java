package com.nask.agent.workflow;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * One node execution within an agent run.
 */
public record WorkflowNodeExecution(
        UUID id,
        UUID taskId,
        UUID runId,
        UUID workflowDefinitionId,
        String nodeId,
        String nodeType,
        UUID agentStepId,
        String status,
        String inputSummary,
        String outputSummary,
        UUID failureId,
        Instant startedAt,
        Instant completedAt,
        Map<String, Object> metadata) {
}
