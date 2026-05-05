package com.nask.agent.memory;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Request body for manually creating a project memory item.
 */
public record CreateProjectMemoryRequest(
        String memoryType,
        String scope,
        String title,
        String content,
        String sourceType,
        UUID sourceId,
        String sourcePath,
        Integer sourceLineStart,
        Integer sourceLineEnd,
        String status,
        Double confidence,
        Instant expiresAt,
        String createdBy,
        Map<String, Object> metadata) {
}
