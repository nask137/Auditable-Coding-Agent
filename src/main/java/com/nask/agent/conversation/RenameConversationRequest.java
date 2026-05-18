package com.nask.agent.conversation;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for renaming a conversation.
 */
public record RenameConversationRequest(@NotBlank String title) {
}
