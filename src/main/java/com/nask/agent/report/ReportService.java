package com.nask.agent.report;

import com.nask.agent.audit.AuditService;
import com.nask.agent.common.ApiException;
import com.nask.agent.conversation.ConversationService;
import com.nask.agent.file.FileChangeRepository;
import com.nask.agent.git.GitToolService;
import com.nask.agent.llm.LlmGateway;
import com.nask.agent.llm.ReportContext;
import com.nask.agent.memory.ProjectMemoryRepository;
import com.nask.agent.runtime.RuntimeFailureService;
import com.nask.agent.task.CodingTask;
import com.nask.agent.workspace.WorkspaceService;
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
    private final FileChangeRepository fileChangeRepository;
    private final AuditService auditService;
    private final RuntimeFailureService runtimeFailureService;
    private final WorkflowService workflowService;
    private final ProjectMemoryRepository projectMemoryRepository;
    private final ConversationService conversationService;
    private final WorkspaceService workspaceService;
    private final GitToolService gitToolService;
    private final LlmGateway llmGateway;

    /**
     * Creates a report service.
     */
    public ReportService(TaskReportRepository repository,
                         FileChangeRepository fileChangeRepository, AuditService auditService,
                         RuntimeFailureService runtimeFailureService, WorkflowService workflowService,
                         ProjectMemoryRepository projectMemoryRepository, ConversationService conversationService,
                         WorkspaceService workspaceService,
                         GitToolService gitToolService, LlmGateway llmGateway) {
        this.repository = repository;
        this.fileChangeRepository = fileChangeRepository;
        this.auditService = auditService;
        this.runtimeFailureService = runtimeFailureService;
        this.workflowService = workflowService;
        this.projectMemoryRepository = projectMemoryRepository;
        this.conversationService = conversationService;
        this.workspaceService = workspaceService;
        this.gitToolService = gitToolService;
        this.llmGateway = llmGateway;
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
        var workflowSummaries = workflowNodes.stream()
                .filter(node -> node.outputSummary() != null && !node.outputSummary().isBlank())
                .map(node -> "%s %s - %s".formatted(node.nodeId(), node.status(), compact(node.outputSummary(), 180)))
                .toList();
        var changedFiles = changes.stream().map(change -> change.path()).distinct().toList();
        var gitStatus = gitToolService.inspectWorkspaceStatus(workspaceService.getRequired(task.workspaceId()), ".");
        var gitChangedFiles = gitStatus.changedFiles();
        var combinedChangedFiles = java.util.stream.Stream.concat(changedFiles.stream(), gitChangedFiles.stream())
                .distinct()
                .toList();
        var previousPrompts = previousTasks.stream().map(previous -> compact(previous.prompt(), 300)).toList();
        var projectContext = new java.util.ArrayList<String>();
        projectContext.add(profile == null ? "Project profile: not available"
                : "Project profile: %s; frameworks %s; build tools %s; test tools %s"
                .formatted(profile.languageSummary(), profile.frameworks(), profile.buildTools(), profile.testTools()));
        retrievals.stream().map(retrieval -> "Retrieval `%s`: %s; query `%s`; sources %s"
                        .formatted(retrieval.id(), retrieval.summary(), retrieval.queryText(),
                                retrieval.resultRefs().stream().map(this::sourceRef).toList()))
                .forEach(projectContext::add);
        proposals.stream().map(proposal -> "Memory proposal `%s` `%s`: %s"
                        .formatted(proposal.id(), proposal.status(), proposal.title()))
                .forEach(projectContext::add);
        var reportContext = new ReportContext(task.id(), runId, task.userRequest(), resultSummary,
                workflowSummaries, combinedChangedFiles, gitStatus.statusLines(), previousPrompts,
                List.of(), projectContext);
        var narrative = reportNarrative(reportContext);
        var content = narrative
                + "\n\n## Runtime Details\n\n"
                + "- Request: %s\n".formatted(compact(task.userRequest(), 500))
                + "- Result: %s\n".formatted(compact(resultSummary, 500))
                + "- Conversation memory: %s\n".formatted(previousPrompts.isEmpty()
                ? "no previous prompt found in this conversation"
                : "previous prompt was `%s`".formatted(previousPrompts.getFirst()))
                + "- Changed files: %s\n".formatted(combinedChangedFiles.isEmpty()
                ? "none" : String.join(", ", combinedChangedFiles))
                + "- Agent-recorded changes: %s\n".formatted(changedFiles.isEmpty()
                ? "none" : String.join(", ", changedFiles))
                + "- Git status: %s\n".formatted(gitStatus.available()
                ? (gitStatus.statusLines().isEmpty() ? "clean" : String.join("; ", gitStatus.statusLines()))
                : "unavailable: " + compact(gitStatus.summary(), 160))
                + "\n## Project Context\n\n"
                + projectContext.stream().map(item -> "- " + item).reduce("", (a, b) -> a + b + "\n")
                + "\n## File Changes\n\n"
                + changes.stream().map(change -> "- `%s` %s".formatted(change.path(), change.changeType()))
                .reduce("", (a, b) -> a + b + "\n")
                + "\n## Git Working Tree\n\n"
                + gitStatusSection(gitStatus)
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

    private String reportNarrative(ReportContext context) {
        try {
            return llmGateway.generateReport(context).markdown().strip();
        } catch (RuntimeException e) {
            return """
                    # Result

                    %s

                    Report narration could not be generated by the model. Runtime details are preserved below.
                    """.formatted(compact(context.resultSummary(), 500)).strip();
        }
    }

    private String gitStatusSection(GitToolService.GitWorkspaceStatus gitStatus) {
        if (!gitStatus.available()) {
            return "- unavailable: " + compact(gitStatus.summary(), 300) + "\n";
        }
        if (gitStatus.statusLines().isEmpty()) {
            return "- clean\n";
        }
        return gitStatus.statusLines().stream()
                .map(line -> "- `" + line + "`")
                .reduce("", (a, b) -> a + b + "\n");
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
