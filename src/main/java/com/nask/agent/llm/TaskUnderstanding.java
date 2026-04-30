package com.nask.agent.llm;

import java.util.List;

/**
 * Structured interpretation of a user task.
 */
public record TaskUnderstanding(String summary, String taskType, List<String> constraints, List<String> initialSearchHints) {
}
