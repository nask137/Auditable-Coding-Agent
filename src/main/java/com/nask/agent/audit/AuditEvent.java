package com.nask.agent.audit;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persisted audit log entry.
 *
 * <p>The event intentionally stores redundant relationship ids and summaries so
 * it remains useful even when a client is only reading the audit stream.</p>
 */
public record AuditEvent(
        UUID id,
        UUID taskId,
        UUID runId,
        UUID stepId,
        UUID actionId,
        String eventType,
        String actor,
        String level,
        Instant occurredAt,
        String inputSummary,
        String outputSummary,
        List<String> relatedFiles,
        UUID relatedToolCallId,
        UUID relatedApprovalId,
        UUID relatedCommandId,
        UUID relatedFileChangeId,
        String permissionLevel,
        String riskLevel,
        String approvalStatus,
        Boolean success,
        String errorCode,
        String errorMessage,
        Map<String, Object> metadata) {
}
