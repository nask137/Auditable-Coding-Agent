package com.nask.agent.llm;

import com.nask.agent.memory.MemoryContext;

import java.util.List;
import java.util.UUID;

/**
 * Context supplied to the model when creating a plan.
 */
public record PlanningContext(UUID taskId, UUID runId, TaskUnderstanding understanding,
                              List<String> observedFiles, List<String> recoveryNotes,
                              MemoryContext memoryContext) {
    public PlanningContext(UUID taskId, UUID runId, TaskUnderstanding understanding,
                           List<String> observedFiles, List<String> recoveryNotes) {
        this(taskId, runId, understanding, observedFiles, recoveryNotes, null);
    }
}
