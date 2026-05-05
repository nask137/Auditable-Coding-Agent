package com.nask.agent.memory;

import com.nask.agent.workspace.WorkspaceService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Query facade for code symbols and file outlines.
 */
@Service
public class CodeSymbolService {
    private final WorkspaceService workspaceService;
    private final ProjectMemoryRepository repository;

    public CodeSymbolService(WorkspaceService workspaceService, ProjectMemoryRepository repository) {
        this.workspaceService = workspaceService;
        this.repository = repository;
    }

    /**
     * Searches symbols by optional name query and optional symbol type.
     */
    public List<CodeSymbol> search(UUID workspaceId, String query, String symbolType) {
        workspaceService.getRequired(workspaceId);
        return repository.searchCodeSymbols(workspaceId, query, symbolType);
    }

    /**
     * Returns all symbols for one file path in outline order.
     */
    public List<CodeSymbol> outline(UUID workspaceId, String path) {
        workspaceService.getRequired(workspaceId);
        return repository.findCodeSymbolsByPath(workspaceId, path);
    }
}
