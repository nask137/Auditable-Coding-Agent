package com.nask.agent.llm;

import com.nask.agent.plan.PlanItem;
import com.nask.agent.memory.MemoryContext;

import java.util.List;
import java.util.UUID;

/**
 * Context supplied to the model when choosing actions for one plan item.
 */
public record ExecutionContext(UUID taskId, UUID runId, UUID stepId, PlanItem currentItem, List<String> observedFiles,
                               List<String> recentToolResults, List<String> recoveryNotes,
                               MemoryContext memoryContext) {
    public ExecutionContext(UUID taskId, UUID runId, UUID stepId, PlanItem currentItem, List<String> observedFiles) {
        this(taskId, runId, stepId, currentItem, observedFiles, List.of(), List.of(), null);
    }

    public ExecutionContext(UUID taskId, UUID runId, UUID stepId, PlanItem currentItem, List<String> observedFiles,
                            List<String> recentToolResults) {
        this(taskId, runId, stepId, currentItem, observedFiles, recentToolResults, List.of(), null);
    }

    public ExecutionContext(UUID taskId, UUID runId, UUID stepId, PlanItem currentItem, List<String> observedFiles,
                            List<String> recentToolResults, List<String> recoveryNotes) {
        this(taskId, runId, stepId, currentItem, observedFiles, recentToolResults, recoveryNotes, null);
    }

    public ExecutionContext(UUID taskId, UUID runId, UUID stepId, PlanItem currentItem, List<String> observedFiles,
                            List<String> recentToolResults, List<String> recoveryNotes,
                            MemoryContext memoryContext) {
        this.taskId = taskId;
        this.runId = runId;
        this.stepId = stepId;
        this.currentItem = currentItem;
        this.observedFiles = observedFiles;
        this.recentToolResults = recentToolResults;
        this.recoveryNotes = recoveryNotes;
        this.memoryContext = memoryContext;
    }
}
