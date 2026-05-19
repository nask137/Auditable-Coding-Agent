package com.nask.agent.workflow;

import com.nask.agent.action.AgentAction;
import com.nask.agent.action.AgentActionService;
import com.nask.agent.command.CommandExecutionRepository;
import com.nask.agent.command.CommandToolService;
import com.nask.agent.common.AgentSettings;
import com.nask.agent.common.Domain;
import com.nask.agent.file.FileChangeRepository;
import com.nask.agent.file.FileToolService;
import com.nask.agent.file.FileChange;
import com.nask.agent.git.GitToolService;
import com.nask.agent.llm.AgentDecision;
import com.nask.agent.llm.LlmGateway;
import com.nask.agent.llm.LlmGatewayException;
import com.nask.agent.llm.PlanDraft;
import com.nask.agent.memory.ProjectContextRetriever;
import com.nask.agent.memory.ProjectMemoryService;
import com.nask.agent.memory.MemoryWriteProposalService;
import com.nask.agent.plan.Plan;
import com.nask.agent.plan.PlanItem;
import com.nask.agent.plan.PlanService;
import com.nask.agent.plan.PlanView;
import com.nask.agent.report.ReportService;
import com.nask.agent.runtime.FailureClassifier;
import com.nask.agent.runtime.RuntimeFailure;
import com.nask.agent.runtime.RuntimeFailureService;
import com.nask.agent.runtime.UserInputRequestService;
import com.nask.agent.step.AgentStep;
import com.nask.agent.step.AgentStepService;
import com.nask.agent.task.CodingTask;
import com.nask.agent.task.TaskService;
import com.nask.agent.tool.ToolExecutionResult;
import com.nask.agent.tool.ToolRecordRepository;
import com.nask.agent.validation.ValidationService;
import com.nask.agent.workspace.Workspace;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentWorkflowNodeExecutorTests {
    private final AgentStepService stepService = mock(AgentStepService.class);
    private final AgentActionService actionService = mock(AgentActionService.class);
    private final PlanService planService = mock(PlanService.class);
    private final LlmGateway llmGateway = mock(LlmGateway.class);
    private final FileToolService fileToolService = mock(FileToolService.class);
    private final GitToolService gitToolService = mock(GitToolService.class);
    private final ReportService reportService = mock(ReportService.class);
    private final CommandToolService commandToolService = mock(CommandToolService.class);
    private final ValidationService validationService = mock(ValidationService.class);
    private final AgentSettings settings = new AgentSettings(10, 20, 1000, 300, 3, 2, 2, 3, 120, 200000);
    private final FileChangeRepository fileChangeRepository = mock(FileChangeRepository.class);
    private final CommandExecutionRepository commandExecutionRepository = mock(CommandExecutionRepository.class);
    private final ToolRecordRepository toolRecordRepository = mock(ToolRecordRepository.class);
    private final RuntimeFailureService runtimeFailureService = mock(RuntimeFailureService.class);
    private final FailureClassifier failureClassifier = new FailureClassifier();
    private final TaskService taskService = mock(TaskService.class);
    private final UserInputRequestService userInputRequestService = mock(UserInputRequestService.class);
    private final ProjectMemoryService projectMemoryService = mock(ProjectMemoryService.class);
    private final ProjectContextRetriever projectContextRetriever = mock(ProjectContextRetriever.class);
    private final MemoryWriteProposalService memoryWriteProposalService = mock(MemoryWriteProposalService.class);
    private final AgentWorkflowNodeExecutor executor = new AgentWorkflowNodeExecutor(stepService, actionService,
            planService, llmGateway, fileToolService, gitToolService, reportService, commandToolService,
            validationService, settings, fileChangeRepository, commandExecutionRepository, toolRecordRepository,
            runtimeFailureService, failureClassifier, taskService, userInputRequestService, projectMemoryService,
            projectContextRetriever, memoryWriteProposalService);

    @Test
    void replansCurrentItemWhenToolIntentIsRejected() {
        var ids = ids();
        var item = planItem(ids.planId(), "Trigger unsupported tool intent");
        var state = state(ids, new PlanView(plan(ids), List.of(item)), item, Map.of());
        var step = step(ids.runId(), item.id(), Domain.StepType.EXECUTE_PLAN_ITEM);
        var action = action(step.id());
        when(stepService.start(ids.taskId(), ids.runId(), item.id(), Domain.StepType.EXECUTE_PLAN_ITEM,
                item.description())).thenReturn(step);
        when(actionService.create(eq(step.id()), eq(Domain.ActionType.CALL_TOOL), any(), eq(Domain.RiskLevel.MEDIUM)))
                .thenReturn(action);
        when(llmGateway.decideNextAction(any())).thenReturn(new AgentDecision(item.id(), List.of(
                new AgentDecision.Action("DELETE_REPO", "Unsupported intent", Map.of()))));
        var failure = failure(ids, step.id(), item.id(), Domain.RuntimeFailureType.UNSUPPORTED_TOOL_INTENT,
                Domain.RecoveryStrategy.REPLAN_CURRENT_ITEM, "Unsupported action type: DELETE_REPO");
        when(runtimeFailureService.record(eq(ids.taskId()), eq(ids.runId()), eq(step.id()), eq(item.id()),
                eq(Domain.RuntimeFailureType.UNSUPPORTED_TOOL_INTENT), any(), any())).thenReturn(failure);
        var recoveryDraft = new PlanDraft(List.of(new PlanDraft.Item("Read README.md after runtime rejection",
                List.of("README.md"), "Recover with supported read")));
        when(llmGateway.replan(any(), any())).thenReturn(recoveryDraft);
        when(stepService.getRequired(step.id())).thenReturn(step);

        var result = executor.execute(state,
                new MapWorkflowNode("execute_plan_item", Domain.WorkflowNodeType.PLAN_ITEM_EXECUTION.name(), Map.of()));

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.summary()).contains("recovery plan appended");
        verify(planService).updateItemStatus(item.id(), Domain.PlanItemStatus.FAILED);
        verify(planService).appendRecoveryItems(ids.taskId(), ids.runId(), ids.planId(), recoveryDraft, item.id(),
                "Unsupported action type: DELETE_REPO", failure.id());
        verify(stepService).complete(ids.taskId(), ids.runId(), step, "Runtime rejected action; recovery plan appended");
    }

    @Test
    void appendsRecoveryPlanWhenRunCommandFailsDuringPlanExecution() {
        var ids = ids();
        var item = planItem(ids.planId(), "Run validation command");
        var state = stateWithChanges(ids, new PlanView(plan(ids), List.of(item)), item, Map.of("taskType", "CODE_EDIT"),
                List.of(fileChange(ids, "src/main/java/App.java")));
        var step = step(ids.runId(), item.id(), Domain.StepType.EXECUTE_PLAN_ITEM);
        var action = action(step.id());
        when(stepService.start(ids.taskId(), ids.runId(), item.id(), Domain.StepType.EXECUTE_PLAN_ITEM,
                item.description()))
                .thenReturn(step);
        when(stepService.getRequired(step.id())).thenReturn(step);
        when(actionService.create(eq(step.id()), eq(Domain.ActionType.CALL_TOOL), any(),
                eq(Domain.RiskLevel.MEDIUM))).thenReturn(action);
        var commandId = UUID.randomUUID();
        when(llmGateway.decideNextAction(any())).thenReturn(new AgentDecision(item.id(), List.of(
                new AgentDecision.Action("RUN_COMMAND", "Run failing validation",
                        Map.of("executable", "java", "arguments", List.of("-bad"), "workingDirectory", ".")))));
        when(commandToolService.runCommand(any(), eq("java"), eq(List.of("-bad")), eq("."), eq("Run failing validation")))
                .thenReturn(ToolExecutionResult.success("exit 1", Map.of("exitCode", 1, "commandId", commandId)));
        var failure = failure(ids, step.id(), item.id(), Domain.RuntimeFailureType.COMMAND_EXECUTION_FAILED,
                Domain.RecoveryStrategy.REPLAN_CURRENT_ITEM, "Command failed: exit 1");
        when(runtimeFailureService.record(eq(ids.taskId()), eq(ids.runId()), eq(step.id()), eq(item.id()),
                eq(Domain.RuntimeFailureType.COMMAND_EXECUTION_FAILED), any(), any())).thenReturn(failure);
        var recoveryDraft = new PlanDraft(List.of(new PlanDraft.Item("Fix validation failure", List.of(),
                "Recover from failing command")));
        when(llmGateway.replan(any(), eq("exit 1"))).thenReturn(recoveryDraft);

        var result = executor.execute(state,
                new MapWorkflowNode("execute_plan_item", Domain.WorkflowNodeType.PLAN_ITEM_EXECUTION.name(), Map.of()));

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.summary()).contains("recovery plan appended");
        verify(planService).updateItemStatus(item.id(), Domain.PlanItemStatus.FAILED);
        verify(planService).appendRecoveryItems(ids.taskId(), ids.runId(), ids.planId(), recoveryDraft, item.id(),
                "exit 1", failure.id());
    }

    @Test
    void reviewWorkflowBlocksWriteToolsDuringPlanExecution() {
        var ids = ids();
        var item = planItem(ids.planId(), "Attempt to stage changes");
        var state = stateWithWorkflowMode(ids, new PlanView(plan(ids), List.of(item)), item,
                Map.of("taskType", "REVIEW"), List.of(), "review only", List.of(), Domain.WorkflowMode.REVIEW);
        var step = step(ids.runId(), item.id(), Domain.StepType.EXECUTE_PLAN_ITEM);
        var action = action(step.id());
        when(stepService.start(ids.taskId(), ids.runId(), item.id(), Domain.StepType.EXECUTE_PLAN_ITEM,
                item.description()))
                .thenReturn(step);
        when(stepService.getRequired(step.id())).thenReturn(step);
        when(actionService.create(eq(step.id()), eq(Domain.ActionType.CALL_TOOL), any(),
                eq(Domain.RiskLevel.MEDIUM))).thenReturn(action);
        when(llmGateway.decideNextAction(any())).thenReturn(new AgentDecision(item.id(), List.of(
                new AgentDecision.Action("GIT_ADD", "Stage changes",
                        Map.of("workingDirectory", ".", "paths", List.of("src/main/java/App.java"))))));
        var failure = failure(ids, step.id(), item.id(), Domain.RuntimeFailureType.TOOL_PERMISSION_BLOCKED,
                Domain.RecoveryStrategy.REPLAN_CURRENT_ITEM,
                "Review workflow is read-only; action not allowed: GIT_ADD");
        when(runtimeFailureService.record(eq(ids.taskId()), eq(ids.runId()), eq(step.id()), eq(item.id()),
                eq(Domain.RuntimeFailureType.TOOL_PERMISSION_BLOCKED), any(), any())).thenReturn(failure);
        var recoveryDraft = new PlanDraft(List.of(new PlanDraft.Item("Inspect changes without writing",
                List.of("src/main/java/App.java"), "Use read-only review tools")));
        when(llmGateway.replan(any(), eq("Review workflow is read-only; action not allowed: GIT_ADD")))
                .thenReturn(recoveryDraft);

        var result = executor.execute(state,
                new MapWorkflowNode("execute_plan_item", Domain.WorkflowNodeType.PLAN_ITEM_EXECUTION.name(), Map.of()));

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.summary()).contains("recovery plan appended");
        verify(gitToolService, never()).add(any(), any(), any());
        verify(planService).appendRecoveryItems(ids.taskId(), ids.runId(), ids.planId(), recoveryDraft, item.id(),
                "Review workflow is read-only; action not allowed: GIT_ADD", failure.id());
    }

    @Test
    void validateFinalizesCompletedCommandsAsValidationResults() {
        var ids = ids();
        var commandId = UUID.randomUUID();
        var command = new com.nask.agent.command.CommandExecution(commandId, ids.workspaceId(), ids.taskId(),
                ids.runId(), UUID.randomUUID(), UUID.randomUUID(), "mvn test", "mvn", List.of("test"),
                ".", Domain.CommandPolicyType.ALLOWLIST.name(), Domain.RiskLevel.MEDIUM.name(), null,
                Domain.CommandExecutionStatus.COMPLETED.name(), 0, "tests passed", Instant.now(), Instant.now(),
                Instant.now());
        var state = stateWithCommands(ids, new PlanView(plan(ids), List.of()), null, Map.of("taskType", "TEST"),
                List.of(), List.of(command));

        var result = executor.execute(state,
                new MapWorkflowNode("validate", Domain.WorkflowNodeType.VALIDATION.name(), Map.of()));

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.summary()).contains("Validation passed");
        verify(validationService).record(ids.taskId(), ids.runId(), null, commandId, Domain.ValidationType.TEST,
                true, "tests passed");
        verify(planService).updatePlanStatus(ids.planId(), Domain.PlanStatus.COMPLETED);
    }

    @Test
    void failsTestWorkflowWhenNoValidationCommandRan() {
        var ids = ids();
        var state = stateWithWorkflowMode(ids, new PlanView(plan(ids), List.of()), null,
                Map.of("taskType", "TEST"), List.of(), "run tests", List.of(), Domain.WorkflowMode.TEST);

        var result = executor.execute(state,
                new MapWorkflowNode("validate", Domain.WorkflowNodeType.VALIDATION.name(), Map.of()));

        assertThat(result.status()).isEqualTo("FAILURE");
        assertThat(result.summary()).contains("No validation command");
        verify(validationService, never()).record(any(), any(), any(), any(), any(), anyBoolean(), any());
        verify(planService).updatePlanStatus(ids.planId(), Domain.PlanStatus.FAILED);
    }

    @Test
    void failsEditTaskWhenNoFileChangesWereMade() {
        var ids = ids();
        var state = state(ids, new PlanView(plan(ids), List.of()), null, Map.of("taskType", "CODE_EDIT"));
        when(commandExecutionRepository.findApprovedWaitingByRun(ids.runId())).thenReturn(java.util.Optional.empty());

        var result = executor.execute(state,
                new MapWorkflowNode("validate", Domain.WorkflowNodeType.VALIDATION.name(), Map.of()));

        assertThat(result.status()).isEqualTo("FAILURE");
        assertThat(result.summary()).contains("No file changes");
        verify(planService).updatePlanStatus(ids.planId(), Domain.PlanStatus.FAILED);
        verify(llmGateway, never()).decideNextAction(any());
    }

    @Test
    void failsEditTaskWithNoFileChangesEvenIfCommandsRan() {
        var ids = ids();
        var commandId = UUID.randomUUID();
        var command = new com.nask.agent.command.CommandExecution(commandId, ids.workspaceId(), ids.taskId(),
                ids.runId(), UUID.randomUUID(), UUID.randomUUID(), "mvn test", "mvn", List.of("test"),
                ".", Domain.CommandPolicyType.ALLOWLIST.name(), Domain.RiskLevel.MEDIUM.name(), null,
                Domain.CommandExecutionStatus.COMPLETED.name(), 0, "tests passed", Instant.now(), Instant.now(),
                Instant.now());
        var state = stateWithRequest(ids, new PlanView(plan(ids), List.of()), null,
                Map.of("taskType", "CODE_EDIT"), List.of(), "fix this and run tests", List.of(command));

        var result = executor.execute(state,
                new MapWorkflowNode("validate", Domain.WorkflowNodeType.VALIDATION.name(), Map.of()));

        assertThat(result.status()).isEqualTo("FAILURE");
        assertThat(result.summary()).contains("No file changes");
        verify(validationService, never()).record(any(), any(), any(), any(), any(), anyBoolean(), any());
        verify(planService).updatePlanStatus(ids.planId(), Domain.PlanStatus.FAILED);
    }

    @Test
    void skipsValidationWithoutModelCallWhenCodingRunMadeNoFileChanges() {
        var ids = ids();
        var state = state(ids, new PlanView(plan(ids), List.of()), null, Map.of("taskType", "REVIEW"));
        when(commandExecutionRepository.findApprovedWaitingByRun(ids.runId())).thenReturn(java.util.Optional.empty());

        var result = executor.execute(state,
                new MapWorkflowNode("validate", Domain.WorkflowNodeType.VALIDATION.name(), Map.of()));

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.summary()).contains("Validation finalized without a command");
        verify(planService).updatePlanStatus(ids.planId(), Domain.PlanStatus.COMPLETED);
        verify(llmGateway, never()).decideNextAction(any());
    }

    @Test
    void inspectsWorkspaceDeepEnoughForMavenPackagePaths() {
        var ids = ids();
        var state = state(ids, null, null, Map.of());
        var step = step(ids.runId(), null, Domain.StepType.INSPECT_WORKSPACE);
        var action = action(step.id());
        when(stepService.start(ids.taskId(), ids.runId(), null, Domain.StepType.INSPECT_WORKSPACE,
                "Inspect workspace")).thenReturn(step);
        when(actionService.create(step.id(), Domain.ActionType.CALL_TOOL, "List workspace files for planning",
                Domain.RiskLevel.LOW)).thenReturn(action);
        when(fileToolService.listFiles(any(), eq("."), eq(6))).thenReturn(ToolExecutionResult.success(
                "Listed 1 files", Map.of("files", List.of("src/main/java/cdu/wangnan/App.java"))));
        when(stepService.getRequired(step.id())).thenReturn(step);

        var result = executor.execute(state,
                new MapWorkflowNode("inspect_workspace", Domain.WorkflowNodeType.WORKSPACE_INSPECTION.name(), Map.of()));

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.payload().get("observedFiles").toString()).contains("App.java");
        verify(fileToolService).listFiles(any(), eq("."), eq(6));
    }

    @Test
    void retriesModelDecisionFailuresBeforeExecutingPlanItem() {
        var ids = ids();
        var item = planItem(ids.planId(), "No action needed");
        var state = state(ids, new PlanView(plan(ids), List.of(item)), item, Map.of());
        var step = step(ids.runId(), item.id(), Domain.StepType.EXECUTE_PLAN_ITEM);
        when(stepService.start(ids.taskId(), ids.runId(), item.id(), Domain.StepType.EXECUTE_PLAN_ITEM,
                item.description())).thenReturn(step);
        var failure = failure(ids, step.id(), item.id(), Domain.RuntimeFailureType.MODEL_OUTPUT_PARSE_FAILED,
                Domain.RecoveryStrategy.RETRY_SAME_ACTION, "bad json");
        when(runtimeFailureService.record(eq(ids.taskId()), eq(ids.runId()), eq(step.id()), eq(item.id()),
                eq(Domain.RuntimeFailureType.MODEL_OUTPUT_PARSE_FAILED), any(), eq("decide next action")))
                .thenReturn(failure);
        when(llmGateway.decideNextAction(any()))
                .thenThrow(new LlmGatewayException("bad json", Domain.RuntimeFailureType.MODEL_OUTPUT_PARSE_FAILED,
                        "decide next action"))
                .thenReturn(new AgentDecision(item.id(), List.of()));

        var result = executor.execute(state,
                new MapWorkflowNode("execute_plan_item", Domain.WorkflowNodeType.PLAN_ITEM_EXECUTION.name(), Map.of()));

        assertThat(result.status()).isEqualTo("SUCCESS");
        verify(llmGateway, times(2)).decideNextAction(any());
        verify(runtimeFailureService).record(eq(ids.taskId()), eq(ids.runId()), eq(step.id()), eq(item.id()),
                eq(Domain.RuntimeFailureType.MODEL_OUTPUT_PARSE_FAILED), any(), eq("decide next action"));
        verify(planService).updateItemStatus(item.id(), Domain.PlanItemStatus.COMPLETED);
    }

    private AgentState state(Ids ids, PlanView plan, PlanItem currentItem, Map<String, Object> transientData) {
        return stateWithChanges(ids, plan, currentItem, transientData, List.of());
    }

    private AgentState stateWithChanges(Ids ids, PlanView plan, PlanItem currentItem,
                                        Map<String, Object> transientData, List<FileChange> changes) {
        return stateWithRequest(ids, plan, currentItem, transientData, changes, "request");
    }

    private AgentState stateWithRequest(Ids ids, PlanView plan, PlanItem currentItem,
                                        Map<String, Object> transientData, List<FileChange> changes, String request) {
        return stateWithRequest(ids, plan, currentItem, transientData, changes, request, List.of());
    }

    private AgentState stateWithRequest(Ids ids, PlanView plan, PlanItem currentItem,
                                        Map<String, Object> transientData, List<FileChange> changes, String request,
                                        List<com.nask.agent.command.CommandExecution> commands) {
        return stateWithWorkflowMode(ids, plan, currentItem, transientData, changes, request, commands,
                Domain.WorkflowMode.CODING);
    }

    private AgentState stateWithWorkflowMode(Ids ids, PlanView plan, PlanItem currentItem,
                                            Map<String, Object> transientData, List<FileChange> changes,
                                            String request,
                                            List<com.nask.agent.command.CommandExecution> commands,
                                            Domain.WorkflowMode workflowMode) {
        var now = Instant.now();
        var task = new CodingTask(ids.taskId(), ids.workspaceId(), null, 1, "task", request,
                Domain.TaskStatus.RUNNING.name(), "CODE_EDIT", now, null, null,
                Map.of("workflow", workflowMode.name().toLowerCase(java.util.Locale.ROOT) + "-agent"), now, now);
        var run = task;
        var workspace = new Workspace(ids.workspaceId(), "workspace", "D:/tmp/workspace", true, List.of(), List.of(),
                List.of(), now, now);
        var workflow = new WorkflowDefinition(ids.workflowId(), workflowMode.name().toLowerCase(java.util.Locale.ROOT)
                + "-agent", 1, workflowMode.name(), workflowMode.name(), true, Map.of(), now, now);
        return new AgentState(task, run, workspace, workflow, plan, currentItem, changes, commands, List.of(),
                null, List.of(), List.of(), null, transientData);
    }

    private AgentState stateWithCommands(Ids ids, PlanView plan, PlanItem currentItem,
                                         Map<String, Object> transientData, List<FileChange> changes,
                                         List<com.nask.agent.command.CommandExecution> commands) {
        return stateWithRequest(ids, plan, currentItem, transientData, changes, "request", commands);
    }

    private FileChange fileChange(Ids ids, String path) {
        var now = Instant.now();
        return new FileChange(UUID.randomUUID(), ids.workspaceId(), ids.taskId(), ids.runId(), UUID.randomUUID(),
                UUID.randomUUID(), path, Domain.ChangeType.MODIFY.name(), "reason", "", "before", "after", null,
                now, Domain.PatchApplyStatus.APPLIED.name(), 1, 0, Domain.RiskLevel.LOW.name(), null, now);
    }

    private Plan plan(Ids ids) {
        var now = Instant.now();
        return new Plan(ids.planId(), ids.taskId(), ids.runId(), Domain.PlanStatus.ACTIVE.name(), now, now);
    }

    private PlanItem planItem(UUID planId, String description) {
        var now = Instant.now();
        return new PlanItem(UUID.randomUUID(), planId, description, Domain.PlanItemStatus.PENDING.name(), List.of(),
                "notes", 1, now, now);
    }

    private AgentStep step(UUID runId, UUID planItemId, Domain.StepType type) {
        return new AgentStep(UUID.randomUUID(), runId, planItemId, type.name(), Domain.StepStatus.RUNNING.name(),
                type.name(), null, Instant.now(), null);
    }

    private AgentAction action(UUID stepId) {
        return new AgentAction(UUID.randomUUID(), stepId, Domain.ActionType.CALL_TOOL.name(), "reason",
                Domain.RiskLevel.MEDIUM.name(), Domain.ActionStatus.CREATED.name(), Instant.now());
    }

    private RuntimeFailure failure(Ids ids, UUID stepId, UUID planItemId, Domain.RuntimeFailureType type,
                                   Domain.RecoveryStrategy strategy, String summary) {
        return new RuntimeFailure(UUID.randomUUID(), ids.taskId(), ids.runId(), stepId, planItemId, type.name(), true,
                strategy.name(), summary, summary, null, null, null, null, 1, Instant.now());
    }

    private Ids ids() {
        var taskId = UUID.randomUUID();
        return new Ids(UUID.randomUUID(), taskId, taskId, UUID.randomUUID(), UUID.randomUUID());
    }

    private record Ids(UUID workspaceId, UUID taskId, UUID runId, UUID planId, UUID workflowId) {
    }
}
