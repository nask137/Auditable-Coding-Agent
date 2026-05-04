package com.nask.agent.workflow;

import com.nask.agent.action.AgentActionService;
import com.nask.agent.command.CommandExecutionRepository;
import com.nask.agent.command.CommandToolService;
import com.nask.agent.common.AgentSettings;
import com.nask.agent.common.Domain;
import com.nask.agent.file.FileChangeRepository;
import com.nask.agent.file.FileToolService;
import com.nask.agent.git.GitToolService;
import com.nask.agent.llm.ExecutionContext;
import com.nask.agent.llm.LlmGatewayException;
import com.nask.agent.llm.PlanningContext;
import com.nask.agent.llm.TaskContext;
import com.nask.agent.llm.TaskUnderstanding;
import com.nask.agent.llm.ValidationContext;
import com.nask.agent.plan.PlanItem;
import com.nask.agent.plan.PlanService;
import com.nask.agent.report.ReportService;
import com.nask.agent.run.AgentRunService;
import com.nask.agent.runtime.FailureClassifier;
import com.nask.agent.runtime.RuntimeFailure;
import com.nask.agent.runtime.RuntimeFailureService;
import com.nask.agent.runtime.UserInputRequestService;
import com.nask.agent.step.AgentStepService;
import com.nask.agent.tool.ToolExecutionContext;
import com.nask.agent.tool.ToolExecutionResult;
import com.nask.agent.tool.ToolRecordRepository;
import com.nask.agent.validation.ValidationService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Workflow-native node executor for the built-in local coding-agent nodes.
 */
@Component
public class AgentWorkflowNodeExecutor implements WorkflowNodeExecutor {
    private final AgentStepService stepService;
    private final AgentActionService actionService;
    private final PlanService planService;
    private final com.nask.agent.llm.LlmGateway llmGateway;
    private final FileToolService fileToolService;
    private final GitToolService gitToolService;
    private final ReportService reportService;
    private final CommandToolService commandToolService;
    private final ValidationService validationService;
    private final AgentSettings settings;
    private final FileChangeRepository fileChangeRepository;
    private final CommandExecutionRepository commandExecutionRepository;
    private final ToolRecordRepository toolRecordRepository;
    private final RuntimeFailureService runtimeFailureService;
    private final FailureClassifier failureClassifier;
    private final AgentRunService runService;
    private final UserInputRequestService userInputRequestService;

    public AgentWorkflowNodeExecutor(AgentStepService stepService, AgentActionService actionService,
                                     PlanService planService, com.nask.agent.llm.LlmGateway llmGateway,
                                     FileToolService fileToolService, GitToolService gitToolService,
                                     ReportService reportService, CommandToolService commandToolService,
                                     ValidationService validationService, AgentSettings settings,
                                     FileChangeRepository fileChangeRepository,
                                     CommandExecutionRepository commandExecutionRepository,
                                     ToolRecordRepository toolRecordRepository,
                                     RuntimeFailureService runtimeFailureService,
                                     FailureClassifier failureClassifier, AgentRunService runService,
                                     UserInputRequestService userInputRequestService) {
        this.stepService = stepService;
        this.actionService = actionService;
        this.planService = planService;
        this.llmGateway = llmGateway;
        this.fileToolService = fileToolService;
        this.gitToolService = gitToolService;
        this.reportService = reportService;
        this.commandToolService = commandToolService;
        this.validationService = validationService;
        this.settings = settings;
        this.fileChangeRepository = fileChangeRepository;
        this.commandExecutionRepository = commandExecutionRepository;
        this.toolRecordRepository = toolRecordRepository;
        this.runtimeFailureService = runtimeFailureService;
        this.failureClassifier = failureClassifier;
        this.runService = runService;
        this.userInputRequestService = userInputRequestService;
    }

    @Override
    public String nodeType() {
        return "*";
    }

