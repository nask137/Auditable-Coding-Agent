package com.nask.agent.memory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persisted record of one project context retrieval.
 */
public record MemoryRetrieval(
        UUID id,
        UUID workspaceId,
        UUID taskId,
        UUID runId,
        UUID workflowNodeExecutionId,
        String queryText,
        Map<String, Object> filters,
        List<SourceReference> resultRefs,
        String summary,
        Instant createdAt) {
}
