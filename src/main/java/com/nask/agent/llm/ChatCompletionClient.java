package com.nask.agent.llm;

/**
 * Minimal chat-completions client used by the HTTP gateway.
 */
public interface ChatCompletionClient {
    ChatCompletionResult complete(LlmPrompt prompt);
}