    @Override
    public NodeExecutionResult execute(AgentState state, MapWorkflowNode node) {
        return switch (Domain.WorkflowNodeType.valueOf(node.type())) {
            case TASK_UNDERSTANDING -> understandTask(state);
            case WORKSPACE_INSPECTION -> inspectWorkspace(state);
            case PROJECT_MEMORY -> projectMemory(state);
            case CODE_UNDERSTANDING -> codeUnderstanding(state);
            case PLAN_CREATION -> createPlan(state);
            case PLAN_ITEM_EXECUTION -> executePlanItem(state);
            case VALIDATION -> validate(state);
            case REPORT -> report(state);
            case FINISH -> finish(state);
            case FAIL -> fail(state);
            default -> NodeExecutionResult.blocked("Unsupported node type: " + node.type());
        };
    }

    private NodeExecutionResult understandTask(AgentState state) {
        if (state.plan() != null) {
            return NodeExecutionResult.success("Task already understood for existing plan", Map.of());
        }
        var step = stepService.start(state.task().id(), state.run().id(), null,
                Domain.StepType.UNDERSTAND_TASK, "Understand task");
        var understanding = callModelWithRecovery(state, step.id(), null, "understand task",
                () -> llmGateway.understandTask(new TaskContext(state.task().id(), state.run().id(), step.id(),
                        state.workspace().id(), state.task().userRequest(), state.recoveryNotes())));
        if (understanding == null) {
            stepService.markWaitingUserInput(state.task().id(), state.run().id(), step, "Waiting for user input");
            return NodeExecutionResult.waitingUserInput("Waiting for user input", Map.of("stepId", step.id().toString()));
        }
        stepService.complete(state.task().id(), state.run().id(), step, understanding.summary());
        return NodeExecutionResult.success(understanding.summary(), Map.of(
                "stepId", step.id().toString(),
                "taskSummary", understanding.summary(),
                "taskType", understanding.taskType(),
                "constraints", understanding.constraints(),
                "searchHints", understanding.initialSearchHints()));
    }

    private NodeExecutionResult inspectWorkspace(AgentState state) {
        var step = stepService.start(state.task().id(), state.run().id(), null,
                Domain.StepType.INSPECT_WORKSPACE, "Inspect workspace");
        var action = actionService.create(step.id(), Domain.ActionType.CALL_TOOL,
                "List workspace files for planning", Domain.RiskLevel.LOW);
        var result = fileToolService.listFiles(new ToolExecutionContext(state.task().id(), state.run().id(), step.id(),
                action.id(), state.workspace()), ".", 4);
        return completeToolStep(state, step.id(), result, Map.of("stepId", step.id().toString()));
    }

    private NodeExecutionResult projectMemory(AgentState state) {
        var notes = state.recoveryNotes().isEmpty() ? "No project memory recorded yet"
                : String.join("; ", state.recoveryNotes());
        return NodeExecutionResult.success(notes, Map.of("memoryNotes", state.recoveryNotes()));
    }

    private NodeExecutionResult codeUnderstanding(AgentState state) {
        var files = state.recentFileChanges().stream()
                .map(change -> change.path())
                .limit(10)
                .toList();
        var summary = files.isEmpty() ? "No recent code changes to summarize" : "Recent code changes: " + files;
        return NodeExecutionResult.success(summary, Map.of("recentChangedFiles", files));
    }

    private NodeExecutionResult createPlan(AgentState state) {
        if (state.plan() != null) {
            return NodeExecutionResult.success("Plan already exists", Map.of("planId", state.plan().plan().id().toString()));
        }
        var step = stepService.start(state.task().id(), state.run().id(), null, Domain.StepType.CREATE_PLAN,
                "Create plan");
        var understanding = new TaskUnderstanding(
                string(state.transientValue("taskSummary"), state.task().userRequest()),
                string(state.transientValue("taskType"), "CODE_EDIT"),
                list(state.transientValue("constraints")),
                list(state.transientValue("searchHints")));
        var planDraft = callModelWithRecovery(state, step.id(), null, "create plan",
                () -> llmGateway.createPlan(new PlanningContext(state.task().id(), state.run().id(), understanding,
                        list(state.transientValue("observedFiles")), state.recoveryNotes())));
        if (planDraft == null) {
            stepService.markWaitingUserInput(state.task().id(), state.run().id(), step, "Waiting for user input");
            return NodeExecutionResult.waitingUserInput("Waiting for user input", Map.of("stepId", step.id().toString()));
        }
        var plan = planService.create(state.task().id(), state.run().id(), planDraft);
        stepService.complete(state.task().id(), state.run().id(), step, "Created " + plan.items().size() + " plan items");
        return NodeExecutionResult.success("Created " + plan.items().size() + " plan items", Map.of(
                "stepId", step.id().toString(),
                "planId", plan.plan().id().toString()));
    }

