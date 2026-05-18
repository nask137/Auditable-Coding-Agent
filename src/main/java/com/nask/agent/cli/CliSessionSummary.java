package com.nask.agent.cli;

import java.time.Instant;

/**
 * Summary of a backend conversation shown in the CLI sessions dashboard.
 */
public record CliSessionSummary(
        String conversationId,
        String conversationTitle,
        String workspaceId,
        int taskCount,
        String latestTaskId,
        String latestTaskStatus,
        Instant updatedAt) {
}
