package com.nask.agent.llm;

import com.nask.agent.plan.PlanItem;

import java.util.List;
import java.util.UUID;

public record ExecutionContext(UUID taskId, UUID runId, PlanItem currentItem, List<String> observedFiles) {
}
