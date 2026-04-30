package com.nask.agent.workspace;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Workspace configuration and trust boundary persisted in the database.
 *
 * @param id workspace identifier
 * @param name display name
 * @param rootPath absolute normalized root path on disk
 * @param trusted whether the runtime should allow operations in this workspace
 * @param allowedOperations currently allowed file operation names
 * @param blockedPaths path segments that cannot be modified or traversed
 * @param sensitivePatterns glob-like names that require extra permission checks
 * @param createdAt creation timestamp
 * @param lastUsedAt last time a run touched this workspace
 */
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
