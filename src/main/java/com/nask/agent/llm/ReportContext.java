package com.nask.agent.llm;

import java.util.UUID;

public record ReportContext(UUID taskId, UUID runId, String taskSummary, String resultSummary) {
}
