package com.nask.agent.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nask.agent.audit.AuditEventDraft;
import com.nask.agent.audit.AuditService;
import com.nask.agent.plan.PlanItem;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HttpLlmGatewayTests {
    private final StructuredLlmOutputValidator validator = new StructuredLlmOutputValidator(
            jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator());

    @Test
    void parsesAndValidatesAgentWorkflowSelection() {
        var auditService = mock(AuditService.class);
        when(auditService.append(any())).thenReturn(UUID.randomUUID());
        ChatCompletionClient client = prompt -> new ChatCompletionResult("deepseek-v4-pro",
                "{\"agent\":\"review-agent\",\"workflow\":\"review-agent\",\"rationale\":\"Read-only inspection\"}",
                "stop", 1, 2, 3);
        var gateway = new HttpLlmGateway(new LlmPromptFactory(), client, new ObjectMapper(), validator, auditService);

        var selection = gateway.selectAgentWorkflow(new TaskContext(UUID.randomUUID(), UUID.randomUUID(),
                null, UUID.randomUUID(), "检查README是否最新版", List.of()));

        assertThat(selection.agent()).isEqualTo("review-agent");
        assertThat(selection.workflow()).isEqualTo("review-agent");
    }

    @Test
    void parsesAndValidatesStructuredDecision() {
        var auditService = mock(AuditService.class);
        when(auditService.append(any())).thenReturn(UUID.randomUUID());
        ChatCompletionClient client = prompt -> new ChatCompletionResult("deepseek-v4-pro",
                "{\"planItemId\":\"00000000-0000-0000-0000-000000000001\",\"actions\":[{\"type\":\"SEARCH_TEXT\",\"reason\":\"Find usage\",\"input\":{\"query\":\"LlmGateway\"}}]}",
                "stop", 1, 2, 3);
        var gateway = new HttpLlmGateway(new LlmPromptFactory(), client, new ObjectMapper(), validator, auditService);
        var item = new PlanItem(UUID.fromString("00000000-0000-0000-0000-000000000001"), UUID.randomUUID(),
                "Search code", "PENDING", List.of(), "notes", 1, Instant.now(), Instant.now());

        var decision = gateway.decideNextAction(new ExecutionContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                item, List.of()));

        assertThat(decision.actions()).hasSize(1);
        assertThat(decision.actions().getFirst().type()).isEqualTo("SEARCH_TEXT");
        verify(auditService, times(2)).append(any());
    }

    @Test
    void acceptsRunCommandStructuredDecisionBeforeRuntimeToolExecution() {
        var auditService = mock(AuditService.class);
        when(auditService.append(any())).thenReturn(UUID.randomUUID());
        ChatCompletionClient client = prompt -> new ChatCompletionResult("deepseek-v4-pro",
                "{\"planItemId\":\"00000000-0000-0000-0000-000000000001\",\"actions\":[{\"type\":\"RUN_COMMAND\",\"reason\":\"Run tests\",\"input\":{\"executable\":\"mvn\",\"arguments\":[\"test\"],\"workingDirectory\":\".\"}}]}",
                "stop", 1, 2, 3);
        var gateway = new HttpLlmGateway(new LlmPromptFactory(), client, new ObjectMapper(), validator, auditService);
        var item = new PlanItem(UUID.fromString("00000000-0000-0000-0000-000000000001"), UUID.randomUUID(),
                "Run tests", "PENDING", List.of(), "notes", 1, Instant.now(), Instant.now());

        var decision = gateway.decideNextAction(new ExecutionContext(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), item, List.of()));

        assertThat(decision.actions()).hasSize(1);
        assertThat(decision.actions().getFirst().type()).isEqualTo("RUN_COMMAND");
    }

    @Test
    void wrapsRawClientFailuresForRuntimeRecovery() {
        var auditService = mock(AuditService.class);
        when(auditService.append(any())).thenReturn(UUID.randomUUID());
        ChatCompletionClient client = prompt -> {
            throw new IllegalStateException("connection reset");
        };
        var gateway = new HttpLlmGateway(new LlmPromptFactory(), client, new ObjectMapper(), validator, auditService);

        assertThatThrownBy(() -> gateway.understandTask(new TaskContext(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), "create note", List.of())))
                .isInstanceOf(LlmGatewayException.class)
                .hasMessageContaining("Model call failed")
                .extracting(error -> ((LlmGatewayException) error).failureType())
                .isEqualTo(com.nask.agent.common.Domain.RuntimeFailureType.MODEL_CALL_FAILED);
    }

    @Test
    void auditsTaskUnderstandingWithRunAndStepCorrelation() {
        var auditService = mock(AuditService.class);
        when(auditService.append(any())).thenReturn(UUID.randomUUID());
        ChatCompletionClient client = prompt -> new ChatCompletionResult("deepseek-v4-pro",
                "{\"summary\":\"Create note\",\"taskType\":\"CODE_EDIT\",\"constraints\":[],\"initialSearchHints\":[]}",
                "stop", 1, 2, 3);
        var gateway = new HttpLlmGateway(new LlmPromptFactory(), client, new ObjectMapper(), validator, auditService);
        var taskId = UUID.randomUUID();
        var runId = UUID.randomUUID();
        var stepId = UUID.randomUUID();

        gateway.understandTask(new TaskContext(taskId, runId, stepId, UUID.randomUUID(), "create note", List.of()));

        var captor = forClass(AuditEventDraft.class);
        verify(auditService, times(2)).append(captor.capture());
        assertThat(captor.getAllValues())
                .allSatisfy(event -> {
                    assertThat(event.taskId()).isEqualTo(taskId);
                    assertThat(event.runId()).isEqualTo(runId);
                    assertThat(event.stepId()).isEqualTo(stepId);
                });
    }
}
