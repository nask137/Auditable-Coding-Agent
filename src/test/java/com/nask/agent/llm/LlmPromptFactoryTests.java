package com.nask.agent.llm;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LlmPromptFactoryTests {
    private final LlmPromptFactory prompts = new LlmPromptFactory();

    @Test
    void includesRecoveryNotesInEarlyModelPrompts() {
        var note = "User answered recovery prompt `Runtime recovery needs guidance`: read README.md first";
        var understanding = new TaskUnderstanding("Create note", "CODE_EDIT", List.of(), List.of());

        var taskPrompt = prompts.taskUnderstanding(new TaskContext(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), "create note", List.of(note)));
        var planPrompt = prompts.planDraft(new PlanningContext(UUID.randomUUID(), UUID.randomUUID(),
                understanding, List.of("README.md"), List.of(note)));
        var validationPrompt = prompts.validationDecision(new ValidationContext(UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), List.of(note)));

        assertThat(taskPrompt.user()).contains(note);
        assertThat(planPrompt.user()).contains(note);
        assertThat(validationPrompt.user()).contains(note);
    }
}
