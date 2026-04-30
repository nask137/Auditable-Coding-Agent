package com.nask.agent.task;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request body for creating a coding task.
 *
 * @param workspaceId workspace that bounds all file and command operations
 * @param title optional display title
 * @param userRequest natural-language instruction for the agent
 */
public record CreateTaskRequest(
        @NotNull UUID workspaceId,
        String title,
        @NotBlank String userRequest) {
}
