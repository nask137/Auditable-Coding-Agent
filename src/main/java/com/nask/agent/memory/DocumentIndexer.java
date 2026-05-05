package com.nask.agent.memory;

import com.nask.agent.common.Domain;
import com.nask.agent.workspace.Workspace;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

/**
 * Indexes workspace documents and config files observed during a project scan.
 */
@Component
public class DocumentIndexer {
    private final DocumentChunker chunker;
    private final DocumentTypeResolver documentTypeResolver;
    private final ConfigDocumentSummarizer configSummarizer;
    private final ProjectMemoryRepository repository;

    public DocumentIndexer(DocumentChunker chunker, DocumentTypeResolver documentTypeResolver,
                           ConfigDocumentSummarizer configSummarizer, ProjectMemoryRepository repository) {
        this.chunker = chunker;
        this.documentTypeResolver = documentTypeResolver;
        this.configSummarizer = configSummarizer;
        this.repository = repository;
    }

    /**
     * Indexes README/docs/config/build/migration observations from a scan.
     */
    public int indexWorkspaceDocuments(Workspace workspace, UUID scanRunId, ProjectScanResult scanResult) {
        var documents = new ArrayList<IndexedDocument>();
        for (var observation : scanResult.observations()) {
            if (!documentTypeResolver.indexable(observation) || !observation.contentRead()
                    || observation.contentSample() == null || observation.contentSample().isBlank()) {
                continue;
            }
            var documentType = documentTypeResolver.resolve(observation);
            var content = shouldSummarize(documentType)
                    ? configSummarizer.summarize(observation.contentSample())
                    : observation.contentSample();
            var title = title(observation.path());
            for (var chunk : chunker.chunk(content)) {
                documents.add(new IndexedDocument(UUID.randomUUID(), workspace.id(), scanRunId, observation.path(),
                        documentType, title, chunk.chunkIndex(), chunk.content(), ContentHash.sha256(chunk.content()),
                        chunk.lineStart(), chunk.lineEnd(), chunk.tokenCount(), Map.of(
                        "source", "workspace_scan",
                        "fileType", observation.fileType().name(),
                        "originalSizeBytes", observation.sizeBytes()), Instant.now()));
            }
        }
        return repository.replaceIndexedDocuments(workspace.id(), "workspace_scan", documents);
    }

    private boolean shouldSummarize(String documentType) {
        return Domain.IndexedDocumentType.CONFIG.name().equals(documentType)
                || Domain.IndexedDocumentType.BUILD_FILE.name().equals(documentType)
                || Domain.IndexedDocumentType.MIGRATION.name().equals(documentType);
    }

    private String title(String path) {
        var normalized = path == null ? "" : path.replace('\\', '/');
        var index = normalized.lastIndexOf('/');
        return index < 0 ? normalized : normalized.substring(index + 1);
    }
}
