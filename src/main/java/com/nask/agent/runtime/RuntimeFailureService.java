package com.nask.agent.runtime;

import com.nask.agent.audit.AuditEventDraft;
import com.nask.agent.audit.AuditService;
import com.nask.agent.common.Domain;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Records runtime failures and their selected recovery decisions.
 */
@Service
public class RuntimeFailureService {
    private final RuntimeFailureRepository repository;
    private final AuditService auditService;
    private final RecoveryPolicy recoveryPolicy;

    public RuntimeFailureService(RuntimeFailureRepository repository, AuditService auditService,
                                 RecoveryPolicy recoveryPolicy) {
        this.repository = repository;
        this.auditService = auditService;
        this.recoveryPolicy = recoveryPolicy;
    }

    @Transactional
    public RuntimeFailure record(UUID taskId, UUID runId, UUID stepId, UUID planItemId,
                                 Domain.RuntimeFailureType type, String summary, String details) {
        var decision = recoveryPolicy.decide(runId, stepId, planItemId, type, details);
        var eventId = auditService.append(new AuditEventDraft(taskId, runId, stepId, null,
                Domain.AuditEventType.RuntimeRejected, Domain.AuditActor.RUNTIME, Domain.AuditLevel.WARN,
                type.name(), summary, List.of(), null, null, null, null, null, Domain.RiskLevel.MEDIUM,
                null, false, type.name(), details, Map.of(
                "recoverable", decision.recoverable(),
                "strategy", decision.strategy().name(),
                "attempt", decision.attempt(),
                "budgetRemaining", decision.budgetRemaining())));
        var failure = new RuntimeFailure(UUID.randomUUID(), taskId, runId, stepId, planItemId, type.name(),
                decision.recoverable(), decision.strategy().name(), summary, details, eventId,
                null, null, null, decision.attempt(), Instant.now());
        repository.insert(failure);
        auditService.append(new AuditEventDraft(taskId, runId, stepId, null, Domain.AuditEventType.RecoveryStarted,
                Domain.AuditActor.RUNTIME, Domain.AuditLevel.INFO, type.name(), decision.strategy().name(),
                List.of(), null, null, null, null, null, Domain.RiskLevel.MEDIUM, null,
                decision.recoverable(), null, null, Map.of("failureId", failure.id().toString(),
                "budgetRemaining", decision.budgetRemaining())));
        return failure;
    }

    public List<RuntimeFailure> findByTask(UUID taskId) {
        return repository.findByTask(taskId);
    }

    public List<RuntimeFailure> findByRun(UUID runId) {
        return repository.findByRun(runId);
    }
}
