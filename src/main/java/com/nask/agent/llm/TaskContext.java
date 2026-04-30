package com.nask.agent.llm;

import java.util.UUID;

/**
 * Context supplied to the model for task-understanding.
 */
public record TaskContext(UUID taskId, UUID workspaceId, String userRequest) {
}
