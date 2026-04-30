package com.nask.agent.tool;

import java.util.Map;
import java.util.UUID;

public record ToolExecutionResult(
        boolean success,
        boolean waitingApproval,
        boolean blocked,
        UUID approvalId,
        String summary,
        Map<String, Object> payload) {
    public static ToolExecutionResult success(String summary, Map<String, Object> payload) {
        return new ToolExecutionResult(true, false, false, null, summary, payload);
    }

    public static ToolExecutionResult waiting(UUID approvalId, String summary) {
        return new ToolExecutionResult(false, true, false, approvalId, summary, Map.of());
    }

    public static ToolExecutionResult blocked(String summary) {
        return new ToolExecutionResult(false, false, true, null, summary, Map.of());
    }
}
