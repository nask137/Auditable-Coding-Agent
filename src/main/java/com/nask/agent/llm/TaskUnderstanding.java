package com.nask.agent.llm;

import java.util.List;

public record TaskUnderstanding(String summary, String taskType, List<String> constraints, List<String> initialSearchHints) {
}
