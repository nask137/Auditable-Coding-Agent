package com.nask.agent.report;

import com.nask.agent.audit.AuditService;
import com.nask.agent.common.ApiException;
import com.nask.agent.conversation.ConversationService;
import com.nask.agent.file.FileChangeRepository;
import com.nask.agent.llm.FinalReportDraft;
import com.nask.agent.llm.LlmGateway;
import com.nask.agent.llm.ReportContext;
import com.nask.agent.memory.ProjectMemoryRepository;
import com.nask.agent.runtime.RuntimeFailureService;
import com.nask.agent.task.CodingTask;
import com.nask.agent.tool.ToolRecordRepository;
import com.nask.agent.workflow.WorkflowService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
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
    private final RuntimeFailureService runtimeFailureService;
    private final WorkflowService workflowService;
    private final ProjectMemoryRepository projectMemoryRepository;
    private final ConversationService conversationService;
    private final ToolRecordRepository toolRecordRepository;

    /**
     * Creates a report service.
     */
    public ReportService(TaskReportRepository repository, LlmGateway llmGateway,
                         FileChangeRepository fileChangeRepository, AuditService auditService,
                         RuntimeFailureService runtimeFailureService, WorkflowService workflowService,
                         ProjectMemoryRepository projectMemoryRepository, ConversationService conversationService,
                         ToolRecordRepository toolRecordRepository) {
        this.repository = repository;
        this.llmGateway = llmGateway;
        this.fileChangeRepository = fileChangeRepository;
        this.auditService = auditService;
        this.runtimeFailureService = runtimeFailureService;
        this.workflowService = workflowService;
        this.projectMemoryRepository = projectMemoryRepository;
        this.conversationService = conversationService;
        this.toolRecordRepository = toolRecordRepository;
    }

    /**
     * Generates and persists a Markdown report for the given task/run.
     */
    public TaskReport generate(CodingTask task, UUID runId, String resultSummary) {
        var changes = fileChangeRepository.findByTask(task.id());
        var events = auditService.eventsForTask(task.id());
        var failures = runtimeFailureService.findByTask(task.id());
        var workflowNodes = workflowService.nodes(runId);
        var workflowEdges = workflowService.edges(runId);
        var profile = projectMemoryRepository.findProfileByWorkspace(task.workspaceId()).orElse(null);
        var retrievals = projectMemoryRepository.findMemoryRetrievalsByRun(runId);
        var proposals = projectMemoryRepository.findMemoryWriteProposalsByRun(runId);
        var previousTasks = conversationService.previousTaskContext(task.conversationId(), task.id(), 5);
        var recentToolObservations = toolRecordRepository.findRecentSummariesByRun(runId, 8);
        var workflowSummaries = workflowNodes.stream()
                .filter(node -> node.outputSummary() != null && !node.outputSummary().isBlank())
                .map(node -> "%s %s - %s".formatted(node.nodeId(), node.status(), compact(node.outputSummary(), 180)))
                .toList();
        var changedFiles = changes.stream().map(change -> change.path()).distinct().toList();
        var previousPrompts = previousTasks.stream().map(previous -> compact(previous.prompt(), 300)).toList();
        FinalReportDraft draft = llmGateway.generateReport(new ReportContext(task.id(), runId,
                task.userRequest(), resultSummary, workflowSummaries, changedFiles, previousPrompts,
                recentToolObservations));
        // The LLM drafts the narrative, while deterministic sections append the
        // exact file-change and audit trails stored by the runtime.
        var content = deterministicSummary(task, resultSummary, workflowSummaries, changedFiles, previousPrompts)
                + "\n## Model Summary\n\n"
                + draft.markdown()
                + "\n## Project Context\n\n"
                + (profile == null ? "- Project profile: not available\n"
                : "- Project profile: %s; frameworks %s; build tools %s; test tools %s\n"
                .formatted(profile.languageSummary(), profile.frameworks(), profile.buildTools(),
                        profile.testTools()))
                + retrievals.stream().map(retrieval -> "- Retrieval `%s`: %s; query `%s`; sources %s"
                        .formatted(retrieval.id(), retrieval.summary(), retrieval.queryText(),
                                retrieval.resultRefs().stream().map(this::sourceRef).toList()))
                .reduce("", (a, b) -> a + b + "\n")
                + proposals.stream().map(proposal -> "- Memory proposal `%s` `%s`: %s"
                        .formatted(proposal.id(), proposal.status(), proposal.title()))
                .reduce("", (a, b) -> a + b + "\n")
                + "\n## File Changes\n\n"
                + changes.stream().map(change -> "- `%s` %s".formatted(change.path(), change.changeType()))
                .reduce("", (a, b) -> a + b + "\n")
                + "\n## Failure and Recovery\n\n"
                + failures.stream().map(failure -> "- `%s` strategy `%s`: %s"
                        .formatted(failure.failureType(), failure.strategy(), failure.summary()))
                .reduce("", (a, b) -> a + b + "\n")
                + "\n## Workflow\n\n"
                + workflowNodes.stream().map(node -> "- Node `%s` `%s`: %s"
                        .formatted(node.nodeId(), node.status(), node.outputSummary()))
                .reduce("", (a, b) -> a + b + "\n")
                + workflowEdges.stream().map(edge -> "- Edge `%s` -> `%s`: %s"
                        .formatted(edge.fromNodeId(), edge.toNodeId(), edge.decisionReason()))
                .reduce("", (a, b) -> a + b + "\n")
                + "\n## Audit Events\n\n"
                + events.stream().map(event -> "- %s `%s`".formatted(event.occurredAt(), event.eventType()))
                .reduce("", (a, b) -> a + b + "\n");
        return repository.insert(new TaskReport(UUID.randomUUID(), task.id(), runId, content, Instant.now()));
    }

    private String deterministicSummary(CodingTask task, String resultSummary, List<String> workflowSummaries,
                                        List<String> changedFiles, List<String> previousPrompts) {
        return """
                # Agent Run Report

                ## Summary

                - Request: %s
                - Result: %s
                - Conversation memory: %s
                - Changed files: %s

                ## Key Outputs

                %s

                """.formatted(compact(task.userRequest(), 500), compact(resultSummary, 500),
                previousPrompts.isEmpty()
                        ? "no previous prompt found in this conversation"
                        : "previous prompt was `%s`".formatted(previousPrompts.getFirst()),
                changedFiles.isEmpty() ? "none" : String.join(", ", changedFiles),
                workflowSummaries.isEmpty()
                        ? "- No workflow output summaries were recorded."
                        : workflowSummaries.stream().limit(8)
                        .map(summary -> "- " + summary)
                        .reduce("", (a, b) -> a + b + "\n").strip());
    }

    private String compact(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "(none)";
        }
        var normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength - 3) + "...";
    }

    private String sourceRef(com.nask.agent.memory.SourceReference ref) {
        if (ref.path() == null || ref.path().isBlank()) {
            return ref.sourceType() + ":" + ref.sourceId();
        }
        var range = ref.lineStart() == null ? "" : ":" + ref.lineStart()
                + (ref.lineEnd() == null ? "" : "-" + ref.lineEnd());
        return ref.sourceType() + ":" + ref.path() + range;
    }

    /**
     * Loads the latest report or raises a REST-friendly 404 exception.
     */
    public TaskReport getLatestRequired(UUID taskId) {
        return repository.findLatestByTask(taskId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "REPORT_NOT_FOUND", "Report not found for task: " + taskId));
    }
}
