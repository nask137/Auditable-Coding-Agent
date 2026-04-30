package com.nask.agent.approval;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ApprovalRequestRecord(
        UUID id,
        UUID taskId,
        UUID runId,
        UUID stepId,
        UUID actionId,
        String approvalType,
        String reason,
        String riskLevel,
        List<String> affectedFiles,
        String command,
        String workingDirectory,
        String patchPreview,
        String status,
        Instant createdAt,
        Instant resolvedAt,
        String resolvedBy,
        String resolutionReason) {
}
