package com.nask.agent.file;

import com.nask.agent.approval.ApprovalService;
import com.nask.agent.audit.AuditEventDraft;
import com.nask.agent.audit.AuditService;
import com.nask.agent.common.AgentSettings;
import com.nask.agent.common.Domain;
import com.nask.agent.permission.PermissionDecision;
import com.nask.agent.permission.PermissionService;
import com.nask.agent.tool.ToolExecutionContext;
import com.nask.agent.tool.ToolExecutionResult;
import com.nask.agent.tool.ToolRecordRepository;
import com.nask.agent.workspace.PathCheck;
import com.nask.agent.workspace.WorkspacePathGuard;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Tool service for audited workspace file operations.
 *
 * <p>Every public method follows the same safety shape: create a tool-call
 * record, resolve the path within the workspace, classify permission, optionally
 * consume/create approval, perform the I/O, then store both audit and result
 * records.</p>
 */
@Service
public class FileToolService {
    private static final List<String> HIGH_IMPACT_FILES = List.of(
            "pom.xml", "build.gradle", "settings.gradle", "package.json", "package-lock.json",
            "pnpm-lock.yaml", "yarn.lock", "Dockerfile", "docker-compose.yml");

    private final WorkspacePathGuard pathGuard;
    private final PermissionService permissionService;
    private final ApprovalService approvalService;
    private final ToolRecordRepository toolRecords;
    private final FileChangeRepository fileChanges;
    private final AuditService auditService;
    private final DiffSupport diffSupport;
    private final AgentSettings settings;

    /**
     * Creates a file tool service.
     */
    public FileToolService(WorkspacePathGuard pathGuard, PermissionService permissionService,
                           ApprovalService approvalService, ToolRecordRepository toolRecords,
                           FileChangeRepository fileChanges, AuditService auditService,
                           DiffSupport diffSupport, AgentSettings settings) {
        this.pathGuard = pathGuard;
        this.permissionService = permissionService;
        this.approvalService = approvalService;
        this.toolRecords = toolRecords;
        this.fileChanges = fileChanges;
        this.auditService = auditService;
        this.diffSupport = diffSupport;
        this.settings = settings;
    }

