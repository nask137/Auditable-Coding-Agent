package com.nask.agent.workflow;

import com.nask.agent.action.AgentActionService;
import com.nask.agent.command.CommandExecutionRepository;
import com.nask.agent.command.CommandToolService;
import com.nask.agent.common.AgentSettings;
import com.nask.agent.common.Domain;
import com.nask.agent.conversation.ConversationContextService;
import com.nask.agent.conversation.ConversationService;
import com.nask.agent.file.FileChangeRepository;
import com.nask.agent.file.FileToolService;
import com.nask.agent.git.GitToolService;
import com.nask.agent.llm.ExecutionContext;
import com.nask.agent.llm.LlmGatewayException;
import com.nask.agent.llm.PlanningContext;
import com.nask.agent.llm.TaskContext;
import com.nask.agent.llm.TaskUnderstanding;
import com.nask.agent.memory.MemoryQuery;
import com.nask.agent.memory.MemoryWriteProposalService;
import com.nask.agent.memory.ProjectContextRetriever;
import com.nask.agent.memory.ProjectMemoryService;
import com.nask.agent.plan.PlanItem;
import com.nask.agent.plan.PlanService;
import com.nask.agent.report.ReportService;
import com.nask.agent.runtime.FailureClassifier;
import com.nask.agent.runtime.RuntimeFailure;
import com.nask.agent.runtime.RuntimeFailureService;
import com.nask.agent.runtime.UserInputRequestService;
import com.nask.agent.step.AgentStepService;
import com.nask.agent.task.TaskService;
import com.nask.agent.tool.ToolExecutionContext;
import com.nask.agent.tool.ToolExecutionResult;
import com.nask.agent.tool.ToolRecordRepository;
import com.nask.agent.validation.ValidationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Workflow-native node executor for the built-in local coding-agent nodes.
 */
@Component
public class AgentWorkflowNodeExecutor implements WorkflowNodeExecutor {
    private static final int PREVIOUS_CONVERSATION_TASK_LIMIT = 3;
    private static final int WORKSPACE_INSPECTION_DEPTH = 6;

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
    private final TaskService taskService;
    private final UserInputRequestService userInputRequestService;
    private final ConversationService conversationService;
    private final ConversationContextService conversationContextService;
    private final ProjectMemoryService projectMemoryService;
    private final ProjectContextRetriever projectContextRetriever;
    private final MemoryWriteProposalService memoryWriteProposalService;

    @Autowired
    public AgentWorkflowNodeExecutor(AgentStepService stepService, AgentActionService actionService,
                                     PlanService planService, com.nask.agent.llm.LlmGateway llmGateway,
                                     FileToolService fileToolService, GitToolService gitToolService,
                                     ReportService reportService, CommandToolService commandToolService,
                                     ValidationService validationService, AgentSettings settings,
                                     FileChangeRepository fileChangeRepository,
                                     CommandExecutionRepository commandExecutionRepository,
                                     ToolRecordRepository toolRecordRepository,
                                     RuntimeFailureService runtimeFailureService,
                                     FailureClassifier failureClassifier, TaskService taskService,
                                     UserInputRequestService userInputRequestService,
                                     ConversationService conversationService,
                                     ConversationContextService conversationContextService,
                                     ProjectMemoryService projectMemoryService,
                                     ProjectContextRetriever projectContextRetriever,
                                     MemoryWriteProposalService memoryWriteProposalService) {
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
        this.taskService = taskService;
        this.userInputRequestService = userInputRequestService;
        this.conversationService = conversationService;
        this.conversationContextService = conversationContextService;
        this.projectMemoryService = projectMemoryService;
        this.projectContextRetriever = projectContextRetriever;
        this.memoryWriteProposalService = memoryWriteProposalService;
    }

