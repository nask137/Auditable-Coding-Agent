package com.nask.agent.git;

import com.nask.agent.approval.ApprovalService;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
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
    private final ApprovalService approvalService;
    private final ToolRecordRepository toolRecords;
    private final AuditService auditService;
    private final AgentSettings settings;

    /**
     * Creates the Git tool service.
     */
    @Autowired
    public GitToolService(WorkspacePathGuard pathGuard, PermissionService permissionService,
                          ApprovalService approvalService, ToolRecordRepository toolRecords,
                          AuditService auditService, AgentSettings settings) {
        this.pathGuard = pathGuard;
        this.permissionService = permissionService;
        this.approvalService = approvalService;
        this.toolRecords = toolRecords;
        this.auditService = auditService;
        this.settings = settings;
    }

    /**
     * Backward-compatible constructor for tests that do not exercise approvals.
     */
    public GitToolService(WorkspacePathGuard pathGuard, PermissionService permissionService,
                          ToolRecordRepository toolRecords, AuditService auditService, AgentSettings settings) {
        this(pathGuard, permissionService, null, toolRecords, auditService, settings);
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
            var gitRoot = resolveGitRoot(cwdCheck.absolutePath().toFile());
            if (gitRoot.exitCode() != 0) {
                var payload = Map.<String, Object>of("exitCode", gitRoot.exitCode(), "output", gitRoot.output());
                complete(call.id(), false, summary("git_diff", gitRoot), payload, gitRoot.output());
                return ToolExecutionResult.blocked(summary("git_diff", gitRoot));
            }
            var repoRoot = Path.of(firstLine(gitRoot.output())).toAbsolutePath().normalize();
            var workspaceRoot = Path.of(context.workspace().rootPath()).toAbsolutePath().normalize();
            if (!repoRoot.startsWith(workspaceRoot)) {
                var reason = "Git repository root is outside trusted workspace";
                complete(call.id(), false, reason, Map.of(), reason);
                return ToolExecutionResult.blocked(reason);
            }
            var names = execute(safeGitArguments(List.of("diff", "--no-ext-diff", "--no-textconv", "--name-only", "-z", "--")),
                    repoRoot.toFile());
            if (names.exitCode() != 0) {
                var payload = Map.<String, Object>of("exitCode", names.exitCode(), "output", names.output());
                complete(call.id(), false, summary("git_diff", names), payload, names.output());
                return ToolExecutionResult.blocked(summary("git_diff", names));
            }

            var allowedOutput = new StringBuilder();
            var included = new ArrayList<String>();
            var filtered = new ArrayList<String>();
            for (var repoPath : parseNullSeparated(names.output())) {
                if (repoPath.isBlank()) {
                    continue;
                }
                var workspacePath = workspaceRoot.relativize(repoRoot.resolve(repoPath).toAbsolutePath().normalize())
                        .toString().replace('\\', '/');
                var check = pathGuard.check(context.workspace(), workspacePath, false);
                var decision = permissionService.fileDecision(check, Domain.FileOperation.FILE_READ, false, 0);
                auditService.append(new AuditEventDraft(context.taskId(), context.runId(), context.stepId(), context.actionId(),
                        Domain.AuditEventType.PermissionChecked, Domain.AuditActor.RUNTIME, Domain.AuditLevel.INFO,
                        "Check git diff path permission", decision.reason(), List.of(workspacePath), call.id(), null, null,
                        null, Domain.PermissionLevel.GIT_READ, decision.riskLevel(), null,
                        decision.decision() == Domain.PermissionDecisionType.ALLOW, null, null,
                        Map.of("decision", decision.decision().name())));
                if (decision.decision() != Domain.PermissionDecisionType.ALLOW) {
                    filtered.add(workspacePath);
                    auditService.append(new AuditEventDraft(context.taskId(), context.runId(), context.stepId(), context.actionId(),
                            Domain.AuditEventType.PermissionBlocked, Domain.AuditActor.RUNTIME, Domain.AuditLevel.WARN,
                            "Git diff path filtered", decision.reason(), List.of(workspacePath), call.id(), null, null,
                            null, Domain.PermissionLevel.GIT_READ, decision.riskLevel(), null,
                            false, "GIT_DIFF_PATH_FILTERED", decision.reason(), Map.of()));
                    continue;
                }
                var fileDiff = execute(safeGitArguments(List.of("diff", "--no-ext-diff", "--no-textconv", "--", repoPath)),
                        repoRoot.toFile());
                if (fileDiff.exitCode() != 0) {
                    var payload = Map.<String, Object>of("exitCode", fileDiff.exitCode(), "output", fileDiff.output(),
                            "path", workspacePath);
                    complete(call.id(), false, summary("git_diff", fileDiff), payload, fileDiff.output());
                    return ToolExecutionResult.blocked(summary("git_diff", fileDiff));
                }
                included.add(workspacePath);
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

    /**
     * Stages specific paths, or all currently changed paths when paths is empty.
     */
    public ToolExecutionResult add(ToolExecutionContext context, String workingDirectory, List<String> paths) {
        return runGitWrite(context, "git_add", "Git add", workingDirectory,
                () -> addArguments(context, workingDirectory, paths), Domain.RiskLevel.HIGH);
    }

    /**
     * Creates a local commit with the supplied message.
     */
    public ToolExecutionResult commit(ToolExecutionContext context, String workingDirectory, String message) {
        if (message == null || message.isBlank()) {
            return ToolExecutionResult.blocked("Git commit message is required");
        }
        return runGitWrite(context, "git_commit", "Git commit", workingDirectory,
                () -> List.of("commit", "--no-verify", "-m", message), Domain.RiskLevel.HIGH);
    }

    /**
     * Pushes commits to a remote. Networked Git operations require approval.
     */
    public ToolExecutionResult push(ToolExecutionContext context, String workingDirectory, String remote, String branch) {
        return runGitWrite(context, "git_push", "Git push", workingDirectory,
                () -> remoteBranchArguments("push", remote, branch), Domain.RiskLevel.CRITICAL);
    }

    /**
     * Pulls from a remote. Networked Git operations require approval.
     */
    public ToolExecutionResult pull(ToolExecutionContext context, String workingDirectory, String remote, String branch) {
        return runGitWrite(context, "git_pull", "Git pull", workingDirectory,
                () -> remoteBranchArguments("pull", remote, branch), Domain.RiskLevel.CRITICAL);
    }

    /**
     * Fetches from a remote.
     */
    public ToolExecutionResult fetch(ToolExecutionContext context, String workingDirectory, String remote) {
        return runGitWrite(context, "git_fetch", "Git fetch", workingDirectory,
                () -> optionalRemoteArguments("fetch", remote), Domain.RiskLevel.HIGH);
    }

    /**
     * Shows recent commit history.
     */
    public ToolExecutionResult log(ToolExecutionContext context, String workingDirectory, int maxCount) {
        var count = Math.max(1, Math.min(maxCount <= 0 ? 10 : maxCount, 50));
        return runGit(context, "git_log", "Git log", workingDirectory,
                List.of("log", "--oneline", "--decorate", "--max-count=" + count));
    }

    /**
     * Shows one revision.
     */
    public ToolExecutionResult show(ToolExecutionContext context, String workingDirectory, String revision) {
        var ref = revision == null || revision.isBlank() ? "HEAD" : revision;
        if (ref.startsWith("-")) {
            return ToolExecutionResult.blocked("Git show revision must not be an option: " + ref);
        }
        return runGit(context, "git_show", "Git show", workingDirectory,
                List.of("show", "--no-ext-diff", "--no-textconv", ref));
    }

    /**
     * Lists local and remote branches.
     */
    public ToolExecutionResult branch(ToolExecutionContext context, String workingDirectory) {
        return runGit(context, "git_branch", "Git branch", workingDirectory, List.of("branch", "--all"));
    }

    /**
     * Checks out a branch or revision.
     */
    public ToolExecutionResult checkout(ToolExecutionContext context, String workingDirectory, String ref) {
        if (ref == null || ref.isBlank()) {
            return ToolExecutionResult.blocked("Git checkout ref is required");
        }
        return runGitWrite(context, "git_checkout", "Git checkout", workingDirectory,
                () -> List.of("checkout", ref), Domain.RiskLevel.HIGH);
    }

    /**
     * Reads real Git working tree status without creating tool/audit records.
     */
    public GitWorkspaceStatus inspectWorkspaceStatus(com.nask.agent.workspace.Workspace workspace, String workingDirectory) {
        var cwd = workingDirectory == null || workingDirectory.isBlank() ? "." : workingDirectory;
        try {
            var cwdCheck = pathGuard.check(workspace, cwd, false);
            if (!cwdCheck.allowed()) {
                return new GitWorkspaceStatus(false, cwdCheck.reason(), List.of(), List.of());
            }
            var gitRoot = resolveGitRoot(cwdCheck.absolutePath().toFile());
            if (gitRoot.exitCode() != 0) {
                return new GitWorkspaceStatus(false, gitRoot.output(), List.of(), List.of());
            }
            var repoRoot = Path.of(firstLine(gitRoot.output())).toAbsolutePath().normalize();
            var workspaceRoot = Path.of(workspace.rootPath()).toAbsolutePath().normalize();
            if (!repoRoot.startsWith(workspaceRoot)) {
                return new GitWorkspaceStatus(false, "Git repository root is outside trusted workspace", List.of(), List.of());
            }
            var status = execute(safeGitArguments(List.of("status", "--short")), repoRoot.toFile());
            if (status.exitCode() != 0) {
                return new GitWorkspaceStatus(false, status.output(), List.of(), List.of());
            }
            var lines = status.output().lines().filter(line -> !line.isBlank()).toList();
            var files = lines.stream().map(this::statusPath).filter(path -> !path.isBlank()).distinct().toList();
            return new GitWorkspaceStatus(true, "git_status exit 0", lines, files);
        } catch (Exception e) {
            return new GitWorkspaceStatus(false, e.getMessage(), List.of(), List.of());
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

    private ToolExecutionResult runGitWrite(ToolExecutionContext context, String toolName, String inputSummary,
                                            String workingDirectory, GitArgumentSupplier arguments,
                                            Domain.RiskLevel riskLevel) {
        var cwd = workingDirectory == null || workingDirectory.isBlank() ? "." : workingDirectory;
        var call = startTool(context, toolName, inputSummary, cwd, Domain.PermissionLevel.GIT_WRITE);
        try {
            var cwdCheck = pathGuard.check(context.workspace(), cwd, false);
            if (!cwdCheck.allowed()) {
                complete(call.id(), false, cwdCheck.reason(), Map.of(), cwdCheck.reason());
                return ToolExecutionResult.blocked(cwdCheck.reason());
            }
            var gitRoot = resolveGitRoot(cwdCheck.absolutePath().toFile());
            if (gitRoot.exitCode() != 0) {
                var payload = Map.<String, Object>of("exitCode", gitRoot.exitCode(), "output", gitRoot.output());
                complete(call.id(), false, summary(toolName, gitRoot), payload, gitRoot.output());
                return ToolExecutionResult.blocked(summary(toolName, gitRoot));
            }
            var repoRoot = Path.of(firstLine(gitRoot.output())).toAbsolutePath().normalize();
            var workspaceRoot = Path.of(context.workspace().rootPath()).toAbsolutePath().normalize();
            if (!repoRoot.startsWith(workspaceRoot)) {
                var reason = "Git repository root is outside trusted workspace";
                complete(call.id(), false, reason, Map.of(), reason);
                return ToolExecutionResult.blocked(reason);
            }
            var gitArguments = arguments.get();
            var commandText = "git " + String.join(" ", gitArguments);
            var approval = handleGitWriteApproval(context, call.id(), commandText, cwd, riskLevel);
            if (approval != null) {
                return approval;
            }
            var result = execute(safeGitArguments(gitArguments), repoRoot.toFile());
            var success = result.exitCode() == 0;
            var payload = Map.<String, Object>of("exitCode", result.exitCode(), "output", result.output(),
                    "command", commandText);
            complete(call.id(), success, summary(toolName, result), payload, success ? null : result.output());
            return success
                    ? ToolExecutionResult.success(summary(toolName, result), payload)
                    : ToolExecutionResult.blocked(summary(toolName, result));
        } catch (Exception e) {
            complete(call.id(), false, e.getMessage(), Map.of(), e.getMessage());
            return ToolExecutionResult.blocked(e.getMessage());
        }
    }

    private ToolExecutionResult handleGitWriteApproval(ToolExecutionContext context, java.util.UUID callId,
                                                       String command, String cwd, Domain.RiskLevel riskLevel) {
        auditService.append(new AuditEventDraft(context.taskId(), context.runId(), context.stepId(), context.actionId(),
                Domain.AuditEventType.PermissionChecked, Domain.AuditActor.RUNTIME, Domain.AuditLevel.INFO,
                "Check git write permission", command, List.of(), callId, null, null, null,
                Domain.PermissionLevel.GIT_WRITE, riskLevel, null, true, null, null,
                Map.of("decision", Domain.PermissionDecisionType.REQUIRE_APPROVAL.name())));
        if (approvalService == null) {
            return null;
        }
        var consumed = approvalService.consumeApproved(context.runId(), Domain.ApprovalType.GIT_WRITE,
                List.of(), command, cwd);
        if (consumed != null) {
            auditService.append(new AuditEventDraft(context.taskId(), context.runId(), context.stepId(), context.actionId(),
                    Domain.AuditEventType.PermissionAllowed, Domain.AuditActor.RUNTIME, Domain.AuditLevel.INFO,
                    "Git write allowed by approved request", command, List.of(), callId, consumed.id(),
                    null, null, Domain.PermissionLevel.GIT_WRITE, riskLevel, Domain.ApprovalStatus.CONSUMED,
                    true, null, null, Map.of()));
            return null;
        }
        var approval = approvalService.create(context.taskId(), context.runId(), context.stepId(), context.actionId(),
                Domain.ApprovalType.GIT_WRITE, riskLevel, "Git write requires approval: " + command,
                List.of(), command, cwd, null);
        toolRecords.completeCall(callId, Domain.ToolCallStatus.BLOCKED);
        toolRecords.insertResult(callId, false, "Waiting for git write approval", Map.of(), null,
                Map.of("approvalId", approval.id().toString()));
        return ToolExecutionResult.waiting(approval.id(), "Git write requires approval");
    }

    private ToolCallRecord startTool(ToolExecutionContext context, String toolName, String inputSummary, String cwd) {
        return startTool(context, toolName, inputSummary, cwd, Domain.PermissionLevel.GIT_READ);
    }

    private ToolCallRecord startTool(ToolExecutionContext context, String toolName, String inputSummary, String cwd,
                                     Domain.PermissionLevel permissionLevel) {
        var call = toolRecords.insertCall(context.actionId(), toolName, permissionLevel,
                inputSummary, Map.of("workingDirectory", cwd));
        auditService.append(new AuditEventDraft(context.taskId(), context.runId(), context.stepId(), context.actionId(),
                Domain.AuditEventType.ToolCallRequested, Domain.AuditActor.AGENT, Domain.AuditLevel.INFO,
                inputSummary, toolName, List.of(), call.id(), null, null, null,
                permissionLevel, permissionLevel == Domain.PermissionLevel.GIT_READ ? Domain.RiskLevel.LOW : Domain.RiskLevel.HIGH,
                null, true, null, null, Map.of()));
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

    private ProcessResult resolveGitRoot(java.io.File cwd) {
        return execute(safeGitArguments(List.of("rev-parse", "--show-toplevel")), cwd);
    }

    private List<String> addArguments(ToolExecutionContext context, String workingDirectory, List<String> requestedPaths) {
        var paths = requestedPaths == null ? List.<String>of() : requestedPaths.stream()
                .filter(path -> path != null && !path.isBlank())
                .toList();
        if (paths.isEmpty() || paths.equals(List.of("."))) {
            paths = inspectWorkspaceStatus(context.workspace(), workingDirectory).changedFiles();
        }
        if (paths.isEmpty()) {
            return List.of("add", "--");
        }
        var args = new ArrayList<String>();
        args.add("add");
        args.add("--");
        for (var path : paths) {
            var check = pathGuard.check(context.workspace(), path, false);
            var decision = permissionService.fileDecision(check, Domain.FileOperation.FILE_READ, false, 0);
            if (decision.decision() != Domain.PermissionDecisionType.ALLOW) {
                throw new IllegalArgumentException("Git add path blocked: " + path + " - " + decision.reason());
            }
            args.add(check.relativePath());
        }
        return args;
    }

    private List<String> remoteBranchArguments(String operation, String remote, String branch) {
        var args = new ArrayList<String>();
        args.add(operation);
        if (remote != null && !remote.isBlank()) {
            args.add(remote);
            if (branch != null && !branch.isBlank()) {
                args.add(branch);
            }
        }
        return args;
    }

    private List<String> optionalRemoteArguments(String operation, String remote) {
        var args = new ArrayList<String>();
        args.add(operation);
        if (remote != null && !remote.isBlank()) {
            args.add(remote);
        }
        return args;
    }

    private ProcessResult execute(List<String> gitArguments, java.io.File cwd) {
        try {
            var command = new ArrayList<String>();
            command.add("git");
            command.add("--no-optional-locks");
            command.addAll(gitArguments);
            var started = new ProcessBuilder(command)
                    .directory(cwd);
            started.environment().put("GIT_EXTERNAL_DIFF", "");
            started.environment().put("GIT_PAGER", "cat");
            started.environment().put("GIT_TERMINAL_PROMPT", "0");
            started.environment().put("GIT_OPTIONAL_LOCKS", "0");
            var processInstance = started
                    .start();
            var outputFuture = CompletableFuture.supplyAsync(() -> readOutput(processInstance.getInputStream()));
            var errorFuture = CompletableFuture.supplyAsync(() -> readOutput(processInstance.getErrorStream()));
            var finished = processInstance.waitFor(settings.commandTimeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                processInstance.destroyForcibly();
                return new ProcessResult(124, truncate("Git command timed out after "
                        + settings.commandTimeoutSeconds() + " seconds\n"
                        + combineOutput(outputFuture.getNow(""), errorFuture.getNow(""))));
            }
            var stdout = outputFuture.get(5, TimeUnit.SECONDS);
            var stderr = errorFuture.get(5, TimeUnit.SECONDS);
            var exitCode = processInstance.exitValue();
            return new ProcessResult(exitCode, truncate(exitCode == 0 ? stdout : combineOutput(stdout, stderr)));
        } catch (Exception e) {
            return new ProcessResult(1, e.getMessage());
        }
    }

    private String combineOutput(String stdout, String stderr) {
        var out = stdout == null ? "" : stdout;
        var err = stderr == null ? "" : stderr;
        if (err.isBlank()) {
            return out;
        }
        if (out.isBlank()) {
            return err;
        }
        return err + "\n" + out;
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

    private String firstLine(String output) {
        if (output == null || output.isBlank()) {
            return "";
        }
        return output.lines().findFirst().orElse("").trim();
    }

    private String statusPath(String line) {
        if (line.length() <= 3) {
            return "";
        }
        var path = line.substring(3).trim();
        var renameIndex = path.indexOf(" -> ");
        if (renameIndex >= 0) {
            return path.substring(renameIndex + 4).trim();
        }
        if (path.length() >= 2 && path.startsWith("\"") && path.endsWith("\"")) {
            return path.substring(1, path.length() - 1);
        }
        return path;
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

    @FunctionalInterface
    private interface GitArgumentSupplier {
        List<String> get();
    }

    public record GitWorkspaceStatus(boolean available, String summary, List<String> statusLines,
                                     List<String> changedFiles) {
        public GitWorkspaceStatus {
            statusLines = statusLines == null ? List.of() : List.copyOf(statusLines);
            changedFiles = changedFiles == null ? List.of() : List.copyOf(changedFiles);
        }
    }
}
