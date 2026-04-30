package com.nask.agent.report;

import java.time.Instant;
import java.util.UUID;

public record TaskReport(UUID id, UUID taskId, UUID runId, String contentMd, Instant createdAt) {
}