    public AgentWorkflowNodeExecutor(AgentStepService stepService, AgentActionService actionService,
                                     PlanService planService, com.nask.agent.llm.LlmGateway llmGateway,
                                     FileToolService fileToolService, GitToolService gitToolService,
                                     ReportService reportService, CommandToolService commandToolService,
                                     ValidationService validationService, AgentSettings settings,
                                     FileChangeRepository fileChangeRepository,
                                     CommandExecutionRepository commandExecutionRepository,
                                     ToolRecordRepository toolRecordRepository,
                                     RuntimeFailureService runtimeFailureService,
                                     FailureClassifier failureClassifier, TaskService taskService,
                                     UserInputRequestService userInputRequestService,
                                     ProjectMemoryService projectMemoryService,
                                     ProjectContextRetriever projectContextRetriever,
                                     MemoryWriteProposalService memoryWriteProposalService) {
        this(stepService, actionService, planService, llmGateway, fileToolService, gitToolService, reportService,
                commandToolService, validationService, settings, fileChangeRepository, commandExecutionRepository,
                toolRecordRepository, runtimeFailureService, failureClassifier, taskService, userInputRequestService,
                null, null, projectMemoryService, projectContextRetriever, memoryWriteProposalService);
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
            case PROJECT_SCAN -> projectScan(state);
            case PROJECT_MEMORY -> projectMemory(state);
            case CODE_UNDERSTANDING -> codeUnderstanding(state);
            case PLAN_CREATION -> createPlan(state);
            case PLAN_ITEM_EXECUTION -> executePlanItem(state);
            case VALIDATION -> validate(state);
            case TASK_SUMMARY_MEMORY -> taskSummaryMemory(state);
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
        var step = stepService.start(state.task().id(), state.execution().id(), null,
                Domain.StepType.UNDERSTAND_TASK, "Understand task");
        var understanding = callModelWithRecovery(state, step.id(), null, "understand task",
                () -> llmGateway.understandTask(new TaskContext(state.task().id(), state.execution().id(), step.id(),
                        state.workspace().id(), state.task().userRequest(), state.recoveryNotes(),
                        previousConversationTasks(state))));
        if (understanding == null) {
            stepService.markWaitingUserInput(state.task().id(), state.execution().id(), step, "Waiting for user input");
            return NodeExecutionResult.waitingUserInput("Waiting for user input", Map.of("stepId", step.id().toString()));
        }
        stepService.complete(state.task().id(), state.execution().id(), step, understanding.summary());
        return NodeExecutionResult.success(understanding.summary(), Map.of(
                "stepId", step.id().toString(),
                "taskSummary", understanding.summary(),
                "taskType", understanding.taskType(),
                "constraints", understanding.constraints(),
                "searchHints", understanding.initialSearchHints()));
    }

    private NodeExecutionResult inspectWorkspace(AgentState state) {
        var step = stepService.start(state.task().id(), state.execution().id(), null,
                Domain.StepType.INSPECT_WORKSPACE, "Inspect workspace");
        var action = actionService.create(step.id(), Domain.ActionType.CALL_TOOL,
                "List workspace files for planning", Domain.RiskLevel.LOW);
        var result = fileToolService.listFiles(new ToolExecutionContext(state.task().id(), state.execution().id(), step.id(),
                action.id(), state.workspace()), ".", WORKSPACE_INSPECTION_DEPTH);
        return completeToolStep(state, step.id(), result, Map.of(
                "stepId", step.id().toString(),
                "observedFiles", list(result.payload().get("files"))));
    }

    private NodeExecutionResult projectScan(AgentState state) {
        var scanRun = projectMemoryService.scan(state.workspace().id());
        return NodeExecutionResult.success("Project scan " + scanRun.status() + ": " + scanRun.summary(), Map.of(
                "scanRunId", scanRun.id().toString(),
                "filesSeen", scanRun.filesSeen(),
                "filesIndexed", scanRun.filesIndexed(),
                "filesSkipped", scanRun.filesSkipped()));
    }

    private NodeExecutionResult projectMemory(AgentState state) {
        var query = string(state.transientValue("taskSummary"), state.task().userRequest()) + " "
                + String.join(" ", list(state.transientValue("searchHints"))) + " "
                + referencedPreviousTaskContext(state);
        var context = projectContextRetriever.retrieve(new MemoryQuery(state.workspace().id(), query,
                state.task().id(), state.execution().id(), null, List.of(), List.of(), List.of(), 10));
        return NodeExecutionResult.success(context.summary(), Map.of(
                "memoryContext", context,
                "memoryRetrievalId", context.retrievalId().toString(),
                "memoryResultCount", context.results().size()));
    }