    private NodeExecutionResult executePlanItem(AgentState state) {
        if (state.plan() == null || state.currentPlanItem() == null) {
            return NodeExecutionResult.success("No pending plan items", Map.of());
        }
        var item = state.currentPlanItem();
        planService.updateItemStatus(item.id(), Domain.PlanItemStatus.IN_PROGRESS);
        var step = stepService.start(state.task().id(), state.run().id(), item.id(),
                Domain.StepType.EXECUTE_PLAN_ITEM, item.description());
        var observedFiles = list(state.transientValue("observedFiles"));
        var decisionResult = decideNextActionWithRecovery(state, state.plan(), item, observedFiles, step.id());
        if (decisionResult.result() != null) {
            return decisionResult.result();
        }
        var decision = decisionResult.decision();
        ToolExecutionResult last = ToolExecutionResult.success("No action required", Map.of());
        for (var actionDraft : decision.actions()) {
            var action = actionService.create(step.id(), Domain.ActionType.CALL_TOOL, actionDraft.reason(),
                    Domain.RiskLevel.MEDIUM);
            var context = new ToolExecutionContext(state.task().id(), state.run().id(), step.id(), action.id(),
                    state.workspace());
            last = executeAction(context, actionDraft.type(), actionDraft.input(), actionDraft.reason());
            if (last.waitingApproval()) {
                stepService.markWaitingApproval(state.task().id(), state.run().id(), step, last.summary());
                return NodeExecutionResult.waitingApproval(last.summary(), Map.of("stepId", step.id().toString()));
            }
            if (last.blocked()) {
                var failure = runtimeFailureService.record(state.task().id(), state.run().id(), step.id(), item.id(),
                        failureClassifier.fromToolResult(last), last.summary(), last.summary());
                if (Domain.RecoveryStrategy.REPLAN_CURRENT_ITEM.name().equals(failure.strategy())) {
                    return replanCurrentItem(state, state.plan(), item, observedFiles, step.id(), failure,
                            last.summary());
                }
                if (Domain.RecoveryStrategy.ASK_USER.name().equals(failure.strategy())) {
                    askUser(state, step.id(), item.id(), failure);
                    stepService.markWaitingUserInput(state.task().id(), state.run().id(), step, failure.summary());
                    return NodeExecutionResult.waitingUserInput(failure.summary(), Map.of("stepId", step.id().toString()));
                }
                stepService.complete(state.task().id(), state.run().id(), step, last.summary());
                return new NodeExecutionResult("BLOCKED", last.summary(), Map.of("stepId", step.id().toString()),
                        failure.failureType(), failure.strategy(), state.plan().plan().id(), item.id());
            }
        }
        stepService.complete(state.task().id(), state.run().id(), step, last.summary());
        planService.updateItemStatus(item.id(), Domain.PlanItemStatus.COMPLETED);
        return NodeExecutionResult.success(last.summary(), Map.of(
                "stepId", step.id().toString(),
                "planItemId", item.id().toString()));
    }

