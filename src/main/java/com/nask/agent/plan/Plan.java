package com.nask.agent.plan;

import java.time.Instant;
import java.util.UUID;

/**
 * Generated plan for a run.
 *
 * @param id plan identifier
 * @param taskId owning task
 * @param runId owning run
 * @param status lifecycle status from {@link com.nask.agent.common.Domain.PlanStatus}
 * @param createdAt creation timestamp
 * @param updatedAt last update timestamp
 */
public record Plan(
        UUID id,
        UUID taskId,
        UUID runId,
        String status,
        Instant createdAt,
        Instant updatedAt) {
}
