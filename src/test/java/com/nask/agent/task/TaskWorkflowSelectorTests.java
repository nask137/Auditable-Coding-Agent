package com.nask.agent.task;

import com.nask.agent.llm.LlmGateway;
import com.nask.agent.llm.TaskContext;
import com.nask.agent.llm.AgentWorkflowSelection;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TaskWorkflowSelectorTests {
    @Test
    void selectsReviewWorkflowFromModelSelection() {
        var llm = mock(LlmGateway.class);
        when(llm.selectAgentWorkflow(any(TaskContext.class)))
                .thenReturn(new AgentWorkflowSelection("review-agent", "review-agent", "Read-only inspection"));
        var selector = new TaskWorkflowSelector(llm);

        assertThat(selector.selectWorkflow(task("检查README是否最新版"), null))
                .isEqualTo("review-agent");
    }

    @Test
    void selectsTestWorkflowFromModelSelection() {
        var llm = mock(LlmGateway.class);
        when(llm.selectAgentWorkflow(any(TaskContext.class)))
                .thenReturn(new AgentWorkflowSelection("test-agent", "test-agent", "Validation only"));
        var selector = new TaskWorkflowSelector(llm);

        assertThat(selector.selectWorkflow(task("验证项目"), null))
                .isEqualTo("test-agent");
    }

    @Test
    void preservesExplicitNonDefaultWorkflow() {
        var llm = mock(LlmGateway.class);
        var selector = new TaskWorkflowSelector(llm);

        assertThat(selector.selectWorkflow(task("验证项目"), "review-agent"))
                .isEqualTo("review-agent");
        verifyNoInteractions(llm);
    }

    @Test
    void preservesExplicitCodingWorkflow() {
        var llm = mock(LlmGateway.class);
        var selector = new TaskWorkflowSelector(llm);

        assertThat(selector.selectWorkflow(task("验证项目"), "coding-agent"))
                .isEqualTo("coding-agent");
        verifyNoInteractions(llm);
    }

    @Test
    void propagatesModelSelectionFailuresInsteadOfUsingDefaultAgent() {
        var llm = mock(LlmGateway.class);
        when(llm.selectAgentWorkflow(any(TaskContext.class))).thenThrow(new IllegalStateException("model unavailable"));
        var selector = new TaskWorkflowSelector(llm);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> selector.selectWorkflow(task("检查README"), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("model unavailable");
    }

    private CodingTask task(String request) {
        var now = Instant.parse("2026-05-23T00:00:00Z");
        return new CodingTask(UUID.randomUUID(), UUID.randomUUID(), null, 1, "task", request,
                "CREATED", null, null, null, null, Map.of(), now, now);
    }
}
