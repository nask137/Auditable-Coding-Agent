package com.nask.agent.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final int maxModelRetries;
    private final int maxReplanAttempts;
    private final int maxUserInputRequestsPerRun;
    private final int commandTimeoutSeconds;
    private final int maxReadBytes;
    private final int projectScanMaxFiles;
    private final int projectScanMaxFileBytes;
    private final int projectScanMaxTotalBytes;

    /**
     * Test-friendly constructor preserving the phase 1 parameter list.
     */
    public AgentSettings(int maxSteps, int maxToolCalls, int maxFileChanges, int maxPatchLines,
                         int maxConsecutiveFailures, int commandTimeoutSeconds, int maxReadBytes) {
        this(maxSteps, maxToolCalls, maxFileChanges, maxPatchLines, maxConsecutiveFailures,
                2, 2, 3, commandTimeoutSeconds, maxReadBytes, 2000, 262144, 10485760);
    }

    /**
     * Test-friendly constructor preserving the phase 2 parameter list.
     */
    public AgentSettings(int maxSteps, int maxToolCalls, int maxFileChanges, int maxPatchLines,
                         int maxConsecutiveFailures, int maxModelRetries, int maxReplanAttempts,
                         int maxUserInputRequestsPerRun, int commandTimeoutSeconds, int maxReadBytes) {
        this(maxSteps, maxToolCalls, maxFileChanges, maxPatchLines, maxConsecutiveFailures, maxModelRetries,
                maxReplanAttempts, maxUserInputRequestsPerRun, commandTimeoutSeconds, maxReadBytes,
                2000, 262144, 10485760);
    }

    /**
     * Creates immutable settings from Spring configuration values.
     */
    @Autowired
    public AgentSettings(
            @Value("${agent.loop.max-steps:20}") int maxSteps,
            @Value("${agent.loop.max-tool-calls:50}") int maxToolCalls,
            @Value("${agent.loop.max-file-changes:5}") int maxFileChanges,
            @Value("${agent.loop.max-patch-lines:300}") int maxPatchLines,
            @Value("${agent.loop.max-consecutive-failures:3}") int maxConsecutiveFailures,
            @Value("${agent.loop.max-model-retries:2}") int maxModelRetries,
            @Value("${agent.loop.max-replan-attempts:2}") int maxReplanAttempts,
            @Value("${agent.loop.max-user-input-requests-per-run:3}") int maxUserInputRequestsPerRun,
            @Value("${agent.command.timeout-seconds:120}") int commandTimeoutSeconds,
            @Value("${agent.file.max-read-bytes:200000}") int maxReadBytes,
            @Value("${agent.project-scan.max-files:2000}") int projectScanMaxFiles,
            @Value("${agent.project-scan.max-file-bytes:262144}") int projectScanMaxFileBytes,
            @Value("${agent.project-scan.max-total-bytes:10485760}") int projectScanMaxTotalBytes) {
        this.maxSteps = maxSteps;
        this.maxToolCalls = maxToolCalls;
        this.maxFileChanges = maxFileChanges;
        this.maxPatchLines = maxPatchLines;
        this.maxConsecutiveFailures = maxConsecutiveFailures;
        this.maxModelRetries = maxModelRetries;
        this.maxReplanAttempts = maxReplanAttempts;
        this.maxUserInputRequestsPerRun = maxUserInputRequestsPerRun;
        this.commandTimeoutSeconds = commandTimeoutSeconds;
        this.maxReadBytes = maxReadBytes;
        this.projectScanMaxFiles = projectScanMaxFiles;
        this.projectScanMaxFileBytes = projectScanMaxFileBytes;
        this.projectScanMaxTotalBytes = projectScanMaxTotalBytes;
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
     * Maximum retry attempts for one model decision before escalation.
     */
    public int maxModelRetries() {
        return maxModelRetries;
    }

    /**
     * Maximum replan attempts for one run before user guidance is required.
     */
    public int maxReplanAttempts() {
        return maxReplanAttempts;
    }

    /**
     * Maximum unresolved user-input requests allowed for one run.
     */
    public int maxUserInputRequestsPerRun() {
        return maxUserInputRequestsPerRun;
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

    /**
     * Maximum number of regular files visited by one project scan.
     */
    public int projectScanMaxFiles() {
        return projectScanMaxFiles;
    }

    /**
     * Maximum bytes read from one file during project scanning.
     */
    public int projectScanMaxFileBytes() {
        return projectScanMaxFileBytes;
    }

    /**
     * Maximum aggregate bytes read during one project scan.
     */
    public int projectScanMaxTotalBytes() {
        return projectScanMaxTotalBytes;
    }
}
