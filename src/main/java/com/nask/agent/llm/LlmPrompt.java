package com.nask.agent.llm;

/**
 * Versioned prompt sent to the model for a single structured decision.
 */
public record LlmPrompt(String version, String system, String user) {
}
