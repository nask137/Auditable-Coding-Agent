package com.nask.agent.workflow;

import com.nask.agent.audit.AuditEventDraft;
import com.nask.agent.audit.AuditService;
import com.nask.agent.common.ApiException;
import com.nask.agent.common.Domain;
import com.nask.agent.task.CodingTask;
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

    public WorkflowDefinition resolveForTask(CodingTask task) {
        ensureBuiltIns();
        var name = task.runtimeMetadata().getOrDefault("workflow", DEFAULT_WORKFLOW).toString();
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
                null, now, completedAt(status, now),
                metadata == null ? Map.of() : metadata));
        auditService.append(AuditEventDraft.info(taskId, runId, stepId, Domain.AuditEventType.WorkflowNodeCompleted,
                Domain.AuditActor.RUNTIME, "Workflow node " + nodeId, status.name() + ": " + outputSummary));
        return node;
    }

    public boolean updateStepNode(UUID taskId, UUID runId, UUID stepId, Domain.WorkflowNodeStatus status,
                                  String inputSummary, String outputSummary, Map<String, Object> metadata) {
        var updated = repository.updateNodeExecutionForStep(runId, stepId, status.name(), inputSummary, outputSummary,
                completedAt(status, Instant.now()), metadata == null ? Map.of() : metadata);
        if (updated > 0) {
            auditService.append(AuditEventDraft.info(taskId, runId, stepId, Domain.AuditEventType.WorkflowNodeCompleted,
                    Domain.AuditActor.RUNTIME, "Workflow node updated", status.name() + ": " + outputSummary));
        }
        return updated > 0;
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

    private Instant completedAt(Domain.WorkflowNodeStatus status, Instant now) {
        return switch (status) {
            case SUCCESS, FAILURE, BLOCKED, FINISHED -> now;
            case RUNNING, WAITING_APPROVAL, WAITING_USER_INPUT -> null;
        };
    }

    private void upsertBuiltIn(String name, Domain.WorkflowMode mode, String description) {
        var now = Instant.now();
        repository.upsertDefinition(new WorkflowDefinition(UUID.randomUUID(), name, 1, description, mode.name(),
                true, definition(name, mode), now, now));
    }

    private Map<String, Object> definition(String name, Domain.WorkflowMode mode) {
        var nodes = switch (mode) {
            case REVIEW -> List.of(
                    node("inspect_workspace", Domain.WorkflowNodeType.WORKSPACE_INSPECTION),
                    node("project_scan", Domain.WorkflowNodeType.PROJECT_SCAN),
                    node("project_memory", Domain.WorkflowNodeType.PROJECT_MEMORY),
                    node("code_understanding", Domain.WorkflowNodeType.CODE_UNDERSTANDING),
                    node("report", Domain.WorkflowNodeType.REPORT),
                    node("finish", Domain.WorkflowNodeType.FINISH));
            case TEST -> List.of(
                    node("inspect_workspace", Domain.WorkflowNodeType.WORKSPACE_INSPECTION),
                    node("project_scan", Domain.WorkflowNodeType.PROJECT_SCAN),
                    node("project_memory", Domain.WorkflowNodeType.PROJECT_MEMORY),
                    node("validate", Domain.WorkflowNodeType.VALIDATION),
                    node("report", Domain.WorkflowNodeType.REPORT),
                    node("finish", Domain.WorkflowNodeType.FINISH));
            default -> List.of(
                    node("understand_task", Domain.WorkflowNodeType.TASK_UNDERSTANDING),
                    node("inspect_workspace", Domain.WorkflowNodeType.WORKSPACE_INSPECTION),
                    node("project_scan", Domain.WorkflowNodeType.PROJECT_SCAN),
                    node("project_memory", Domain.WorkflowNodeType.PROJECT_MEMORY),
                    node("code_understanding", Domain.WorkflowNodeType.CODE_UNDERSTANDING),
                    node("create_plan", Domain.WorkflowNodeType.PLAN_CREATION),
                    node("execute_plan_item", Domain.WorkflowNodeType.PLAN_ITEM_EXECUTION),
                    node("validate", Domain.WorkflowNodeType.VALIDATION),
                    node("task_summary_memory", Domain.WorkflowNodeType.TASK_SUMMARY_MEMORY),
                    node("report", Domain.WorkflowNodeType.REPORT),
                    node("finish", Domain.WorkflowNodeType.FINISH));
        };
        var start = switch (mode) {
            case REVIEW, TEST -> "inspect_workspace";
            default -> "understand_task";
        };
        var edges = switch (mode) {
            case REVIEW -> List.of(
                    edge("inspect_workspace", "project_scan", Domain.WorkflowEdgeType.ON_SUCCESS),
                    edge("project_scan", "project_memory", Domain.WorkflowEdgeType.ON_SUCCESS),
                    edge("project_memory", "code_understanding", Domain.WorkflowEdgeType.ON_SUCCESS),
                    edge("code_understanding", "report", Domain.WorkflowEdgeType.ON_SUCCESS),
                    edge("report", "finish", Domain.WorkflowEdgeType.ON_SUCCESS));
            case TEST -> List.of(
                    edge("inspect_workspace", "project_scan", Domain.WorkflowEdgeType.ON_SUCCESS),
                    edge("project_scan", "project_memory", Domain.WorkflowEdgeType.ON_SUCCESS),
                    edge("project_memory", "validate", Domain.WorkflowEdgeType.ON_SUCCESS),
                    edge("validate", "report", Domain.WorkflowEdgeType.ON_SUCCESS),
                    edge("report", "finish", Domain.WorkflowEdgeType.ON_SUCCESS));
            default -> List.of(
                    edge("understand_task", "inspect_workspace", Domain.WorkflowEdgeType.ON_SUCCESS),
                    edge("inspect_workspace", "project_scan", Domain.WorkflowEdgeType.ON_SUCCESS),
                    edge("project_scan", "project_memory", Domain.WorkflowEdgeType.ON_SUCCESS),
                    edge("project_memory", "code_understanding", Domain.WorkflowEdgeType.ON_SUCCESS),
                    edge("code_understanding", "create_plan", Domain.WorkflowEdgeType.ON_SUCCESS),
                    edge("create_plan", "execute_plan_item", Domain.WorkflowEdgeType.ON_SUCCESS),
                    edge("execute_plan_item", "execute_plan_item", Domain.WorkflowEdgeType.CONDITION,
                            "plan.hasPendingItems"),
                    edge("execute_plan_item", "validate", Domain.WorkflowEdgeType.CONDITION, "plan.completed"),
                    edge("validate", "execute_plan_item", Domain.WorkflowEdgeType.CONDITION, "plan.hasPendingItems"),
                    edge("validate", "task_summary_memory", Domain.WorkflowEdgeType.ON_SUCCESS),
                    edge("task_summary_memory", "report", Domain.WorkflowEdgeType.ON_SUCCESS),
                    edge("report", "finish", Domain.WorkflowEdgeType.ON_SUCCESS));
        };
        return Map.of(
                "name", name,
                "version", 1,
                "mode", mode.name(),
                "start", start,
                "limits", Map.of("maxNodes", 30, "maxLoops", 10, "maxFailures", 3),
                "nodes", nodes,
                "edges", edges);
    }

    private Map<String, Object> node(String id, Domain.WorkflowNodeType type) {
        return Map.of("id", id, "type", type.name(), "input", Map.of());
    }

    private Map<String, Object> edge(String from, String to, Domain.WorkflowEdgeType type) {
        return edge(from, to, type, "");
    }

    private Map<String, Object> edge(String from, String to, Domain.WorkflowEdgeType type, String condition) {
        return Map.of("from", from, "to", to, "type", type.name(), "condition", condition);
    }
}