    private NodeExecutionResult validate(AgentState state) {
        var approved = commandExecutionRepository.findApprovedWaitingByRun(state.run().id());
        if (approved.isPresent()) {
            var command = approved.get();
            var step = stepService.getRequired(command.stepId());
            var result = commandToolService.resumeApprovedCommand(new ToolExecutionContext(state.task().id(),
                    state.run().id(), command.stepId(), command.actionId(), state.workspace()), command);
            return finishValidationResult(state, step.id(), result);
        }
        var decision = callModelWithRecovery(state, null, null, "suggest validation",
                () -> llmGateway.suggestValidation(new ValidationContext(state.task().id(), state.run().id(),
                        state.workspace().id(), state.recoveryNotes())));
        if (decision == null) {
            return NodeExecutionResult.waitingUserInput("Waiting for user input");
        }
        if (!decision.shouldValidate() || decision.executableAndArgs().isEmpty()) {
            if (state.plan() != null) {
                planService.updatePlanStatus(state.plan().plan().id(), Domain.PlanStatus.COMPLETED);
            }
            return NodeExecutionResult.success("No validation command selected", Map.of());
        }
        var step = stepService.start(state.task().id(), state.run().id(), null, Domain.StepType.VALIDATE,
                decision.reason());
        var action = actionService.create(step.id(), Domain.ActionType.RUN_VALIDATION, decision.reason(),
                Domain.RiskLevel.MEDIUM);
        var executable = decision.executableAndArgs().getFirst();
        var args = decision.executableAndArgs().subList(1, decision.executableAndArgs().size());
        var result = commandToolService.runCommand(new ToolExecutionContext(state.task().id(), state.run().id(),
                step.id(), action.id(), state.workspace()), executable, args, ".", decision.reason());
        if (result.waitingApproval()) {
            stepService.markWaitingApproval(state.task().id(), state.run().id(), step, result.summary());
            return NodeExecutionResult.waitingApproval(result.summary(), Map.of("stepId", step.id().toString()));
        }
        return finishValidationResult(state, step.id(), result);
    }

    private NodeExecutionResult finishValidationResult(AgentState state, UUID stepId, ToolExecutionResult result) {
        var step = stepService.getRequired(stepId);
        if (result.blocked() || result.waitingApproval()) {
            stepService.fail(state.task().id(), state.run().id(), step, result.summary());
            return NodeExecutionResult.blocked(result.summary());
        }
        var exitCode = integer(result.payload().get("exitCode"), 1);
        var commandId = result.payload().get("commandId") == null ? null
                : UUID.fromString(result.payload().get("commandId").toString());
        validationService.record(state.task().id(), state.run().id(), step.id(), commandId, Domain.ValidationType.TEST,
                exitCode == 0, result.summary());
        stepService.complete(state.task().id(), state.run().id(), step, result.summary());
        if (exitCode != 0) {
            var failure = runtimeFailureService.record(state.task().id(), state.run().id(), step.id(), null,
                    Domain.RuntimeFailureType.VALIDATION_FAILED, "Validation failed: " + result.summary(),
                    result.summary());
            if (Domain.RecoveryStrategy.REPLAN_REMAINING_PLAN.name().equals(failure.strategy())
                    && state.plan() != null) {
                var currentItem = state.plan().items().isEmpty() ? null : state.plan().items().getLast();
                var recoveryDraft = callModelWithRecovery(state, step.id(), null, "replan after validation failure",
                        () -> llmGateway.replan(new ExecutionContext(state.task().id(), state.run().id(), step.id(),
                                currentItem, List.of(), toolRecordRepository.findRecentSummariesByRun(state.run().id(), 8),
                                state.recoveryNotes()), result.summary()));
                if (recoveryDraft != null) {
                    planService.updatePlanStatus(state.plan().plan().id(), Domain.PlanStatus.ACTIVE);
                    planService.appendRecoveryItems(state.task().id(), state.run().id(), state.plan().plan().id(),
                            recoveryDraft, null, result.summary(), failure.id());
                    return NodeExecutionResult.success("Validation failed; recovery plan appended",
                            Map.of("stepId", step.id().toString()));
                }
                stepService.markWaitingUserInput(state.task().id(), state.run().id(), step, "Waiting for user input");
                return NodeExecutionResult.waitingUserInput("Waiting for user input", Map.of("stepId", step.id().toString()));
            }
            if (Domain.RecoveryStrategy.ASK_USER.name().equals(failure.strategy())) {
                askUser(state, step.id(), null, failure);
                stepService.markWaitingUserInput(state.task().id(), state.run().id(), step, failure.summary());
                return NodeExecutionResult.waitingUserInput(failure.summary(), Map.of("stepId", step.id().toString()));
            }
            return NodeExecutionResult.failure("Validation failed: " + result.summary());
        }
        if (state.plan() != null) {
            planService.updatePlanStatus(state.plan().plan().id(), Domain.PlanStatus.COMPLETED);
        }
        return NodeExecutionResult.success("Validation passed: " + result.summary(),
                Map.of("stepId", step.id().toString()));
    }

