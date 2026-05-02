package com.nask.agent.git;

import com.nask.agent.audit.AuditEventDraft;
import com.nask.agent.audit.AuditService;
import com.nask.agent.common.AgentSettings;
import com.nask.agent.common.Domain;
import com.nask.agent.permission.PermissionService;
import com.nask.agent.tool.ToolExecutionContext;
import com.nask.agent.tool.ToolExecutionResult;
import com.nask.agent.tool.ToolCallRecord;
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
    private final PermissionService permissionService;
    private final ToolRecordRepository toolRecords;
    private final AuditService auditService;
    private final AgentSettings settings;

    /**
     * Creates the Git tool service.
     */
    public GitToolService(WorkspacePathGuard pathGuard, PermissionService permissionService, ToolRecordRepository toolRecords,
                          AuditService auditService, AgentSettings settings) {
        this.pathGuard = pathGuard;
        this.permissionService = permissionService;
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
        var cwd = workingDirectory == null || workingDirectory.isBlank() ? "." : workingDirectory;
        var call = startTool(context, "git_diff", "Git diff", cwd);
        try {
            var cwdCheck = pathGuard.check(context.workspace(), cwd, false);
            if (!cwdCheck.allowed()) {
                complete(call.id(), false, cwdCheck.reason(), Map.of(), cwdCheck.reason());
                return ToolExecutionResult.blocked(cwdCheck.reason());
            }
            var names = execute(safeGitArguments(List.of("diff", "--no-ext-diff", "--no-textconv", "--name-only", "-z", "--")),
                    cwdCheck.absolutePath().toFile());
            if (names.exitCode() != 0) {
                var payload = Map.<String, Object>of("exitCode", names.exitCode(), "output", names.output());
                complete(call.id(), false, summary("git_diff", names), payload, names.output());
                return ToolExecutionResult.blocked(summary("git_diff", names));
            }

            var allowedOutput = new StringBuilder();
            var included = new ArrayList<String>();
            var filtered = new ArrayList<String>();
            for (var path : parseNullSeparated(names.output())) {
                if (path.isBlank()) {
                    continue;
                }
                var check = pathGuard.check(context.workspace(), path, false);
                var decision = permissionService.fileDecision(check, Domain.FileOperation.FILE_READ, false, 0);
                auditService.append(new AuditEventDraft(context.taskId(), context.runId(), context.stepId(), context.actionId(),
                        Domain.AuditEventType.PermissionChecked, Domain.AuditActor.RUNTIME, Domain.AuditLevel.INFO,
                        "Check git diff path permission", decision.reason(), List.of(path), call.id(), null, null,
                        null, Domain.PermissionLevel.GIT_READ, decision.riskLevel(), null,
                        decision.decision() == Domain.PermissionDecisionType.ALLOW, null, null,
                        Map.of("decision", decision.decision().name())));
                if (decision.decision() != Domain.PermissionDecisionType.ALLOW) {
                    filtered.add(path);
                    auditService.append(new AuditEventDraft(context.taskId(), context.runId(), context.stepId(), context.actionId(),
                            Domain.AuditEventType.PermissionBlocked, Domain.AuditActor.RUNTIME, Domain.AuditLevel.WARN,
                            "Git diff path filtered", decision.reason(), List.of(path), call.id(), null, null,
                            null, Domain.PermissionLevel.GIT_READ, decision.riskLevel(), null,
                            false, "GIT_DIFF_PATH_FILTERED", decision.reason(), Map.of()));
                    continue;
                }
                var fileDiff = execute(safeGitArguments(List.of("diff", "--no-ext-diff", "--no-textconv", "--", path)),
                        cwdCheck.absolutePath().toFile());
                if (fileDiff.exitCode() != 0) {
                    var payload = Map.<String, Object>of("exitCode", fileDiff.exitCode(), "output", fileDiff.output(),
                            "path", path);
                    complete(call.id(), false, summary("git_diff", fileDiff), payload, fileDiff.output());
                    return ToolExecutionResult.blocked(summary("git_diff", fileDiff));
                }
                included.add(path);
                allowedOutput.append(fileDiff.output());
                if (!fileDiff.output().endsWith("\n")) {
                    allowedOutput.append('\n');
                }
            }

            if (!filtered.isEmpty()) {
                allowedOutput.append("\nFiltered git diff paths: ").append(filtered).append('\n');
            }
            var output = truncate(allowedOutput.toString());
            var payload = Map.<String, Object>of("exitCode", 0, "output", output, "includedFiles", included,
                    "filteredFiles", filtered);
            var result = new ProcessResult(0, output);
            complete(call.id(), true, summary("git_diff", result), payload, null);
            return ToolExecutionResult.success(summary("git_diff", result), payload);
        } catch (Exception e) {
            complete(call.id(), false, e.getMessage(), Map.of(), e.getMessage());
            return ToolExecutionResult.blocked(e.getMessage());
        }
    }

    private ToolExecutionResult runGit(ToolExecutionContext context, String toolName, String inputSummary,
                                       String workingDirectory, List<String> arguments) {
        var cwd = workingDirectory == null || workingDirectory.isBlank() ? "." : workingDirectory;
        var call = startTool(context, toolName, inputSummary, cwd);
        try {
            var cwdCheck = pathGuard.check(context.workspace(), cwd, false);
            if (!cwdCheck.allowed()) {
                complete(call.id(), false, cwdCheck.reason(), Map.of(), cwdCheck.reason());
                return ToolExecutionResult.blocked(cwdCheck.reason());
            }
            var result = execute(safeGitArguments(arguments), cwdCheck.absolutePath().toFile());
            var success = result.exitCode() == 0;
            var payload = Map.<String, Object>of("exitCode", result.exitCode(), "output", result.output());
            complete(call.id(), success, summary(toolName, result), payload, success ? null : result.output());
            return success
                    ? ToolExecutionResult.success(summary(toolName, result), payload)
                    : ToolExecutionResult.blocked(summary(toolName, result));
        } catch (Exception e) {
            complete(call.id(), false, e.getMessage(), Map.of(), e.getMessage());
            return ToolExecutionResult.blocked(e.getMessage());
        }
    }

    private ToolCallRecord startTool(ToolExecutionContext context, String toolName, String inputSummary, String cwd) {
        var call = toolRecords.insertCall(context.actionId(), toolName, Domain.PermissionLevel.GIT_READ,
                inputSummary, Map.of("workingDirectory", cwd));
        auditService.append(new AuditEventDraft(context.taskId(), context.runId(), context.stepId(), context.actionId(),
                Domain.AuditEventType.ToolCallRequested, Domain.AuditActor.AGENT, Domain.AuditLevel.INFO,
                inputSummary, toolName, List.of(), call.id(), null, null, null,
                Domain.PermissionLevel.GIT_READ, Domain.RiskLevel.LOW, null, true, null, null, Map.of()));
        return call;
    }

    private List<String> safeGitArguments(List<String> gitArguments) {
        var args = new ArrayList<String>();
        args.add("-c");
        args.add("core.fsmonitor=false");
        args.add("-c");
        args.add("core.untrackedCache=false");
        args.add("-c");
        args.add("diff.external=");
        args.addAll(gitArguments);
        return args;
    }

    private ProcessResult execute(List<String> gitArguments, java.io.File cwd) {
        try {
            var command = new ArrayList<String>();
            command.add("git");
            command.add("--no-optional-locks");
            command.addAll(gitArguments);
            var started = new ProcessBuilder(command)
                    .directory(cwd)
                    .redirectErrorStream(true);
            started.environment().put("GIT_EXTERNAL_DIFF", "");
            started.environment().put("GIT_PAGER", "cat");
            started.environment().put("GIT_TERMINAL_PROMPT", "0");
            started.environment().put("GIT_OPTIONAL_LOCKS", "0");
            var processInstance = started
                    .start();
            var outputFuture = CompletableFuture.supplyAsync(() -> readOutput(processInstance.getInputStream()));
            var finished = processInstance.waitFor(settings.commandTimeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                processInstance.destroyForcibly();
                return new ProcessResult(124, truncate("Git command timed out after "
                        + settings.commandTimeoutSeconds() + " seconds\n" + outputFuture.getNow("")));
            }
            return new ProcessResult(processInstance.exitValue(), truncate(outputFuture.get(5, TimeUnit.SECONDS)));
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

    private List<String> parseNullSeparated(String output) {
        if (output == null || output.isEmpty()) {
            return List.of();
        }
        return java.util.Arrays.stream(output.split("\u0000"))
                .filter(value -> !value.isBlank())
                .toList();
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
