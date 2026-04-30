package com.nask.agent.run;

import com.nask.agent.action.AgentActionService;
import com.nask.agent.command.CommandToolService;
import com.nask.agent.common.AgentSettings;
import com.nask.agent.common.Domain;
import com.nask.agent.file.FileChangeRepository;
import com.nask.agent.file.FileToolService;
import com.nask.agent.llm.ExecutionContext;
import com.nask.agent.llm.LlmGateway;
import com.nask.agent.llm.PlanningContext;
import com.nask.agent.llm.TaskContext;
import com.nask.agent.llm.ValidationContext;
import com.nask.agent.plan.PlanItem;
import com.nask.agent.plan.PlanService;
import com.nask.agent.report.ReportService;
import com.nask.agent.step.AgentStepService;
import com.nask.agent.task.TaskService;
import com.nask.agent.tool.ToolExecutionContext;
import com.nask.agent.tool.ToolExecutionResult;
import com.nask.agent.validation.ValidationService;
import com.nask.agent.workspace.WorkspaceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DefaultAgentLoopExecutor implements AgentLoopExecutor {
    private final AgentRunService runService;
    private final TaskService taskService;
    private final WorkspaceService workspaceService;
    private final AgentStepService stepService;
    private final AgentActionService actionService;
    private final PlanService planService;
    private final LlmGateway llmGateway;
    private final FileToolService fileToolService;
    private final ReportService reportService;
    private final CommandToolService commandToolService;
    private final ValidationService validationService;
    private final AgentSettings settings;
    private final FileChangeRepository fileChangeRepository;

    public DefaultAgentLoopExecutor(AgentRunService runService, TaskService taskService,
                                    WorkspaceService workspaceService, AgentStepService stepService,
                                    AgentActionService actionService, PlanService planService,
                                    LlmGateway llmGateway, FileToolService fileToolService,
                                    ReportService reportService, CommandToolService commandToolService,
                                    ValidationService validationService, AgentSettings settings,
                                    FileChangeRepository fileChangeRepository) {
        this.runService = runService;
        this.taskService = taskService;
        this.workspaceService = workspaceService;
        this.stepService = stepService;
        this.actionService = actionService;
        this.planService = planService;
        this.llmGateway = llmGateway;
        this.fileToolService = fileToolService;
        this.reportService = reportService;
        this.commandToolService = commandToolService;
        this.validationService = validationService;
        this.settings = settings;
        this.fileChangeRepository = fileChangeRepository;
    }

    @Override
    @Transactional
    public void execute(UUID runId) {
        var run = runService.getRequired(runId);
        if (!Domain.AgentRunStatus.RUNNING.name().equals(run.status())) {
            return;
        }
        var task = taskService.getRequired(run.taskId());
        var workspace = workspaceService.getRequired(task.workspaceId());
        workspaceService.touch(workspace.id());

        try {
            var understandStep = stepService.start(task.id(), run.id(), null, Domain.StepType.UNDERSTAND_TASK, "Understand task");
            var understanding = llmGateway.understandTask(new TaskContext(task.id(), workspace.id(), task.userRequest()));
            stepService.complete(task.id(), run.id(), understandStep, understanding.summary());

            var inspectStep = stepService.start(task.id(), run.id(), null, Domain.StepType.INSPECT_WORKSPACE, "Inspect workspace");
            var inspectAction = actionService.create(inspectStep.id(), Domain.ActionType.CALL_TOOL,
                    "List workspace files for planning", Domain.RiskLevel.LOW);
            var listed = fileToolService.listFiles(new ToolExecutionContext(task.id(), run.id(), inspectStep.id(),
                    inspectAction.id(), workspace), ".", 4);
            if (listed.waitingApproval()) {
                stepService.complete(task.id(), run.id(), inspectStep, listed.summary());
                return;
            }
            if (listed.blocked()) {
                stepService.fail(task.id(), run.id(), inspectStep, listed.summary());
                runService.fail(run.id(), task.id(), listed.summary());
                reportService.generate(task, run.id(), "Failed during workspace inspection: " + listed.summary());
                return;
            }
            stepService.complete(task.id(), run.id(), inspectStep, listed.summary());

            var observedFiles = observedFiles(listed.payload());
            var planStep = stepService.start(task.id(), run.id(), null, Domain.StepType.CREATE_PLAN, "Create plan");
            var plan = planService.create(task.id(), run.id(), llmGateway.createPlan(
                    new PlanningContext(task.id(), run.id(), understanding, observedFiles)));
            stepService.complete(task.id(), run.id(), planStep, "Created " + plan.items().size() + " plan items");

            var executedSteps = 0;
            PlanItem next;
            while ((next = planService.nextPending(plan.plan().id())) != null) {
                if (++executedSteps > settings.maxSteps()) {
                    runService.fail(run.id(), task.id(), "Maximum agent step count exceeded");
                    return;
                }
                var result = executePlanItem(task.id(), run.id(), workspace, next, observedFiles);
                if (result.waitingApproval()) {
                    return;
                }
                if (result.blocked()) {
                    planService.updateItemStatus(next.id(), Domain.PlanItemStatus.FAILED);
                    runService.fail(run.id(), task.id(), result.summary());
                    reportService.generate(task, run.id(), "Failed: " + result.summary());
                    return;
                }
                planService.updateItemStatus(next.id(), Domain.PlanItemStatus.COMPLETED);
            }

            planService.updatePlanStatus(plan.plan().id(), Domain.PlanStatus.COMPLETED);
            var validationResult = validateIfNeeded(task.id(), run.id(), workspace);
            if (validationResult.waitingApproval()) {
                return;
            }
            if (validationResult.blocked()) {
                runService.fail(run.id(), task.id(), validationResult.summary());
                reportService.generate(task, run.id(), "Failed validation: " + validationResult.summary());
                return;
            }
            reportService.generate(task, run.id(), validationResult.summary());
            runService.complete(run.id(), task.id());
        } catch (Exception e) {
            runService.fail(run.id(), task.id(), e.getMessage());
            reportService.generate(task, run.id(), "Failed: " + e.getMessage());
        }
    }

    private ToolExecutionResult validateIfNeeded(UUID taskId, UUID runId, com.nask.agent.workspace.Workspace workspace) {
        var validation = llmGateway.suggestValidation(new ValidationContext(taskId, runId, workspace.id()));
        if (!validation.shouldValidate() || validation.executableAndArgs().isEmpty()) {
            return ToolExecutionResult.success("Task completed. No validation command selected.", Map.of());
        }
        var step = stepService.start(taskId, runId, null, Domain.StepType.VALIDATE, validation.reason());
        var action = actionService.create(step.id(), Domain.ActionType.RUN_VALIDATION, validation.reason(), Domain.RiskLevel.MEDIUM);
        var executable = validation.executableAndArgs().get(0);
        var arguments = validation.executableAndArgs().subList(1, validation.executableAndArgs().size());
        var result = commandToolService.runCommand(new ToolExecutionContext(taskId, runId, step.id(), action.id(), workspace),
                executable, arguments, ".", validation.reason());
        if (result.waitingApproval() || result.blocked()) {
            stepService.complete(taskId, runId, step, result.summary());
            return result;
        }
        var exitCode = integer(result.payload(), "exitCode", 1);
        var commandId = uuid(result.payload().get("commandId"));
        validationService.record(taskId, runId, step.id(), commandId, Domain.ValidationType.TEST, exitCode == 0, result.summary());
        stepService.complete(taskId, runId, step, result.summary());
        return exitCode == 0
                ? ToolExecutionResult.success("Task completed. Validation passed: " + result.summary(), result.payload())
                : ToolExecutionResult.blocked("Validation failed: " + result.summary());
    }

    private ToolExecutionResult executePlanItem(UUID taskId, UUID runId, com.nask.agent.workspace.Workspace workspace,
                                                PlanItem item, List<String> observedFiles) {
        planService.updateItemStatus(item.id(), Domain.PlanItemStatus.IN_PROGRESS);
        var step = stepService.start(taskId, runId, item.id(), Domain.StepType.EXECUTE_PLAN_ITEM, item.description());
        var decision = llmGateway.decideNextAction(new ExecutionContext(taskId, runId, item, observedFiles));
        ToolExecutionResult last = ToolExecutionResult.success("No action required", Map.of());
        for (var actionDraft : decision.actions()) {
            var action = actionService.create(step.id(), Domain.ActionType.CALL_TOOL, actionDraft.reason(), Domain.RiskLevel.MEDIUM);
            var context = new ToolExecutionContext(taskId, runId, step.id(), action.id(), workspace);
            last = executeAction(context, actionDraft.type(), actionDraft.input(), actionDraft.reason());
            if (last.waitingApproval() || last.blocked()) {
                stepService.complete(taskId, runId, step, last.summary());
                return last;
            }
        }
        stepService.complete(taskId, runId, step, last.summary());
        return last;
    }

    private ToolExecutionResult executeAction(ToolExecutionContext context, String type, Map<String, Object> input, String reason) {
        return switch (type) {
            case "LIST_FILES" -> fileToolService.listFiles(context, string(input, "path", "."), integer(input, "maxDepth", 4));
            case "READ_FILE" -> fileToolService.readFile(context, string(input, "path", "."));
            case "SEARCH_TEXT" -> fileToolService.searchText(context, string(input, "query", ""));
            case "CREATE_FILE" -> {
                var beforeCount = fileChangeRepository.countByRun(context.runId());
                if (beforeCount >= settings.maxFileChanges()) {
                    yield ToolExecutionResult.blocked("Maximum file change count exceeded");
                }
                yield fileToolService.createFile(context, string(input, "path", "AGENT_TASK_NOTE.md"),
                        string(input, "content", ""), reason);
            }
            default -> ToolExecutionResult.blocked("Unsupported action type: " + type);
        };
    }

    @SuppressWarnings("unchecked")
    private List<String> observedFiles(Map<String, Object> payload) {
        var value = payload.get("files");
        if (value instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return List.of();
    }

    private String string(Map<String, Object> input, String key, String defaultValue) {
        var value = input.get(key);
        return value == null ? defaultValue : value.toString();
    }

    private int integer(Map<String, Object> input, String key, int defaultValue) {
        var value = input.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            return Integer.parseInt(value.toString());
        }
        return defaultValue;
    }

    private UUID uuid(Object value) {
        return value == null ? null : UUID.fromString(value.toString());
    }
}
