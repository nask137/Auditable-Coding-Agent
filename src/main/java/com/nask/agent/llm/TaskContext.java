package com.nask.agent.llm;

import com.nask.agent.conversation.ConversationTaskContext;

import java.util.List;
import java.util.UUID;

/**
 * Context supplied to the model for task-understanding.
 */
public record TaskContext(UUID taskId, UUID runId, UUID stepId, UUID workspaceId,
                          String userRequest, List<String> recoveryNotes,
                          List<ConversationTaskContext> previousTasks) {
    public TaskContext(UUID taskId, UUID runId, UUID stepId, UUID workspaceId,
                       String userRequest, List<String> recoveryNotes) {
        this(taskId, runId, stepId, workspaceId, userRequest, recoveryNotes, List.of());
    }
}