    private NodeExecutionResult codeUnderstanding(AgentState state) {
        var files = state.memoryContext() == null ? state.recentFileChanges().stream()
                .map(change -> change.path())
                .limit(10)
                .toList() : state.memoryContext().results().stream()
                .filter(result -> "SYMBOL".equals(result.resultType()))
                .map(result -> result.source().path())
                .distinct()
                .limit(10)
                .toList();
        var summary = files.isEmpty() ? "No related code symbols found" : "Related code files: " + files;
        return NodeExecutionResult.success(summary, Map.of("relatedCodeFiles", files));
    }

    private NodeExecutionResult createPlan(AgentState state) {
        if (state.plan() != null) {
            return NodeExecutionResult.success("Plan already exists", Map.of("planId", state.plan().plan().id().toString()));
        }
        var step = stepService.start(state.task().id(), state.execution().id(), null, Domain.StepType.CREATE_PLAN,
                "Create plan");
        var understanding = new TaskUnderstanding(
                string(state.transientValue("taskSummary"), state.task().userRequest()),
                string(state.transientValue("taskType"), state.execution().agentMode()),
                list(state.transientValue("constraints")),
                list(state.transientValue("searchHints")));
        var planDraft = callModelWithRecovery(state, step.id(), null, "create plan",
                () -> llmGateway.createPlan(new PlanningContext(state.task().id(), state.execution().id(), understanding,
                        list(state.transientValue("observedFiles")), state.recoveryNotes(), state.memoryContext())));
        if (planDraft == null) {
            stepService.markWaitingUserInput(state.task().id(), state.execution().id(), step, "Waiting for user input");
            return NodeExecutionResult.waitingUserInput("Waiting for user input", Map.of("stepId", step.id().toString()));
        }
        var plan = planService.create(state.task().id(), state.execution().id(), planDraft);
        stepService.complete(state.task().id(), state.execution().id(), step, "Created " + plan.items().size() + " plan items");
        return NodeExecutionResult.success("Created " + plan.items().size() + " plan items", Map.of(
                "stepId", step.id().toString(),
                "planId", plan.plan().id().toString()));
    }

