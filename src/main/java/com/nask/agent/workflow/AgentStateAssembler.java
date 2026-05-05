package com.nask.agent.workflow;

import com.nask.agent.command.CommandExecutionRepository;
import com.nask.agent.file.FileChangeRepository;
import com.nask.agent.memory.MemoryContext;
import com.nask.agent.memory.ProjectMemoryRepository;
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
    private final ProjectMemoryRepository projectMemoryRepository;

    public AgentStateAssembler(AgentRunService runService, TaskService taskService, WorkspaceService workspaceService,
                               WorkflowService workflowService, PlanService planService,
                               FileChangeRepository fileChangeRepository,
                               CommandExecutionRepository commandExecutionRepository,
                               ValidationRepository validationRepository,
                               UserInputRequestService userInputRequestService,
                               RuntimeFailureService runtimeFailureService,
                               ProjectMemoryRepository projectMemoryRepository) {
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
        this.projectMemoryRepository = projectMemoryRepository;
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

        // 装配 MemoryContext
        var memoryContext = loadMemoryContextForRun(runId, task.workspaceId());

        return new AgentState(task, run, workspace, workflow, plan, currentItem,
                fileChangeRepository.findByTask(task.id()),
                commandExecutionRepository.findByTask(task.id()),
                validationRepository.findByTask(task.id()),
                userInputRequestService.pendingByRun(runId),
                failures,
                List.copyOf(notes),
                memoryContext,
                java.util.Map.of());
    }

    private MemoryContext loadMemoryContextForRun(UUID runId, UUID workspaceId) {
        // 尝试从本次运行的最新检索记录中加载
        try {
            var retrievals = projectMemoryRepository.findMemoryRetrievalsByRun(runId);
            if (retrievals != null && !retrievals.isEmpty()) {
                // 获取最新的检索记录（已按 created_at desc 排序）
                var latest = retrievals.get(0);

                // 获取项目画像
                var profile = projectMemoryRepository.findProfileByWorkspace(workspaceId).orElse(null);

                // 使用检索记录的信息和项目画像构造 MemoryContext
                // （方便后续节点使用这个上下文）
                return new MemoryContext(
                        latest.id(),
                        workspaceId,
                        latest.queryText(),
                        profile,
                        List.of(),  // 结果通过 source refs 获取
                        latest.resultRefs(),
                        latest.summary());
            }
        } catch (Exception e) {
            // 静默处理异常 - 如果无法加载记忆上下文，继续进行
            // 原始逻辑（在上游节点中）应该能够处理 null memoryContext
        }
        return null;
    }
}
