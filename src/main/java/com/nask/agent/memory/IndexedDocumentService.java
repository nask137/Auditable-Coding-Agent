package com.nask.agent.memory;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Read facade for indexed document chunks.
 */
@Service
public class IndexedDocumentService {
    private final ProjectMemoryRepository repository;

    public IndexedDocumentService(ProjectMemoryRepository repository) {
        this.repository = repository;
    }

    /**
     * Lists indexed document chunks for a workspace.
     */
    public List<IndexedDocument> findByWorkspace(UUID workspaceId) {
        return repository.findIndexedDocumentsByWorkspace(workspaceId);
    }
}
