package com.nask.agent.workflow;

import com.nask.agent.action.AgentActionService;
import com.nask.agent.command.CommandExecution;
import com.nask.agent.command.CommandExecutionRepository;
import com.nask.agent.command.CommandToolService;
import com.nask.agent.common.Domain;
import com.nask.agent.file.FileToolService;
import com.nask.agent.llm.ValidationContext;
import com.nask.agent.llm.LlmGateway;
import com.nask.agent.report.ReportService;
import com.nask.agent.run.AgentLoopExecutor;
import com.nask.agent.run.AgentRun;
import com.nask.agent.run.AgentRunService;
import com.nask.agent.run.DefaultAgentLoopExecutor;
import com.nask.agent.step.AgentStep;
import com.nask.agent.step.AgentStepService;
import com.nask.agent.task.TaskService;
import com.nask.agent.tool.ToolExecutionContext;
import com.nask.agent.validation.ValidationService;
import com.nask.agent.workspace.WorkspaceService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.Map;
import java.util.UUID;

/**
 * Phase 3 workflow-aware executor.
 *
 * <p>The coding workflow initially reuses the stable fixed loop and backfills
 * workflow node/edge records from durable AgentStep state. Review and test
 * workflows execute lightweight dedicated paths to prove multiple workflow modes
 * can run through the same default executor.</p>
 */
@Primary
@Service
public class WorkflowAgentExecutor implements AgentLoopExecutor {
    private final DefaultAgentLoopExecutor fixedExecutor;
    private final WorkflowService workflowService;
    private final AgentRunService runService;
    private final TaskService taskService;
    private final WorkspaceService workspaceService;
    private final AgentStepService stepService;
    private final AgentActionService actionService;
    private final FileToolService fileToolService;
    private final CommandExecutionRepository commandExecutionRepository;
    private final CommandToolService commandToolService;
    private final ValidationService validationService;
    private final LlmGateway llmGateway;
    private final ReportService reportService;

    public WorkflowAgentExecutor(DefaultAgentLoopExecutor fixedExecutor, WorkflowService workflowService,
                                 AgentRunService runService, TaskService taskService,
                                 WorkspaceService workspaceService, AgentStepService stepService,
                                 AgentActionService actionService, FileToolService fileToolService,
                                 CommandExecutionRepository commandExecutionRepository,
                                 CommandToolService commandToolService, ValidationService validationService,
                                 LlmGateway llmGateway, ReportService reportService) {
        this.fixedExecutor = fixedExecutor;
        this.workflowService = workflowService;
        this.runService = runService;
        this.taskService = taskService;
        this.workspaceService = workspaceService;
        this.stepService = stepService;
        this.actionService = actionService;
        this.fileToolService = fileToolService;
        this.commandExecutionRepository = commandExecutionRepository;
        this.commandToolService = commandToolService;
        this.validationService = validationService;
        this.llmGateway = llmGateway;
        this.reportService = reportService;
    }

    @Override
    public void execute(UUID runId) {
        var run = runService.getRequired(runId);
        var workflow = workflowService.resolveForRun(run);
        if (Domain.WorkflowMode.REVIEW.name().equals(workflow.mode())) {
            executeReview(run, workflow);
            return;
        }
        if (Domain.WorkflowMode.TEST.name().equals(workflow.mode())) {
            executeTest(run, workflow);
            return;
        }
        fixedExecutor.execute(runId);
        backfillFromSteps(runService.getRequired(runId), workflow);
    }

