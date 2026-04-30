package com.nask.agent.tool;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ToolCallRecord(
        UUID id,
        UUID actionId,
        String toolName,
        String permissionLevel,
        String inputSummary,
        Map<String, Object> inputPayload,
        String status,
        Instant startedAt,
        Instant finishedAt) {
}
