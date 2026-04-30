package com.nask.agent.workspace;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record Workspace(
        UUID id,
        String name,
        String rootPath,
        boolean trusted,
        List<String> allowedOperations,
        List<String> blockedPaths,
        List<String> sensitivePatterns,
        Instant createdAt,
        Instant lastUsedAt) {
}
