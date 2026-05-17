package com.nask.agent.llm;

import com.nask.agent.memory.MemoryContext;

import java.util.List;
import java.util.UUID;

/**
 * Context supplied when the model chooses a validation command.
 */
public record ValidationContext(UUID taskId, UUID runId, UUID workspaceId, List<String> recoveryNotes,
                                MemoryContext memoryContext, String taskType, String userRequest,
                                List<String> changedFiles, List<String> recentCommands) {
    public ValidationContext {
        recoveryNotes = recoveryNotes == null ? List.of() : List.copyOf(recoveryNotes);
        changedFiles = changedFiles == null ? List.of() : List.copyOf(changedFiles);
        recentCommands = recentCommands == null ? List.of() : List.copyOf(recentCommands);
    }

    public ValidationContext(UUID taskId, UUID runId, UUID workspaceId, List<String> recoveryNotes) {
        this(taskId, runId, workspaceId, recoveryNotes, null);
    }

    public ValidationContext(UUID taskId, UUID runId, UUID workspaceId, List<String> recoveryNotes,
                             MemoryContext memoryContext) {
        this(taskId, runId, workspaceId, recoveryNotes, memoryContext, "OTHER", "",
                List.of(), List.of());
    }
}
