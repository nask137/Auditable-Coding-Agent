package com.nask.agent.memory;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST API for phase 4 project scanning and project profiles.
 */
@RestController
@RequestMapping("/api/workspaces")
public class ProjectMemoryController {
    private final ProjectMemoryService service;
    private final CodeSymbolService codeSymbolService;
    private final ProjectContextRetriever contextRetriever;

    public ProjectMemoryController(ProjectMemoryService service, CodeSymbolService codeSymbolService,
                                   ProjectContextRetriever contextRetriever) {
        this.service = service;
        this.codeSymbolService = codeSymbolService;
        this.contextRetriever = contextRetriever;
    }

    /**
     * Triggers a bounded project scan.
     */
    @PostMapping("/{workspaceId}/scan")
    ProjectScanRun scan(@PathVariable UUID workspaceId) {
        return service.scan(workspaceId);
    }

    /**
     * Returns the latest project profile for a workspace.
     */
    @GetMapping("/{workspaceId}/profile")
    ProjectProfile profile(@PathVariable UUID workspaceId) {
        return service.getProfile(workspaceId);
    }

    /**
     * Lists scan history for a workspace.
     */
    @GetMapping("/{workspaceId}/scan-runs")
    List<ProjectScanRun> scanRuns(@PathVariable UUID workspaceId) {
        return service.scanRuns(workspaceId);
    }

    /**
     * Lists scan execution history for a workspace.
     */
    @GetMapping("/{workspaceId}/scan-executions")
    List<ProjectScanRun> scanExecutions(@PathVariable UUID workspaceId) {
        return service.scanRuns(workspaceId);
    }

    /**
     * Lists project memory items for a workspace.
     */
    @GetMapping("/{workspaceId}/memory")
    List<ProjectMemoryItem> memory(@PathVariable UUID workspaceId) {
        return service.memoryItems(workspaceId);
    }

    /**
     * Manually records a project memory item.
     */
    @PostMapping("/{workspaceId}/memory")
    ProjectMemoryItem remember(@PathVariable UUID workspaceId, @RequestBody CreateProjectMemoryRequest request) {
        return service.createMemoryItem(workspaceId, request);
    }

    /**
     * Searches code symbols for a workspace.
     */
    @GetMapping("/{workspaceId}/symbols")
    List<CodeSymbol> symbols(@PathVariable UUID workspaceId,
                             @RequestParam(required = false) String query,
                             @RequestParam(required = false) String type) {
        return codeSymbolService.search(workspaceId, query, type);
    }

    /**
     * Returns the code outline for one workspace-relative file path.
     */
    @GetMapping("/{workspaceId}/outline")
    List<CodeSymbol> outline(@PathVariable UUID workspaceId, @RequestParam String path) {
        return codeSymbolService.outline(workspaceId, path);
    }

    /**
     * Retrieves unified project context from profile, docs, symbols, and memory.
     */
    @GetMapping("/{workspaceId}/search-context")
    MemoryContext searchContext(@PathVariable UUID workspaceId,
                                @RequestParam(name = "q", defaultValue = "") String query,
                                @RequestParam(required = false) List<String> memoryType,
                                @RequestParam(required = false) List<String> documentType,
                                @RequestParam(required = false) List<String> symbolType,
                                @RequestParam(defaultValue = "10") int limit) {
        return contextRetriever.retrieve(new MemoryQuery(workspaceId, query, null, null, null,
                memoryType, documentType, symbolType, limit));
    }

    /**
     * Lists persisted context retrieval records for a workspace.
     */
    @GetMapping("/{workspaceId}/memory-retrievals")
    List<MemoryRetrieval> memoryRetrievals(@PathVariable UUID workspaceId) {
        return contextRetriever.retrievals(workspaceId);
    }
}
