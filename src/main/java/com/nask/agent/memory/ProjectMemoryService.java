package com.nask.agent.memory;

import com.nask.agent.audit.AuditEventDraft;
import com.nask.agent.audit.AuditService;
import com.nask.agent.common.ApiException;
import com.nask.agent.common.Domain;
import com.nask.agent.workspace.WorkspaceService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Application service for phase 4 project scanning and project profiles.
 */
@Service
public class ProjectMemoryService {
    private final WorkspaceService workspaceService;
    private final ProjectScanner scanner;
    private final ProjectProfileBuilder profileBuilder;
    private final ProjectMemoryRepository repository;
    private final AuditService auditService;
    private final DocumentIndexer documentIndexer;
    private final TaskReportIndexer taskReportIndexer;
    private final CodeSymbolIndexer codeSymbolIndexer;

    public ProjectMemoryService(WorkspaceService workspaceService, ProjectScanner scanner,
                                ProjectProfileBuilder profileBuilder, ProjectMemoryRepository repository,
                                AuditService auditService, DocumentIndexer documentIndexer,
                                TaskReportIndexer taskReportIndexer, CodeSymbolIndexer codeSymbolIndexer) {
        this.workspaceService = workspaceService;
        this.scanner = scanner;
        this.profileBuilder = profileBuilder;
        this.repository = repository;
        this.auditService = auditService;
        this.documentIndexer = documentIndexer;
        this.taskReportIndexer = taskReportIndexer;
        this.codeSymbolIndexer = codeSymbolIndexer;
    }

