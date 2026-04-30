package com.nask.agent.llm;

import java.util.UUID;

/**
 * Context supplied to the model when drafting the final report.
 */
public record ReportContext(UUID taskId, UUID runId, String taskSummary, String resultSummary) {
}
