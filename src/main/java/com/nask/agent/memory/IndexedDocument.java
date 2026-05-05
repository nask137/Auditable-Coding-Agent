package com.nask.agent.memory;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Searchable document chunk produced from workspace docs, config, or reports.
 */
public record IndexedDocument(
        UUID id,
        UUID workspaceId,
        UUID scanRunId,
        String path,
        String documentType,
        String title,
        int chunkIndex,
        String content,
        String contentHash,
        int lineStart,
        int lineEnd,
        int tokenCount,
        Map<String, Object> metadata,
        Instant createdAt) {
}
