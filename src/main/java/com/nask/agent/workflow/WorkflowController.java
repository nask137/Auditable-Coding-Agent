package com.nask.agent.workflow;

import com.nask.agent.task.TaskService;
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
    private final TaskService taskService;

    public WorkflowController(WorkflowService workflowService, TaskService taskService) {
        this.workflowService = workflowService;
        this.taskService = taskService;
    }

    @GetMapping("/workflows")
    List<WorkflowDefinition> workflows() {
        return workflowService.listDefinitions();
    }

    @GetMapping("/workflows/{workflowId}")
    WorkflowDefinition workflow(@PathVariable UUID workflowId) {
        return workflowService.getDefinition(workflowId);
    }

    @GetMapping("/tasks/{taskId}/workflow")
    WorkflowDefinition taskWorkflow(@PathVariable UUID taskId) {
        var task = taskService.getRequired(taskId);
        return workflowService.resolveForTask(task);
    }

    @GetMapping("/tasks/{taskId}/workflow/nodes")
    List<WorkflowNodeExecution> nodes(@PathVariable UUID taskId) {
        return workflowService.nodes(taskId);
    }

    @GetMapping("/tasks/{taskId}/workflow/edges")
    List<WorkflowEdgeDecision> edges(@PathVariable UUID taskId) {
        return workflowService.edges(taskId);
    }
}