    private NodeExecutionResult executePlanItem(AgentState state) {
        var resumed = resumeApprovedCommandIfPresent(state);
        if (resumed != null) {
            return resumed;
        }
        if (state.plan() == null || state.currentPlanItem() == null) {
            return NodeExecutionResult.success("No pending plan items", Map.of());
        }
        var item = state.currentPlanItem();
        planService.updateItemStatus(item.id(), Domain.PlanItemStatus.IN_PROGRESS);
        var step = stepService.start(state.task().id(), state.execution().id(), item.id(),
                Domain.StepType.EXECUTE_PLAN_ITEM, item.description());
        var observedFiles = list(state.transientValue("observedFiles"));
        var decisionResult = decideNextActionWithRecovery(state, state.plan(), item, observedFiles, step.id());
        if (decisionResult.result() != null) {
            return decisionResult.result();
        }
        var decision = decisionResult.decision();
        ToolExecutionResult last = ToolExecutionResult.success("No action required", Map.of());
        var actionSummaries = new ArrayList<String>();
        for (var actionDraft : decision.actions()) {
            var action = actionService.create(step.id(), Domain.ActionType.CALL_TOOL, actionDraft.reason(),
                    Domain.RiskLevel.MEDIUM);
            var context = new ToolExecutionContext(state.task().id(), state.execution().id(), step.id(), action.id(),
                    state.workspace());
            last = reviewAllowsAction(state, actionDraft.type())
                    ? executeAction(context, actionDraft.type(), actionDraft.input(), actionDraft.reason())
                    : ToolExecutionResult.blocked("Review workflow is read-only; action not allowed: "
                    + actionDraft.type());
            actionSummaries.add(actionDraft.type() + ": " + last.summary());
            if (last.waitingApproval()) {
                stepService.markWaitingApproval(state.task().id(), state.execution().id(), step, last.summary());
                return NodeExecutionResult.waitingApproval(last.summary(), Map.of("stepId", step.id().toString()));
            }
            if (last.blocked()) {
                if ("RUN_COMMAND".equals(actionDraft.type())) {
                    stepService.fail(state.task().id(), state.execution().id(), step, last.summary());
                    planService.updateItemStatus(item.id(), Domain.PlanItemStatus.FAILED);
                    return NodeExecutionResult.blocked(last.summary());
                }
                var failure = runtimeFailureService.record(state.task().id(), state.execution().id(), step.id(), item.id(),
                        failureClassifier.fromToolResult(last), last.summary(), last.summary());
                if (Domain.RecoveryStrategy.REPLAN_CURRENT_ITEM.name().equals(failure.strategy())) {
                    return replanCurrentItem(state, state.plan(), item, observedFiles, step.id(), failure,
                            last.summary());
                }
                if (Domain.RecoveryStrategy.ASK_USER.name().equals(failure.strategy())) {
                    askUser(state, step.id(), item.id(), failure);
                    stepService.markWaitingUserInput(state.task().id(), state.execution().id(), step, failure.summary());
                    return NodeExecutionResult.waitingUserInput(failure.summary(), Map.of("stepId", step.id().toString()));
                }
                stepService.complete(state.task().id(), state.execution().id(), step, last.summary());
                return new NodeExecutionResult("BLOCKED", last.summary(), Map.of("stepId", step.id().toString()),
                        failure.failureType(), failure.strategy(), state.plan().plan().id(), item.id());
            }
            if (commandFailed(actionDraft.type(), last)) {
                var failure = runtimeFailureService.record(state.task().id(), state.execution().id(), step.id(), item.id(),
                        Domain.RuntimeFailureType.COMMAND_EXECUTION_FAILED, "Command failed: " + last.summary(),
                        last.summary());
                if (Domain.RecoveryStrategy.REPLAN_CURRENT_ITEM.name().equals(failure.strategy())) {
                    return replanCurrentItem(state, state.plan(), item, observedFiles, step.id(), failure,
                            last.summary());
                }
                if (Domain.RecoveryStrategy.ASK_USER.name().equals(failure.strategy())) {
                    askUser(state, step.id(), item.id(), failure);
                    stepService.markWaitingUserInput(state.task().id(), state.execution().id(), step, failure.summary());
                    return NodeExecutionResult.waitingUserInput(failure.summary(), Map.of("stepId", step.id().toString()));
                }
                stepService.complete(state.task().id(), state.execution().id(), step, last.summary());
                return new NodeExecutionResult("BLOCKED", last.summary(), Map.of("stepId", step.id().toString()),
                        failure.failureType(), failure.strategy(), state.plan().plan().id(), item.id());
            }
        }
        var stepSummary = summarizeActionResults(actionSummaries, last.summary());
        stepService.complete(state.task().id(), state.execution().id(), step, stepSummary);
        planService.updateItemStatus(item.id(), Domain.PlanItemStatus.COMPLETED);
        return NodeExecutionResult.success(stepSummary, Map.of(
                "stepId", step.id().toString(),
                "planItemId", item.id().toString(),
                "actionSummaries", actionSummaries));
    }

