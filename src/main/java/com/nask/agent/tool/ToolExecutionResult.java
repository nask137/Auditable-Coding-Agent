package com.nask.agent.tool;

import java.util.Map;
import java.util.UUID;

/**
 * Normalized result returned by tool services to the agent loop.
 *
 * <p>The loop only needs to distinguish success, approval pause, and hard block;
 * detailed payloads remain available for planning, validation, and reports.</p>
 */
public record ToolExecutionResult(
        boolean success,
        boolean waitingApproval,
        boolean blocked,
        UUID approvalId,
        String summary,
        Map<String, Object> payload) {
    /**
     * Creates a successful tool result.
     */
    public static ToolExecutionResult success(String summary, Map<String, Object> payload) {
        return new ToolExecutionResult(true, false, false, null, summary, payload);
    }

    /**
     * Creates a result that pauses the run until approval is resolved.
     */
    public static ToolExecutionResult waiting(UUID approvalId, String summary) {
        return new ToolExecutionResult(false, true, false, approvalId, summary, Map.of());
    }

    /**
     * Creates a hard-blocked result that should fail the current flow.
     */
    public static ToolExecutionResult blocked(String summary) {
        return new ToolExecutionResult(false, false, true, null, summary, Map.of());
    }
}
