package com.nask.agent.memory;

import com.nask.agent.workspace.WorkspaceService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Assembles project profile and ranked retrieval hits into a workflow-ready context.
 */
@Service
public class ProjectContextRetriever {
    private final WorkspaceService workspaceService;
    private final ProjectMemoryRepository repository;
    private final MemorySearchService searchService;

    public ProjectContextRetriever(WorkspaceService workspaceService, ProjectMemoryRepository repository,
                                   MemorySearchService searchService) {
        this.workspaceService = workspaceService;
        this.repository = repository;
        this.searchService = searchService;
    }

    /**
     * Retrieves context and records the selected source references.
     */
    public MemoryContext retrieve(MemoryQuery query) {
        workspaceService.getRequired(query.workspaceId());
        var profile = repository.findProfileByWorkspace(query.workspaceId()).orElse(null);
        var results = searchService.search(query);
        var refs = results.stream().map(MemorySearchResult::source).toList();
        var summary = "Retrieved " + results.size() + " context results for query: " + query.queryText();
        var retrieval = repository.insertMemoryRetrieval(new MemoryRetrieval(UUID.randomUUID(), query.workspaceId(),
                query.taskId(), query.runId(), query.workflowNodeExecutionId(), query.queryText(),
                filters(query), refs, summary, Instant.now()));
        return new MemoryContext(retrieval.id(), query.workspaceId(), query.queryText(), profile,
                results, refs, summary);
    }

    /**
     * Lists persisted retrieval records for inspection.
     */
    public List<MemoryRetrieval> retrievals(UUID workspaceId) {
        workspaceService.getRequired(workspaceId);
        return repository.findMemoryRetrievalsByWorkspace(workspaceId);
    }

    private Map<String, Object> filters(MemoryQuery query) {
        var filters = new LinkedHashMap<String, Object>();
        filters.put("memoryTypes", query.memoryTypes());
        filters.put("documentTypes", query.documentTypes());
        filters.put("symbolTypes", query.symbolTypes());
        filters.put("limit", query.limit());
        return filters;
    }
}
