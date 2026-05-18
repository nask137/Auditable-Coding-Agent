package com.nask.agent.conversation;

import java.util.List;

/**
 * Budgeted conversation history prepared for model prompts and status output.
 */
public record ConversationContextWindow(
        List<ConversationTaskContext> tasks,
        int usedBytes,
        int rawBytes,
        int maxBytes,
        boolean compressed,
        int tasksIncluded,
        int tasksAvailable) {
}
