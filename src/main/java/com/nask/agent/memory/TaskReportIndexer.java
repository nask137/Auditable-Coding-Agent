package com.nask.agent.memory;

import com.nask.agent.common.Domain;
import com.nask.agent.workspace.Workspace;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

/**
 * Indexes historical task reports as workspace project memory context.
 */
@Component
public class TaskReportIndexer {
    private final DocumentChunker chunker;
    private final ProjectMemoryRepository repository;

    public TaskReportIndexer(DocumentChunker chunker, ProjectMemoryRepository repository) {
        this.chunker = chunker;
        this.repository = repository;
    }

    /**
     * Indexes reports for tasks attached to the workspace.
     */
    public int indexWorkspaceReports(Workspace workspace, UUID scanRunId) {
        var documents = new ArrayList<IndexedDocument>();
        for (var report : repository.findTaskReportsByWorkspace(workspace.id())) {
            var path = "task-reports/%s/%s.md".formatted(report.taskId(), report.id());
            for (var chunk : chunker.chunk(report.contentMd())) {
                documents.add(new IndexedDocument(UUID.randomUUID(), workspace.id(), scanRunId, path,
                        Domain.IndexedDocumentType.TASK_REPORT.name(), "Task report " + report.taskId(),
                        chunk.chunkIndex(), chunk.content(), ContentHash.sha256(chunk.content()), chunk.lineStart(),
                        chunk.lineEnd(), chunk.tokenCount(), Map.of(
                        "source", "task_report",
                        "taskId", report.taskId().toString(),
                        "runId", report.runId().toString(),
                        "reportId", report.id().toString()), Instant.now()));
            }
        }
        return repository.replaceIndexedDocuments(workspace.id(), "task_report", documents);
    }
}
