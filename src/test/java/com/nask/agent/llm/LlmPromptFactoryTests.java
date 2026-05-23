package com.nask.agent.llm;

import org.junit.jupiter.api.Test;

import com.nask.agent.common.Domain;
import com.nask.agent.conversation.ConversationTaskContext;
import com.nask.agent.plan.PlanItem;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LlmPromptFactoryTests {
    private final LlmPromptFactory prompts = new LlmPromptFactory();

    @Test
    void agentWorkflowSelectionPromptDefinesSelectorRoleAndAgents() {
        var prompt = prompts.agentWorkflowSelection(new TaskContext(UUID.randomUUID(), UUID.randomUUID(),
                null, UUID.randomUUID(), "检查README是否最新版", List.of()));

        assertThat(prompt.version()).isEqualTo("agent-workflow-selection-v1");
        assertThat(prompt.system())
                .contains("agent type selector")
                .contains("choose exactly one agent/workflow");
        assertThat(prompt.user())
                .contains("\"agent\": \"coding-agent|review-agent|test-agent\"")
                .contains("coding-agent: Use for tasks that may create or modify files")
                .contains("review-agent: Use for read-only inspection")
                .contains("test-agent: Use for validation-only tasks")
                .contains("agent and workflow must be identical")
                .contains("检查README是否最新版");
    }

    @Test
    void includesRecoveryNotesInEarlyModelPrompts() {
        var note = "User answered recovery prompt `Runtime recovery needs guidance`: read README.md first";
        var understanding = new TaskUnderstanding("Create note", "CODE_EDIT", List.of(), List.of());

        var taskPrompt = prompts.taskUnderstanding(new TaskContext(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), "create note", List.of(note)));
        var planPrompt = prompts.planDraft(new PlanningContext(UUID.randomUUID(), UUID.randomUUID(),
                understanding, List.of("README.md"), List.of(note)));

        assertThat(taskPrompt.user()).contains(note);
        assertThat(planPrompt.user()).contains(note);
    }

    @Test
    void agentDecisionPromptUsesConcreteJsonExamplesForFileAndDirectoryActions() {
        var planItem = new PlanItem(UUID.randomUUID(), UUID.randomUUID(),
                "Create standard Maven directory layout", Domain.PlanItemStatus.PENDING.name(),
                List.of("src/main/java", "pom.xml"), "notes", 1, Instant.now(), Instant.now());

        var prompt = prompts.agentDecision(new ExecutionContext(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), planItem, List.of()));

        assertThat(prompt.user())
                .contains("Example JSON output for creating directories")
                .contains("actions array must contain no more than 5 actions")
                .contains("\"type\": \"CREATE_DIRECTORY\"")
                .contains("\"path\": \"src/main/java\"")
                .contains("Example JSON output for creating a file")
                .contains("\"type\": \"CREATE_FILE\"")
                .contains("\"content\":")
                .contains("Every CREATE_FILE action must include a non-empty content string")
                .contains("Do not use CREATE_FILE to create directories")
                .contains("reject outputs with more than 5 actions");
    }

    @Test
    void finalReportPromptIncludesToolObservations() {
        var prompt = prompts.finalReport(new ReportContext(UUID.randomUUID(), UUID.randomUUID(),
                "这个项目是做什么的", "Task completed.", List.of("execute_plan_item SUCCESS - Read README.md"),
                List.of(), List.of(), List.of(), List.of("read_file success=true payload={content=# Auditable Coding Agent}"),
                List.of("Project profile: Java; frameworks [Spring Boot]")));

        assertThat(prompt.user())
                .contains("Recent tool observations")
                .contains("Auditable Coding Agent")
                .contains("Project context")
                .contains("Spring Boot");
    }

    @Test
    void taskUnderstandingIncludesCompactPreviousReportForExplicitReferences() {
        var oldReport = "OLD_REPORT_DETAIL ".repeat(200);
        var previousTask = new ConversationTaskContext(UUID.randomUUID(),
                "previously update README and run validation", "COMPLETED", oldReport,
                List.of("README.md"), Instant.now());

        var prompt = prompts.taskUnderstanding(new TaskContext(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), "now inspect command policy", List.of(),
                List.of(previousTask)));

        assertThat(prompt.user())
                .contains("lightweight orientation")
                .contains("previously update README")
                .contains("Affected files: [README.md]")
                .contains("Report excerpt: OLD_REPORT_DETAIL")
                .contains("If the user")
                .doesNotContain(oldReport);
    }
}