    /**
     * Lists readable regular files under a workspace-relative path.
     */
    public ToolExecutionResult listFiles(ToolExecutionContext context, String path, int maxDepth) {
        var input = Map.<String, Object>of("path", path, "maxDepth", maxDepth);
        var call = startTool(context, "list_files", Domain.PermissionLevel.READ_ONLY, "List files", input);
        try {
            var check = pathGuard.check(context.workspace(), path, false);
            var decision = permissionService.fileDecision(check, Domain.FileOperation.FILE_READ, false, 0);
            var permission = handlePermission(context, call.id(), decision, List.of(check.relativePath()), null, null, null);
            if (permission != null) {
                return permission;
            }
            var root = check.absolutePath();
            var files = new ArrayList<String>();
            try (Stream<Path> walk = Files.walk(root, Math.max(1, maxDepth))) {
                // The walk is capped and then each file is re-checked through the
                // permission service, so broad listings do not leak sensitive paths.
                walk.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))
                        .sorted(Comparator.comparing(Path::toString))
                        .limit(500)
                        .forEach(p -> addIfReadable(context, p, files));
            }
            completeTool(call.id(), true, "Listed " + files.size() + " files", Map.of("files", files, "truncated", files.size() >= 500), null);
            return ToolExecutionResult.success("Listed " + files.size() + " files", Map.of("files", files));
        } catch (Exception e) {
            failTool(call.id(), e.getMessage());
            return ToolExecutionResult.blocked(e.getMessage());
        }
    }

    /**
     * Reads a UTF-8 file after path and permission checks.
     */
    public ToolExecutionResult readFile(ToolExecutionContext context, String path) {
        var input = Map.<String, Object>of("path", path);
        var call = startTool(context, "read_file", Domain.PermissionLevel.READ_ONLY, "Read file", input);
        try {
            var check = pathGuard.check(context.workspace(), path, false);
            var decision = permissionService.fileDecision(check, Domain.FileOperation.FILE_READ, false, 0);
            var permission = handlePermission(context, call.id(), decision, List.of(check.relativePath()), null, null, null);
            if (permission != null) {
                return permission;
            }
            var content = Files.readString(check.absolutePath(), StandardCharsets.UTF_8);
            if (content.length() > settings.maxReadBytes()) {
                // Return a bounded prefix rather than failing so the model can
                // still inspect large files without overloading storage/API output.
                content = content.substring(0, settings.maxReadBytes());
            }
            auditService.append(new AuditEventDraft(context.taskId(), context.runId(), context.stepId(), context.actionId(),
                    Domain.AuditEventType.FileRead, Domain.AuditActor.TOOL, Domain.AuditLevel.INFO,
                    "Read " + check.relativePath(), "Read " + content.length() + " chars",
                    List.of(check.relativePath()), call.id(), null, null, null, Domain.PermissionLevel.READ_ONLY,
                    decision.riskLevel(), null, true, null, null, Map.of()));
            completeTool(call.id(), true, "Read " + check.relativePath(), Map.of("content", content), null);
            return ToolExecutionResult.success("Read " + check.relativePath(), Map.of("content", content));
        } catch (Exception e) {
            failTool(call.id(), e.getMessage());
            return ToolExecutionResult.blocked(e.getMessage());
        }
    }

    /**
     * Searches readable files for a literal text query.
     */
    public ToolExecutionResult searchText(ToolExecutionContext context, String query) {
        var call = startTool(context, "search_text", Domain.PermissionLevel.READ_ONLY, "Search text", Map.of("query", query));
        try {
            var root = Path.of(context.workspace().rootPath()).toAbsolutePath().normalize();
            var matches = new ArrayList<String>();
            try (var walk = Files.walk(root, 8)) {
                walk.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))
                        .limit(1000)
                        .forEach(p -> searchFile(context, root, p, query, matches));
            }
            completeTool(call.id(), true, "Found " + matches.size() + " matches", Map.of("matches", matches), null);
            return ToolExecutionResult.success("Found " + matches.size() + " matches", Map.of("matches", matches));
        } catch (Exception e) {
            failTool(call.id(), e.getMessage());
            return ToolExecutionResult.blocked(e.getMessage());
        }
    }

    /**
     * Creates a new UTF-8 file and records the resulting file change.
     */
    public ToolExecutionResult createFile(ToolExecutionContext context, String path, String content, String reason) {
        var input = Map.<String, Object>of("path", path, "reason", reason);
        var call = startTool(context, "create_file", Domain.PermissionLevel.WORKSPACE_WRITE, "Create file", input);
        try {
            var check = pathGuard.check(context.workspace(), path, true);
            var patchLines = content == null ? 0 : content.split("\\R", -1).length;
            var decision = permissionService.fileDecision(check, Domain.FileOperation.FILE_CREATE, highImpact(check.relativePath()), patchLines);
            var permission = handlePermission(context, call.id(), decision, List.of(check.relativePath()), null, null, content);
            if (permission != null) {
                return permission;
            }
            if (Files.exists(check.absolutePath())) {
                // Existing files are not overwritten by createFile. Modifications
                // must go through applyPatch so diffs and patch budgets are clear.
                completeTool(call.id(), true, "File already exists: " + check.relativePath(), Map.of("path", check.relativePath(), "changed", false), null);
                return ToolExecutionResult.success("File already exists: " + check.relativePath(), Map.of("changed", false));
            }
            Files.createDirectories(check.absolutePath().getParent());
            var before = "";
            var after = content == null ? "" : content;
            Files.writeString(check.absolutePath(), after, StandardCharsets.UTF_8);
            var diff = diffSupport.simpleUnifiedDiff(check.relativePath(), before, after);
            var change = recordChange(context, check.relativePath(), Domain.ChangeType.CREATE, reason, diff, before, after,
                    Domain.PatchApplyStatus.APPLIED, diffSupport.addedLines(before, after), 0, decision.riskLevel(), null);
            auditService.append(new AuditEventDraft(context.taskId(), context.runId(), context.stepId(), context.actionId(),
                    Domain.AuditEventType.FileCreated, Domain.AuditActor.TOOL, Domain.AuditLevel.INFO,
                    "Create " + check.relativePath(), reason, List.of(check.relativePath()), call.id(), null, null,
                    change.id(), Domain.PermissionLevel.WORKSPACE_WRITE, decision.riskLevel(), null, true, null, null, Map.of()));
            completeTool(call.id(), true, "Created " + check.relativePath(), Map.of("path", check.relativePath(), "changed", true), null);
            return ToolExecutionResult.success("Created " + check.relativePath(), Map.of("changed", true, "path", check.relativePath()));
        } catch (Exception e) {
            failTool(call.id(), e.getMessage());
            return ToolExecutionResult.blocked(e.getMessage());
        }
    }

    /**
     * Replaces one exact text fragment in a file and records the mutation.
     */
    public ToolExecutionResult applyPatch(ToolExecutionContext context, String path, String oldText, String newText, String reason) {
        var call = startTool(context, "apply_patch", Domain.PermissionLevel.WORKSPACE_WRITE, "Apply patch",
                Map.of("path", path, "reason", reason));
        try {
            var check = pathGuard.check(context.workspace(), path, true);
            var before = Files.readString(check.absolutePath(), StandardCharsets.UTF_8);
            if (!before.contains(oldText)) {
                // Exact matching prevents accidental broad edits when the model's
                // view of the file is stale.
                failTool(call.id(), "Patch oldText not found");
                return ToolExecutionResult.blocked("Patch oldText not found: " + check.relativePath());
            }
            var after = before.replace(oldText, newText);
            var added = diffSupport.addedLines(before, after);
            var deleted = diffSupport.deletedLines(before, after);
            var decision = permissionService.fileDecision(check, Domain.FileOperation.FILE_MODIFY, highImpact(check.relativePath()), added + deleted);
            var permission = handlePermission(context, call.id(), decision, List.of(check.relativePath()), null, null, newText);
            if (permission != null) {
                return permission;
            }
            Files.writeString(check.absolutePath(), after, StandardCharsets.UTF_8);
            var diff = diffSupport.simpleUnifiedDiff(check.relativePath(), before, after);
            var change = recordChange(context, check.relativePath(), Domain.ChangeType.MODIFY, reason, diff, before, after,
                    Domain.PatchApplyStatus.APPLIED, added, deleted, decision.riskLevel(), null);
            auditService.append(new AuditEventDraft(context.taskId(), context.runId(), context.stepId(), context.actionId(),
                    Domain.AuditEventType.FileModified, Domain.AuditActor.TOOL, Domain.AuditLevel.INFO,
                    "Modify " + check.relativePath(), reason, List.of(check.relativePath()), call.id(), null, null,
                    change.id(), Domain.PermissionLevel.WORKSPACE_WRITE, decision.riskLevel(), null, true, null, null, Map.of()));
            completeTool(call.id(), true, "Modified " + check.relativePath(), Map.of("path", check.relativePath()), null);
            return ToolExecutionResult.success("Modified " + check.relativePath(), Map.of("path", check.relativePath()));
        } catch (Exception e) {
            failTool(call.id(), e.getMessage());
            return ToolExecutionResult.blocked(e.getMessage());
        }
    }

    /**
     * Opens a tool-call record and emits the corresponding audit event.
     */
    private com.nask.agent.tool.ToolCallRecord startTool(ToolExecutionContext context, String toolName,
                                                          Domain.PermissionLevel permissionLevel,
                                                          String inputSummary, Map<String, Object> input) {
        var call = toolRecords.insertCall(context.actionId(), toolName, permissionLevel, inputSummary, input);
        auditService.append(new AuditEventDraft(context.taskId(), context.runId(), context.stepId(), context.actionId(),
                Domain.AuditEventType.ToolCallRequested, Domain.AuditActor.AGENT, Domain.AuditLevel.INFO,
                inputSummary, toolName, List.of(), call.id(), null, null, null, permissionLevel,
                null, null, true, null, null, Map.of()));
        return call;
    }

    /**
     * Completes a tool-call row and stores its result payload.
     */
    private void completeTool(UUID callId, boolean success, String summary, Map<String, Object> payload, String error) {
        toolRecords.completeCall(callId, success ? Domain.ToolCallStatus.COMPLETED : Domain.ToolCallStatus.FAILED);
        toolRecords.insertResult(callId, success, summary, payload == null ? Map.of() : payload, error, Map.of());
    }

    /**
     * Records a failed tool result with a uniform empty payload.
     */
    private void failTool(UUID callId, String error) {
        completeTool(callId, false, error, Map.of(), error);
    }

    /**
     * Converts a permission decision into either allow, approval pause, or block.
     */
    private ToolExecutionResult handlePermission(ToolExecutionContext context, UUID callId, PermissionDecision decision,
                                                 List<String> files, String command, String cwd, String preview) {
        auditService.append(new AuditEventDraft(context.taskId(), context.runId(), context.stepId(), context.actionId(),
                Domain.AuditEventType.PermissionChecked, Domain.AuditActor.RUNTIME, Domain.AuditLevel.INFO,
                "Check permission", decision.reason(), files, callId, null, null, null, null,
                decision.riskLevel(), null, true, null, null, Map.of("decision", decision.decision().name())));
        if (decision.decision() == Domain.PermissionDecisionType.ALLOW) {
            auditService.append(new AuditEventDraft(context.taskId(), context.runId(), context.stepId(), context.actionId(),
                    Domain.AuditEventType.PermissionAllowed, Domain.AuditActor.RUNTIME, Domain.AuditLevel.INFO,
                    "Permission allowed", decision.reason(), files, callId, null, null, null, null,
                    decision.riskLevel(), null, true, null, null, Map.of()));
            return null;
        }
        if (decision.decision() == Domain.PermissionDecisionType.REQUIRE_APPROVAL) {
            var consumed = approvalService.consumeApproved(context.runId(), decision.approvalType(), files, command, cwd);
            if (consumed != null) {
                // One-time approval consumption lets a retried operation continue
                // without opening a new request, while keeping the audit link.
                auditService.append(new AuditEventDraft(context.taskId(), context.runId(), context.stepId(), context.actionId(),
                        Domain.AuditEventType.PermissionAllowed, Domain.AuditActor.RUNTIME, Domain.AuditLevel.INFO,
                        "Permission allowed by approved request", decision.reason(), files, callId, consumed.id(),
                        null, null, null, decision.riskLevel(), Domain.ApprovalStatus.CONSUMED,
                        true, null, null, Map.of()));
                return null;
            }
            var approval = approvalService.create(context.taskId(), context.runId(), context.stepId(), context.actionId(),
                    decision.approvalType(), decision.riskLevel(), decision.reason(), files, command, cwd, preview);
            toolRecords.completeCall(callId, Domain.ToolCallStatus.BLOCKED);
            toolRecords.insertResult(callId, false, "Waiting for approval", Map.of(), null, Map.of("approvalId", approval.id().toString()));
            return ToolExecutionResult.waiting(approval.id(), decision.reason());
        }
        auditService.append(new AuditEventDraft(context.taskId(), context.runId(), context.stepId(), context.actionId(),
                Domain.AuditEventType.PermissionBlocked, Domain.AuditActor.RUNTIME, Domain.AuditLevel.WARN,
                "Permission blocked", decision.reason(), files, callId, null, null, null, null,
                decision.riskLevel(), null, false, "PERMISSION_BLOCKED", decision.reason(), Map.of()));
        toolRecords.completeCall(callId, Domain.ToolCallStatus.BLOCKED);
        toolRecords.insertResult(callId, false, decision.reason(), Map.of(), decision.reason(), Map.of());
        auditService.append(new AuditEventDraft(context.taskId(), context.runId(), context.stepId(), context.actionId(),
                Domain.AuditEventType.FileAccessBlocked, Domain.AuditActor.RUNTIME, Domain.AuditLevel.WARN,
                "File access blocked", decision.reason(), files, callId, null, null, null, null,
                decision.riskLevel(), null, false, "FILE_ACCESS_BLOCKED", decision.reason(), Map.of()));
        return ToolExecutionResult.blocked(decision.reason());
    }

    /**
     * Persists a file change with hashes used to identify before/after content.
     */
    private FileChange recordChange(ToolExecutionContext context, String path, Domain.ChangeType changeType, String reason,
                                    String diff, String before, String after, Domain.PatchApplyStatus patchStatus,
                                    int added, int deleted, Domain.RiskLevel riskLevel, UUID approvalId) {
        return fileChanges.insert(new FileChange(UUID.randomUUID(), context.workspace().id(), context.taskId(),
                context.runId(), context.stepId(), context.actionId(), path, changeType.name(), reason, diff,
                diffSupport.sha256(before), diffSupport.sha256(after), null, Instant.now(), patchStatus.name(),
                added, deleted, riskLevel.name(), approvalId, Instant.now()));
    }

    /**
     * Identifies files that should require approval even when not secret-like.
     */
    private boolean highImpact(String relativePath) {
        var name = Path.of(relativePath).getFileName();
        return name != null && HIGH_IMPACT_FILES.contains(name.toString());
    }

    /**
     * Adds a file to a listing only if it remains readable after guard checks.
     */
    private void addIfReadable(ToolExecutionContext context, Path file, List<String> files) {
        var root = Path.of(context.workspace().rootPath()).toAbsolutePath().normalize();
        var relative = root.relativize(file.toAbsolutePath().normalize()).toString().replace('\\', '/');
        var check = pathGuard.check(context.workspace(), relative, false);
        var decision = permissionService.fileDecision(check, Domain.FileOperation.FILE_READ, false, 0);
        if (decision.decision() == Domain.PermissionDecisionType.ALLOW) {
            files.add(relative);
        }
    }

    /**
     * Searches one file and ignores binary/unreadable content.
     */
    private void searchFile(ToolExecutionContext context, Path root, Path file, String query, List<String> matches) {
        if (query == null || query.isBlank() || matches.size() >= 100) {
            return;
        }
        try {
            var relative = root.relativize(file.toAbsolutePath().normalize()).toString().replace('\\', '/');
            var check = pathGuard.check(context.workspace(), relative, false);
            var decision = permissionService.fileDecision(check, Domain.FileOperation.FILE_READ, false, 0);
            if (decision.decision() != Domain.PermissionDecisionType.ALLOW) {
                return;
            }
            var content = Files.readString(file, StandardCharsets.UTF_8);
            if (content.contains(query)) {
                matches.add(relative);
            }
        } catch (Exception ignored) {
            // Binary or unreadable files are ignored by text search.
        }
    }
}
