package com.nask.agent.memory;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * One code symbol extracted from a source file outline.
 */
public record CodeSymbol(
        UUID id,
        UUID workspaceId,
        UUID scanRunId,
        String path,
        String language,
        String symbolType,
        String symbolName,
        String containerName,
        String signature,
        int lineStart,
        int lineEnd,
        String visibility,
        Map<String, Object> metadata,
        Instant createdAt) {
}