    private NodeExecutionResult resumeApprovedCommandIfPresent(AgentState state) {
        var approved = commandExecutionRepository.findApprovedWaitingByRun(state.execution().id());
        if (approved.isEmpty()) {
            return null;
        }
        var command = approved.get();
        var step = stepService.getRequired(command.stepId());
        if (isReviewWorkflow(state)) {
            var summary = "Review workflow is read-only; approved command will not be resumed";
            stepService.fail(state.task().id(), state.execution().id(), step, summary);
            return NodeExecutionResult.blocked(summary);
        }
        var result = commandToolService.resumeApprovedCommand(new ToolExecutionContext(state.task().id(),
                state.execution().id(), command.stepId(), command.actionId(), state.workspace()), command);
        if (result.waitingApproval()) {
            stepService.markWaitingApproval(state.task().id(), state.execution().id(), step, result.summary());
            return NodeExecutionResult.waitingApproval(result.summary(), Map.of("stepId", step.id().toString()));
        }
        if (result.blocked()) {
            stepService.fail(state.task().id(), state.execution().id(), step, result.summary());
            return new NodeExecutionResult("BLOCKED", result.summary(), Map.of("stepId", step.id().toString()),
                    null, null, null, null);
        }
        if (commandFailed("RUN_COMMAND", result)) {
            stepService.fail(state.task().id(), state.execution().id(), step, result.summary());
            if (step.planItemId() != null) {
                planService.updateItemStatus(step.planItemId(), Domain.PlanItemStatus.FAILED);
            }
            return NodeExecutionResult.failure("Command failed: " + result.summary(),
                    Map.of("stepId", step.id().toString()));
        }
        stepService.complete(state.task().id(), state.execution().id(), step, result.summary());
        if (step.planItemId() != null) {
            planService.updateItemStatus(step.planItemId(), Domain.PlanItemStatus.COMPLETED);
        }
        var payload = new java.util.HashMap<String, Object>(result.payload());
        payload.put("stepId", step.id().toString());
        if (step.planItemId() != null) {
            payload.put("planItemId", step.planItemId().toString());
        }
        return NodeExecutionResult.success(result.summary(), payload);
    }

    private NodeExecutionResult validate(AgentState state) {
        var changedFiles = changedFilesForRun(state);
        if (requiresFileChange(state) && changedFiles.isEmpty()) {
            return failNoFileChangesForEditTask(state, changedFiles);
        }
        var validationCommands = completedCommandsForRun(state);
        if (requiresValidationCommand(state) && validationCommands.isEmpty()) {
            if (state.plan() != null) {
                planService.updatePlanStatus(state.plan().plan().id(), Domain.PlanStatus.FAILED);
            }
            return NodeExecutionResult.failure("No validation command was run for a test workflow",
                    Map.of("changedFiles", changedFiles, "validationCommandCount", 0));
        }
        for (var command : validationCommands) {
            validationService.record(state.task().id(), state.execution().id(), null, command.id(),
                    Domain.ValidationType.TEST, command.exitCode() != null && command.exitCode() == 0,
                    command.outputSummary() == null ? command.command() : command.outputSummary());
        }
        if (state.plan() != null) {
            planService.updatePlanStatus(state.plan().plan().id(), Domain.PlanStatus.COMPLETED);
        }
        var summary = validationCommands.isEmpty()
                ? "Validation finalized without a command"
                : "Validation passed: finalized from " + validationCommands.size() + " command(s)";
        return NodeExecutionResult.success(summary, Map.of("changedFiles", changedFiles,
                "validationCommandCount", validationCommands.size()));
    }

    private boolean requiresFileChange(AgentState state) {
        var taskType = string(state.transientValue("taskType"), state.execution().agentMode());
        return "CODE_EDIT".equalsIgnoreCase(taskType) || "BUG_FIX".equalsIgnoreCase(taskType);
    }

    private boolean requiresValidationCommand(AgentState state) {
        var taskType = string(state.transientValue("taskType"), state.execution().agentMode());
        return Domain.WorkflowMode.TEST.name().equals(state.workflow().mode())
                || "TEST".equalsIgnoreCase(taskType);
    }

    private List<String> changedFilesForRun(AgentState state) {
        return state.recentFileChanges().stream()
                .filter(change -> state.execution().id().equals(change.runId()))
                .map(change -> change.path())
                .distinct()
                .toList();
    }

    private List<com.nask.agent.command.CommandExecution> completedCommandsForRun(AgentState state) {
        return state.recentCommandExecutions().stream()
                .filter(command -> state.execution().id().equals(command.runId()))
                .filter(command -> Domain.CommandExecutionStatus.COMPLETED.name().equals(command.status()))
                .toList();
    }

