package com.nask.agent.workflow;

import com.nask.agent.common.Domain;
import com.nask.agent.report.ReportService;
import com.nask.agent.run.AgentLoopExecutor;
import com.nask.agent.run.AgentRun;
import com.nask.agent.run.AgentRunService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Workflow-native executor for built-in local agent modes.
 */
@Primary
@Service
public class WorkflowAgentExecutor implements AgentLoopExecutor {
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
            var next = selectNext(graph, node.id(), withTransientData(stateAssembler.assemble(runId), transientData),
                    result);
            if (next == null) {
                failRun(runId, "No workflow edge matched after node " + node.id() + " with status "
                        + lastResult.status());
                return;
            }
            workflowService.recordEdge(state.task().id(), runId, workflow, node.id(), next.to(),
                    Domain.WorkflowEdgeType.valueOf(next.type()), edgeCondition(next), "Selected by workflow runtime",
                    Map.of("lastStatus", result.status()));
            current = next.to();
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
                node.id(), result.summary(), result.payload())) {
            return;
        }
        workflowService.recordNode(state.task().id(), state.run().id(), workflow, node.id(),
                Domain.WorkflowNodeType.valueOf(node.type()), stepId, status, node.id(), result.summary(),
                result.payload());
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
                state.recoveryNotes(), Map.copyOf(transientData));
    }

    private void failRun(UUID runId, String reason) {
        var state = stateAssembler.assemble(runId);
        runService.fail(runId, state.task().id(), reason);
        reportService.generate(state.task(), runId, "Failed: " + reason);
    }
}
