package com.nask.agent.llm;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StructuredLlmOutputValidatorTests {
    private final StructuredLlmOutputValidator validator = new StructuredLlmOutputValidator(
            Validation.buildDefaultValidatorFactory().getValidator());

    @Test
    void acceptsSupportedToolIntent() {
        var decision = new AgentDecision(UUID.randomUUID(), List.of(
                new AgentDecision.Action("READ_FILE", "Inspect target file", Map.of("path", "src/App.java"))));

        assertThat(validator.validate(decision)).isSameAs(decision);
    }

    @Test
    void acceptsPatchAndGitToolIntent() {
        var decision = new AgentDecision(UUID.randomUUID(), List.of(
                new AgentDecision.Action("APPLY_PATCH", "Fix exact bug",
                        Map.of("path", "src/App.java", "oldText", "return false;", "newText", "return true;")),
                new AgentDecision.Action("GIT_STATUS", "Inspect changed files", Map.of("workingDirectory", ".")),
                new AgentDecision.Action("GIT_DIFF", "Inspect diff", Map.of("workingDirectory", "."))));

        assertThat(validator.validate(decision)).isSameAs(decision);
    }

    @Test
    void acceptsCreateDirectoryToolIntentWithoutContent() {
        var decision = new AgentDecision(UUID.randomUUID(), List.of(
                new AgentDecision.Action("CREATE_DIRECTORY", "Create Maven layout",
                        Map.of("path", "src/main/java"))));

        assertThat(validator.validate(decision)).isSameAs(decision);
    }

    @Test
    void acceptsRunCommandToolIntent() {
        var decision = new AgentDecision(UUID.randomUUID(), List.of(
                new AgentDecision.Action("RUN_COMMAND", "Run tests",
                        Map.of("executable", "mvn", "arguments", List.of("test"), "workingDirectory", "."))));

        assertThat(validator.validate(decision)).isSameAs(decision);
    }

    @Test
    void rejectsUnsupportedToolIntent() {
        var decision = new AgentDecision(UUID.randomUUID(), List.of(
                new AgentDecision.Action("DELETE_REPO", "Bypass runtime", Map.of())));

        assertThatThrownBy(() -> validator.validate(decision))
                .isInstanceOf(LlmGatewayException.class)
                .hasMessageContaining("Unsupported action type");
    }

    @Test
    void rejectsMissingRequiredActionInput() {
        var decision = new AgentDecision(UUID.randomUUID(), List.of(
                new AgentDecision.Action("CREATE_FILE", "Create file", Map.of("path", "note.md"))));

        assertThatThrownBy(() -> validator.validate(decision))
                .isInstanceOf(LlmGatewayException.class)
                .hasMessageContaining("content");
    }

    @Test
    void rejectsPatchWithoutExactOldText() {
        var decision = new AgentDecision(UUID.randomUUID(), List.of(
                new AgentDecision.Action("APPLY_PATCH", "Patch file",
                        Map.of("path", "src/App.java", "newText", "replacement"))));

        assertThatThrownBy(() -> validator.validate(decision))
                .isInstanceOf(LlmGatewayException.class)
                .hasMessageContaining("oldText");
    }

    @Test
    void rejectsVirtualTaskReportPathAsToolInput() {
        var decision = new AgentDecision(UUID.randomUUID(), List.of(
                new AgentDecision.Action("READ_FILE", "Read historical report",
                        Map.of("path", "task-reports/task/report.md"))));

        assertThatThrownBy(() -> validator.validate(decision))
                .isInstanceOf(LlmGatewayException.class)
                .hasMessageContaining("virtual task report path");
    }

    @Test
    void rejectsAbsolutePathAsToolInput() {
        var decision = new AgentDecision(UUID.randomUUID(), List.of(
                new AgentDecision.Action("READ_FILE", "Read absolute path",
                        Map.of("path", "D:/workspace/Agent Test/src/main/java/App.java"))));

        assertThatThrownBy(() -> validator.validate(decision))
                .isInstanceOf(LlmGatewayException.class)
                .hasMessageContaining("workspace-relative");
    }

    @Test
    void rejectsVirtualTaskReportPathInPlanRelatedFiles() {
        var plan = new PlanDraft(List.of(new PlanDraft.Item("Read old report",
                List.of("task-reports/task/report.md"), "bad source path")));

        assertThatThrownBy(() -> validator.validate(plan))
                .isInstanceOf(LlmGatewayException.class)
                .hasMessageContaining("virtual task report path");
    }

    @Test
    void rejectsInvalidTaskType() {
        var understanding = new TaskUnderstanding("summary", "DELETE_REPO", List.of(), List.of());

        assertThatThrownBy(() -> validator.validate(understanding))
                .isInstanceOf(LlmGatewayException.class)
                .hasMessageContaining("Unsupported task type");
    }
}