    private void executeReview(AgentRun run, WorkflowDefinition workflow) {
        if (!Domain.AgentRunStatus.RUNNING.name().equals(run.status())) {
            return;
        }
        var task = taskService.getRequired(run.taskId());
        var workspace = workspaceService.getRequired(task.workspaceId());
        var step = stepService.start(task.id(), run.id(), null, Domain.StepType.INSPECT_WORKSPACE,
                "Review workspace files");
        var action = actionService.create(step.id(), Domain.ActionType.CALL_TOOL,
                "Read-only review workspace inspection", Domain.RiskLevel.LOW);
        var result = fileToolService.listFiles(new ToolExecutionContext(task.id(), run.id(), step.id(), action.id(),
                workspace), ".", 4);
        if (result.waitingApproval()) {
            stepService.markWaitingApproval(task.id(), run.id(), step, result.summary());
            recordSingle(workflow, task.id(), run.id(), "inspect_workspace",
                    Domain.WorkflowNodeType.WORKSPACE_INSPECTION, step, Domain.WorkflowNodeStatus.WAITING_APPROVAL,
                    result.summary());
            return;
        }
        if (result.blocked()) {
            stepService.fail(task.id(), run.id(), step, result.summary());
            recordSingle(workflow, task.id(), run.id(), "inspect_workspace",
                    Domain.WorkflowNodeType.WORKSPACE_INSPECTION, step, Domain.WorkflowNodeStatus.FAILURE,
                    result.summary());
            runService.fail(run.id(), task.id(), result.summary());
            return;
        }
        stepService.complete(task.id(), run.id(), step, result.summary());
        recordSingle(workflow, task.id(), run.id(), "inspect_workspace",
                Domain.WorkflowNodeType.WORKSPACE_INSPECTION, step, Domain.WorkflowNodeStatus.SUCCESS,
                result.summary());
        workflowService.recordEdge(task.id(), run.id(), workflow, "inspect_workspace", "report",
                Domain.WorkflowEdgeType.ON_SUCCESS, "review inspection completed", "Proceed to report", Map.of());
        reportService.generate(task, run.id(), "Review completed. " + result.summary());
        workflowService.recordNode(task.id(), run.id(), workflow, "report", Domain.WorkflowNodeType.REPORT,
                null, Domain.WorkflowNodeStatus.SUCCESS, "Generate review report", "Report generated", Map.of());
        workflowService.recordEdge(task.id(), run.id(), workflow, "report", "finish", Domain.WorkflowEdgeType.ON_SUCCESS,
                "report generated", "Finish review workflow", Map.of());
        workflowService.recordNode(task.id(), run.id(), workflow, "finish", Domain.WorkflowNodeType.FINISH,
                null, Domain.WorkflowNodeStatus.FINISHED, "Finish", "Review workflow finished", Map.of());
        runService.complete(run.id(), task.id());
    }

    private void executeTest(AgentRun run, WorkflowDefinition workflow) {
        if (!Domain.AgentRunStatus.RUNNING.name().equals(run.status())) {
            return;
        }
        var task = taskService.getRequired(run.taskId());
        var workspace = workspaceService.getRequired(task.workspaceId());
        var approvedCommand = commandExecutionRepository.findApprovedWaitingByRun(run.id());
        if (approvedCommand.isPresent()) {
            resumeApprovedTestCommand(workflow, task.id(), run.id(), workspace, approvedCommand.get());
            return;
        }
        var decision = llmGateway.suggestValidation(new ValidationContext(task.id(), run.id(), workspace.id(), java.util.List.of()));
        if (!decision.shouldValidate() || decision.executableAndArgs().isEmpty()) {
            workflowService.recordNode(task.id(), run.id(), workflow, "finish", Domain.WorkflowNodeType.FINISH,
                    null, Domain.WorkflowNodeStatus.FINISHED, "Finish", "No validation selected", Map.of());
            reportService.generate(task, run.id(), "Test workflow completed. No validation selected.");
            runService.complete(run.id(), task.id());
            return;
        }
        var step = stepService.start(task.id(), run.id(), null, Domain.StepType.VALIDATE, decision.reason());
        var action = actionService.create(step.id(), Domain.ActionType.RUN_VALIDATION, decision.reason(),
                Domain.RiskLevel.MEDIUM);
        var executable = decision.executableAndArgs().getFirst();
        var args = decision.executableAndArgs().subList(1, decision.executableAndArgs().size());
        var result = commandToolService.runCommand(new ToolExecutionContext(task.id(), run.id(), step.id(),
                action.id(), workspace), executable, args, ".", decision.reason());
        if (result.waitingApproval()) {
            stepService.markWaitingApproval(task.id(), run.id(), step, result.summary());
            recordSingle(workflow, task.id(), run.id(), "validate", Domain.WorkflowNodeType.VALIDATION,
                    step, Domain.WorkflowNodeStatus.WAITING_APPROVAL, result.summary());
            return;
        }
        var exitCode = integer(result.payload().get("exitCode"), 1);
        var commandId = result.payload().get("commandId") == null ? null
                : UUID.fromString(result.payload().get("commandId").toString());
        validationService.record(task.id(), run.id(), step.id(), commandId, Domain.ValidationType.TEST,
                exitCode == 0, result.summary());
        stepService.complete(task.id(), run.id(), step, result.summary());
        var status = exitCode == 0 ? Domain.WorkflowNodeStatus.SUCCESS : Domain.WorkflowNodeStatus.FAILURE;
        recordSingle(workflow, task.id(), run.id(), "validate", Domain.WorkflowNodeType.VALIDATION, step,
                status, result.summary());
        if (exitCode != 0) {
            reportService.generate(task, run.id(), "Test workflow validation failed: " + result.summary());
            runService.fail(run.id(), task.id(), "Validation failed: " + result.summary());
            return;
        }
        finishTestWorkflow(workflow, task.id(), run.id(), result.summary());
        reportService.generate(task, run.id(), "Test workflow validation passed: " + result.summary());
        runService.complete(run.id(), task.id());
    }

