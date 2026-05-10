package com.nask.agent.cli;

import java.time.Instant;

/**
 * Summary of a local CLI transcript known to the backend process.
 */
public record CliSessionSummary(
        String sessionId,
        String workspaceId,
        String runId,
        String taskId,
        String status,
        Instant updatedAt) {
}