    private NodeExecutionResult report(AgentState state) {
        var summary = Domain.WorkflowMode.REVIEW.name().equals(state.workflow().mode())
                ? "Review completed."
                : "Task completed.";
        reportService.generate(state.task(), state.run().id(), summary);
        return NodeExecutionResult.success("Report generated", Map.of());
    }

    private NodeExecutionResult finish(AgentState state) {
        runService.complete(state.run().id(), state.task().id());
        return NodeExecutionResult.success("Workflow finished", Map.of());
    }

    private NodeExecutionResult fail(AgentState state) {
        runService.fail(state.run().id(), state.task().id(), "Workflow failed");
        return NodeExecutionResult.failure("Workflow failed");
    }

    private NodeExecutionResult completeToolStep(AgentState state, UUID stepId, ToolExecutionResult result,
                                                Map<String, Object> payload) {
        var step = stepService.getRequired(stepId);
        if (result.waitingApproval()) {
            stepService.markWaitingApproval(state.task().id(), state.run().id(), step, result.summary());
            return NodeExecutionResult.waitingApproval(result.summary(), Map.of("stepId", step.id().toString()));
        }
        if (result.blocked()) {
            stepService.fail(state.task().id(), state.run().id(), step, result.summary());
            return NodeExecutionResult.blocked(result.summary());
        }
        stepService.complete(state.task().id(), state.run().id(), step, result.summary());
        var merged = new java.util.HashMap<>(result.payload());
        merged.putAll(payload);
        return NodeExecutionResult.success(result.summary(), merged);
    }

    private ToolExecutionResult executeAction(ToolExecutionContext context, String type, Map<String, Object> input,
                                              String reason) {
        return switch (type) {
            case "LIST_FILES" -> fileToolService.listFiles(context, string(input.get("path"), "."),
                    integer(input.get("maxDepth"), 4));
            case "READ_FILE" -> fileToolService.readFile(context, string(input.get("path"), "."));
            case "SEARCH_TEXT" -> fileToolService.searchText(context, string(input.get("query"), ""));
            case "CREATE_FILE" -> {
                if (fileChangeRepository.countByRun(context.runId()) >= settings.maxFileChanges()) {
                    yield ToolExecutionResult.blocked("Maximum file change count exceeded");
                }
                yield fileToolService.createFile(context, string(input.get("path"), "AGENT_TASK_NOTE.md"),
                        string(input.get("content"), ""), reason);
            }
            case "APPLY_PATCH" -> {
                if (fileChangeRepository.countByRun(context.runId()) >= settings.maxFileChanges()) {
                    yield ToolExecutionResult.blocked("Maximum file change count exceeded");
                }
                yield fileToolService.applyPatch(context, string(input.get("path"), "."),
                        string(input.get("oldText"), ""), string(input.get("newText"), ""), reason);
            }
            case "GIT_STATUS" -> gitToolService.status(context, string(input.get("workingDirectory"), "."));
            case "GIT_DIFF" -> gitToolService.diff(context, string(input.get("workingDirectory"), "."));
            default -> ToolExecutionResult.blocked("Unsupported action type: " + type);
        };
    }

