package com.nask.agent.workflow;

import com.nask.agent.common.Domain;
import com.nask.agent.report.ReportService;
import com.nask.agent.run.AgentLoopExecutor;
import com.nask.agent.run.AgentRun;
import com.nask.agent.run.AgentRunService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.nask.agent.memory.MemoryContext;

/**
 * Workflow-native executor for built-in local agent modes.
 */
@Primary
@Service
public class WorkflowAgentExecutor implements AgentLoopExecutor {
    private static final Logger log = LoggerFactory.getLogger(WorkflowAgentExecutor.class);

    private final WorkflowService workflowService;
    private final AgentRunService runService;
    private final AgentStateAssembler stateAssembler;
    private final WorkflowNodeRegistry nodeRegistry;
    private final ConditionEvaluator conditionEvaluator;
    private final ReportService reportService;

    public WorkflowAgentExecutor(WorkflowService workflowService, AgentRunService runService,
                                 AgentStateAssembler stateAssembler, WorkflowNodeRegistry nodeRegistry,
                                 ConditionEvaluator conditionEvaluator, ReportService reportService) {
        this.workflowService = workflowService;
        this.runService = runService;
        this.stateAssembler = stateAssembler;
        this.nodeRegistry = nodeRegistry;
        this.conditionEvaluator = conditionEvaluator;
        this.reportService = reportService;
    }

    @Override
    public void execute(UUID runId) {
        try {
            var run = runService.getRequired(runId);
            if (!Domain.AgentRunStatus.RUNNING.name().equals(run.status())) {
                return;
            }
            var workflow = workflowService.resolveForRun(run);
            var graph = WorkflowGraph.from(workflow);
            var current = resumeNode(run, graph);
            var transientData = new HashMap<String, Object>();
            NodeExecutionResult lastResult = null;
            var visited = 0;
            while (current != null) {
                if (++visited > graph.maxNodes()) {
                    failRun(runId, "Maximum workflow node count exceeded");
                    return;
                }
                var node = graph.nodes().get(current);
                if (node == null) {
                    failRun(runId, "Workflow node not found: " + current);
                    return;
                }
                var state = withTransientData(stateAssembler.assemble(runId), transientData);
                var result = nodeRegistry.get(node.type()).execute(state, node);
                transientData.putAll(result.payload());
                recordNode(workflow, state, node, result);
                lastResult = result;
                if (isPaused(result)) {
                    return;
                }
                if (isFailed(result)) {
                    failRun(runId, result.summary());
                    return;
                }
                if (Domain.WorkflowNodeType.FINISH.name().equals(node.type())) {
                    return;
                }
                var decisionState = withTransientData(stateAssembler.assemble(runId), transientData);
                var next = selectNext(graph, node.id(), decisionState, result);
                if (next == null) {
                    failRun(runId, "No workflow edge matched after node " + node.id() + " with status "
                            + lastResult.status());
                    return;
                }
                workflowService.recordEdge(state.task().id(), runId, workflow, node.id(), next.to(),
                        Domain.WorkflowEdgeType.valueOf(next.type()), edgeCondition(next),
                        edgeDecisionReason(next, decisionState, result), edgeMetadata(next, decisionState, result));
                current = next.to();
            }
        } catch (RuntimeException e) {
            failRun(runId, "Workflow execution failed: " + message(e));
        }
    }

    private String resumeNode(AgentRun run, WorkflowGraph graph) {
        var waiting = workflowService.nodes(run.id()).stream()
                .filter(node -> Domain.WorkflowNodeStatus.WAITING_APPROVAL.name().equals(node.status())
                        || Domain.WorkflowNodeStatus.WAITING_USER_INPUT.name().equals(node.status()))
                .reduce((first, second) -> second);
        if (waiting.isPresent() && graph.nodes().containsKey(waiting.get().nodeId())) {
            return waiting.get().nodeId();
        }
        return graph.start();
    }

    private void recordNode(WorkflowDefinition workflow, AgentState state, MapWorkflowNode node,
                            NodeExecutionResult result) {
        var stepId = result.payload().get("stepId") == null ? null
                : UUID.fromString(result.payload().get("stepId").toString());
        var status = status(node, result);
        if (stepId != null && workflowService.updateStepNode(state.task().id(), state.run().id(), stepId, status,
                node.id(), result.summary(), persistedPayload(result.payload()))) {
            return;
        }
        workflowService.recordNode(state.task().id(), state.run().id(), workflow, node.id(),
                Domain.WorkflowNodeType.valueOf(node.type()), stepId, status, node.id(), result.summary(),
                persistedPayload(result.payload()));
    }

    private Domain.WorkflowNodeStatus status(MapWorkflowNode node, NodeExecutionResult result) {
        if (Domain.WorkflowNodeType.FINISH.name().equals(node.type())) {
            return Domain.WorkflowNodeStatus.FINISHED;
        }
        return switch (result.status()) {
            case "SUCCESS" -> Domain.WorkflowNodeStatus.SUCCESS;
            case "WAITING_APPROVAL" -> Domain.WorkflowNodeStatus.WAITING_APPROVAL;
            case "WAITING_USER_INPUT" -> Domain.WorkflowNodeStatus.WAITING_USER_INPUT;
            case "BLOCKED" -> Domain.WorkflowNodeStatus.BLOCKED;
            case "FAILURE" -> Domain.WorkflowNodeStatus.FAILURE;
            default -> Domain.WorkflowNodeStatus.RUNNING;
        };
    }

