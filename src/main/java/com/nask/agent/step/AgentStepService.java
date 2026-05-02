package com.nask.agent.step;

import com.nask.agent.audit.AuditEventDraft;
import com.nask.agent.audit.AuditService;
import com.nask.agent.common.ApiException;
import com.nask.agent.common.Domain;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Creates and completes steps while writing matching audit events.
 */
@Service
public class AgentStepService {
    private final AgentStepRepository repository;
    private final AuditService auditService;

    /**
     * Creates a step service.
     */
    public AgentStepService(AgentStepRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    /**
     * Starts a new step for a run, optionally linked to a plan item.
     */
    public AgentStep start(UUID taskId, UUID runId, UUID planItemId, Domain.StepType stepType, String inputSummary) {
        var step = new AgentStep(UUID.randomUUID(), runId, planItemId, stepType.name(), Domain.StepStatus.RUNNING.name(),
                inputSummary, null, Instant.now(), null);
        repository.insert(step);
        auditService.append(AuditEventDraft.info(taskId, runId, step.id(), Domain.AuditEventType.StepStarted,
                Domain.AuditActor.RUNTIME, inputSummary, stepType.name()));
        return step;
    }

    /**
     * Completes a step and appends an audit event.
     */
    public void complete(UUID taskId, UUID runId, AgentStep step, String outputSummary) {
        repository.complete(step.id(), outputSummary);
        auditService.append(AuditEventDraft.info(taskId, runId, step.id(), Domain.AuditEventType.StepCompleted,
                Domain.AuditActor.RUNTIME, step.stepType(), outputSummary));
    }

    /**
     * Pauses a step until a related approval request is resolved.
     */
    public void markWaitingApproval(UUID taskId, UUID runId, AgentStep step, String outputSummary) {
        repository.markWaitingApproval(step.id(), outputSummary);
        auditService.append(AuditEventDraft.info(taskId, runId, step.id(), Domain.AuditEventType.AgentRunPaused,
                Domain.AuditActor.RUNTIME, step.stepType(), outputSummary));
    }

    /**
     * Pauses a step until a user-input request is answered.
     */
    public void markWaitingUserInput(UUID taskId, UUID runId, AgentStep step, String outputSummary) {
        repository.markWaitingUserInput(step.id(), outputSummary);
        auditService.append(AuditEventDraft.info(taskId, runId, step.id(), Domain.AuditEventType.AgentRunPaused,
                Domain.AuditActor.RUNTIME, step.stepType(), outputSummary));
    }

    /**
     * Fails a step and appends an error audit event.
     */
    public void fail(UUID taskId, UUID runId, AgentStep step, String outputSummary) {
        repository.fail(step.id(), outputSummary);
        auditService.append(new AuditEventDraft(taskId, runId, step.id(), null, Domain.AuditEventType.StepFailed,
                Domain.AuditActor.RUNTIME, Domain.AuditLevel.ERROR, step.stepType(), outputSummary,
                java.util.List.of(), null, null, null, null, null, Domain.RiskLevel.MEDIUM,
                null, false, "STEP_FAILED", outputSummary, java.util.Map.of()));
    }

    /**
     * Lists steps for a run.
     */
    public List<AgentStep> findByRun(UUID runId) {
        return repository.findByRun(runId);
    }

    /**
     * Loads a step or raises a REST-friendly 404 exception.
     */
    public AgentStep getRequired(UUID id) {
        return repository.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "STEP_NOT_FOUND", "AgentStep not found: " + id));
    }
}
