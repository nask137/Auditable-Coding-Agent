package com.nask.agent.cli;

/**
 * Runtime defaults shared by the terminal CLI and dashboard settings page.
 */
public record CliRuntimeSettings(
        String baseUrl,
        String workspaceId,
        String workflow,
        String permissionPreset,
        String model,
        String profile) {
    public static CliRuntimeSettings defaults() {
        return new CliRuntimeSettings("http://localhost:8080", "", "coding-agent",
                "workspace-write", "", "default");
    }
}
