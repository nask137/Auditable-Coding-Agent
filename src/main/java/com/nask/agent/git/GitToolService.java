package com.nask.agent.git;

import com.nask.agent.audit.AuditEventDraft;
import com.nask.agent.audit.AuditService;
import com.nask.agent.common.AgentSettings;
import com.nask.agent.common.Domain;
import com.nask.agent.tool.ToolExecutionContext;
import com.nask.agent.tool.ToolExecutionResult;
import com.nask.agent.tool.ToolRecordRepository;
import com.nask.agent.workspace.WorkspacePathGuard;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Read-only Git inspection tools scoped to the workspace root.
 */
@Service
public class GitToolService {
    private final WorkspacePathGuard pathGuard;
    private final ToolRecordRepository toolRecords;
    private final AuditService auditService;
    private final AgentSettings settings;

    /**
     * Creates the Git tool service.
     */
    public GitToolService(WorkspacePathGuard pathGuard, ToolRecordRepository toolRecords,
                          AuditService auditService, AgentSettings settings) {
        this.pathGuard = pathGuard;
        this.toolRecords = toolRecords;
        this.auditService = auditService;
        this.settings = settings;
    }

    /**
     * Runs {@code git status --short} inside a workspace-relative directory.
     */
    public ToolExecutionResult status(ToolExecutionContext context, String workingDirectory) {
        return runGit(context, "git_status", "Git status", workingDirectory, List.of("status", "--short"));
    }

    /**
     * Runs {@code git diff --} inside a workspace-relative directory.
     */
    public ToolExecutionResult diff(ToolExecutionContext context, String workingDirectory) {
        return runGit(context, "git_diff", "Git diff", workingDirectory, List.of("diff", "--"));
    }

    private ToolExecutionResult runGit(ToolExecutionContext context, String toolName, String inputSummary,
                                       String workingDirectory, List<String> arguments) {
        var cwd = workingDirectory == null || workingDirectory.isBlank() ? "." : workingDirectory;
        var call = toolRecords.insertCall(context.actionId(), toolName, Domain.PermissionLevel.GIT_READ,
                inputSummary, Map.of("workingDirectory", cwd));
        auditService.append(new AuditEventDraft(context.taskId(), context.runId(), context.stepId(), context.actionId(),
                Domain.AuditEventType.ToolCallRequested, Domain.AuditActor.AGENT, Domain.AuditLevel.INFO,
                inputSummary, toolName, List.of(), call.id(), null, null, null,
                Domain.PermissionLevel.GIT_READ, Domain.RiskLevel.LOW, null, true, null, null, Map.of()));
        try {
            var cwdCheck = pathGuard.check(context.workspace(), cwd, false);
            if (!cwdCheck.allowed()) {
                complete(call.id(), false, cwdCheck.reason(), Map.of(), cwdCheck.reason());
                return ToolExecutionResult.blocked(cwdCheck.reason());
            }
            var result = execute(arguments, cwdCheck.absolutePath().toFile());
            var success = result.exitCode() == 0;
            var payload = Map.<String, Object>of("exitCode", result.exitCode(), "output", result.output());
            complete(call.id(), success, summary(toolName, result), payload, success ? null : result.output());
            return ToolExecutionResult.success(summary(toolName, result), payload);
        } catch (Exception e) {
            complete(call.id(), false, e.getMessage(), Map.of(), e.getMessage());
            return ToolExecutionResult.blocked(e.getMessage());
        }
    }

    private ProcessResult execute(List<String> gitArguments, java.io.File cwd) {
        try {
            var command = new ArrayList<String>();
            command.add("git");
            command.addAll(gitArguments);
            var process = new ProcessBuilder(command)
                    .directory(cwd)
                    .redirectErrorStream(true)
                    .start();
            var outputFuture = CompletableFuture.supplyAsync(() -> readOutput(process.getInputStream()));
            var finished = process.waitFor(settings.commandTimeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new ProcessResult(124, truncate("Git command timed out after "
                        + settings.commandTimeoutSeconds() + " seconds\n" + outputFuture.getNow("")));
            }
            return new ProcessResult(process.exitValue(), truncate(outputFuture.get(5, TimeUnit.SECONDS)));
        } catch (Exception e) {
            return new ProcessResult(1, e.getMessage());
        }
    }

    private String readOutput(InputStream stream) {
        try {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    private void complete(java.util.UUID callId, boolean success, String summary, Map<String, Object> payload, String error) {
        toolRecords.completeCall(callId, success ? Domain.ToolCallStatus.COMPLETED : Domain.ToolCallStatus.FAILED);
        toolRecords.insertResult(callId, success, summary, payload, error, Map.of());
    }

    private String summary(String toolName, ProcessResult result) {
        var output = result.output().isBlank() ? "(no output)" : result.output();
        return toolName + " exit " + result.exitCode() + ": " + output;
    }

    private String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= settings.maxReadBytes() ? value : value.substring(0, settings.maxReadBytes());
    }

    private record ProcessResult(int exitCode, String output) {
    }
}
