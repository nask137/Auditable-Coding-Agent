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

                Runtime recovery notes:
                %s
                """.formatted(context.userRequest(), context.recoveryNotes()));
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
                Return json with this exact shape:
                {
                  "planItemId": "%s",
                  "actions": [
                    {
                      "type": "LIST_FILES|READ_FILE|SEARCH_TEXT|CREATE_FILE|APPLY_PATCH|GIT_STATUS|GIT_DIFF",
                      "reason": "why this tool intent is needed",
                      "input": {}
                    }
                  ]
                }
                Allowed input shapes:
                LIST_FILES: {"path": ".", "maxDepth": 4}
                READ_FILE: {"path": "relative/path"}
                SEARCH_TEXT: {"query": "text"}
                CREATE_FILE: {"path": "relative/path", "content": "full file content"}
                APPLY_PATCH: {"path": "relative/path", "oldText": "exact existing text", "newText": "replacement text, may be empty"}
                GIT_STATUS: {"workingDirectory": "."}
                GIT_DIFF: {"workingDirectory": "."}
                Return an empty actions array if no action is needed. The Runtime will reject unsupported types.
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
                """.formatted(context.currentItem().id(), context.currentItem(), context.observedFiles(),
                context.recentToolResults(), context.recoveryNotes(), memorySummary(context.memoryContext())));
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
        return new LlmPrompt("validation-decision-v1", SHARED_SYSTEM, """
                Return json with this exact shape:
                {
                  "shouldValidate": true,
                  "executableAndArgs": ["mvn", "test"],
                  "reason": "why this validation is appropriate"
                }
                If no safe validation command is obvious, set shouldValidate to false and executableAndArgs to [].
                The Runtime command policy and approval flow decide whether the command may run.

                Task id: %s
                Run id: %s
                Workspace id: %s

                Runtime recovery notes:
                %s

                Project memory context:
                %s
                """.formatted(context.taskId(), context.runId(), context.workspaceId(), context.recoveryNotes(),
                memorySummary(context.memoryContext())));
    }

    public LlmPrompt finalReport(ReportContext context) {
        return new LlmPrompt("final-report-v1", SHARED_SYSTEM, """
                Return json with this exact shape:
                {
                  "markdown": "# Agent Run Report\\n\\n..."
                }
                Keep the report concise. Do not invent tool results.

                User request:
                %s

                Runtime result summary:
                %s
                """.formatted(context.taskSummary(), context.resultSummary()));
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
}