    private void resumeApprovedTestCommand(WorkflowDefinition workflow, UUID taskId, UUID runId,
                                           com.nask.agent.workspace.Workspace workspace, CommandExecution command) {
        var step = stepService.getRequired(command.stepId());
        var result = commandToolService.resumeApprovedCommand(new ToolExecutionContext(taskId, runId,
                command.stepId(), command.actionId(), workspace), command);
        if (result.blocked() || result.waitingApproval()) {
            stepService.fail(taskId, runId, step, result.summary());
            recordSingle(workflow, taskId, runId, "validate", Domain.WorkflowNodeType.VALIDATION, step,
                    Domain.WorkflowNodeStatus.FAILURE, result.summary());
            runService.fail(runId, taskId, result.summary());
            return;
        }
        var exitCode = integer(result.payload().get("exitCode"), 1);
        var commandId = result.payload().get("commandId") == null ? null
                : UUID.fromString(result.payload().get("commandId").toString());
        validationService.record(taskId, runId, step.id(), commandId, Domain.ValidationType.TEST,
                exitCode == 0, result.summary());
        stepService.complete(taskId, runId, step, result.summary());
        var status = exitCode == 0 ? Domain.WorkflowNodeStatus.SUCCESS : Domain.WorkflowNodeStatus.FAILURE;
        recordSingle(workflow, taskId, runId, "validate", Domain.WorkflowNodeType.VALIDATION, step, status,
                result.summary());
        var task = taskService.getRequired(taskId);
        if (exitCode != 0) {
            reportService.generate(task, runId, "Test workflow validation failed: " + result.summary());
            runService.fail(runId, taskId, "Validation failed: " + result.summary());
            return;
        }
        finishTestWorkflow(workflow, taskId, runId, result.summary());
        reportService.generate(task, runId, "Test workflow validation passed: " + result.summary());
        runService.complete(runId, taskId);
    }

    private void finishTestWorkflow(WorkflowDefinition workflow, UUID taskId, UUID runId, String validationSummary) {
        workflowService.recordEdge(taskId, runId, workflow, "validate", "report", Domain.WorkflowEdgeType.ON_SUCCESS,
                "validation passed", "Proceed to report", Map.of());
        workflowService.recordNode(taskId, runId, workflow, "report", Domain.WorkflowNodeType.REPORT,
                null, Domain.WorkflowNodeStatus.SUCCESS, "Generate test report",
                "Validation passed: " + validationSummary, Map.of());
        workflowService.recordEdge(taskId, runId, workflow, "report", "finish", Domain.WorkflowEdgeType.ON_SUCCESS,
                "report generated", "Finish test workflow", Map.of());
        workflowService.recordNode(taskId, runId, workflow, "finish", Domain.WorkflowNodeType.FINISH,
                null, Domain.WorkflowNodeStatus.FINISHED, "Finish", "Test workflow finished", Map.of());
    }

