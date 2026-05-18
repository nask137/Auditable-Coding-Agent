package com.nask.agent.task;

import com.nask.agent.plan.PlanService;
import com.nask.agent.plan.PlanView;
import com.nask.agent.step.AgentStep;
import com.nask.agent.step.AgentStepService;
import com.nask.agent.common.TaskIntentClassifier;
import com.nask.agent.workflow.WorkflowService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST API for task creation, lookup, starting, and cancellation.
 */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    private final TaskService taskService;
    private final TaskExecutionExecutor executionExecutor;
    private final TaskExecutionAsyncExecutor asyncExecutor;
    private final WorkflowService workflowService;
    private final TaskTimelineService timelineService;
    private final PlanService planService;
    private final AgentStepService stepService;

    /**
     * Creates a task controller with task orchestration services.
     */
    public TaskController(TaskService taskService, TaskExecutionExecutor executionExecutor,
                          TaskExecutionAsyncExecutor asyncExecutor, WorkflowService workflowService,
                          TaskTimelineService timelineService, PlanService planService, AgentStepService stepService) {
        this.taskService = taskService;
        this.executionExecutor = executionExecutor;
        this.asyncExecutor = asyncExecutor;
        this.workflowService = workflowService;
        this.timelineService = timelineService;
        this.planService = planService;
        this.stepService = stepService;
    }

    /**
     * Lists tasks for read-only dashboard selectors.
     */
    @GetMapping
    List<CodingTask> list() {
        return taskService.list();
    }

    /**
     * Creates a task in {@code CREATED} status.
     */
    @PostMapping
    CodingTask create(@Valid @RequestBody CreateTaskRequest request) {
        return taskService.create(request);
    }

    /**
     * Fetches the current task state.
     */
    @GetMapping("/{taskId}")
    CodingTask get(@PathVariable UUID taskId) {
        return taskService.getRequired(taskId);
    }

    /**
     * Starts a synchronous execution for the task.
     */
    @PostMapping("/{taskId}/start")
    CodingTask start(@PathVariable UUID taskId,
                   @RequestParam(name = "workflow", required = false) String workflow) {
        var task = taskService.getRequired(taskId);
        var selectedWorkflow = TaskIntentClassifier.defaultWorkflowFor(workflow, task.userRequest());
        var workflowDefinition = workflowService.requireEnabledByName(selectedWorkflow);
        taskService.startExecution(task, workflowDefinition.name());
        // Phase 1 executes inline so API clients can immediately observe the final
        // state or a WAITING_APPROVAL pause without a background worker.
        executionExecutor.execute(task.id());
        return taskService.getRequired(taskId);
    }

    /**
     * Starts a task execution on a background worker for interactive polling clients.
     */
    @PostMapping("/{taskId}/start-async")
    CodingTask startAsync(@PathVariable UUID taskId,
                        @RequestParam(name = "workflow", required = false) String workflow) {
        var task = taskService.getRequired(taskId);
        var selectedWorkflow = TaskIntentClassifier.defaultWorkflowFor(workflow, task.userRequest());
        var workflowDefinition = workflowService.requireEnabledByName(selectedWorkflow);
        taskService.startExecution(task, workflowDefinition.name());
        asyncExecutor.submit(task.id());
        return taskService.getRequired(taskId);
    }

    /**
     * Cancels a task from the API surface.
     */
    @PostMapping("/{taskId}/cancel")
    CodingTask cancel(@PathVariable UUID taskId) {
        return taskService.cancel(taskId);
    }

    /**
     * Returns the full task execution timeline. Task id is the execution id in the single-run model.
     */
    @GetMapping("/{taskId}/timeline")
    TaskTimeline timeline(@PathVariable UUID taskId) {
        return timelineService.get(taskId);
    }

    @GetMapping("/{taskId}/plan")
    PlanView plan(@PathVariable UUID taskId) {
        return planService.getByRun(taskId);
    }

    @GetMapping("/{taskId}/steps")
    List<AgentStep> steps(@PathVariable UUID taskId) {
        return stepService.findByRun(taskId);
    }

}
