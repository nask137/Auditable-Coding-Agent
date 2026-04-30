package com.nask.agent.run;

import com.nask.agent.audit.AuditEventDraft;
import com.nask.agent.audit.AuditService;
import com.nask.agent.common.ApiException;
import com.nask.agent.common.Domain;
import com.nask.agent.task.CodingTask;
import com.nask.agent.task.TaskService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class AgentRunService {
    private final AgentRunRepository repository;
    private final TaskService taskService;
    private final AuditService auditService;

    public AgentRunService(AgentRunRepository repository, TaskService taskService, AuditService auditService) {
        this.repository = repository;
        this.taskService = taskService;
        this.auditService = auditService;
    }

    @Transactional
    public AgentRun createRun(CodingTask task) {
        var run = new AgentRun(UUID.randomUUID(), task.id(), "CODE_EDIT", Domain.AgentRunStatus.RUNNING.name(),
                Instant.now(), null, null, Map.of("loop", "phase1-fixed"));
        repository.insert(run);
        taskService.updateStatus(task.id(), Domain.TaskStatus.RUNNING);
        auditService.append(AuditEventDraft.info(task.id(), run.id(), null, Domain.AuditEventType.AgentRunStarted,
                Domain.AuditActor.RUNTIME, "Start agent run", "Agent mode CODE_EDIT"));
        return run;
    }

    public AgentRun getRequired(UUID id) {
        return repository.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "RUN_NOT_FOUND", "AgentRun not found: " + id));
    }

    public void markWaitingApproval(UUID runId, UUID taskId) {
        repository.updateStatus(runId, Domain.AgentRunStatus.WAITING_APPROVAL, null);
        taskService.updateStatus(taskId, Domain.TaskStatus.WAITING_APPROVAL);
    }

    public void markRunning(UUID runId, UUID taskId) {
        repository.updateStatus(runId, Domain.AgentRunStatus.RUNNING, null);
        taskService.updateStatus(taskId, Domain.TaskStatus.RUNNING);
    }

    public void complete(UUID runId, UUID taskId) {
        repository.updateStatus(runId, Domain.AgentRunStatus.COMPLETED, null);
        taskService.updateStatus(taskId, Domain.TaskStatus.COMPLETED);
        auditService.append(AuditEventDraft.info(taskId, runId, null, Domain.AuditEventType.AgentFinished,
                Domain.AuditActor.RUNTIME, "Finish agent run", "Task completed"));
    }

    public void fail(UUID runId, UUID taskId, String reason) {
        repository.updateStatus(runId, Domain.AgentRunStatus.FAILED, reason);
        taskService.updateStatus(taskId, Domain.TaskStatus.FAILED);
        auditService.append(new AuditEventDraft(taskId, runId, null, null, Domain.AuditEventType.AgentFailed,
                Domain.AuditActor.RUNTIME, Domain.AuditLevel.ERROR, "Fail agent run", reason,
                java.util.List.of(), null, null, null, null, null, Domain.RiskLevel.MEDIUM,
                null, false, "AGENT_RUN_FAILED", reason, Map.of()));
    }
}
