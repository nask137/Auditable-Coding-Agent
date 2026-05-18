package com.nask.agent.llm;

import org.springframework.stereotype.Component;

/**
 * Builds versioned prompts that require JSON-only model output.
 */
@Component
public class LlmPromptFactory {
    private static final String SHARED_SYSTEM = """
            You are the planning brain for an auditable local coding agent runtime.
            You must output only valid json. Do not include markdown fences.
            You never execute tools directly. You only propose structured intent.
            The Runtime enforces workspace boundaries, command policy, approvals, and audit logging.
            Prefer small, reversible actions and avoid unrelated refactors.
            """;

    public LlmPrompt taskUnderstanding(TaskContext context) {
        return new LlmPrompt("task-understanding-v1", SHARED_SYSTEM, """
                Return json with this exact shape:
                {
                  "summary": "short task summary",
                  "taskType": "BUG_FIX|TEST|CODE_EDIT|REVIEW|OTHER",
                  "constraints": ["runtime or user constraints"],
                  "initialSearchHints": ["short search hints"]
                }

                User request:
                %s

                Previous tasks in this conversation. Use these only for lightweight orientation; do not treat
                earlier task goals or assumptions as current requirements unless the user explicitly refers to them:
                %s

                Runtime recovery notes:
                %s
                """.formatted(context.userRequest(), conversationHistory(context), context.recoveryNotes()));
    }

    public LlmPrompt planDraft(PlanningContext context) {
        return new LlmPrompt("plan-draft-v1", SHARED_SYSTEM, """
                Return json with this exact shape:
                {
                  "items": [
                    {
                      "description": "one small auditable step",
                      "relatedFiles": ["relative/path"],
                      "notes": "why this step is needed"
                    }
                  ]
                }
                Create 2 to 6 small plan items. Do not include actions that bypass Runtime approval.

                Task understanding:
                %s

                Observed workspace files:
                %s

                Runtime recovery notes:
                %s

                Project memory context:
                %s
                """.formatted(context.understanding(), context.observedFiles(), context.recoveryNotes(),
                memorySummary(context.memoryContext())));
    }

    public LlmPrompt agentDecision(ExecutionContext context) {
        return new LlmPrompt("agent-decision-v1", SHARED_SYSTEM, """
                Return one JSON object with this exact top-level shape:
                {
                  "planItemId": "%s",
                  "actions": [
                    {
                      "type": "CREATE_FILE",
                      "reason": "why this tool intent is needed",
                      "input": {
                        "path": "relative/path",
                        "content": "complete file content when type is CREATE_FILE"
                      }
                    }
                  ]
                }

                The action type must be exactly one of:
                LIST_FILES, READ_FILE, SEARCH_TEXT, CREATE_DIRECTORY, CREATE_FILE, APPLY_PATCH, GIT_STATUS, GIT_DIFF.

                Required input object by action type:
                LIST_FILES: {"path": ".", "maxDepth": 4}
                READ_FILE: {"path": "relative/path"}
                SEARCH_TEXT: {"query": "text"}
                CREATE_DIRECTORY: {"path": "relative/path"}
                CREATE_FILE: {"path": "relative/path", "content": "full file content"}
                APPLY_PATCH: {"path": "relative/path", "oldText": "exact existing text", "newText": "replacement text, may be empty"}
                GIT_STATUS: {"workingDirectory": "."}
                GIT_DIFF: {"workingDirectory": "."}

                Example JSON output for creating directories:
                {
                  "planItemId": "%s",
                  "actions": [
                    {
                      "type": "CREATE_DIRECTORY",
                      "reason": "Create the Maven source directory",
                      "input": {
                        "path": "src/main/java"
                      }
                    }
                  ]
                }

                Example JSON output for creating a file:
                {
                  "planItemId": "%s",
                  "actions": [
                    {
                      "type": "CREATE_FILE",
                      "reason": "Create a minimal Maven pom.xml",
                      "input": {
                        "path": "pom.xml",
                        "content": "<project xmlns=\\"http://maven.apache.org/POM/4.0.0\\">\\n  <modelVersion>4.0.0</modelVersion>\\n</project>\\n"
                      }
                    }
                  ]
                }

                Return an empty actions array if no action is needed. The Runtime will reject unsupported types.
                Do not use CREATE_FILE to create directories. Use CREATE_DIRECTORY for directory paths.
                Every CREATE_FILE action must include a non-empty content string.
                Use READ_FILE before APPLY_PATCH unless recent tool results already include the exact target content.

                Current plan item:
                %s

                Observed workspace files:
                %s

                Recent tool results:
                %s

                Runtime recovery notes:
                %s

                Project memory context:
                %s
                """.formatted(context.currentItem().id(), context.currentItem().id(), context.currentItem().id(),
                context.currentItem(), context.observedFiles(), context.recentToolResults(), context.recoveryNotes(),
                memorySummary(context.memoryContext())));
    }

