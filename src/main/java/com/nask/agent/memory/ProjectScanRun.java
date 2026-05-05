package com.nask.agent.memory;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Auditable record of one bounded workspace scan.
 */
public record ProjectScanRun(
        UUID id,
        UUID workspaceId,
        UUID taskId,
        UUID runId,
        String status,
        String scanReason,
        Instant startedAt,
        Instant completedAt,
        int filesSeen,
        int filesIndexed,
        int filesSkipped,
        String summary,
        Map<String, Object> metadata) {
}