    /**
     * Runs a bounded project scan and updates the workspace profile.
     */
    public ProjectScanRun scan(UUID workspaceId) {
        var workspace = workspaceService.getRequired(workspaceId);
        var started = Instant.now();
        var scanRun = new ProjectScanRun(UUID.randomUUID(), workspaceId, null, null,
                Domain.ProjectScanStatus.RUNNING.name(), "manual", started, null, 0, 0, 0,
                "Project scan running", Map.of());
        repository.insertScanRun(scanRun);
        appendAudit(Domain.AuditEventType.ProjectScanStarted, Domain.AuditLevel.INFO, true,
                "Scan workspace " + workspaceId, "Project scan started", workspaceId, scanRun.id(), null,
                Map.of("rootPath", workspace.rootPath()));
        try {
            var result = scanner.scan(workspace);
            var indexedWorkspaceDocuments = documentIndexer.indexWorkspaceDocuments(workspace, scanRun.id(), result);
            var indexedTaskReports = taskReportIndexer.indexWorkspaceReports(workspace, scanRun.id());
            var indexedCodeSymbols = codeSymbolIndexer.indexWorkspaceSymbols(workspace, scanRun.id(), result);
            var metadata = new java.util.LinkedHashMap<>(result.metadata());
            metadata.put("indexedWorkspaceDocuments", indexedWorkspaceDocuments);
            metadata.put("indexedTaskReports", indexedTaskReports);
            metadata.put("indexedCodeSymbols", indexedCodeSymbols);
            var summary = result.summary() + "; indexed documents " + indexedWorkspaceDocuments
                    + "; indexed task reports " + indexedTaskReports
                    + "; indexed code symbols " + indexedCodeSymbols;
            var completed = new ProjectScanRun(scanRun.id(), workspaceId, null, null,
                    Domain.ProjectScanStatus.COMPLETED.name(), scanRun.scanReason(), started, Instant.now(),
                    result.filesSeen(), result.filesIndexed(), result.filesSkipped(), summary, metadata);
            repository.updateScanRun(completed);
            repository.upsertProfile(profileBuilder.build(workspace, scanRun.id(), result));
            appendAudit(Domain.AuditEventType.ProjectScanCompleted, Domain.AuditLevel.INFO, true,
                    "Scan workspace " + workspaceId, summary, workspaceId, scanRun.id(), null,
                    Map.of("filesSeen", result.filesSeen(), "filesIndexed", result.filesIndexed(),
                            "filesSkipped", result.filesSkipped(), "indexedWorkspaceDocuments",
                            indexedWorkspaceDocuments, "indexedTaskReports", indexedTaskReports,
                            "indexedCodeSymbols", indexedCodeSymbols));
            return completed;
        } catch (RuntimeException e) {
            var failed = new ProjectScanRun(scanRun.id(), workspaceId, null, null,
                    Domain.ProjectScanStatus.FAILED.name(), scanRun.scanReason(), started, Instant.now(),
                    0, 0, 0, "Project scan failed: " + e.getMessage(),
                    Map.of("error", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            repository.updateScanRun(failed);
            appendAudit(Domain.AuditEventType.ProjectScanFailed, Domain.AuditLevel.ERROR, false,
                    "Scan workspace " + workspaceId, failed.summary(), workspaceId, scanRun.id(), e,
                    Map.of("errorType", e.getClass().getSimpleName()));
            throw e;
        }
    }

    /**
     * Loads the latest profile for a workspace.
     */
    public ProjectProfile getProfile(UUID workspaceId) {
        workspaceService.getRequired(workspaceId);
        return repository.findProfileByWorkspace(workspaceId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "PROJECT_PROFILE_NOT_FOUND",
                        "Project profile not found for workspace: " + workspaceId));
    }

    /**
     * Lists scan runs for a workspace.
     */
    public List<ProjectScanRun> scanRuns(UUID workspaceId) {
        workspaceService.getRequired(workspaceId);
        return repository.findScanRunsByWorkspace(workspaceId);
    }

    /**
     * Lists reusable project memory items for a workspace.
     */
    public List<ProjectMemoryItem> memoryItems(UUID workspaceId) {
        workspaceService.getRequired(workspaceId);
        return repository.findProjectMemoryItemsByWorkspace(workspaceId);
    }

    /**
     * Manually creates a project memory item. User-created memory is approved by
     * default because the caller is explicitly recording it.
     */
    public ProjectMemoryItem createMemoryItem(UUID workspaceId, CreateProjectMemoryRequest request) {
        workspaceService.getRequired(workspaceId);
        var memoryType = enumName(Domain.ProjectMemoryType.class, request.memoryType(), "memoryType");
        var status = request.status() == null || request.status().isBlank()
                ? Domain.ProjectMemoryStatus.APPROVED.name()
                : enumName(Domain.ProjectMemoryStatus.class, request.status(), "status");
        var now = Instant.now();
        var item = new ProjectMemoryItem(UUID.randomUUID(), workspaceId, memoryType,
                blankDefault(request.scope(), "workspace"), required(request.title(), "title"),
                required(request.content(), "content"), blankDefault(request.sourceType(), "USER"),
                request.sourceId(), request.sourcePath(), request.sourceLineStart(), request.sourceLineEnd(),
                status, request.confidence() == null ? 1.0 : request.confidence(), request.expiresAt(),
                blankDefault(request.createdBy(), "api"), now,
                Domain.ProjectMemoryStatus.APPROVED.name().equals(status) ? blankDefault(request.createdBy(), "api") : null,
                Domain.ProjectMemoryStatus.APPROVED.name().equals(status) ? now : null,
                request.metadata() == null ? Map.of() : request.metadata());
        return repository.insertProjectMemoryItem(item);
    }

    private void appendAudit(Domain.AuditEventType eventType, Domain.AuditLevel level, boolean success,
                             String inputSummary, String outputSummary, UUID workspaceId, UUID scanRunId,
                             Exception error, Map<String, Object> metadata) {
        var merged = new java.util.LinkedHashMap<>(metadata);
        merged.put("workspaceId", workspaceId.toString());
        merged.put("scanRunId", scanRunId.toString());
        auditService.append(new AuditEventDraft(null, null, null, null, eventType, Domain.AuditActor.RUNTIME,
                level, inputSummary, outputSummary, List.of(), null, null, null, null,
                Domain.PermissionLevel.READ_ONLY, Domain.RiskLevel.LOW, null, success,
                error == null ? null : "PROJECT_SCAN_FAILED", error == null ? null : error.getMessage(), merged));
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PROJECT_MEMORY",
                    "Project memory " + field + " is required");
        }
        return value;
    }

    private String blankDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private <E extends Enum<E>> String enumName(Class<E> enumType, String value, String field) {
        try {
            return Enum.valueOf(enumType, required(value, field)).name();
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PROJECT_MEMORY",
                    "Invalid project memory " + field + ": " + value);
        }
    }
}
