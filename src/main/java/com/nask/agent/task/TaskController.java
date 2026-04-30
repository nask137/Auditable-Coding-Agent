package com.nask.agent.task;

import com.nask.agent.run.AgentLoopExecutor;
import com.nask.agent.run.AgentRun;
import com.nask.agent.run.AgentRunService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    private final TaskService taskService;
    private final AgentRunService runService;
    private final AgentLoopExecutor loopExecutor;

    public TaskController(TaskService taskService, AgentRunService runService, AgentLoopExecutor loopExecutor) {
        this.taskService = taskService;
        this.runService = runService;
        this.loopExecutor = loopExecutor;
    }

    @PostMapping
    CodingTask create(@Valid @RequestBody CreateTaskRequest request) {
        return taskService.create(request);
    }

    @GetMapping("/{taskId}")
    CodingTask get(@PathVariable UUID taskId) {
        return taskService.getRequired(taskId);
    }

    @PostMapping("/{taskId}/start")
    AgentRun start(@PathVariable UUID taskId) {
        var task = taskService.getRequired(taskId);
        var run = runService.createRun(task);
        loopExecutor.execute(run.id());
        return runService.getRequired(run.id());
    }

    @PostMapping("/{taskId}/cancel")
    CodingTask cancel(@PathVariable UUID taskId) {
        return taskService.cancel(taskId);
    }
}
