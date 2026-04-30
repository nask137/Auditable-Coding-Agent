package com.nask.agent.workspace;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for registering a workspace root with the service.
 *
 * @param name optional display name; the service falls back to the directory name
 * @param rootPath local filesystem path used as the workspace trust boundary
 * @param trusted whether the workspace is trusted; defaults to true
 */
public record CreateWorkspaceRequest(
        String name,
        @NotBlank String rootPath,
        Boolean trusted) {
}
