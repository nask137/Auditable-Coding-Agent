package com.nask.agent.conversation;

import java.time.Instant;
import java.util.UUID;

/**
 * User-facing conversation that groups related task prompts in one workspace.
 */
public record Conversation(
        UUID id,
        UUID workspaceId,
        String title,
        Instant createdAt,
        Instant updatedAt) {
}
