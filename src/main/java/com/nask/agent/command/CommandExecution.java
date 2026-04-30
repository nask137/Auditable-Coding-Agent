package com.nask.agent.command;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Persisted shell command attempt and its final output metadata.
 */
public record CommandExecution(
        UUID id,
        UUID workspaceId,
        UUID taskId,
        UUID runId,
        UUID stepId,
        UUID actionId,
        String command,
        String executable,
        List<String> arguments,
        String workingDirectory,
        String policyType,
        String riskLevel,
        UUID approvalId,
        String status,
        Integer exitCode,
        String outputSummary,
        Instant startedAt,
        Instant finishedAt,
        Instant createdAt) {
}
