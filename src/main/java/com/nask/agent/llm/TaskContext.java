package com.nask.agent.llm;

import java.util.UUID;

public record TaskContext(UUID taskId, UUID workspaceId, String userRequest) {
}
