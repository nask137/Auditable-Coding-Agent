package com.nask.agent.llm;

import com.nask.agent.plan.PlanItem;

import java.util.List;
import java.util.UUID;

/**
 * Context supplied to the model when choosing actions for one plan item.
 */
public record ExecutionContext(UUID taskId, UUID runId, UUID stepId, PlanItem currentItem, List<String> observedFiles,
                               List<String> recentToolResults) {
    public ExecutionContext(UUID taskId, UUID runId, UUID stepId, PlanItem currentItem, List<String> observedFiles) {
        this(taskId, runId, stepId, currentItem, observedFiles, List.of());
    }

    public ExecutionContext(UUID taskId, UUID runId, UUID stepId, PlanItem currentItem, List<String> observedFiles,
                            List<String> recentToolResults) {
        this.taskId = taskId;
        this.runId = runId;
        this.stepId = stepId;
        this.currentItem = currentItem;
        this.observedFiles = observedFiles;
        this.recentToolResults = recentToolResults;
    }
}
