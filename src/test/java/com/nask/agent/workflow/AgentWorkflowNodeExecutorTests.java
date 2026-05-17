package com.nask.agent.workflow;

import com.nask.agent.action.AgentAction;
import com.nask.agent.action.AgentActionService;
import com.nask.agent.command.CommandExecutionRepository;
import com.nask.agent.command.CommandToolService;
import com.nask.agent.common.AgentSettings;
import com.nask.agent.common.Domain;
import com.nask.agent.file.FileChangeRepository;
import com.nask.agent.file.FileToolService;
import com.nask.agent.git.GitToolService;
import com.nask.agent.llm.AgentDecision;
import com.nask.agent.llm.LlmGateway;
import com.nask.agent.llm.LlmGatewayException;
import com.nask.agent.llm.PlanDraft;
import com.nask.agent.llm.ValidationDecision;
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
                new AgentDecision.Action("RUN_COMMAND", "Unsupported intent", Map.of()))));
        var failure = failure(ids, step.id(), item.id(), Domain.RuntimeFailureType.UNSUPPORTED_TOOL_INTENT,
                Domain.RecoveryStrategy.REPLAN_CURRENT_ITEM, "Unsupported action type: RUN_COMMAND");
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
                "Unsupported action type: RUN_COMMAND", failure.id());
        verify(stepService).complete(ids.taskId(), ids.runId(), step, "Runtime rejected action; recovery plan appended");
    }

    @Test
    void appendsRecoveryPlanWhenValidationFails() {
        var ids = ids();
        var item = planItem(ids.planId(), "Validate");
        var state = state(ids, new PlanView(plan(ids), List.of(item)), null, Map.of("taskType", "TEST"));
        var step = step(ids.runId(), null, Domain.StepType.VALIDATE);
        var action = action(step.id());
        when(commandExecutionRepository.findApprovedWaitingByRun(ids.runId())).thenReturn(java.util.Optional.empty());
        when(llmGateway.suggestValidation(any())).thenReturn(new ValidationDecision(true, List.of("java", "-bad"),
                "Run failing validation"));
        when(stepService.start(ids.taskId(), ids.runId(), null, Domain.StepType.VALIDATE, "Run failing validation"))
                .thenReturn(step);
        when(stepService.getRequired(step.id())).thenReturn(step);
        when(actionService.create(step.id(), Domain.ActionType.RUN_VALIDATION, "Run failing validation",
                Domain.RiskLevel.MEDIUM)).thenReturn(action);
        var commandId = UUID.randomUUID();
        when(commandToolService.runCommand(any(), eq("java"), eq(List.of("-bad")), eq("."), eq("Run failing validation")))
                .thenReturn(ToolExecutionResult.success("exit 1", Map.of("exitCode", 1, "commandId", commandId)));
        var failure = failure(ids, step.id(), null, Domain.RuntimeFailureType.VALIDATION_FAILED,
                Domain.RecoveryStrategy.REPLAN_REMAINING_PLAN, "Validation failed: exit 1");
        when(runtimeFailureService.record(eq(ids.taskId()), eq(ids.runId()), eq(step.id()), eq(null),
                eq(Domain.RuntimeFailureType.VALIDATION_FAILED), any(), any())).thenReturn(failure);
        var recoveryDraft = new PlanDraft(List.of(new PlanDraft.Item("Fix validation failure", List.of(),
                "Recover from failing command")));
        when(llmGateway.replan(any(), eq("exit 1"))).thenReturn(recoveryDraft);

        var result = executor.execute(state,
                new MapWorkflowNode("validate", Domain.WorkflowNodeType.VALIDATION.name(), Map.of()));

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.summary()).contains("recovery plan appended");
        verify(validationService).record(ids.taskId(), ids.runId(), step.id(), commandId, Domain.ValidationType.TEST,
                false, "exit 1");
        verify(planService).updatePlanStatus(ids.planId(), Domain.PlanStatus.ACTIVE);
        verify(planService).appendRecoveryItems(ids.taskId(), ids.runId(), ids.planId(), recoveryDraft, null,
                "exit 1", failure.id());
    }

    @Test
    void skipsValidationWithoutModelCallWhenCodingRunMadeNoFileChanges() {
        var ids = ids();
        var state = state(ids, new PlanView(plan(ids), List.of()), null, Map.of("taskType", "REVIEW"));
        when(commandExecutionRepository.findApprovedWaitingByRun(ids.runId())).thenReturn(java.util.Optional.empty());

        var result = executor.execute(state,
                new MapWorkflowNode("validate", Domain.WorkflowNodeType.VALIDATION.name(), Map.of()));

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.summary()).contains("Skipped validation");
        verify(planService).updatePlanStatus(ids.planId(), Domain.PlanStatus.COMPLETED);
        verify(llmGateway, never()).suggestValidation(any());
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
        var now = Instant.now();
        var task = new CodingTask(ids.taskId(), ids.workspaceId(), null, 1, "task", "request",
                Domain.TaskStatus.RUNNING.name(), "CODE_EDIT", now, null, null,
                Map.of("workflow", "coding-agent"), now, now);
        var run = task;
        var workspace = new Workspace(ids.workspaceId(), "workspace", "D:/tmp/workspace", true, List.of(), List.of(),
                List.of(), now, now);
        var workflow = new WorkflowDefinition(ids.workflowId(), "coding-agent", 1, "Coding",
                Domain.WorkflowMode.CODING.name(), true, Map.of(), now, now);
        return new AgentState(task, run, workspace, workflow, plan, currentItem, List.of(), List.of(), List.of(),
                null, List.of(), List.of(), null, transientData);
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
