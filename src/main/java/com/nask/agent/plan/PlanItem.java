package com.nask.agent.plan;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Single executable item in a generated plan.
 *
 * @param id plan item identifier
 * @param planId owning plan
 * @param description action-oriented item description
 * @param status lifecycle status from {@link com.nask.agent.common.Domain.PlanItemStatus}
 * @param relatedFiles files the model believes are relevant
 * @param notes model notes for developers and audit readers
 * @param orderIndex stable execution order, starting at 1
 * @param createdAt creation timestamp
 * @param updatedAt last update timestamp
 */
public record PlanItem(
        UUID id,
        UUID planId,
        String description,
        String status,
        List<String> relatedFiles,
        String notes,
        int orderIndex,
        Instant createdAt,
        Instant updatedAt) {
}
