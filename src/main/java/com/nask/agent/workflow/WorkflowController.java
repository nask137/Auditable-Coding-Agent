package com.nask.agent.workflow;

import com.nask.agent.run.AgentRun;
import com.nask.agent.run.AgentRunService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST API for workflow definitions and workflow execution observations.
 */
@RestController
@RequestMapping("/api")
public class WorkflowController {
    private final WorkflowService workflowService;
    private final AgentRunService runService;

    public WorkflowController(WorkflowService workflowService, AgentRunService runService) {
        this.workflowService = workflowService;
        this.runService = runService;
    }

    @GetMapping("/workflows")
    List<WorkflowDefinition> workflows() {
        return workflowService.listDefinitions();
    }

    @GetMapping("/workflows/{workflowId}")
    WorkflowDefinition workflow(@PathVariable UUID workflowId) {
        return workflowService.getDefinition(workflowId);
    }

    @GetMapping("/runs/{runId}/workflow")
    WorkflowDefinition runWorkflow(@PathVariable UUID runId) {
        AgentRun run = runService.getRequired(runId);
        return workflowService.resolveForRun(run);
    }

    @GetMapping("/runs/{runId}/workflow/nodes")
    List<WorkflowNodeExecution> nodes(@PathVariable UUID runId) {
        return workflowService.nodes(runId);
    }

    @GetMapping("/runs/{runId}/workflow/edges")
    List<WorkflowEdgeDecision> edges(@PathVariable UUID runId) {
        return workflowService.edges(runId);
    }
}
