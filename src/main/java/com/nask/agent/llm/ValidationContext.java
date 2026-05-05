package com.nask.agent.llm;

import com.nask.agent.memory.MemoryContext;

import java.util.List;
import java.util.UUID;

/**
 * Context supplied when the model chooses a validation command.
 */
public record ValidationContext(UUID taskId, UUID runId, UUID workspaceId, List<String> recoveryNotes,
                                MemoryContext memoryContext) {
    public ValidationContext(UUID taskId, UUID runId, UUID workspaceId, List<String> recoveryNotes) {
        this(taskId, runId, workspaceId, recoveryNotes, null);
    }
}
