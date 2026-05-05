package com.nask.agent.memory;

import java.util.List;
import java.util.Map;

/**
 * In-memory scanner result used to build persistent scan and profile records.
 */
public record ProjectScanResult(
        List<ProjectScanObservation> observations,
        int filesSeen,
        int filesIndexed,
        int filesSkipped,
        String summary,
        Map<String, Object> metadata) {
}
