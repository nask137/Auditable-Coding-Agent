package com.nask.agent.task;

import com.nask.agent.run.AgentLoopExecutor;
import com.nask.agent.run.AgentRunService;
import com.nask.agent.run.RunTimeline;
import com.nask.agent.run.RunTimelineService;
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
    private final AgentRunService runService;
    private final AgentLoopExecutor loopExecutor;
    private final com.nask.agent.run.AgentRunAsyncExecutor asyncExecutor;
    private final WorkflowService workflowService;
    private final RunTimelineService timelineService;

    /**
     * Creates a task controller with task and run orchestration services.
     */
    public TaskController(TaskService taskService, AgentRunService runService, AgentLoopExecutor loopExecutor,
                          com.nask.agent.run.AgentRunAsyncExecutor asyncExecutor, WorkflowService workflowService,
                          RunTimelineService timelineService) {
        this.taskService = taskService;
        this.runService = runService;
        this.loopExecutor = loopExecutor;
        this.asyncExecutor = asyncExecutor;
        this.workflowService = workflowService;
        this.timelineService = timelineService;
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
     * Starts a synchronous Phase 1 agent run for the task.
     */
    @PostMapping("/{taskId}/start")
    CodingTask start(@PathVariable UUID taskId,
                   @RequestParam(name = "workflow", required = false, defaultValue = "coding-agent") String workflow) {
        var task = taskService.getRequired(taskId);
        var workflowDefinition = workflowService.requireEnabledByName(workflow);
        var run = runService.createRun(task, workflowDefinition.name());
        // Phase 1 runs inline so API clients can immediately observe the final
        // state or a WAITING_APPROVAL pause without a background worker.
        loopExecutor.execute(run.id());
        return taskService.getRequired(taskId);
    }

    /**
     * Starts a run on a background worker for interactive polling clients.
     */
    @PostMapping("/{taskId}/start-async")
    CodingTask startAsync(@PathVariable UUID taskId,
                        @RequestParam(name = "workflow", required = false, defaultValue = "coding-agent") String workflow) {
        var task = taskService.getRequired(taskId);
        var workflowDefinition = workflowService.requireEnabledByName(workflow);
        var run = runService.createRun(task, workflowDefinition.name());
        asyncExecutor.submit(task.id(), run.id());
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
    RunTimeline timeline(@PathVariable UUID taskId) {
        return timelineService.get(taskId);
    }
}
