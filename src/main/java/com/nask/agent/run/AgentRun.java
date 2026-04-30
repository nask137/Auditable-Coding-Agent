package com.nask.agent.run;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Concrete execution attempt for a task.
 *
 * @param id run identifier
 * @param taskId owning task
 * @param agentMode mode name used by the executor
 * @param status lifecycle status from {@link com.nask.agent.common.Domain.AgentRunStatus}
 * @param startedAt start timestamp
 * @param finishedAt terminal timestamp, if complete or failed
 * @param failureReason terminal failure reason, if any
 * @param runtimeMetadata implementation-specific details useful for audit/debugging
 */
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
