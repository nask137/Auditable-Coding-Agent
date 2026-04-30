package com.nask.agent.plan;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
