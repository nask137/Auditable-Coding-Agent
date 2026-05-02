package com.nask.agent.run;

import com.nask.agent.action.AgentActionService;
import com.nask.agent.command.CommandExecutionRepository;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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
    private static final Logger log = LoggerFactory.getLogger(DefaultAgentLoopExecutor.class);

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
    private final CommandExecutionRepository commandExecutionRepository;

    /**
     * Wires the executor to all domain services that own persistence and policy.
     */
    public DefaultAgentLoopExecutor(AgentRunService runService, TaskService taskService,
                                    WorkspaceService workspaceService, AgentStepService stepService,
                                    AgentActionService actionService, PlanService planService,
                                    LlmGateway llmGateway, FileToolService fileToolService,
                                    ReportService reportService, CommandToolService commandToolService,
                                    ValidationService validationService, AgentSettings settings,
                                    FileChangeRepository fileChangeRepository,
                                    CommandExecutionRepository commandExecutionRepository) {
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
        this.commandExecutionRepository = commandExecutionRepository;
    }

    /**
     * Executes a run if it is currently in {@code RUNNING} status.
     *
     * <p>Returning without marking the run complete is intentional for approval
     * pauses. The approval service moves the run back to running when a user
     * approves, after which the caller can invoke the loop again.</p>
     */
    @Override
    public void execute(UUID runId) {
        log.info("Starting agent loop execution for run {}", runId);
        var run = runService.getRequired(runId);
        // Avoid replaying terminal or paused runs. This keeps repeated API calls
        // idempotent from the perspective of stored state.
        if (!Domain.AgentRunStatus.RUNNING.name().equals(run.status())) {
            log.info("Skipping agent loop execution for run {} because status is {}", run.id(), run.status());
            return;
        }
        var task = taskService.getRequired(run.taskId());
        var workspace = workspaceService.getRequired(task.workspaceId());
        log.info("Loaded agent run {} for task {} in workspace {}", run.id(), task.id(), workspace.id());
        workspaceService.touch(workspace.id());

        try {
            if (resumeApprovedPauseIfPresent(task, run, workspace)) {
                return;
            }

            // Phase 1 keeps model interactions explicit and step-scoped so each
            // model-derived artifact can be correlated with a run timeline entry.
            log.debug("Run {} starting task understanding step", run.id());
            var understandStep = stepService.start(task.id(), run.id(), null, Domain.StepType.UNDERSTAND_TASK, "Understand task");
            var understanding = llmGateway.understandTask(new TaskContext(task.id(), run.id(), understandStep.id(),
                    workspace.id(), task.userRequest()));
            stepService.complete(task.id(), run.id(), understandStep, understanding.summary());
            log.debug("Run {} completed task understanding: {}", run.id(), understanding.summary());

            log.debug("Run {} starting workspace inspection", run.id());
            var inspectStep = stepService.start(task.id(), run.id(), null, Domain.StepType.INSPECT_WORKSPACE, "Inspect workspace");
            var inspectAction = actionService.create(inspectStep.id(), Domain.ActionType.CALL_TOOL,
                    "List workspace files for planning", Domain.RiskLevel.LOW);
            var listed = fileToolService.listFiles(new ToolExecutionContext(task.id(), run.id(), inspectStep.id(),
                    inspectAction.id(), workspace), ".", 4);
            if (listed.waitingApproval()) {
                // Approval pauses are not failures: the run status has already
                // been changed by the permission layer, so the loop simply exits.
                stepService.markWaitingApproval(task.id(), run.id(), inspectStep, listed.summary());
                log.info("Run {} paused during workspace inspection awaiting approval: {}", run.id(), listed.summary());
                return;
            }
            if (listed.blocked()) {
                // A blocked inspection means planning would be based on an unsafe
                // or unavailable workspace, so fail the whole run immediately.
                stepService.fail(task.id(), run.id(), inspectStep, listed.summary());
                runService.fail(run.id(), task.id(), listed.summary());
                reportService.generate(task, run.id(), "Failed during workspace inspection: " + listed.summary());
                log.warn("Run {} blocked during workspace inspection: {}", run.id(), listed.summary());
                return;
            }
            stepService.complete(task.id(), run.id(), inspectStep, listed.summary());
            log.debug("Run {} completed workspace inspection: {}", run.id(), listed.summary());

            var observedFiles = observedFiles(listed.payload());
            log.info("Run {} observed {} workspace files for planning", run.id(), observedFiles.size());
            var planStep = stepService.start(task.id(), run.id(), null, Domain.StepType.CREATE_PLAN, "Create plan");
            var plan = planService.create(task.id(), run.id(), llmGateway.createPlan(
                    new PlanningContext(task.id(), run.id(), understanding, observedFiles)));
            stepService.complete(task.id(), run.id(), planStep, "Created " + plan.items().size() + " plan items");
            log.info("Run {} created plan {} with {} items", run.id(), plan.plan().id(), plan.items().size());

            var executedSteps = 0;
            PlanItem next;
            while ((next = planService.nextPending(plan.plan().id())) != null) {
                if (++executedSteps > settings.maxSteps()) {
                    // A persisted plan can be malformed or unexpectedly long; the
                    // runtime guard prevents an unbounded loop even if planning did
                    // not respect expected limits.
                    runService.fail(run.id(), task.id(), "Maximum agent step count exceeded");
                    log.warn("Run {} exceeded maximum agent step count {}", run.id(), settings.maxSteps());
                    return;
                }
                log.info("Run {} executing plan item {} ({}/{})", run.id(), next.id(), executedSteps, settings.maxSteps());
                var result = executePlanItem(task.id(), run.id(), workspace, next, observedFiles);
                if (result.waitingApproval()) {
                    // The current plan item remains in progress so the timeline
                    // shows exactly where user approval interrupted execution.
                    log.info("Run {} paused while executing plan item {} awaiting approval: {}", run.id(), next.id(), result.summary());
                    return;
                }
                if (result.blocked()) {
                    planService.updateItemStatus(next.id(), Domain.PlanItemStatus.FAILED);
                    runService.fail(run.id(), task.id(), result.summary());
                    reportService.generate(task, run.id(), "Failed: " + result.summary());
                    log.warn("Run {} blocked while executing plan item {}: {}", run.id(), next.id(), result.summary());
                    return;
                }
                planService.updateItemStatus(next.id(), Domain.PlanItemStatus.COMPLETED);
                log.info("Run {} completed plan item {}: {}", run.id(), next.id(), result.summary());
            }

            planService.updatePlanStatus(plan.plan().id(), Domain.PlanStatus.COMPLETED);
            log.info("Run {} completed plan {}", run.id(), plan.plan().id());
            var validationResult = validateIfNeeded(task.id(), run.id(), workspace);
            if (validationResult.waitingApproval()) {
                log.info("Run {} paused during validation awaiting approval: {}", run.id(), validationResult.summary());
                return;
            }
            if (validationResult.blocked()) {
                runService.fail(run.id(), task.id(), validationResult.summary());
                reportService.generate(task, run.id(), "Failed validation: " + validationResult.summary());
                log.warn("Run {} failed validation: {}", run.id(), validationResult.summary());
                return;
            }
            reportService.generate(task, run.id(), validationResult.summary());
            runService.complete(run.id(), task.id());
            log.info("Run {} completed successfully: {}", run.id(), validationResult.summary());
        } catch (Exception e) {
            // Convert unexpected runtime failures into normal domain state so API
            // clients and audit readers get a report instead of only a stacktrace.
            log.error("Agent run {} failed", run.id(), e);
            var reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            runService.fail(run.id(), task.id(), reason);
            generateFailureReport(task, run.id(), reason);
        }
    }

    /**
     * Writes a failure report without letting report generation hide the
     * original runtime failure.
     */
    private void generateFailureReport(com.nask.agent.task.CodingTask task, UUID runId, String reason) {
        try {
            reportService.generate(task, runId, "Failed: " + reason);
        } catch (Exception reportError) {
            log.warn("Failed to generate report for failed agent run {}", runId, reportError);
        }
    }

    /**
     * Continues a command that paused for approval instead of replaying the
     * whole agent loop from task understanding.
     */
    private boolean resumeApprovedPauseIfPresent(com.nask.agent.task.CodingTask task, AgentRun run,
                                                 com.nask.agent.workspace.Workspace workspace) {
        var command = commandExecutionRepository.findApprovedWaitingByRun(run.id());
        if (command.isEmpty()) {
            return false;
        }

        var execution = command.get();
        var step = stepService.getRequired(execution.stepId());
        log.info("Run {} resuming approved command execution {}", run.id(), execution.id());
        var result = commandToolService.resumeApprovedCommand(
                new ToolExecutionContext(task.id(), run.id(), execution.stepId(), execution.actionId(), workspace), execution);
        if (result.blocked() || result.waitingApproval()) {
            stepService.fail(task.id(), run.id(), step, result.summary());
            runService.fail(run.id(), task.id(), result.summary());
            reportService.generate(task, run.id(), "Failed while resuming approved command: " + result.summary());
            log.warn("Run {} failed while resuming approved command {}: {}", run.id(), execution.id(), result.summary());
            return true;
        }

        if (Domain.StepType.VALIDATE.name().equals(step.stepType())) {
            var exitCode = integer(result.payload(), "exitCode", 1);
            var commandId = uuid(result.payload().get("commandId"));
            validationService.record(task.id(), run.id(), step.id(), commandId, Domain.ValidationType.TEST,
                    exitCode == 0, result.summary());
            stepService.complete(task.id(), run.id(), step, result.summary());
            if (exitCode == 0) {
                reportService.generate(task, run.id(), "Task completed. Validation passed: " + result.summary());
                runService.complete(run.id(), task.id());
                log.info("Run {} completed after resuming approved validation command {}", run.id(), execution.id());
            } else {
                var summary = "Validation failed: " + result.summary();
                runService.fail(run.id(), task.id(), summary);
                reportService.generate(task, run.id(), "Failed validation: " + result.summary());
                log.warn("Run {} failed validation after resuming approved command {}: {}", run.id(), execution.id(), result.summary());
            }
            return true;
        }

        stepService.complete(task.id(), run.id(), step, result.summary());
        reportService.generate(task, run.id(), "Task completed. Approved command completed: " + result.summary());
        runService.complete(run.id(), task.id());
        log.info("Run {} completed after resuming approved command {}", run.id(), execution.id());
        return true;
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
        if (result.waitingApproval()) {
            stepService.markWaitingApproval(taskId, runId, step, result.summary());
            return result;
        }
        if (result.blocked()) {
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
        var decision = llmGateway.decideNextAction(new ExecutionContext(taskId, runId, step.id(), item, observedFiles));
        ToolExecutionResult last = ToolExecutionResult.success("No action required", Map.of());
        for (var actionDraft : decision.actions()) {
            // Each model action becomes an auditable domain action before a tool
            // is called. This preserves intent even if the tool blocks later.
            var action = actionService.create(step.id(), Domain.ActionType.CALL_TOOL, actionDraft.reason(), Domain.RiskLevel.MEDIUM);
            var context = new ToolExecutionContext(taskId, runId, step.id(), action.id(), workspace);
            last = executeAction(context, actionDraft.type(), actionDraft.input(), actionDraft.reason());
            if (last.waitingApproval()) {
                stepService.markWaitingApproval(taskId, runId, step, last.summary());
                return last;
            }
            if (last.blocked()) {
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
