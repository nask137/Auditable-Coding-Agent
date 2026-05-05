package com.nask.agent.memory;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Long-lived project memory item with explicit source and approval status.
 */
public record ProjectMemoryItem(
        UUID id,
        UUID workspaceId,
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
        double confidence,
        Instant expiresAt,
        String createdBy,
        Instant createdAt,
        String approvedBy,
        Instant approvedAt,
        Map<String, Object> metadata) {
}
