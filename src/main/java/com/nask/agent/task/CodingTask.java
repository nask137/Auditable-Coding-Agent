package com.nask.agent.task;

import java.time.Instant;
import java.util.UUID;

/**
 * User-facing task that describes a requested coding change.
 *
 * @param id task identifier
 * @param workspaceId workspace where the task will run
 * @param title short display title
 * @param userRequest original user instruction
 * @param status lifecycle status from {@link com.nask.agent.common.Domain.TaskStatus}
 * @param createdAt creation timestamp
 * @param updatedAt last status update timestamp
 */
public record CodingTask(
        UUID id,
        UUID workspaceId,
        String title,
        String userRequest,
        String status,
        Instant createdAt,
        Instant updatedAt) {
}
