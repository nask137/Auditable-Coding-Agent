package com.nask.agent.task;

import java.util.UUID;

/**
 * Strategy interface for executing a task.
 */
public interface TaskExecutionExecutor {
    /**
     * Executes or resumes the task identified by {@code taskId}.
     */
    void execute(UUID taskId);
}
