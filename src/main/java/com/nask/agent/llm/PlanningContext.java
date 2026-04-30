package com.nask.agent.llm;

import java.util.List;
import java.util.UUID;

public record PlanningContext(UUID taskId, UUID runId, TaskUnderstanding understanding, List<String> observedFiles) {
}
