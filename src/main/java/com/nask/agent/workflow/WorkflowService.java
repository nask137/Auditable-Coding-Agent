package com.nask.agent.workflow;

import com.nask.agent.audit.AuditEventDraft;
import com.nask.agent.audit.AuditService;
import com.nask.agent.common.ApiException;
import com.nask.agent.common.Domain;
import com.nask.agent.run.AgentRun;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Application service for built-in workflow definitions and execution records.
 */
@Service
public class WorkflowService {
    public static final String DEFAULT_WORKFLOW = "coding-agent";

    private final WorkflowRepository repository;
    private final AuditService auditService;

    public WorkflowService(WorkflowRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    public List<WorkflowDefinition> listDefinitions() {
        ensureBuiltIns();
        return repository.findDefinitions();
    }

    public WorkflowDefinition getDefinition(UUID id) {
        ensureBuiltIns();
        return repository.findDefinition(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "WORKFLOW_NOT_FOUND", "Workflow not found: " + id));
    }

    public WorkflowDefinition resolveForRun(AgentRun run) {
        ensureBuiltIns();
        var name = run.runtimeMetadata().getOrDefault("workflow", DEFAULT_WORKFLOW).toString();
        return requireEnabledByName(name);
    }

    public WorkflowDefinition requireEnabledByName(String name) {
        ensureBuiltIns();
        var workflowName = name == null || name.isBlank() ? DEFAULT_WORKFLOW : name;
        return repository.findLatestEnabledByName(workflowName).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "WORKFLOW_NOT_FOUND", "Workflow not found: " + workflowName));
    }

    public void ensureBuiltIns() {
        upsertBuiltIn("coding-agent", Domain.WorkflowMode.CODING, "Default audited coding workflow");
        upsertBuiltIn("review-agent", Domain.WorkflowMode.REVIEW, "Read-only review workflow");
        upsertBuiltIn("test-agent", Domain.WorkflowMode.TEST, "Validation-focused workflow");
    }

    public WorkflowNodeExecution recordNode(UUID taskId, UUID runId, WorkflowDefinition workflow, String nodeId,
                                            Domain.WorkflowNodeType nodeType, UUID stepId,
                                            Domain.WorkflowNodeStatus status, String inputSummary,
                                            String outputSummary, Map<String, Object> metadata) {
        var now = Instant.now();
        var node = repository.insertNodeExecution(new WorkflowNodeExecution(UUID.randomUUID(), taskId, runId,
                workflow.id(), nodeId, nodeType.name(), stepId, status.name(), inputSummary, outputSummary,
                null, now, status == Domain.WorkflowNodeStatus.RUNNING ? null : now,
                metadata == null ? Map.of() : metadata));
        auditService.append(AuditEventDraft.info(taskId, runId, stepId, Domain.AuditEventType.WorkflowNodeCompleted,
                Domain.AuditActor.RUNTIME, "Workflow node " + nodeId, status.name() + ": " + outputSummary));
        return node;
    }

    public WorkflowEdgeDecision recordEdge(UUID taskId, UUID runId, WorkflowDefinition workflow, String fromNode,
                                           String toNode, Domain.WorkflowEdgeType edgeType,
                                           String conditionSummary, String reason, Map<String, Object> metadata) {
        var decision = repository.insertEdgeDecision(new WorkflowEdgeDecision(UUID.randomUUID(), taskId, runId,
                workflow.id(), fromNode, toNode, edgeType.name(), conditionSummary, reason, true,
                Instant.now(), metadata == null ? Map.of() : metadata));
        auditService.append(AuditEventDraft.info(taskId, runId, null, Domain.AuditEventType.WorkflowEdgeSelected,
                Domain.AuditActor.RUNTIME, "Workflow edge " + fromNode + " -> " + toNode, reason));
        return decision;
    }

    public List<WorkflowNodeExecution> nodes(UUID runId) {
        return repository.findNodeExecutionsByRun(runId);
    }

    public List<WorkflowEdgeDecision> edges(UUID runId) {
        return repository.findEdgeDecisionsByRun(runId);
    }

    private void upsertBuiltIn(String name, Domain.WorkflowMode mode, String description) {
        var now = Instant.now();
        repository.upsertDefinition(new WorkflowDefinition(UUID.randomUUID(), name, 1, description, mode.name(),
                true, definition(name, mode), now, now));
    }

    private Map<String, Object> definition(String name, Domain.WorkflowMode mode) {
        var nodes = switch (mode) {
            case REVIEW -> List.of("inspect_workspace", "report", "finish");
            case TEST -> List.of("inspect_workspace", "validate", "report", "finish");
            default -> List.of("understand_task", "inspect_workspace", "create_plan", "execute_plan_item",
                    "validate", "report", "finish");
        };
        return Map.of(
                "name", name,
                "version", 1,
                "mode", mode.name(),
                "start", nodes.getFirst(),
                "limits", Map.of("maxNodes", 30, "maxLoops", 10, "maxFailures", 3),
                "nodes", nodes);
    }
}
