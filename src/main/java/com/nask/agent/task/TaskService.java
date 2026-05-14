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
     * Cancels a task and writes an audit event.
     */
    public CodingTask cancel(UUID taskId) {
        var task = getRequired(taskId);
        updateStatus(taskId, Domain.TaskStatus.CANCELLED);
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
