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
    void rejectsUnsupportedToolIntent() {
        var decision = new AgentDecision(UUID.randomUUID(), List.of(
                new AgentDecision.Action("RUN_COMMAND", "Bypass runtime", Map.of("executable", "mvn"))));

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
    void rejectsInvalidTaskType() {
        var understanding = new TaskUnderstanding("summary", "DELETE_REPO", List.of(), List.of());

        assertThatThrownBy(() -> validator.validate(understanding))
                .isInstanceOf(LlmGatewayException.class)
                .hasMessageContaining("Unsupported task type");
    }
}
