package com.nask.agent.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Centralized runtime limits for the agent loop and tool execution.
 *
 * <p>Values are injected from {@code application.properties}; defaults keep the
 * Phase 1 loop bounded even when no explicit configuration is provided.</p>
 */
@Component
public class AgentSettings {
    private final int maxSteps;
    private final int maxToolCalls;
    private final int maxFileChanges;
    private final int maxPatchLines;
    private final int maxConsecutiveFailures;
    private final int commandTimeoutSeconds;
    private final int maxReadBytes;

    /**
     * Creates immutable settings from Spring configuration values.
     */
    public AgentSettings(
            @Value("${agent.loop.max-steps:20}") int maxSteps,
            @Value("${agent.loop.max-tool-calls:50}") int maxToolCalls,
            @Value("${agent.loop.max-file-changes:5}") int maxFileChanges,
            @Value("${agent.loop.max-patch-lines:300}") int maxPatchLines,
            @Value("${agent.loop.max-consecutive-failures:3}") int maxConsecutiveFailures,
            @Value("${agent.command.timeout-seconds:120}") int commandTimeoutSeconds,
            @Value("${agent.file.max-read-bytes:200000}") int maxReadBytes) {
        this.maxSteps = maxSteps;
        this.maxToolCalls = maxToolCalls;
        this.maxFileChanges = maxFileChanges;
        this.maxPatchLines = maxPatchLines;
        this.maxConsecutiveFailures = maxConsecutiveFailures;
        this.commandTimeoutSeconds = commandTimeoutSeconds;
        this.maxReadBytes = maxReadBytes;
    }

    /**
     * Maximum number of plan items the fixed loop may execute in a run.
     */
    public int maxSteps() {
        return maxSteps;
    }

    /**
     * Maximum number of tool calls allowed for a run.
     */
    public int maxToolCalls() {
        return maxToolCalls;
    }

    /**
     * Maximum number of file changes allowed before the loop blocks.
     */
    public int maxFileChanges() {
        return maxFileChanges;
    }

    /**
     * Patch size threshold used by permission logic.
     */
    public int maxPatchLines() {
        return maxPatchLines;
    }

    /**
     * Failure threshold reserved for loop implementations that retry actions.
     */
    public int maxConsecutiveFailures() {
        return maxConsecutiveFailures;
    }

    /**
     * Wall-clock timeout for a spawned process.
     */
    public int commandTimeoutSeconds() {
        return commandTimeoutSeconds;
    }

    /**
     * Maximum number of characters returned by a file read tool call.
     */
    public int maxReadBytes() {
        return maxReadBytes;
    }
}
