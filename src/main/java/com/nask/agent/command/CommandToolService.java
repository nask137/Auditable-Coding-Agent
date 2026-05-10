package com.nask.agent.command;

import com.nask.agent.approval.ApprovalService;
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
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Tool service that executes shell commands through policy, approval, and audit.
 */
@Service
public class CommandToolService {
    private final CommandPolicyService policyService;
    private final CommandExecutionRepository commandRepository;
    private final ApprovalService approvalService;
    private final AuditService auditService;
    private final ToolRecordRepository toolRecords;
    private final WorkspacePathGuard pathGuard;
    private final AgentSettings settings;

    /**
     * Creates a command tool service.
     */
    public CommandToolService(CommandPolicyService policyService, CommandExecutionRepository commandRepository,
                              ApprovalService approvalService, AuditService auditService,
                              ToolRecordRepository toolRecords, WorkspacePathGuard pathGuard,
                              AgentSettings settings) {
        this.policyService = policyService;
        this.commandRepository = commandRepository;
        this.approvalService = approvalService;
        this.auditService = auditService;
        this.toolRecords = toolRecords;
        this.pathGuard = pathGuard;
        this.settings = settings;
    }

    /**
     * Runs a command if policy allows it or if a matching approval is consumed.
     *
     * <p>The method records a tool call immediately, validates the working
     * directory against the workspace boundary, persists a command execution row,
     * then either blocks, waits for approval, or spawns the process.</p>
     */
    public ToolExecutionResult runCommand(ToolExecutionContext context, String executable, List<String> arguments,
                                          String workingDirectory, String reason) {
        var args = arguments == null ? List.<String>of() : arguments;
        var commandText = executable + (args.isEmpty() ? "" : " " + String.join(" ", args));
        var call = toolRecords.insertCall(context.actionId(), "run_command", Domain.PermissionLevel.SHELL_SAFE,
                "Run command", Map.of("executable", executable, "arguments", args, "workingDirectory", workingDirectory));
        auditService.append(new AuditEventDraft(context.taskId(), context.runId(), context.stepId(), context.actionId(),
                Domain.AuditEventType.CommandRequested, Domain.AuditActor.AGENT, Domain.AuditLevel.INFO,
                "Command requested", commandText, List.of(), call.id(), null, null, null,
                Domain.PermissionLevel.SHELL_SAFE, Domain.RiskLevel.MEDIUM, null, true, null, null, Map.of()));

        // Commands inherit the same workspace boundary as file tools; even a safe
        // executable cannot run from a directory that resolves outside the root.
        var cwdCheck = pathGuard.check(context.workspace(), workingDirectory == null ? "." : workingDirectory, false);
        if (!cwdCheck.allowed()) {
            var execution = createExecution(context, commandText, executable, args, workingDirectory, Domain.CommandPolicyType.BLOCKED,
                    Domain.RiskLevel.CRITICAL, null, Domain.CommandExecutionStatus.BLOCKED);
            commandRepository.complete(execution.id(), Domain.CommandExecutionStatus.BLOCKED.name(), null, cwdCheck.reason());
            auditBlocked(context, call.id(), execution.id(), commandText, cwdCheck.reason());
            toolRecords.completeCall(call.id(), Domain.ToolCallStatus.BLOCKED);
            toolRecords.insertResult(call.id(), false, cwdCheck.reason(), Map.of(), cwdCheck.reason(), Map.of());
            return ToolExecutionResult.blocked(cwdCheck.reason());
        }

        var policy = policyService.classify(context.workspace().id(), executable, args);
        var risk = policy == Domain.CommandPolicyType.ALLOWLIST ? Domain.RiskLevel.MEDIUM : Domain.RiskLevel.HIGH;
        var status = policy == Domain.CommandPolicyType.ALLOWLIST
                ? Domain.CommandExecutionStatus.RUNNING
                : policy == Domain.CommandPolicyType.APPROVAL_REQUIRED
                ? Domain.CommandExecutionStatus.WAITING_APPROVAL
                : Domain.CommandExecutionStatus.BLOCKED;
        var execution = createExecution(context, commandText, executable, args, workingDirectory, policy, risk, null, status);

        if (policy == Domain.CommandPolicyType.BLOCKED) {
            commandRepository.complete(execution.id(), Domain.CommandExecutionStatus.BLOCKED.name(), null, "Command blocked by policy");
            auditBlocked(context, call.id(), execution.id(), commandText, "Command blocked by policy");
            toolRecords.completeCall(call.id(), Domain.ToolCallStatus.BLOCKED);
            toolRecords.insertResult(call.id(), false, "Command blocked by policy", Map.of(), "Command blocked by policy", Map.of());
            return ToolExecutionResult.blocked("Command blocked by policy");
        }

        if (policy == Domain.CommandPolicyType.APPROVAL_REQUIRED) {
            var consumed = approvalService.consumeApproved(context.runId(), Domain.ApprovalType.COMMAND_EXECUTION,
                    List.of(), commandText, cwdCheck.relativePath());
            if (consumed != null) {
                // Approved requests are one-time capabilities. Once consumed, the
                // command can run but future identical attempts need fresh approval.
                auditService.append(new AuditEventDraft(context.taskId(), context.runId(), context.stepId(), context.actionId(),
                        Domain.AuditEventType.CommandAllowed, Domain.AuditActor.RUNTIME, Domain.AuditLevel.INFO,
                        "Command allowed by approved request", commandText, List.of(), call.id(), consumed.id(), execution.id(), null,
                        Domain.PermissionLevel.SHELL_RISKY, Domain.RiskLevel.HIGH, Domain.ApprovalStatus.CONSUMED,
                        true, null, null, Map.of()));
                var result = executeProcess(executable, args, cwdCheck.absolutePath());
                var finalStatus = result.exitCode() == 0 ? Domain.CommandExecutionStatus.COMPLETED : Domain.CommandExecutionStatus.FAILED;
                commandRepository.complete(execution.id(), finalStatus.name(), result.exitCode(), result.summary());
                auditService.append(new AuditEventDraft(context.taskId(), context.runId(), context.stepId(), context.actionId(),
                        Domain.AuditEventType.CommandExecuted, Domain.AuditActor.TOOL, result.exitCode() == 0 ? Domain.AuditLevel.INFO : Domain.AuditLevel.ERROR,
                        "Command executed", result.summary(), List.of(), call.id(), consumed.id(), execution.id(), null,
                        Domain.PermissionLevel.SHELL_RISKY, Domain.RiskLevel.HIGH, Domain.ApprovalStatus.CONSUMED,
                        result.exitCode() == 0, result.exitCode() == 0 ? null : "COMMAND_FAILED",
                        result.exitCode() == 0 ? null : result.summary(), Map.of("exitCode", result.exitCode())));
                toolRecords.completeCall(call.id(), result.exitCode() == 0 ? Domain.ToolCallStatus.COMPLETED : Domain.ToolCallStatus.FAILED);
                var payload = Map.<String, Object>of("exitCode", result.exitCode(), "commandId", execution.id().toString());
                toolRecords.insertResult(call.id(), result.exitCode() == 0, result.summary(), payload, null, Map.of());
                return ToolExecutionResult.success(result.summary(), payload);
            }
            var approval = approvalService.create(context.taskId(), context.runId(), context.stepId(), context.actionId(),
                    Domain.ApprovalType.COMMAND_EXECUTION, Domain.RiskLevel.HIGH,
                    "Command requires approval: " + commandText, List.of(), commandText, cwdCheck.relativePath(), null);
            commandRepository.attachApproval(execution.id(), approval.id());
            auditService.append(new AuditEventDraft(context.taskId(), context.runId(), context.stepId(), context.actionId(),
                    Domain.AuditEventType.CommandApprovalRequired, Domain.AuditActor.RUNTIME, Domain.AuditLevel.WARN,
                    "Command approval required", commandText, List.of(), call.id(), approval.id(), execution.id(), null,
                    Domain.PermissionLevel.SHELL_RISKY, Domain.RiskLevel.HIGH, Domain.ApprovalStatus.PENDING,
                    true, null, null, Map.of()));
            // Tool calls in WAITING_APPROVAL are represented as BLOCKED at the
            // tool-call row level because no process has executed yet.
            toolRecords.completeCall(call.id(), Domain.ToolCallStatus.BLOCKED);
            toolRecords.insertResult(call.id(), false, "Waiting for command approval", Map.of(), null, Map.of("approvalId", approval.id().toString()));
            return ToolExecutionResult.waiting(approval.id(), "Command requires approval");
        }

        auditService.append(new AuditEventDraft(context.taskId(), context.runId(), context.stepId(), context.actionId(),
                Domain.AuditEventType.CommandAllowed, Domain.AuditActor.RUNTIME, Domain.AuditLevel.INFO,
                "Command allowed", commandText, List.of(), call.id(), null, execution.id(), null,
                Domain.PermissionLevel.SHELL_SAFE, Domain.RiskLevel.MEDIUM, null, true, null, null, Map.of()));
        var result = executeProcess(executable, args, cwdCheck.absolutePath());
        var finalStatus = result.exitCode() == 0 ? Domain.CommandExecutionStatus.COMPLETED : Domain.CommandExecutionStatus.FAILED;
        commandRepository.complete(execution.id(), finalStatus.name(), result.exitCode(), result.summary());
        auditService.append(new AuditEventDraft(context.taskId(), context.runId(), context.stepId(), context.actionId(),
                Domain.AuditEventType.CommandExecuted, Domain.AuditActor.TOOL, result.exitCode() == 0 ? Domain.AuditLevel.INFO : Domain.AuditLevel.ERROR,
                "Command executed", result.summary(), List.of(), call.id(), null, execution.id(), null,
                Domain.PermissionLevel.SHELL_SAFE, Domain.RiskLevel.MEDIUM, null, result.exitCode() == 0,
                result.exitCode() == 0 ? null : "COMMAND_FAILED", result.exitCode() == 0 ? null : result.summary(), Map.of("exitCode", result.exitCode())));
        toolRecords.completeCall(call.id(), result.exitCode() == 0 ? Domain.ToolCallStatus.COMPLETED : Domain.ToolCallStatus.FAILED);
        var payload = Map.<String, Object>of("exitCode", result.exitCode(), "commandId", execution.id().toString());
        toolRecords.insertResult(call.id(), result.exitCode() == 0, result.summary(), payload, null, Map.of());
        return ToolExecutionResult.success(result.summary(), payload);
    }

