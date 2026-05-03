package com.nask.agent.workflow;

import com.nask.agent.command.CommandExecutionRepository;
import com.nask.agent.file.FileChangeRepository;
import com.nask.agent.plan.PlanService;
import com.nask.agent.runtime.RuntimeFailureService;
import com.nask.agent.runtime.UserInputRequestService;
import com.nask.agent.run.AgentRunService;
import com.nask.agent.task.TaskService;
import com.nask.agent.validation.ValidationRepository;
import com.nask.agent.workspace.WorkspaceService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Builds a structured workflow state view from existing durable runtime facts.
 */
@Component
public class AgentStateAssembler {
    private final AgentRunService runService;
    private final TaskService taskService;
    private final WorkspaceService workspaceService;
    private final WorkflowService workflowService;
    private final PlanService planService;
    private final FileChangeRepository fileChangeRepository;
    private final CommandExecutionRepository commandExecutionRepository;
    private final ValidationRepository validationRepository;
    private final UserInputRequestService userInputRequestService;
    private final RuntimeFailureService runtimeFailureService;

    public AgentStateAssembler(AgentRunService runService, TaskService taskService, WorkspaceService workspaceService,
                               WorkflowService workflowService, PlanService planService,
                               FileChangeRepository fileChangeRepository,
                               CommandExecutionRepository commandExecutionRepository,
                               ValidationRepository validationRepository,
                               UserInputRequestService userInputRequestService,
                               RuntimeFailureService runtimeFailureService) {
        this.runService = runService;
        this.taskService = taskService;
        this.workspaceService = workspaceService;
        this.workflowService = workflowService;
        this.planService = planService;
        this.fileChangeRepository = fileChangeRepository;
        this.commandExecutionRepository = commandExecutionRepository;
        this.validationRepository = validationRepository;
        this.userInputRequestService = userInputRequestService;
        this.runtimeFailureService = runtimeFailureService;
    }

    public AgentState assemble(UUID runId) {
        var run = runService.getRequired(runId);
        var task = taskService.getRequired(run.taskId());
        var workspace = workspaceService.getRequired(task.workspaceId());
        var workflow = workflowService.resolveForRun(run);
        var plan = planService.findByRun(runId);
        var currentItem = plan == null ? null : planService.nextPending(plan.plan().id());
        var failures = runtimeFailureService.findByRun(runId);
        var notes = failures.stream()
                .map(failure -> failure.failureType() + ": " + failure.summary())
                .limit(5)
                .toList();
        return new AgentState(task, run, workspace, workflow, plan, currentItem,
                fileChangeRepository.findByTask(task.id()),
                commandExecutionRepository.findByTask(task.id()),
                validationRepository.findByTask(task.id()),
                userInputRequestService.pendingByRun(runId),
                failures,
                List.copyOf(notes));
    }
}
