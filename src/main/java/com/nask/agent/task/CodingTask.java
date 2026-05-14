package com.nask.agent.task;

import java.time.Instant;
import java.util.UUID;

/**
 * User-facing task that describes a requested coding change.
 *
 * @param id task identifier
 * @param workspaceId workspace where the task will run
 * @param conversationId conversation that groups related task prompts
 * @param promptIndex 1-based prompt order within the conversation
 * @param title short display title
 * @param userRequest original user instruction
 * @param status lifecycle status from {@link com.nask.agent.common.Domain.TaskStatus}
 * @param createdAt creation timestamp
 * @param updatedAt last status update timestamp
 */
public record CodingTask(
        UUID id,
        UUID workspaceId,
        UUID conversationId,
        int promptIndex,
        String title,
        String userRequest,
        String status,
        Instant createdAt,
        Instant updatedAt) {
    public CodingTask(UUID id, UUID workspaceId, String title, String userRequest, String status,
                      Instant createdAt, Instant updatedAt) {
        this(id, workspaceId, null, 1, title, userRequest, status, createdAt, updatedAt);
    }
}
