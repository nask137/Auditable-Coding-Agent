package com.nask.agent.approval;

import com.nask.agent.audit.AuditEventDraft;
import com.nask.agent.audit.AuditService;
import com.nask.agent.common.ApiException;
import com.nask.agent.common.Domain;
import com.nask.agent.run.AgentRunService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class ApprovalService {
    private final ApprovalRepository repository;
    private final AuditService auditService;
    private final AgentRunService runService;

    public ApprovalService(ApprovalRepository repository, AuditService auditService, AgentRunService runService) {
        this.repository = repository;
        this.auditService = auditService;
        this.runService = runService;
    }

    @Transactional
    public ApprovalRequestRecord create(UUID taskId, UUID runId, UUID stepId, UUID actionId,
                                        Domain.ApprovalType type, Domain.RiskLevel riskLevel, String reason,
                                        List<String> affectedFiles, String command, String workingDirectory,
                                        String patchPreview) {
        var approval = new ApprovalRequestRecord(UUID.randomUUID(), taskId, runId, stepId, actionId,
                type.name(), reason, riskLevel.name(), affectedFiles == null ? List.of() : affectedFiles,
                command, workingDirectory, patchPreview, Domain.ApprovalStatus.PENDING.name(),
                Instant.now(), null, null, null);
        repository.insert(approval);
        auditService.append(new AuditEventDraft(taskId, runId, stepId, actionId, Domain.AuditEventType.ApprovalRequested,
                Domain.AuditActor.RUNTIME, Domain.AuditLevel.WARN, "Approval required", reason,
                approval.affectedFiles(), null, approval.id(), null, null, null, riskLevel,
                Domain.ApprovalStatus.PENDING, true, null, null, Map.of("approvalType", type.name())));
        runService.markWaitingApproval(runId, taskId);
        return approval;
    }

    public List<ApprovalRequestRecord> list(String status) {
        if (status == null || status.isBlank()) {
            return repository.findAll();
        }
        return repository.findByStatus(Domain.ApprovalStatus.valueOf(status));
    }

    public ApprovalRequestRecord getRequired(UUID id) {
        return repository.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "APPROVAL_NOT_FOUND", "Approval not found: " + id));
    }

    @Transactional
    public ApprovalRequestRecord consumeApproved(UUID runId, Domain.ApprovalType type, List<String> affectedFiles,
                                                  String command, String workingDirectory) {
        var files = affectedFiles == null ? List.<String>of() : affectedFiles;
        for (var candidate : repository.findApprovedCandidates(runId, type)) {
            if (!candidate.affectedFiles().equals(files)) {
                continue;
            }
            if (!sameNullable(candidate.command(), command)) {
                continue;
            }
            if (!sameNullable(candidate.workingDirectory(), workingDirectory)) {
                continue;
            }
            repository.consume(candidate.id());
            auditService.append(new AuditEventDraft(candidate.taskId(), candidate.runId(), candidate.stepId(), candidate.actionId(),
                    Domain.AuditEventType.PermissionAllowed, Domain.AuditActor.RUNTIME, Domain.AuditLevel.INFO,
                    "Consume approved request", candidate.reason(), candidate.affectedFiles(), null, candidate.id(),
                    null, null, null, Domain.RiskLevel.valueOf(candidate.riskLevel()),
                    Domain.ApprovalStatus.CONSUMED, true, null, null, Map.of("approvalType", type.name())));
            return getRequired(candidate.id());
        }
        return null;
    }

    @Transactional
    public ApprovalRequestRecord approve(UUID id, ResolveApprovalRequest request) {
        var approval = getRequired(id);
        repository.resolve(id, Domain.ApprovalStatus.APPROVED, actor(request), request == null ? null : request.reason());
        auditService.append(new AuditEventDraft(approval.taskId(), approval.runId(), approval.stepId(), approval.actionId(),
                Domain.AuditEventType.ApprovalGranted, Domain.AuditActor.USER, Domain.AuditLevel.INFO,
                "Approve request", approval.reason(), approval.affectedFiles(), null, approval.id(), null, null,
                null, Domain.RiskLevel.valueOf(approval.riskLevel()), Domain.ApprovalStatus.APPROVED, true,
                null, null, Map.of()));
        runService.markRunning(approval.runId(), approval.taskId());
        return getRequired(id);
    }

    @Transactional
    public ApprovalRequestRecord deny(UUID id, ResolveApprovalRequest request) {
        var approval = getRequired(id);
        var reason = request == null ? "Denied" : request.reason();
        repository.resolve(id, Domain.ApprovalStatus.DENIED, actor(request), reason);
        auditService.append(new AuditEventDraft(approval.taskId(), approval.runId(), approval.stepId(), approval.actionId(),
                Domain.AuditEventType.ApprovalDenied, Domain.AuditActor.USER, Domain.AuditLevel.WARN,
                "Deny request", reason, approval.affectedFiles(), null, approval.id(), null, null,
                null, Domain.RiskLevel.valueOf(approval.riskLevel()), Domain.ApprovalStatus.DENIED, true,
                null, null, Map.of()));
        runService.fail(approval.runId(), approval.taskId(), "Approval denied: " + reason);
        return getRequired(id);
    }

    private String actor(ResolveApprovalRequest request) {
        return request == null || request.resolvedBy() == null || request.resolvedBy().isBlank()
                ? "local-user" : request.resolvedBy();
    }

    private boolean sameNullable(String left, String right) {
        return Objects.equals(normalize(left), normalize(right));
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
