package com.nask.agent.llm;

import java.util.List;
import java.util.UUID;

/**
 * Context supplied to the model for task-understanding.
 */
public record TaskContext(UUID taskId, UUID runId, UUID stepId, UUID workspaceId,
                          String userRequest, List<String> recoveryNotes) {
}
