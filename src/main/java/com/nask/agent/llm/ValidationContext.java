package com.nask.agent.llm;

import java.util.UUID;

/**
 * Context supplied when the model chooses a validation command.
 */
public record ValidationContext(UUID taskId, UUID runId, UUID workspaceId) {
}