    /**
     * Resumes the exact command execution that was paused for user approval.
     */
    public ToolExecutionResult resumeApprovedCommand(ToolExecutionContext context, CommandExecution execution) {
        if (!Domain.CommandExecutionStatus.WAITING_APPROVAL.name().equals(execution.status())) {
            return ToolExecutionResult.blocked("Command is not waiting for approval");
        }
        if (execution.approvalId() == null) {
            return ToolExecutionResult.blocked("Command has no linked approval request");
        }
        var cwdCheck = pathGuard.check(context.workspace(), execution.workingDirectory(), false);
        if (!cwdCheck.allowed()) {
            commandRepository.complete(execution.id(), Domain.CommandExecutionStatus.BLOCKED.name(), null, cwdCheck.reason());
            auditBlocked(context, null, execution.id(), execution.command(), cwdCheck.reason());
            return ToolExecutionResult.blocked(cwdCheck.reason());
        }
        var consumed = approvalService.consumeApproved(context.runId(), Domain.ApprovalType.COMMAND_EXECUTION,
                List.of(), execution.command(), cwdCheck.relativePath());
        if (consumed == null) {
            return ToolExecutionResult.blocked("No approved command request is available to resume");
        }
        auditService.append(new AuditEventDraft(context.taskId(), context.runId(), context.stepId(), context.actionId(),
                Domain.AuditEventType.CommandAllowed, Domain.AuditActor.RUNTIME, Domain.AuditLevel.INFO,
                "Command allowed by approved request", execution.command(), List.of(), null, consumed.id(), execution.id(), null,
                Domain.PermissionLevel.SHELL_RISKY, Domain.RiskLevel.HIGH, Domain.ApprovalStatus.CONSUMED,
                true, null, null, Map.of()));
        commandRepository.markRunning(execution.id());
        var result = executeProcess(execution.executable(), execution.arguments(), cwdCheck.absolutePath());
        var finalStatus = result.exitCode() == 0 ? Domain.CommandExecutionStatus.COMPLETED : Domain.CommandExecutionStatus.FAILED;
        commandRepository.complete(execution.id(), finalStatus.name(), result.exitCode(), result.summary());
        var toolCall = toolRecords.findLatestCall(execution.actionId(), "run_command");
        var payload = Map.<String, Object>of("exitCode", result.exitCode(), "commandId", execution.id().toString());
        if (toolCall.isPresent()) {
            toolRecords.completeCall(toolCall.get().id(), result.exitCode() == 0 ? Domain.ToolCallStatus.COMPLETED : Domain.ToolCallStatus.FAILED);
            toolRecords.insertResult(toolCall.get().id(), result.exitCode() == 0, result.summary(), payload,
                    result.exitCode() == 0 ? null : result.summary(), Map.of("resumedFromApproval", true));
        }
        auditService.append(new AuditEventDraft(context.taskId(), context.runId(), context.stepId(), context.actionId(),
                Domain.AuditEventType.CommandExecuted, Domain.AuditActor.TOOL, result.exitCode() == 0 ? Domain.AuditLevel.INFO : Domain.AuditLevel.ERROR,
                "Command executed", result.summary(), List.of(), null, consumed.id(), execution.id(), null,
                Domain.PermissionLevel.SHELL_RISKY, Domain.RiskLevel.HIGH, Domain.ApprovalStatus.CONSUMED,
                result.exitCode() == 0, result.exitCode() == 0 ? null : "COMMAND_FAILED",
                result.exitCode() == 0 ? null : result.summary(), Map.of("exitCode", result.exitCode())));
        return ToolExecutionResult.success(result.summary(), payload);
    }

