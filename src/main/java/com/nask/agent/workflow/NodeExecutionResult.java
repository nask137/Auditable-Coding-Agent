package com.nask.agent.workflow;

import java.util.Map;
import java.util.UUID;

/**
 * Uniform result returned by workflow node execution.
 */
public record NodeExecutionResult(
        String status,
        String summary,
        Map<String, Object> payload,
        String failureType,
        String recoveryStrategy,
        UUID updatedPlanId,
        UUID updatedPlanItemId) {
    public static NodeExecutionResult success(String summary, Map<String, Object> payload) {
        return new NodeExecutionResult("SUCCESS", summary, payload == null ? Map.of() : payload, null, null, null, null);
    }

    public static NodeExecutionResult waitingApproval(String summary) {
        return new NodeExecutionResult("WAITING_APPROVAL", summary, Map.of(), null, null, null, null);
    }

    public static NodeExecutionResult waitingUserInput(String summary) {
        return new NodeExecutionResult("WAITING_USER_INPUT", summary, Map.of(), null, null, null, null);
    }

    public static NodeExecutionResult failure(String summary) {
        return new NodeExecutionResult("FAILURE", summary, Map.of(), null, null, null, null);
    }
}
