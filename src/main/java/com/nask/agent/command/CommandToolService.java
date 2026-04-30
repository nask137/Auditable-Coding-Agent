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
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class CommandToolService {
    private final CommandPolicyService policyService;
    private final CommandExecutionRepository commandRepository;
    private final ApprovalService approvalService;
    private final AuditService auditService;
    private final ToolRecordRepository toolRecords;
    private final WorkspacePathGuard pathGuard;
    private final AgentSettings settings;

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
            auditService.append(new AuditEventDraft(context.taskId(), context.runId(), context.stepId(), context.actionId(),
                    Domain.AuditEventType.CommandApprovalRequired, Domain.AuditActor.RUNTIME, Domain.AuditLevel.WARN,
                    "Command approval required", commandText, List.of(), call.id(), approval.id(), execution.id(), null,
                    Domain.PermissionLevel.SHELL_RISKY, Domain.RiskLevel.HIGH, Domain.ApprovalStatus.PENDING,
                    true, null, null, Map.of()));
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

    private CommandExecution createExecution(ToolExecutionContext context, String commandText, String executable,
                                             List<String> arguments, String cwd, Domain.CommandPolicyType policy,
                                             Domain.RiskLevel risk, UUID approvalId, Domain.CommandExecutionStatus status) {
        return commandRepository.insert(new CommandExecution(UUID.randomUUID(), context.workspace().id(), context.taskId(),
                context.runId(), context.stepId(), context.actionId(), commandText, executable, arguments,
                cwd == null ? "." : cwd, policy.name(), risk.name(), approvalId, status.name(),
                null, null, status == Domain.CommandExecutionStatus.RUNNING ? Instant.now() : null, null, Instant.now()));
    }

    private ProcessResult executeProcess(String executable, List<String> arguments, Path cwd) {
        try {
            var command = new ArrayList<String>();
            command.add(executable);
            command.addAll(arguments);
            var process = new ProcessBuilder(command)
                    .directory(cwd.toFile())
                    .redirectErrorStream(true)
                    .start();
            var outputFuture = CompletableFuture.supplyAsync(() -> readOutput(process.getInputStream()));
            var finished = process.waitFor(settings.commandTimeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
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

    private String readOutput(InputStream stream) {
        try {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    private String summarize(String output) {
        if (output == null || output.isBlank()) {
            return "";
        }
        return output.length() <= 4000 ? output : output.substring(0, 4000);
    }

    private void auditBlocked(ToolExecutionContext context, UUID callId, UUID commandId, String command, String reason) {
        auditService.append(new AuditEventDraft(context.taskId(), context.runId(), context.stepId(), context.actionId(),
                Domain.AuditEventType.CommandBlocked, Domain.AuditActor.RUNTIME, Domain.AuditLevel.WARN,
                "Command blocked", command + " - " + reason, List.of(), callId, null, commandId, null,
                Domain.PermissionLevel.SHELL_RISKY, Domain.RiskLevel.CRITICAL, null, false,
                "COMMAND_BLOCKED", reason, Map.of()));
    }

    private record ProcessResult(int exitCode, String summary) {
    }
}
