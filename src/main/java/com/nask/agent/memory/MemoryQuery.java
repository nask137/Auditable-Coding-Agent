package com.nask.agent.memory;

import java.util.List;
import java.util.UUID;

/**
 * Structured query for project memory retrieval.
 */
public record MemoryQuery(
        UUID workspaceId,
        String queryText,
        UUID taskId,
        UUID runId,
        UUID workflowNodeExecutionId,
        List<String> memoryTypes,
        List<String> documentTypes,
        List<String> symbolTypes,
        int limit) {

    public MemoryQuery {
        memoryTypes = memoryTypes == null ? List.of() : List.copyOf(memoryTypes);
        documentTypes = documentTypes == null ? List.of() : List.copyOf(documentTypes);
        symbolTypes = symbolTypes == null ? List.of() : List.copyOf(symbolTypes);
        limit = limit <= 0 ? 10 : Math.min(limit, 50);
        queryText = queryText == null ? "" : queryText.strip();
    }
}