    public LlmPrompt replan(ExecutionContext context, String failureSummary) {
        return new LlmPrompt("replan-after-failure-v1", SHARED_SYSTEM, """
                Return json with this exact shape:
                {
                  "items": [
                    {
                      "description": "one small recovery step",
                      "relatedFiles": ["relative/path"],
                      "notes": "why this recovery step addresses the runtime rejection"
                    }
                  ]
                }
                Create 1 to 3 small recovery plan items. Do not repeat a rejected action.
                Prefer reading current file contents before patching when the failure involved paths or patches.

                Current failed plan item:
                %s

                Runtime rejection or validation failure:
                %s

                Observed workspace files:
                %s

                Recent tool results:
                %s

                Runtime recovery notes:
                %s

                Project memory context:
                %s
                """.formatted(context.currentItem(), failureSummary, context.observedFiles(),
                context.recentToolResults(), context.recoveryNotes(), memorySummary(context.memoryContext())));
    }

    public LlmPrompt validationDecision(ValidationContext context) {
        return new LlmPrompt("validation-decision-v2", SHARED_SYSTEM, """
                Return json with this exact shape:
                {
                  "shouldValidate": false,
                  "executableAndArgs": [],
                  "reason": "why validation should be skipped or which minimal validation is appropriate"
                }
                Validation is risk-based, not mandatory. Set shouldValidate to false when no files changed in
                this run, unless the user explicitly asked to run tests or validation.
                Set shouldValidate to false for read-only review, search, explanation, planning, git status, or
                diff-only work.
                Set shouldValidate to true when source, test, build, configuration, migration, or runtime files
                were created or modified, or when the task type is TEST.
                Prefer the narrowest safe command that matches the changed files and project memory. Use the full
                project test suite only when it is the appropriate minimal validation for the change.
                The Runtime command policy and approval flow decide whether the command may run.

                Task id: %s
                Run id: %s
                Workspace id: %s
                Task type: %s
                User request:
                %s

                Files changed in this run:
                %s

                Recent commands in this run:
                %s

                Runtime recovery notes:
                %s

                Project memory context:
                %s
                """.formatted(context.taskId(), context.runId(), context.workspaceId(), context.taskType(),
                context.userRequest(), context.changedFiles(), context.recentCommands(), context.recoveryNotes(),
                memorySummary(context.memoryContext())));
    }

    public LlmPrompt finalReport(ReportContext context) {
        return new LlmPrompt("final-report-v1", SHARED_SYSTEM, """
                Return json with this exact shape:
                {
                  "markdown": "# Agent Run Report\\n\\n..."
                }
                Keep the report concise. Do not invent tool results.
                Summarize the actual outcome for the terminal user. If the user asked about earlier context,
                answer from the previous conversation prompts below.

                User request:
                %s

                Runtime result summary:
                %s

                Workflow outputs:
                %s

                Recent tool observations:
                %s

                Project context:
                %s

                Changed files:
                %s

                Previous prompts in this conversation, newest first:
                %s
                """.formatted(context.taskSummary(), context.resultSummary(), context.workflowSummaries(),
                context.recentToolObservations(), context.projectContext(), context.changedFiles(),
                context.previousConversationPrompts()));
    }

    private String memorySummary(com.nask.agent.memory.MemoryContext context) {
        if (context == null) {
            return "No project memory context retrieved.";
        }
        var profile = context.profile() == null ? "No project profile." : context.profile().languageSummary();
        var results = context.results().stream()
                .limit(8)
                .map(result -> "- %s %.1f %s %s".formatted(result.resultType(), result.score(),
                        result.title(), result.source()))
                .toList();
        return "Retrieval %s: %s%nProfile: %s%nResults:%n%s".formatted(
                context.retrievalId(), context.summary(), profile, String.join("\n", results));
    }

    private String conversationHistory(TaskContext context) {
        if (context.previousTasks() == null || context.previousTasks().isEmpty()) {
            return "No earlier tasks in this conversation.";
        }
        return context.previousTasks().stream()
                .map(task -> """
                        - Task %s [%s]
                          Prompt: %s
                          Affected files: %s
                        """.formatted(task.taskId(), task.status(), compact(task.prompt(), 300), task.affectedFiles()))
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    private String compact(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "(none)";
        }
        var normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength - 3) + "...";
    }
}
