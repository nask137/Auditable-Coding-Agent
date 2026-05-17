package com.nask.agent.llm;

import java.util.UUID;
import java.util.List;

/**
 * Context supplied to the model when drafting the final report.
 */
public record ReportContext(UUID taskId, UUID runId, String taskSummary, String resultSummary,
                            List<String> workflowSummaries, List<String> changedFiles,
                            List<String> previousConversationPrompts, List<String> recentToolObservations) {
    public ReportContext(UUID taskId, UUID runId, String taskSummary, String resultSummary) {
        this(taskId, runId, taskSummary, resultSummary, List.of(), List.of(), List.of(), List.of());
    }

    public ReportContext {
        workflowSummaries = workflowSummaries == null ? List.of() : List.copyOf(workflowSummaries);
        changedFiles = changedFiles == null ? List.of() : List.copyOf(changedFiles);
        previousConversationPrompts = previousConversationPrompts == null
                ? List.of() : List.copyOf(previousConversationPrompts);
        recentToolObservations = recentToolObservations == null ? List.of() : List.copyOf(recentToolObservations);
    }
}