    /**
     * Persists the command execution row in its initial status.
     */
    private CommandExecution createExecution(ToolExecutionContext context, String commandText, String executable,
                                             List<String> arguments, String cwd, Domain.CommandPolicyType policy,
                                             Domain.RiskLevel risk, UUID approvalId, Domain.CommandExecutionStatus status) {
        return commandRepository.insert(new CommandExecution(UUID.randomUUID(), context.workspace().id(), context.taskId(),
                context.runId(), context.stepId(), context.actionId(), commandText, executable, arguments,
                cwd == null ? "." : cwd, policy.name(), risk.name(), approvalId, status.name(),
                null, null, status == Domain.CommandExecutionStatus.RUNNING ? Instant.now() : null, null, Instant.now()));
    }

    /**
     * Spawns a process with merged stdout/stderr and bounded execution time.
     */
    private ProcessResult executeProcess(String executable, List<String> arguments, Path cwd) {
        try {
            var command = new ArrayList<String>();
            command.add(resolveExecutableForProcess(executable, System.getenv(), isWindows()));
            command.addAll(arguments);
            var process = new ProcessBuilder(command)
                    .directory(cwd.toFile())
                    .redirectErrorStream(true)
                    .start();
            var outputFuture = CompletableFuture.supplyAsync(() -> readOutput(process.getInputStream()));
            var finished = process.waitFor(settings.commandTimeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                // Exit code 124 mirrors common Unix timeout behavior, making the
                // summary easier to interpret for developers.
                process.destroyForcibly();
                return new ProcessResult(124, "Command timed out after " + settings.commandTimeoutSeconds()
                        + " seconds\n" + summarize(outputFuture.getNow("")));
            }
            var output = outputFuture.get(5, TimeUnit.SECONDS);
            return new ProcessResult(process.exitValue(), summarize(output));
        } catch (Exception e) {
            return new ProcessResult(1, e.getMessage());
        }
    }