    private AgentDecisionResult decideNextActionWithRecovery(AgentState state, com.nask.agent.plan.PlanView plan,
                                                             PlanItem item, List<String> observedFiles, UUID stepId) {
        while (true) {
            try {
                var decision = llmGateway.decideNextAction(new ExecutionContext(state.task().id(), state.run().id(), stepId,
                        item, observedFiles, toolRecordRepository.findRecentSummariesByRun(state.run().id(), 8),
                        state.recoveryNotes()));
                return new AgentDecisionResult(decision, null);
            } catch (LlmGatewayException e) {
                var failure = runtimeFailureService.record(state.task().id(), state.run().id(), stepId, item.id(),
                        failureClassifier.fromModelException(e), e.getMessage(), "decide next action");
                if (Domain.RecoveryStrategy.RETRY_SAME_ACTION.name().equals(failure.strategy())) {
                    continue;
                }
                if (Domain.RecoveryStrategy.REPLAN_CURRENT_ITEM.name().equals(failure.strategy())) {
                    return new AgentDecisionResult(null,
                            replanCurrentItem(state, plan, item, observedFiles, stepId, failure, e.getMessage()));
                }
                if (Domain.RecoveryStrategy.ASK_USER.name().equals(failure.strategy())) {
                    askUser(state, stepId, item.id(), failure);
                    var step = stepService.getRequired(stepId);
                    stepService.markWaitingUserInput(state.task().id(), state.run().id(), step, failure.summary());
                    return new AgentDecisionResult(null,
                            NodeExecutionResult.waitingUserInput(failure.summary(), Map.of("stepId", stepId.toString())));
                }
                var step = stepService.getRequired(stepId);
                stepService.complete(state.task().id(), state.run().id(), step, failure.summary());
                return new AgentDecisionResult(null,
                        new NodeExecutionResult("BLOCKED", failure.summary(), Map.of("stepId", stepId.toString()),
                                failure.failureType(), failure.strategy(), plan.plan().id(), item.id()));
            }
        }
    }

    private record AgentDecisionResult(com.nask.agent.llm.AgentDecision decision, NodeExecutionResult result) {
    }

    private NodeExecutionResult replanCurrentItem(AgentState state, com.nask.agent.plan.PlanView plan, PlanItem item,
                                                  List<String> observedFiles, UUID stepId, RuntimeFailure failure,
                                                  String failureSummary) {
        var recoveryDraft = callModelWithRecovery(state, stepId, item.id(), "replan current item",
                () -> llmGateway.replan(new ExecutionContext(state.task().id(), state.run().id(), stepId, item,
                        observedFiles, toolRecordRepository.findRecentSummariesByRun(state.run().id(), 8),
                        state.recoveryNotes()), failureSummary));
        var step = stepService.getRequired(stepId);
        if (recoveryDraft != null) {
            planService.updateItemStatus(item.id(), Domain.PlanItemStatus.FAILED);
            planService.appendRecoveryItems(state.task().id(), state.run().id(), plan.plan().id(), recoveryDraft,
                    item.id(), failureSummary, failure.id());
            stepService.complete(state.task().id(), state.run().id(), step,
                    "Runtime rejected action; recovery plan appended");
            return NodeExecutionResult.success("Runtime rejected action; recovery plan appended",
                    Map.of("stepId", stepId.toString()));
        }
        stepService.markWaitingUserInput(state.task().id(), state.run().id(), step, "Waiting for user input");
        return NodeExecutionResult.waitingUserInput("Waiting for user input", Map.of("stepId", stepId.toString()));
    }

    private <T> T callModelWithRecovery(AgentState state, UUID stepId, UUID planItemId, String decisionType,
                                        java.util.function.Supplier<T> supplier) {
        while (true) {
            try {
                return supplier.get();
            } catch (LlmGatewayException e) {
                var failure = runtimeFailureService.record(state.task().id(), state.run().id(), stepId, planItemId,
                        failureClassifier.fromModelException(e), e.getMessage(), decisionType);
                if (Domain.RecoveryStrategy.RETRY_SAME_ACTION.name().equals(failure.strategy())) {
                    continue;
                }
                if (Domain.RecoveryStrategy.ASK_USER.name().equals(failure.strategy())) {
                    askUser(state, stepId, planItemId, failure);
                    return null;
                }
                throw e;
            }
        }
    }

    private void askUser(AgentState state, UUID stepId, UUID planItemId, RuntimeFailure failure) {
        userInputRequestService.create(state.task().id(), state.run().id(), stepId, planItemId,
                "Runtime recovery needs guidance for " + failure.failureType(),
                failure.summary(), List.of("Retry with corrected model output", "Adjust task instructions",
                        "Cancel task"));
    }

    @SuppressWarnings("unchecked")
    private List<String> list(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return List.of();
    }

    private String string(Object value, String defaultValue) {
        return value == null ? defaultValue : value.toString();
    }

    private int integer(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return value == null ? defaultValue : Integer.parseInt(value.toString());
    }
}
