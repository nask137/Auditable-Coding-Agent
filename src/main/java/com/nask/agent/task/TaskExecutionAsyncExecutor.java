package com.nask.agent.task;

import com.nask.agent.audit.AuditEventDraft;
import com.nask.agent.audit.AuditService;
import com.nask.agent.common.Domain;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * Runs agent workflows on a bounded background executor for interactive clients.
 */
@Service
public class TaskExecutionAsyncExecutor {
    private final TaskExecutionExecutor executionExecutor;
    private final TaskService taskService;
    private final AuditService auditService;
    private final TaskExecutor agentTaskExecutor;

    public TaskExecutionAsyncExecutor(TaskExecutionExecutor executionExecutor, TaskService taskService,
                                      AuditService auditService, TaskExecutor agentTaskExecutor) {
        this.executionExecutor = executionExecutor;
        this.taskService = taskService;
        this.auditService = auditService;
        this.agentTaskExecutor = agentTaskExecutor;
    }

    /**
     * Schedules a task execution and guarantees unexpected worker errors become durable task failures.
     */
    public void submit(UUID taskId) {
        agentTaskExecutor.execute(() -> {
            try {
                executionExecutor.execute(taskId);
            } catch (Exception e) {
                taskService.fail(taskId, "Async task execution failed: " + e.getMessage());
                auditService.append(new AuditEventDraft(taskId, taskId, null, null,
                        Domain.AuditEventType.TaskExecutionFailed, Domain.AuditActor.RUNTIME, Domain.AuditLevel.ERROR,
                        "Async task execution failed", e.getMessage(), java.util.List.of(), null, null, null, null,
                        null, Domain.RiskLevel.MEDIUM, null, false, "ASYNC_TASK_FAILED", e.getMessage(), Map.of()));
            }
        });
    }
}

