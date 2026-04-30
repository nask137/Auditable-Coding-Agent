package com.nask.agent.workspace;

/**
 * Raised when a path cannot be safely resolved within a workspace.
 */
public class WorkspacePathException extends RuntimeException {
    /**
     * Creates an exception with a user-facing path validation message.
     */
    public WorkspacePathException(String message) {
        super(message);
    }
}
