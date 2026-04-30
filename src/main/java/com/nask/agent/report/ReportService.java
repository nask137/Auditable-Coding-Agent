package com.nask.agent.report;

import com.nask.agent.audit.AuditService;
import com.nask.agent.common.ApiException;
import com.nask.agent.file.FileChangeRepository;
import com.nask.agent.llm.FinalReportDraft;
import com.nask.agent.llm.LlmGateway;
import com.nask.agent.llm.ReportContext;
import com.nask.agent.task.CodingTask;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Builds final run reports from model output, file changes, and audit events.
 */
@Service
public class ReportService {
    private final TaskReportRepository repository;
    private final LlmGateway llmGateway;
    private final FileChangeRepository fileChangeRepository;
    private final AuditService auditService;

    /**
     * Creates a report service.
     */
    public ReportService(TaskReportRepository repository, LlmGateway llmGateway,
                         FileChangeRepository fileChangeRepository, AuditService auditService) {
        this.repository = repository;
        this.llmGateway = llmGateway;
        this.fileChangeRepository = fileChangeRepository;
        this.auditService = auditService;
    }

    /**
     * Generates and persists a Markdown report for the given task/run.
     */
    public TaskReport generate(CodingTask task, UUID runId, String resultSummary) {
        FinalReportDraft draft = llmGateway.generateReport(new ReportContext(task.id(), runId, task.userRequest(), resultSummary));
        var changes = fileChangeRepository.findByTask(task.id());
        var events = auditService.eventsForTask(task.id());
        // The LLM drafts the narrative, while deterministic sections append the
        // exact file-change and audit trails stored by the runtime.
        var content = draft.markdown()
                + "\n## File Changes\n\n"
                + changes.stream().map(change -> "- `%s` %s".formatted(change.path(), change.changeType()))
                .reduce("", (a, b) -> a + b + "\n")
                + "\n## Audit Events\n\n"
                + events.stream().map(event -> "- %s `%s`".formatted(event.occurredAt(), event.eventType()))
                .reduce("", (a, b) -> a + b + "\n");
        return repository.insert(new TaskReport(UUID.randomUUID(), task.id(), runId, content, Instant.now()));
    }

    /**
     * Loads the latest report or raises a REST-friendly 404 exception.
     */
    public TaskReport getLatestRequired(UUID taskId) {
        return repository.findLatestByTask(taskId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "REPORT_NOT_FOUND", "Report not found for task: " + taskId));
    }
}
