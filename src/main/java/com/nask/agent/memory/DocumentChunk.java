package com.nask.agent.memory;

/**
 * One line-bounded document chunk before persistence metadata is assigned.
 */
public record DocumentChunk(int chunkIndex, String content, int lineStart, int lineEnd, int tokenCount) {
}