    /**
     * Resolves extension-less Windows commands through PATH/PATHEXT before
     * passing them to ProcessBuilder, which does not invoke shell lookup.
     */
    static String resolveExecutableForProcess(String executable, Map<String, String> environment, boolean windows) {
        if (!windows || executable == null || executable.isBlank()) {
            return executable;
        }
        var env = caseInsensitive(environment);
        var pathDirs = pathDirs(env.get("PATH"));
        var extensions = pathExtensions(env.get("PATHEXT"));
        var hasPath = executable.contains("\\") || executable.contains("/") || Path.of(executable).isAbsolute();
        if (hasPath) {
            var resolved = resolveCandidate(Path.of(executable), extensions);
            return resolved == null ? executable : resolved.toString();
        }
        for (var dir : pathDirs) {
            var resolved = resolveCandidate(dir.resolve(executable), extensions);
            if (resolved != null) {
                return resolved.toString();
            }
        }
        return executable;
    }

    private static Path resolveCandidate(Path base, List<String> extensions) {
        if (hasExtension(base)) {
            return Files.isRegularFile(base) ? base : null;
        }
        for (var extension : extensions) {
            var candidate = Path.of(base.toString() + extension);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean hasExtension(Path path) {
        var fileName = path.getFileName();
        if (fileName == null) {
            return false;
        }
        var name = fileName.toString();
        var index = name.lastIndexOf('.');
        return index > 0 && index < name.length() - 1;
    }

    private static List<Path> pathDirs(String path) {
        if (path == null || path.isBlank()) {
            return List.of();
        }
        return Arrays.stream(path.split(java.util.regex.Pattern.quote(File.pathSeparator)))
                .filter(value -> !value.isBlank())
                .map(Path::of)
                .toList();
    }

    private static List<String> pathExtensions(String pathext) {
        var value = pathext == null || pathext.isBlank() ? ".COM;.EXE;.BAT;.CMD" : pathext;
        return Arrays.stream(value.split(";"))
                .map(String::trim)
                .filter(extension -> !extension.isBlank())
                .map(extension -> extension.startsWith(".") ? extension : "." + extension)
                .toList();
    }

    private static Map<String, String> caseInsensitive(Map<String, String> environment) {
        var values = new LinkedHashMap<String, String>();
        if (environment != null) {
            environment.forEach((key, value) -> values.put(key.toUpperCase(Locale.ROOT), value));
        }
        return values;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    /**
     * Reads process output as UTF-8.
     */
    private String readOutput(InputStream stream) {
        try {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    /**
     * Truncates command output stored in audit/result rows.
     */
    private String summarize(String output) {
        if (output == null || output.isBlank()) {
            return "";
        }
        return output.length() <= 4000 ? output : output.substring(0, 4000);
    }

    /**
     * Appends a standardized command-blocked audit event.
     */
    private void auditBlocked(ToolExecutionContext context, UUID callId, UUID commandId, String command, String reason) {
        auditService.append(new AuditEventDraft(context.taskId(), context.runId(), context.stepId(), context.actionId(),
                Domain.AuditEventType.CommandBlocked, Domain.AuditActor.RUNTIME, Domain.AuditLevel.WARN,
                "Command blocked", command + " - " + reason, List.of(), callId, null, commandId, null,
                Domain.PermissionLevel.SHELL_RISKY, Domain.RiskLevel.CRITICAL, null, false,
                "COMMAND_BLOCKED", reason, Map.of()));
    }

    /**
     * Process exit code and summarized output.
     */
    private record ProcessResult(int exitCode, String summary) {
    }
}
