package com.nask.agent.task;

import com.nask.agent.audit.AuditEventDraft;
import com.nask.agent.audit.AuditService;
import com.nask.agent.common.ApiException;
import com.nask.agent.common.Domain;
import com.nask.agent.conversation.ConversationService;
import com.nask.agent.workspace.WorkspaceService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Application service for task lifecycle changes.
 */
@Service
public class TaskService {
    private final TaskRepository repository;
    private final WorkspaceService workspaceService;
    private final ConversationService conversationService;
    private final AuditService auditService;

    /**
     * Creates a task service.
     */
    public TaskService(TaskRepository repository, WorkspaceService workspaceService,
                       ConversationService conversationService, AuditService auditService) {
        this.repository = repository;
        this.workspaceService = workspaceService;
        this.conversationService = conversationService;
        this.auditService = auditService;
    }

    /**
     * Creates a task after verifying that the target workspace exists.
     */
    @Transactional
    public CodingTask create(CreateTaskRequest request) {
        workspaceService.getRequired(request.workspaceId());
        var conversation = conversationService.ensure(request.workspaceId(), request.conversationId(),
                titleHint(request));
        var now = Instant.now();
        var task = new CodingTask(
                UUID.randomUUID(),
                request.workspaceId(),
                conversation.id(),
                conversationService.nextTaskIndex(conversation.id()),
                request.title() == null || request.title().isBlank() ? "Coding task" : request.title(),
                request.userRequest(),
                Domain.TaskStatus.CREATED.name(),
                null,
                null,
                null,
                null,
                Map.of(),
                now,
                now);
        repository.insert(task);
        conversationService.touch(conversation.id());
        auditService.append(AuditEventDraft.info(task.id(), null, null, Domain.AuditEventType.TaskCreated,
                Domain.AuditActor.USER, "Create task", task.userRequest()));
        return task;
    }

    /**
     * Loads a task or raises a REST-friendly 404 exception.
     */
    public CodingTask getRequired(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "Task not found: " + id));
    }

    /**
     * Lists existing tasks for observation clients.
     */
    public List<CodingTask> list() {
        return repository.findAll();
    }

    /**
     * Updates the task status without creating an audit event.
     */
    public void updateStatus(UUID taskId, Domain.TaskStatus status) {
        repository.updateStatus(taskId, status);
    }

    /**
     * Starts the task's single execution.
     */
    @Transactional
    public CodingTask startExecution(CodingTask task, String workflowName) {
        if (!Domain.TaskStatus.CREATED.name().equals(task.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "TASK_ALREADY_EXECUTED",
                    "Task can only be started once: " + task.id());
        }
        if (workflowName == null || workflowName.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "WORKFLOW_REQUIRED",
                    "Task execution requires an explicit or model-selected workflow");
        }
        var workflow = workflowName;
        var mode = switch (workflow) {
            case "review-agent" -> "REVIEW";
            case "test-agent" -> "TEST";
            default -> "CODE_EDIT";
        };
        var started = repository.startExecution(task.id(), mode, workflow, Instant.now());
        if (!started) {
            throw new ApiException(HttpStatus.CONFLICT, "TASK_ALREADY_EXECUTED",
                    "Task can only be started once: " + task.id());
        }
        var updated = getRequired(task.id());
        auditService.append(AuditEventDraft.info(updated.id(), updated.executionId(), null,
                Domain.AuditEventType.TaskExecutionStarted, Domain.AuditActor.RUNTIME, "Start task execution",
                "Agent mode " + mode + " workflow " + workflow));
        return updated;
    }

    /**
     * Pauses task state until approval is resolved.
     */
    public void markWaitingApproval(UUID taskId) {
        repository.updateExecutionStatus(taskId, Domain.TaskStatus.WAITING_APPROVAL, null);
        auditService.append(AuditEventDraft.info(taskId, taskId, null, Domain.AuditEventType.TaskExecutionPaused,
                Domain.AuditActor.RUNTIME, "Pause task execution", "Waiting for approval"));
    }

    /**
     * Pauses task state until user input is supplied.
     */
    public void markWaitingUserInput(UUID taskId, String reason) {
        repository.updateExecutionStatus(taskId, Domain.TaskStatus.WAITING_USER_INPUT, null);
        auditService.append(AuditEventDraft.info(taskId, taskId, null, Domain.AuditEventType.TaskExecutionPaused,
                Domain.AuditActor.RUNTIME, "Pause task execution", reason));
    }

    /**
     * Returns a paused task to running state.
     */
    public void markRunning(UUID taskId) {
        repository.updateExecutionStatus(taskId, Domain.TaskStatus.RUNNING, null);
        auditService.append(AuditEventDraft.info(taskId, taskId, null, Domain.AuditEventType.TaskExecutionResumed,
                Domain.AuditActor.RUNTIME, "Resume task execution", "Task returned to RUNNING"));
    }

    /**
     * Marks the task completed and writes a terminal audit event.
     */
    public void complete(UUID taskId) {
        repository.updateExecutionStatus(taskId, Domain.TaskStatus.COMPLETED, null);
        auditService.append(AuditEventDraft.info(taskId, taskId, null, Domain.AuditEventType.AgentFinished,
                Domain.AuditActor.RUNTIME, "Finish task execution", "Task completed"));
    }

    /**
     * Marks the task failed with a durable reason and audit event.
     */
    public void fail(UUID taskId, String reason) {
        repository.updateExecutionStatus(taskId, Domain.TaskStatus.FAILED, reason);
        auditService.append(new AuditEventDraft(taskId, taskId, null, null, Domain.AuditEventType.AgentFailed,
                Domain.AuditActor.RUNTIME, Domain.AuditLevel.ERROR, "Fail task execution", reason,
                java.util.List.of(), null, null, null, null, null, Domain.RiskLevel.MEDIUM,
                null, false, "TASK_EXECUTION_FAILED", reason, Map.of()));
    }

    /**
     * Cancels a task and writes an audit event.
     */
    public CodingTask cancel(UUID taskId) {
        var task = getRequired(taskId);
        repository.updateExecutionStatus(taskId, Domain.TaskStatus.CANCELLED, null);
        auditService.append(AuditEventDraft.info(task.id(), null, null, Domain.AuditEventType.TaskCancelled,
                Domain.AuditActor.USER, "Cancel task", "Task cancelled"));
        return getRequired(taskId);
    }

    private String titleHint(CreateTaskRequest request) {
        if (request.title() != null && !request.title().isBlank()) {
            return request.title();
        }
        var text = request.userRequest() == null ? "" : request.userRequest().strip();
        return text.length() <= 60 ? text : text.substring(0, 57) + "...";
    }
}
