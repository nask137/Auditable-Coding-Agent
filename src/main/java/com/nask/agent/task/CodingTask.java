package com.nask.agent.task;

import java.time.Instant;
import java.util.Map;
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
 * @param agentMode mode name used by the executor
 * @param executionStartedAt execution start timestamp, if started
 * @param executionFinishedAt terminal timestamp, if complete or failed
 * @param failureReason terminal failure reason, if any
 * @param runtimeMetadata implementation-specific details useful for audit/debugging
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
        String agentMode,
        Instant executionStartedAt,
        Instant executionFinishedAt,
        String failureReason,
        Map<String, Object> runtimeMetadata,
        Instant createdAt,
        Instant updatedAt) {
    public CodingTask(UUID id, UUID workspaceId, String title, String userRequest, String status,
                      Instant createdAt, Instant updatedAt) {
        this(id, workspaceId, null, 1, title, userRequest, status, null, null, null, null, Map.of(),
                createdAt, updatedAt);
    }

    public UUID executionId() {
        return id;
    }
}