    private NodeExecutionResult failNoFileChangesForEditTask(AgentState state, List<String> changedFiles) {
        return failNoFileChangesForEditTask(state, changedFiles, null);
    }

    private NodeExecutionResult failNoFileChangesForEditTask(AgentState state, List<String> changedFiles, UUID stepId) {
        if (state.plan() != null) {
            planService.updatePlanStatus(state.plan().plan().id(), Domain.PlanStatus.FAILED);
        }
        var payload = new java.util.HashMap<String, Object>();
        payload.put("changedFiles", changedFiles);
        if (stepId != null) {
            payload.put("stepId", stepId.toString());
        }
        return NodeExecutionResult.failure("No file changes were made for an edit task", payload);
    }

    private NodeExecutionResult report(AgentState state) {
        var summary = Domain.WorkflowMode.REVIEW.name().equals(state.workflow().mode())
                ? "Review completed."
                : "Task completed.";
        reportService.generate(state.task(), state.execution().id(), summary);
        return NodeExecutionResult.success("Report generated", Map.of());
    }

    private NodeExecutionResult taskSummaryMemory(AgentState state) {
        var proposals = memoryWriteProposalService.proposeForTaskSummary(state);
        if (proposals.isEmpty()) {
            return NodeExecutionResult.success("No new task summary memory proposals", Map.of());
        }
        return NodeExecutionResult.success("Created " + proposals.size() + " memory write proposal(s)",
                Map.of("memoryProposalIds", proposals.stream().map(proposal -> proposal.id().toString()).toList()));
    }

    private NodeExecutionResult finish(AgentState state) {
        taskService.complete(state.task().id());
        return NodeExecutionResult.success("Workflow finished", Map.of());
    }

    private NodeExecutionResult fail(AgentState state) {
        taskService.fail(state.task().id(), "Workflow failed");
        return NodeExecutionResult.failure("Workflow failed");
    }

