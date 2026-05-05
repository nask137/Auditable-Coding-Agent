package com.nask.agent.workflow;

import com.nask.agent.action.AgentActionService;
import com.nask.agent.command.CommandExecutionRepository;
import com.nask.agent.command.CommandToolService;
import com.nask.agent.common.AgentSettings;
import com.nask.agent.common.Domain;
import com.nask.agent.file.FileChangeRepository;
import com.nask.agent.file.FileToolService;
import com.nask.agent.git.GitToolService;
import com.nask.agent.llm.LlmGateway;
import com.nask.agent.memory.ProjectContextRetriever;
import com.nask.agent.memory.ProjectMemoryService;
import com.nask.agent.memory.MemoryWriteProposalService;
import com.nask.agent.plan.PlanService;
import com.nask.agent.report.ReportService;
import com.nask.agent.run.AgentRun;
import com.nask.agent.run.AgentRunService;
import com.nask.agent.runtime.FailureClassifier;
import com.nask.agent.runtime.RuntimeFailureService;
import com.nask.agent.runtime.UserInputRequestService;
import com.nask.agent.tool.ToolRecordRepository;
import com.nask.agent.validation.ValidationService;
import com.nask.agent.workspace.Workspace;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowAgentExecutorTests {
    private final WorkflowService workflowService = mock(WorkflowService.class);
    private final AgentRunService runService = mock(AgentRunService.class);
    private final AgentStateAssembler stateAssembler = mock(AgentStateAssembler.class);
    private final ConditionEvaluator conditionEvaluator = mock(ConditionEvaluator.class);
    private final ReportService reportService = mock(ReportService.class);

    @Test
    void failsRunWhenReportNodeGenerationThrows() {
        var ids = ids();
        var run = run(ids);
        var workflow = workflow(ids);
        var state = state(ids, run, workflow);
        var nodeExecutor = reportNodeExecutor();
        var nodeRegistry = mock(WorkflowNodeRegistry.class);
        var executor = new WorkflowAgentExecutor(workflowService, runService, stateAssembler, nodeRegistry,
                conditionEvaluator, reportService);
        when(runService.getRequired(ids.runId())).thenReturn(run);
        when(workflowService.resolveForRun(run)).thenReturn(workflow);
        when(workflowService.nodes(ids.runId())).thenReturn(List.of());
        when(stateAssembler.assemble(ids.runId())).thenReturn(state);
        when(nodeRegistry.get(Domain.WorkflowNodeType.REPORT.name())).thenReturn(nodeExecutor);
        doThrow(new RuntimeException("final report failed")).when(reportService).generate(any(), eq(ids.runId()),
                any());

        assertThatNoException().isThrownBy(() -> executor.execute(ids.runId()));

        verify(runService).fail(ids.runId(), ids.taskId(),
                "Workflow execution failed: final report failed");
    }

    private AgentWorkflowNodeExecutor reportNodeExecutor() {
        return new AgentWorkflowNodeExecutor(mock(com.nask.agent.step.AgentStepService.class),
                mock(AgentActionService.class), mock(PlanService.class), mock(LlmGateway.class),
                mock(FileToolService.class), mock(GitToolService.class), reportService,
                mock(CommandToolService.class), mock(ValidationService.class),
                new AgentSettings(10, 20, 1000, 300, 3, 2, 2, 3, 120, 200000),
                mock(FileChangeRepository.class), mock(CommandExecutionRepository.class),
                mock(ToolRecordRepository.class), mock(RuntimeFailureService.class), new FailureClassifier(),
                runService, mock(UserInputRequestService.class), mock(ProjectMemoryService.class),
                mock(ProjectContextRetriever.class), mock(MemoryWriteProposalService.class));
    }

    private WorkflowDefinition workflow(Ids ids) {
        var now = Instant.now();
        return new WorkflowDefinition(ids.workflowId(), "coding-agent", 1, "Coding",
                Domain.WorkflowMode.CODING.name(), true, Map.of(
                "start", "report",
                "limits", Map.of("maxNodes", 3),
                "nodes", List.of(Map.of("id", "report", "type", Domain.WorkflowNodeType.REPORT.name(),
                        "input", Map.of())),
                "edges", List.of()), now, now);
    }

    private AgentRun run(Ids ids) {
        return new AgentRun(ids.runId(), ids.taskId(), "CODE_EDIT", Domain.AgentRunStatus.RUNNING.name(),
                Instant.now(), null, null, Map.of("workflow", "coding-agent"));
    }

    private AgentState state(Ids ids, AgentRun run, WorkflowDefinition workflow) {
        var now = Instant.now();
        var task = new com.nask.agent.task.CodingTask(ids.taskId(), ids.workspaceId(), "task", "request",
                Domain.TaskStatus.RUNNING.name(), now, now);
        var workspace = new Workspace(ids.workspaceId(), "workspace", "D:/tmp/workspace", true, List.of(), List.of(),
                List.of(), now, now);
        return new AgentState(task, run, workspace, workflow, null, null, List.of(), List.of(), List.of(),
                null, List.of(), List.of(), null, Map.of());
    }

    private Ids ids() {
        return new Ids(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }

    private record Ids(UUID workspaceId, UUID taskId, UUID runId, UUID workflowId) {
    }
}
