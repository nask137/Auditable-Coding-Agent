package com.nask.agent.tool;

import com.nask.agent.workspace.Workspace;

import java.util.UUID;

/**
 * Correlation context passed to all tool services.
 */
public record ToolExecutionContext(
        UUID taskId,
        UUID runId,
        UUID stepId,
        UUID actionId,
        Workspace workspace) {
}
