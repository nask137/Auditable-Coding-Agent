package com.nask.agent.tool;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Persistent output of a tool call.
 */
public record ToolResultRecord(
        UUID id,
        UUID toolCallId,
        boolean success,
        String outputSummary,
        Map<String, Object> outputPayload,
        String errorMessage,
        Map<String, Object> metadata,
        Instant createdAt) {
}
