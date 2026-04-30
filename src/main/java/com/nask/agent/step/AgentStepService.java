package com.nask.agent.step;

import com.nask.agent.audit.AuditEventDraft;
import com.nask.agent.audit.AuditService;
import com.nask.agent.common.Domain;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AgentStepService {
    private final AgentStepRepository repository;
    private final AuditService auditService;

    public AgentStepService(AgentStepRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    public AgentStep start(UUID taskId, UUID runId, UUID planItemId, Domain.StepType stepType, String inputSummary) {
        var step = new AgentStep(UUID.randomUUID(), runId, planItemId, stepType.name(), Domain.StepStatus.RUNNING.name(),
                inputSummary, null, Instant.now(), null);
        repository.insert(step);
        auditService.append(AuditEventDraft.info(taskId, runId, step.id(), Domain.AuditEventType.StepStarted,
                Domain.AuditActor.RUNTIME, inputSummary, stepType.name()));
        return step;
    }

    public void complete(UUID taskId, UUID runId, AgentStep step, String outputSummary) {
        repository.complete(step.id(), outputSummary);
        auditService.append(AuditEventDraft.info(taskId, runId, step.id(), Domain.AuditEventType.StepCompleted,
                Domain.AuditActor.RUNTIME, step.stepType(), outputSummary));
    }

    public void fail(UUID taskId, UUID runId, AgentStep step, String outputSummary) {
        repository.fail(step.id(), outputSummary);
        auditService.append(new AuditEventDraft(taskId, runId, step.id(), null, Domain.AuditEventType.StepFailed,
                Domain.AuditActor.RUNTIME, Domain.AuditLevel.ERROR, step.stepType(), outputSummary,
                java.util.List.of(), null, null, null, null, null, Domain.RiskLevel.MEDIUM,
                null, false, "STEP_FAILED", outputSummary, java.util.Map.of()));
    }

    public List<AgentStep> findByRun(UUID runId) {
        return repository.findByRun(runId);
    }
}