    private NodeExecutionResult completeToolStep(AgentState state, UUID stepId, ToolExecutionResult result,
                                                Map<String, Object> payload) {
        var step = stepService.getRequired(stepId);
        if (result.waitingApproval()) {
            stepService.markWaitingApproval(state.task().id(), state.execution().id(), step, result.summary());
            return NodeExecutionResult.waitingApproval(result.summary(), Map.of("stepId", step.id().toString()));
        }
        if (result.blocked()) {
            stepService.fail(state.task().id(), state.execution().id(), step, result.summary());
            return NodeExecutionResult.blocked(result.summary());
        }
        stepService.complete(state.task().id(), state.execution().id(), step, result.summary());
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
            case "CREATE_DIRECTORY" -> fileToolService.createDirectory(context, string(input.get("path"), "."), reason);
            case "APPLY_PATCH" -> {
                if (fileChangeRepository.countByRun(context.runId()) >= settings.maxFileChanges()) {
                    yield ToolExecutionResult.blocked("Maximum file change count exceeded");
                }
                yield fileToolService.applyPatch(context, string(input.get("path"), "."),
                        string(input.get("oldText"), ""), string(input.get("newText"), ""), reason);
            }
            case "GIT_STATUS" -> gitToolService.status(context, string(input.get("workingDirectory"), "."));
            case "GIT_DIFF" -> gitToolService.diff(context, string(input.get("workingDirectory"), "."));
            case "GIT_ADD" -> gitToolService.add(context, string(input.get("workingDirectory"), "."),
                    list(input.get("paths")));
            case "GIT_COMMIT" -> gitToolService.commit(context, string(input.get("workingDirectory"), "."),
                    string(input.get("message"), ""));
            case "GIT_PUSH" -> gitToolService.push(context, string(input.get("workingDirectory"), "."),
                    string(input.get("remote"), ""), string(input.get("branch"), ""));
            case "GIT_PULL" -> gitToolService.pull(context, string(input.get("workingDirectory"), "."),
                    string(input.get("remote"), ""), string(input.get("branch"), ""));
            case "GIT_FETCH" -> gitToolService.fetch(context, string(input.get("workingDirectory"), "."),
                    string(input.get("remote"), ""));
            case "GIT_LOG" -> gitToolService.log(context, string(input.get("workingDirectory"), "."),
                    integer(input.get("maxCount"), 10));
            case "GIT_SHOW" -> gitToolService.show(context, string(input.get("workingDirectory"), "."),
                    string(input.get("revision"), "HEAD"));
            case "GIT_BRANCH" -> gitToolService.branch(context, string(input.get("workingDirectory"), "."));
            case "GIT_CHECKOUT" -> gitToolService.checkout(context, string(input.get("workingDirectory"), "."),
                    string(input.get("ref"), ""));
            case "RUN_COMMAND" -> commandToolService.runCommand(context, string(input.get("executable"), ""),
                    list(input.get("arguments")), string(input.get("workingDirectory"), "."), reason);
            default -> ToolExecutionResult.blocked("Unsupported action type: " + type);
        };
    }

    private boolean commandFailed(String actionType, ToolExecutionResult result) {
        return "RUN_COMMAND".equals(actionType) && integer(result.payload().get("exitCode"), 0) != 0;
    }

    private boolean reviewAllowsAction(AgentState state, String actionType) {
        return !isReviewWorkflow(state) || isReadOnlyAction(actionType);
    }

    private boolean isReviewWorkflow(AgentState state) {
        return Domain.WorkflowMode.REVIEW.name().equals(state.workflow().mode());
    }

    private boolean isReadOnlyAction(String actionType) {
        return switch (actionType) {
            case "LIST_FILES", "READ_FILE", "SEARCH_TEXT",
                 "GIT_STATUS", "GIT_DIFF", "GIT_LOG", "GIT_SHOW", "GIT_BRANCH" -> true;
            default -> false;
        };
    }

    private AgentDecisionResult decideNextActionWithRecovery(AgentState state, com.nask.agent.plan.PlanView plan,
                                                             PlanItem item, List<String> observedFiles, UUID stepId) {
        var recoveryNotes = new ArrayList<>(state.recoveryNotes());
        while (true) {
            try {
                var decision = llmGateway.decideNextAction(new ExecutionContext(state.task().id(), state.execution().id(), stepId,
                        item, observedFiles, toolRecordRepository.findRecentSummariesByRun(state.execution().id(), 8),
                        recoveryNotes, state.memoryContext()));
                return new AgentDecisionResult(decision, null);
            } catch (LlmGatewayException e) {
                var failure = runtimeFailureService.record(state.task().id(), state.execution().id(), stepId, item.id(),
                        failureClassifier.fromModelException(e), e.getMessage(), "decide next action");
                if (Domain.RecoveryStrategy.RETRY_SAME_ACTION.name().equals(failure.strategy())) {
                    recoveryNotes.add(modelDecisionCorrectionNote(e.getMessage()));
                    continue;
                }
                if (Domain.RecoveryStrategy.REPLAN_CURRENT_ITEM.name().equals(failure.strategy())) {
                    return new AgentDecisionResult(null,
                            replanCurrentItem(state, plan, item, observedFiles, stepId, failure, e.getMessage()));
                }
                if (Domain.RecoveryStrategy.ASK_USER.name().equals(failure.strategy())) {
                    askUser(state, stepId, item.id(), failure);
                    var step = stepService.getRequired(stepId);
                    stepService.markWaitingUserInput(state.task().id(), state.execution().id(), step, failure.summary());
                    return new AgentDecisionResult(null,
                            NodeExecutionResult.waitingUserInput(failure.summary(), Map.of("stepId", stepId.toString())));
                }
                var step = stepService.getRequired(stepId);
                stepService.complete(state.task().id(), state.execution().id(), step, failure.summary());
                return new AgentDecisionResult(null,
                        new NodeExecutionResult("BLOCKED", failure.summary(), Map.of("stepId", stepId.toString()),
                                failure.failureType(), failure.strategy(), plan.plan().id(), item.id()));
            }
        }
    }

    private record AgentDecisionResult(com.nask.agent.llm.AgentDecision decision, NodeExecutionResult result) {
    }

    private String summarizeActionResults(List<String> actionSummaries, String fallbackSummary) {
        if (actionSummaries.isEmpty()) {
            return fallbackSummary;
        }
        if (actionSummaries.size() == 1) {
            return actionSummaries.getFirst();
        }
        return "Completed " + actionSummaries.size() + " actions: "
                + String.join("; ", actionSummaries);
    }

    private String modelDecisionCorrectionNote(String failureSummary) {
        return "Previous model decision was rejected: " + compact(failureSummary, 240)
                + ". Return valid JSON with at most 5 actions. If recent tool results already satisfy the plan item, "
                + "return an empty actions array.";
    }

    private NodeExecutionResult replanCurrentItem(AgentState state, com.nask.agent.plan.PlanView plan, PlanItem item,
                                                  List<String> observedFiles, UUID stepId, RuntimeFailure failure,
                                                  String failureSummary) {
        var recoveryDraft = callModelWithRecovery(state, stepId, item.id(), "replan current item",
                () -> llmGateway.replan(new ExecutionContext(state.task().id(), state.execution().id(), stepId, item,
                        observedFiles, toolRecordRepository.findRecentSummariesByRun(state.execution().id(), 8),
                        state.recoveryNotes(), state.memoryContext()), failureSummary));
        var step = stepService.getRequired(stepId);
        if (recoveryDraft != null) {
            planService.updateItemStatus(item.id(), Domain.PlanItemStatus.FAILED);
            planService.appendRecoveryItems(state.task().id(), state.execution().id(), plan.plan().id(), recoveryDraft,
                    item.id(), failureSummary, failure.id());
            stepService.complete(state.task().id(), state.execution().id(), step,
                    "Runtime rejected action; recovery plan appended");
            return NodeExecutionResult.success("Runtime rejected action; recovery plan appended",
                    Map.of("stepId", stepId.toString()));
        }
        stepService.markWaitingUserInput(state.task().id(), state.execution().id(), step, "Waiting for user input");
        return NodeExecutionResult.waitingUserInput("Waiting for user input", Map.of("stepId", stepId.toString()));
    }

    private <T> T callModelWithRecovery(AgentState state, UUID stepId, UUID planItemId, String decisionType,
                                        java.util.function.Supplier<T> supplier) {
        while (true) {
            try {
                return supplier.get();
            } catch (LlmGatewayException e) {
                var failure = runtimeFailureService.record(state.task().id(), state.execution().id(), stepId, planItemId,
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
        userInputRequestService.create(state.task().id(), state.execution().id(), stepId, planItemId,
                "Runtime recovery needs guidance for " + failure.failureType(),
                failure.summary(), List.of("Retry with corrected model output", "Adjust task instructions",
                        "Cancel task"));
    }

    private List<com.nask.agent.conversation.ConversationTaskContext> previousConversationTasks(AgentState state) {
        if (conversationContextService != null) {
            return conversationContextService.window(state.task().conversationId(), state.task().id()).tasks();
        }
        if (conversationService == null) {
            return List.of();
        }
        return conversationService.previousTaskContext(state.task().conversationId(), state.task().id(),
                PREVIOUS_CONVERSATION_TASK_LIMIT);
    }

    private String referencedPreviousTaskContext(AgentState state) {
        if (!referencesPreviousContext(state.task().userRequest())) {
            return "";
        }
        return previousConversationTasks(state).stream()
                .map(task -> compact(task.prompt(), 200) + " " + compact(task.finalReport(), 800)
                        + " " + String.join(" ", task.affectedFiles()))
                .collect(java.util.stream.Collectors.joining(" "));
    }

    private boolean referencesPreviousContext(String request) {
        if (request == null || request.isBlank()) {
            return false;
        }
        var lower = request.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("above")
                || lower.contains("previous")
                || lower.contains("last")
                || lower.contains("上述")
                || lower.contains("上面")
                || lower.contains("之前")
                || lower.contains("建议");
    }

    private String compact(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "";
        }
        var normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength - 3) + "...";
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
