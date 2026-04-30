package com.nask.agent.llm;

import java.util.UUID;

public record ValidationContext(UUID taskId, UUID runId, UUID workspaceId) {
}
