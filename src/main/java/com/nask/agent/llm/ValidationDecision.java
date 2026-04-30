package com.nask.agent.llm;

import java.util.List;

/**
 * Model decision describing whether to validate and which command to run.
 */
public record ValidationDecision(boolean shouldValidate, List<String> executableAndArgs, String reason) {
}
