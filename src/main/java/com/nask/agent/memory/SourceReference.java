package com.nask.agent.memory;

import java.util.UUID;

/**
 * Stable source pointer attached to a memory retrieval result.
 */
public record SourceReference(
        String sourceType,
        UUID sourceId,
        String path,
        Integer lineStart,
        Integer lineEnd,
        String symbolName,
        UUID scanRunId,
        UUID taskId,
        UUID runId) {
}
