package com.nask.agent.memory;

import java.util.List;
import java.util.UUID;

/**
 * Context package returned to workflow nodes and API clients after retrieval.
 */
public record MemoryContext(
        UUID retrievalId,
        UUID workspaceId,
        String queryText,
        ProjectProfile profile,
        List<MemorySearchResult> results,
        List<SourceReference> sourceReferences,
        String summary) {
}