    private WorkflowEdge selectNext(WorkflowGraph graph, String current, AgentState state, NodeExecutionResult result) {
        for (var edge : graph.edges()) {
            if (!current.equals(edge.from())) {
                continue;
            }
            if (matches(edge, state, result)) {
                return edge;
            }
        }
        return null;
    }

    private boolean matches(WorkflowEdge edge, AgentState state, NodeExecutionResult result) {
        var type = Domain.WorkflowEdgeType.valueOf(edge.type());
        return switch (type) {
            case ALWAYS -> true;
            case ON_SUCCESS -> "SUCCESS".equals(result.status());
            case ON_FAILURE -> "FAILURE".equals(result.status());
            case ON_BLOCKED -> "BLOCKED".equals(result.status());
            case ON_WAITING_APPROVAL -> "WAITING_APPROVAL".equals(result.status());
            case ON_WAITING_USER_INPUT -> "WAITING_USER_INPUT".equals(result.status());
            case CONDITION -> !edge.condition().isBlank()
                    && conditionEvaluator.evaluate(edge.condition(), state, result);
            default -> false;
        };
    }

    private String edgeCondition(WorkflowEdge edge) {
        return edge.condition().isBlank() ? edge.type() : edge.condition();
    }

    private String edgeDecisionReason(WorkflowEdge edge, AgentState state, NodeExecutionResult result) {
        var type = Domain.WorkflowEdgeType.valueOf(edge.type());
        var base = switch (type) {
            case ALWAYS -> "Edge is unconditional";
            case ON_SUCCESS -> "Previous node completed with SUCCESS";
            case ON_FAILURE -> "Previous node completed with FAILURE";
            case ON_BLOCKED -> "Previous node completed with BLOCKED";
            case ON_WAITING_APPROVAL -> "Previous node is waiting for approval";
            case ON_WAITING_USER_INPUT -> "Previous node is waiting for user input";
            case CONDITION -> "Condition '" + edge.condition() + "' evaluated to true";
            default -> "Workflow edge matched";
        };
        return base + "; selected transition " + edge.from() + " -> " + edge.to()
                + "; node output: " + summarize(result.summary())
                + planItemContext(state);
    }

    private Map<String, Object> edgeMetadata(WorkflowEdge edge, AgentState state, NodeExecutionResult result) {
        var metadata = new HashMap<String, Object>();
        metadata.put("fromNode", edge.from());
        metadata.put("toNode", edge.to());
        metadata.put("edgeType", edge.type());
        metadata.put("condition", edge.condition());
        metadata.put("lastStatus", result.status());
        metadata.put("lastSummary", summarize(result.summary()));
        if (state.currentPlanItem() != null) {
            var item = state.currentPlanItem();
            metadata.put("currentPlanItemId", item.id().toString());
            metadata.put("currentPlanItemOrder", item.orderIndex());
            metadata.put("currentPlanItemStatus", item.status());
            metadata.put("currentPlanItem", "#" + item.orderIndex() + " " + item.status() + " - "
                    + summarize(item.description()));
        } else {
            metadata.put("currentPlanItem", "none");
        }
        metadata.put("hasPlan", state.plan() != null);
        metadata.put("recentValidationCount", state.recentValidationResults().size());
        metadata.put("recentFailureCount", state.runtimeFailures().size());
        return metadata;
    }

    private String planItemContext(AgentState state) {
        if (state.currentPlanItem() == null) {
            return "; current plan item: none";
        }
        var item = state.currentPlanItem();
        return "; current plan item: #" + item.orderIndex() + " " + item.status() + " - "
                + summarize(item.description());
    }

    private String summarize(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        var normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 180 ? normalized : normalized.substring(0, 177) + "...";
    }

    private boolean isPaused(NodeExecutionResult result) {
        return "WAITING_APPROVAL".equals(result.status()) || "WAITING_USER_INPUT".equals(result.status());
    }

    private boolean isFailed(NodeExecutionResult result) {
        return "BLOCKED".equals(result.status()) || "FAILURE".equals(result.status());
    }

    private AgentState withTransientData(AgentState state, Map<String, Object> transientData) {
        return new AgentState(state.task(), state.run(), state.workspace(), state.workflow(), state.plan(),
                state.currentPlanItem(), state.recentFileChanges(), state.recentCommandExecutions(),
                state.recentValidationResults(), state.pendingUserInput(), state.runtimeFailures(),
                state.recoveryNotes(), memoryContext(transientData), Map.copyOf(transientData));
    }

    private MemoryContext memoryContext(Map<String, Object> transientData) {
        var value = transientData.get("memoryContext");
        return value instanceof MemoryContext context ? context : null;
    }

    private Map<String, Object> persistedPayload(Map<String, Object> payload) {
        var persisted = new HashMap<String, Object>(payload);
        var context = persisted.get("memoryContext");
        if (context instanceof MemoryContext memoryContext) {
            persisted.put("memoryContext", Map.of(
                    "retrievalId", memoryContext.retrievalId().toString(),
                    "queryText", memoryContext.queryText(),
                    "resultCount", memoryContext.results().size(),
                    "summary", memoryContext.summary()));
        }
        return persisted;
    }

    private void failRun(UUID runId, String reason) {
        var state = stateAssembler.assemble(runId);
        runService.fail(runId, state.task().id(), reason);
        try {
            reportService.generate(state.task(), runId, "Failed: " + reason);
        } catch (RuntimeException e) {
            log.warn("Failed to generate failure report for run {}", runId, e);
        }
    }

    private String message(RuntimeException e) {
        return e.getMessage() == null || e.getMessage().isBlank() ? e.getClass().getSimpleName() : e.getMessage();
    }
}
