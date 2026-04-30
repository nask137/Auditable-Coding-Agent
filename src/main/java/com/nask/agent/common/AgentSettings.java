package com.nask.agent.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AgentSettings {
    private final int maxSteps;
    private final int maxToolCalls;
    private final int maxFileChanges;
    private final int maxPatchLines;
    private final int maxConsecutiveFailures;
    private final int commandTimeoutSeconds;
    private final int maxReadBytes;

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

    public int maxSteps() {
        return maxSteps;
    }

    public int maxToolCalls() {
        return maxToolCalls;
    }

    public int maxFileChanges() {
        return maxFileChanges;
    }

    public int maxPatchLines() {
        return maxPatchLines;
    }

    public int maxConsecutiveFailures() {
        return maxConsecutiveFailures;
    }

    public int commandTimeoutSeconds() {
        return commandTimeoutSeconds;
    }

    public int maxReadBytes() {
        return maxReadBytes;
    }
}
