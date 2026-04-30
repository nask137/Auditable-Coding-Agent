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

/**
 * Fixed Phase 1 implementation of the agent loop.
 *
 * <p>The loop is intentionally deterministic around persistence and safety:
 * understand the task, inspect the workspace, create a plan, execute pending
 * plan items through tool services, optionally validate, then write a final
 * report. LLM output is treated as intent; every concrete operation still passes
 * through the service layer that records audit events and applies permissions.</p>
 */
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

    /**
     * Wires the executor to all domain services that own persistence and policy.
     */
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

    /**
     * Executes a run if it is currently in {@code RUNNING} status.
     *
     * <p>Returning without marking the run complete is intentional for approval
     * pauses. The approval service moves the run back to running when a user
     * approves, after which the caller can invoke the loop again.</p>
     */
    @Override
    @Transactional
    public void execute(UUID runId) {
        var run = runService.getRequired(runId);
        // Avoid replaying terminal or paused runs. This keeps repeated API calls
        // idempotent from the perspective of stored state.
        if (!Domain.AgentRunStatus.RUNNING.name().equals(run.status())) {
            return;
        }
        var task = taskService.getRequired(run.taskId());
        var workspace = workspaceService.getRequired(task.workspaceId());
        workspaceService.touch(workspace.id());

        try {
            // Phase 1 keeps model interactions explicit and step-scoped so each
            // model-derived artifact can be correlated with a run timeline entry.
            var understandStep = stepService.start(task.id(), run.id(), null, Domain.StepType.UNDERSTAND_TASK, "Understand task");
            var understanding = llmGateway.understandTask(new TaskContext(task.id(), workspace.id(), task.userRequest()));
            stepService.complete(task.id(), run.id(), understandStep, understanding.summary());

            var inspectStep = stepService.start(task.id(), run.id(), null, Domain.StepType.INSPECT_WORKSPACE, "Inspect workspace");
            var inspectAction = actionService.create(inspectStep.id(), Domain.ActionType.CALL_TOOL,
                    "List workspace files for planning", Domain.RiskLevel.LOW);
            var listed = fileToolService.listFiles(new ToolExecutionContext(task.id(), run.id(), inspectStep.id(),
                    inspectAction.id(), workspace), ".", 4);
            if (listed.waitingApproval()) {
                // Approval pauses are not failures: the run status has already
                // been changed by the permission layer, so the loop simply exits.
                stepService.complete(task.id(), run.id(), inspectStep, listed.summary());
                return;
            }
            if (listed.blocked()) {
                // A blocked inspection means planning would be based on an unsafe
                // or unavailable workspace, so fail the whole run immediately.
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
                    // A persisted plan can be malformed or unexpectedly long; the
                    // runtime guard prevents an unbounded loop even if planning did
                    // not respect expected limits.
                    runService.fail(run.id(), task.id(), "Maximum agent step count exceeded");
                    return;
                }
                var result = executePlanItem(task.id(), run.id(), workspace, next, observedFiles);
                if (result.waitingApproval()) {
                    // The current plan item remains in progress so the timeline
                    // shows exactly where user approval interrupted execution.
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
            // Convert unexpected runtime failures into normal domain state so API
            // clients and audit readers get a report instead of only a stacktrace.
            runService.fail(run.id(), task.id(), e.getMessage());
            reportService.generate(task, run.id(), "Failed: " + e.getMessage());
        }
    }

    /**
     * Asks the gateway for a validation command and records its result when run.
     */
    private ToolExecutionResult validateIfNeeded(UUID taskId, UUID runId, com.nask.agent.workspace.Workspace workspace) {
        var validation = llmGateway.suggestValidation(new ValidationContext(taskId, runId, workspace.id()));
        if (!validation.shouldValidate() || validation.executableAndArgs().isEmpty()) {
            return ToolExecutionResult.success("Task completed. No validation command selected.", Map.of());
        }
        var step = stepService.start(taskId, runId, null, Domain.StepType.VALIDATE, validation.reason());
        var action = actionService.create(step.id(), Domain.ActionType.RUN_VALIDATION, validation.reason(), Domain.RiskLevel.MEDIUM);
        var executable = validation.executableAndArgs().get(0);
        var arguments = validation.executableAndArgs().subList(1, validation.executableAndArgs().size());
        // Validation is deliberately routed through the same command tool as any
        // other shell execution so command policy and approval rules remain
        // centralized.
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

    /**
     * Executes one plan item by asking the gateway for tool actions.
     */
    private ToolExecutionResult executePlanItem(UUID taskId, UUID runId, com.nask.agent.workspace.Workspace workspace,
                                                PlanItem item, List<String> observedFiles) {
        planService.updateItemStatus(item.id(), Domain.PlanItemStatus.IN_PROGRESS);
        var step = stepService.start(taskId, runId, item.id(), Domain.StepType.EXECUTE_PLAN_ITEM, item.description());
        var decision = llmGateway.decideNextAction(new ExecutionContext(taskId, runId, item, observedFiles));
        ToolExecutionResult last = ToolExecutionResult.success("No action required", Map.of());
        for (var actionDraft : decision.actions()) {
            // Each model action becomes an auditable domain action before a tool
            // is called. This preserves intent even if the tool blocks later.
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

    /**
     * Dispatches model action names to local tool services.
     */
    private ToolExecutionResult executeAction(ToolExecutionContext context, String type, Map<String, Object> input, String reason) {
        return switch (type) {
            case "LIST_FILES" -> fileToolService.listFiles(context, string(input, "path", "."), integer(input, "maxDepth", 4));
            case "READ_FILE" -> fileToolService.readFile(context, string(input, "path", "."));
            case "SEARCH_TEXT" -> fileToolService.searchText(context, string(input, "query", ""));
            case "CREATE_FILE" -> {
                var beforeCount = fileChangeRepository.countByRun(context.runId());
                if (beforeCount >= settings.maxFileChanges()) {
                    // The file tool also enforces permissions, but the loop owns
                    // the coarse "how many changes may this run make" budget.
                    yield ToolExecutionResult.blocked("Maximum file change count exceeded");
                }
                yield fileToolService.createFile(context, string(input, "path", "AGENT_TASK_NOTE.md"),
                        string(input, "content", ""), reason);
            }
            default -> ToolExecutionResult.blocked("Unsupported action type: " + type);
        };
    }

    /**
     * Extracts file names from a tool payload in a defensive way.
     */
    @SuppressWarnings("unchecked")
    private List<String> observedFiles(Map<String, Object> payload) {
        var value = payload.get("files");
        if (value instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return List.of();
    }

    /**
     * Reads a string input value with a default.
     */
    private String string(Map<String, Object> input, String key, String defaultValue) {
        var value = input.get(key);
        return value == null ? defaultValue : value.toString();
    }

    /**
     * Reads an integer input value with a default.
     */
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

    /**
     * Converts a payload value to a UUID, preserving nulls.
     */
    private UUID uuid(Object value) {
        return value == null ? null : UUID.fromString(value.toString());
    }
}
