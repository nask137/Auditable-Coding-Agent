package com.nask.agent.llm;

/**
 * Provider response normalized for structured parsing and audit metadata.
 */
public record ChatCompletionResult(
        String model,
        String content,
        String finishReason,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens) {
}
