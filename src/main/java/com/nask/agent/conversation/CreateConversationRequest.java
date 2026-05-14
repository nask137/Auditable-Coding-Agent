package com.nask.agent.conversation;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request body for creating a conversation.
 */
public record CreateConversationRequest(
        @NotNull UUID workspaceId,
        String title) {
}
