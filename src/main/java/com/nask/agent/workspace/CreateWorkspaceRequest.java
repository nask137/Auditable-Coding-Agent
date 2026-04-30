package com.nask.agent.workspace;

import jakarta.validation.constraints.NotBlank;

public record CreateWorkspaceRequest(
        String name,
        @NotBlank String rootPath,
        Boolean trusted) {
}
