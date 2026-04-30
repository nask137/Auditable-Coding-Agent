package com.nask.agent.task;

import java.time.Instant;
import java.util.UUID;

public record CodingTask(
        UUID id,
        UUID workspaceId,
        String title,
        String userRequest,
        String status,
        Instant createdAt,
        Instant updatedAt) {
}
