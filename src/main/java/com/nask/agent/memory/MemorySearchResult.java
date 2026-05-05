package com.nask.agent.memory;

import java.util.Map;

/**
 * Ranked memory retrieval hit with its original source reference.
 */
public record MemorySearchResult(
        String resultType,
        double score,
        String title,
        String snippet,
        SourceReference source,
        Map<String, Object> metadata) {
}
