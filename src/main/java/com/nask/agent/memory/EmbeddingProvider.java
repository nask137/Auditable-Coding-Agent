package com.nask.agent.memory;

import java.util.List;

/**
 * Optional extension point for vector search. The default milestone 5 search
 * path is deterministic keyword scoring and does not require an implementation.
 */
public interface EmbeddingProvider {
    List<Float> embed(String text);
}
