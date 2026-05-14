package com.nask.agent.conversation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Compact history from earlier tasks in the same conversation.
 */
public record ConversationTaskContext(
        UUID taskId,
        String prompt,
        String status,
        String finalReport,
        List<String> affectedFiles,
        Instant createdAt) {
}
