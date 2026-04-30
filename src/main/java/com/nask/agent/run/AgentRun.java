package com.nask.agent.run;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AgentRun(
        UUID id,
        UUID taskId,
        String agentMode,
        String status,
        Instant startedAt,
        Instant finishedAt,
        String failureReason,
        Map<String, Object> runtimeMetadata) {
}
