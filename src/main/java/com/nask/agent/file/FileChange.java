package com.nask.agent.file;

import java.time.Instant;
import java.util.UUID;

public record FileChange(
        UUID id,
        UUID workspaceId,
        UUID taskId,
        UUID runId,
        UUID stepId,
        UUID actionId,
        String path,
        String changeType,
        String reason,
        String diff,
        String beforeHash,
        String afterHash,
        String baseRevision,
        Instant observedAt,
        String patchApplyStatus,
        int lineAdded,
        int lineDeleted,
        String riskLevel,
        UUID approvalId,
        Instant createdAt) {
}