    private void backfillFromSteps(AgentRun run, WorkflowDefinition workflow) {
        var taskId = run.taskId();
        var task = taskService.getRequired(taskId);
        var existingStepIds = workflowService.nodes(run.id()).stream()
                .map(WorkflowNodeExecution::agentStepId)
                .collect(java.util.stream.Collectors.toSet());
        var steps = stepService.findByRun(run.id()).stream()
                .sorted(Comparator.comparing(AgentStep::startedAt))
                .filter(step -> !existingStepIds.contains(step.id()))
                .toList();
        String previous = null;
        for (var step : steps) {
            var nodeId = nodeId(step.stepType());
            workflowService.recordNode(taskId, run.id(), workflow, nodeId, nodeType(step.stepType()), step.id(),
                    nodeStatus(step.status()), step.inputSummary(), step.outputSummary(), Map.of("stepType", step.stepType()));
            if (previous != null) {
                workflowService.recordEdge(taskId, run.id(), workflow, previous, nodeId,
                        Domain.WorkflowEdgeType.ON_SUCCESS, "AgentStep order", "Backfilled from fixed loop", Map.of());
            }
            previous = nodeId;
        }
        var current = runService.getRequired(run.id());
        if (Domain.AgentRunStatus.COMPLETED.name().equals(current.status())) {
            workflowService.recordEdge(taskId, run.id(), workflow, previous == null ? "report" : previous, "finish",
                    Domain.WorkflowEdgeType.ON_SUCCESS, "run completed", "Finish workflow", Map.of());
            workflowService.recordNode(taskId, run.id(), workflow, "finish", Domain.WorkflowNodeType.FINISH,
                    null, Domain.WorkflowNodeStatus.FINISHED, "Finish", "Workflow finished", Map.of());
            reportService.generate(task, run.id(), "Task completed. Validation passed through workflow "
                    + workflow.name());
        }
        if (Domain.AgentRunStatus.FAILED.name().equals(current.status())) {
            workflowService.recordEdge(taskId, run.id(), workflow, previous == null ? "unknown" : previous, "fail",
                    Domain.WorkflowEdgeType.ON_FAILURE, "run failed", current.failureReason(), Map.of());
            workflowService.recordNode(taskId, run.id(), workflow, "fail", Domain.WorkflowNodeType.FAIL,
                    null, Domain.WorkflowNodeStatus.FAILURE, "Fail", current.failureReason(), Map.of());
            reportService.generate(task, run.id(), "Task failed through workflow " + workflow.name() + ": "
                    + current.failureReason());
        }
    }

    private void recordSingle(WorkflowDefinition workflow, UUID taskId, UUID runId, String nodeId,
                              Domain.WorkflowNodeType nodeType, AgentStep step, Domain.WorkflowNodeStatus status,
                              String summary) {
        workflowService.recordNode(taskId, runId, workflow, nodeId, nodeType, step.id(), status,
                step.inputSummary(), summary, Map.of());
    }

    private String nodeId(String stepType) {
        return switch (Domain.StepType.valueOf(stepType)) {
            case UNDERSTAND_TASK -> "understand_task";
            case INSPECT_WORKSPACE -> "inspect_workspace";
            case CREATE_PLAN -> "create_plan";
            case EXECUTE_PLAN_ITEM -> "execute_plan_item";
            case VALIDATE -> "validate";
            case FINISH -> "finish";
            case FAIL -> "fail";
            default -> stepType.toLowerCase(java.util.Locale.ROOT);
        };
    }

    private Domain.WorkflowNodeType nodeType(String stepType) {
        return switch (Domain.StepType.valueOf(stepType)) {
            case UNDERSTAND_TASK -> Domain.WorkflowNodeType.TASK_UNDERSTANDING;
            case INSPECT_WORKSPACE -> Domain.WorkflowNodeType.WORKSPACE_INSPECTION;
            case CREATE_PLAN -> Domain.WorkflowNodeType.PLAN_CREATION;
            case EXECUTE_PLAN_ITEM -> Domain.WorkflowNodeType.PLAN_ITEM_EXECUTION;
            case VALIDATE -> Domain.WorkflowNodeType.VALIDATION;
            case FINISH -> Domain.WorkflowNodeType.FINISH;
            case FAIL -> Domain.WorkflowNodeType.FAIL;
            default -> Domain.WorkflowNodeType.CONDITION;
        };
    }

    private Domain.WorkflowNodeStatus nodeStatus(String stepStatus) {
        return switch (Domain.StepStatus.valueOf(stepStatus)) {
            case COMPLETED -> Domain.WorkflowNodeStatus.SUCCESS;
            case WAITING_APPROVAL -> Domain.WorkflowNodeStatus.WAITING_APPROVAL;
            case WAITING_USER_INPUT -> Domain.WorkflowNodeStatus.WAITING_USER_INPUT;
            case FAILED -> Domain.WorkflowNodeStatus.FAILURE;
            default -> Domain.WorkflowNodeStatus.RUNNING;
        };
    }

    private int integer(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return value == null ? defaultValue : Integer.parseInt(value.toString());
    }
}
