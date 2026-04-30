package com.nask.agent.task;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateTaskRequest(
        @NotNull UUID workspaceId,
        String title,
        @NotBlank String userRequest) {
}
