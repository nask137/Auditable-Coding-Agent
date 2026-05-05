package com.nask.agent.memory;

import com.nask.agent.common.Domain;
import com.nask.agent.workspace.Workspace;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.UUID;

/**
 * Indexes code symbols from scan observations.
 */
@Component
public class CodeSymbolIndexer {
    private final JavaSymbolExtractor javaSymbolExtractor;
    private final ProjectMemoryRepository repository;

    public CodeSymbolIndexer(JavaSymbolExtractor javaSymbolExtractor, ProjectMemoryRepository repository) {
        this.javaSymbolExtractor = javaSymbolExtractor;
        this.repository = repository;
    }

    /**
     * Replaces the workspace symbol index with symbols from this scan.
     */
    public int indexWorkspaceSymbols(Workspace workspace, UUID scanRunId, ProjectScanResult scanResult) {
        var symbols = new ArrayList<CodeSymbol>();
        for (var observation : scanResult.observations()) {
            if ((observation.fileType() == Domain.ProjectFileType.SOURCE
                    || observation.fileType() == Domain.ProjectFileType.TEST)
                    && observation.contentRead() && observation.path().endsWith(".java")) {
                symbols.addAll(javaSymbolExtractor.extract(workspace.id(), scanRunId, observation));
            }
        }
        repository.replaceCodeSymbols(workspace.id(), symbols);
        return symbols.size();
    }
}
