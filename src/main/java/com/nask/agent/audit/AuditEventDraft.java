package com.nask.agent.audit;

import com.nask.agent.common.Domain;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AuditEventDraft(
        UUID taskId,
        UUID runId,
        UUID stepId,
        UUID actionId,
        Domain.AuditEventType eventType,
        Domain.AuditActor actor,
        Domain.AuditLevel level,
        String inputSummary,
        String outputSummary,
        List<String> relatedFiles,
        UUID relatedToolCallId,
        UUID relatedApprovalId,
        UUID relatedCommandId,
        UUID relatedFileChangeId,
        Domain.PermissionLevel permissionLevel,
        Domain.RiskLevel riskLevel,
        Domain.ApprovalStatus approvalStatus,
        Boolean success,
        String errorCode,
        String errorMessage,
        Map<String, Object> metadata) {

    public static AuditEventDraft info(UUID taskId, UUID runId, UUID stepId, Domain.AuditEventType eventType,
                                       Domain.AuditActor actor, String inputSummary, String outputSummary) {
        return new AuditEventDraft(taskId, runId, stepId, null, eventType, actor, Domain.AuditLevel.INFO,
                inputSummary, outputSummary, List.of(), null, null, null, null,
                null, null, null, true, null, null, Map.of());
    }
}
