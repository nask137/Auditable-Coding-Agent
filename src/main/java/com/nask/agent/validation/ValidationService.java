package com.nask.agent.validation;

import com.nask.agent.audit.AuditEventDraft;
import com.nask.agent.audit.AuditService;
import com.nask.agent.common.Domain;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Records validation outcomes and mirrors them into the audit log.
 */
@Service
public class ValidationService {
    private final ValidationRepository repository;
    private final AuditService auditService;

    /**
     * Creates a validation service.
     */
    public ValidationService(ValidationRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    /**
     * Persists a validation result and appends start/completion audit events.
     */
    public ValidationResultRecord record(UUID taskId, UUID runId, UUID stepId, UUID commandId,
                                         Domain.ValidationType type, boolean success, String summary) {
        auditService.append(AuditEventDraft.info(taskId, runId, stepId, Domain.AuditEventType.ValidationStarted,
                Domain.AuditActor.RUNTIME, "Validation started", type.name()));
        var result = repository.insert(new ValidationResultRecord(UUID.randomUUID(), taskId, runId, stepId, commandId,
                type.name(), success, summary, Instant.now()));
        auditService.append(new AuditEventDraft(taskId, runId, stepId, null, Domain.AuditEventType.ValidationCompleted,
                Domain.AuditActor.RUNTIME, success ? Domain.AuditLevel.INFO : Domain.AuditLevel.ERROR,
                "Validation completed", summary, List.of(), null, null, commandId, null, Domain.PermissionLevel.SHELL_SAFE,
                Domain.RiskLevel.MEDIUM, null, success, success ? null : "VALIDATION_FAILED",
                success ? null : summary, java.util.Map.of("validationId", result.id().toString())));
        return result;
    }

    /**
     * Returns validation results for a task.
     */
    public List<ValidationResultRecord> findByTask(UUID taskId) {
        return repository.findByTask(taskId);
    }
}
