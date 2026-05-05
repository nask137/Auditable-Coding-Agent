package com.nask.agent.memory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Workspace-level project profile generated from a bounded local scan.
 */
public record ProjectProfile(
        UUID id,
        UUID workspaceId,
        String languageSummary,
        List<String> frameworks,
        List<String> buildTools,
        List<String> testTools,
        List<String> packageManagers,
        List<String> entrypoints,
        List<String> importantPaths,
        List<String> docsPaths,
        List<String> configPaths,
        UUID lastScanRunId,
        double confidence,
        Instant createdAt,
        Instant updatedAt) {
}
