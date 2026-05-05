package com.nask.agent.memory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Proposed long-term memory write awaiting user approval.
 */
public record MemoryWriteProposal(
        UUID id,
        UUID workspaceId,
        UUID taskId,
        UUID runId,
        String proposalType,
        String title,
        String content,
        List<SourceReference> sourceRefs,
        String status,
        UUID approvalRequestId,
        UUID projectMemoryItemId,
        Instant createdAt,
        Instant resolvedAt,
        Map<String, Object> metadata) {
}
