package com.nask.agent.llm;

import java.util.List;

public record ValidationDecision(boolean shouldValidate, List<String> executableAndArgs, String reason) {
}
