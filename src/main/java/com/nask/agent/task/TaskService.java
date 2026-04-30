package com.nask.agent.task;

import com.nask.agent.audit.AuditEventDraft;
import com.nask.agent.audit.AuditService;
import com.nask.agent.common.ApiException;
import com.nask.agent.common.Domain;
import com.nask.agent.workspace.WorkspaceService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class TaskService {
    private final TaskRepository repository;
    private final WorkspaceService workspaceService;
    private final AuditService auditService;

    public TaskService(TaskRepository repository, WorkspaceService workspaceService, AuditService auditService) {
        this.repository = repository;
        this.workspaceService = workspaceService;
        this.auditService = auditService;
    }

    @Transactional
    public CodingTask create(CreateTaskRequest request) {
        workspaceService.getRequired(request.workspaceId());
        var now = Instant.now();
        var task = new CodingTask(
                UUID.randomUUID(),
                request.workspaceId(),
                request.title() == null || request.title().isBlank() ? "Coding task" : request.title(),
                request.userRequest(),
                Domain.TaskStatus.CREATED.name(),
                now,
                now);
        repository.insert(task);
        auditService.append(AuditEventDraft.info(task.id(), null, null, Domain.AuditEventType.TaskCreated,
                Domain.AuditActor.USER, "Create task", task.userRequest()));
        return task;
    }

    public CodingTask getRequired(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "Task not found: " + id));
    }

    public void updateStatus(UUID taskId, Domain.TaskStatus status) {
        repository.updateStatus(taskId, status);
    }

    public CodingTask cancel(UUID taskId) {
        var task = getRequired(taskId);
        updateStatus(taskId, Domain.TaskStatus.CANCELLED);
        auditService.append(AuditEventDraft.info(task.id(), null, null, Domain.AuditEventType.TaskCancelled,
                Domain.AuditActor.USER, "Cancel task", "Task cancelled"));
        return getRequired(taskId);
    }
}
