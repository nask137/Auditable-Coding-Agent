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

    public LlmPrompt agentWorkflowSelection(TaskContext context) {
        return new LlmPrompt("agent-workflow-selection-v1", """
                You are an agent type selector for an auditable local coding agent runtime.
                You must output only valid json. Do not include markdown fences.
                Your only job is to choose exactly one agent/workflow for the user's next task.
                You do not plan implementation steps and you do not execute tools.
                """, """
                Return json with this exact shape:
                {
                  "agent": "coding-agent|review-agent|test-agent",
                  "workflow": "coding-agent|review-agent|test-agent",
                  "rationale": "brief reason for the selection"
                }

                Available agents:
                - coding-agent: Use for tasks that may create or modify files, fix bugs, implement features,
                  refactor code, update documentation, or otherwise change the workspace. It may read,
                  plan, edit, and validate through the audited Runtime.
                - review-agent: Use for read-only inspection, explanation, summarization, code review,
                  architecture analysis, README freshness checks, and questions where the user asks for
                  findings or guidance without requesting changes. It must not require file changes.
                - test-agent: Use for validation-only tasks such as running tests, compiling, checking build
                  health, or reporting test results. It should not modify source files.

                Selection rules:
                - Choose coding-agent only when the prompt asks for a change or the task cannot be completed
                  correctly without changing workspace files.
                - Choose review-agent when the prompt asks to inspect, explain, compare, audit, or report.
                - Choose test-agent when the prompt asks only to run or verify tests/builds.
                - If a prompt is ambiguous, choose the least-privileged agent that can satisfy the request.
                - agent and workflow must be identical and must be one of the three names above.

                User request:
                %s

                Previous tasks in this conversation. Use these only for lightweight orientation. If the user
                explicitly refers to prior output using words like above, previous, last, 上述, 上面, 之前, or 建议,
                use the prior report to resolve the reference. Otherwise, do not treat earlier task goals or
                assumptions as current requirements:
                %s
                """.formatted(context.userRequest(), conversationHistory(context)));
    }

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

                Previous tasks in this conversation. Use these only for lightweight orientation. If the user
                explicitly refers to prior output using words like above, previous, last, 上述, 上面, 之前, or 建议,
                use the prior report to resolve the reference. Otherwise, do not treat earlier task goals or
                assumptions as current requirements:
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
                relatedFiles must contain only real workspace-relative source/config/test paths from the observed
                workspace or code symbols. Do not put task-reports paths in relatedFiles; those are historical
                memory references, not editable workspace files.

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
                The actions array must contain no more than 5 actions. Prefer fewer actions; return an empty
                actions array when the current plan item can be completed from recent tool results or project context.

                The action type must be exactly one of:
                LIST_FILES, READ_FILE, SEARCH_TEXT, CREATE_DIRECTORY, CREATE_FILE, APPLY_PATCH,
                GIT_STATUS, GIT_DIFF, GIT_ADD, GIT_COMMIT, GIT_PUSH, GIT_PULL, GIT_FETCH,
                GIT_LOG, GIT_SHOW, GIT_BRANCH, GIT_CHECKOUT, RUN_COMMAND.

                Required input object by action type:
                LIST_FILES: {"path": ".", "maxDepth": 4}
                READ_FILE: {"path": "relative/path"}
                SEARCH_TEXT: {"query": "text"}
                CREATE_DIRECTORY: {"path": "relative/path"}
                CREATE_FILE: {"path": "relative/path", "content": "full file content"}
                APPLY_PATCH: {"path": "relative/path", "oldText": "exact existing text", "newText": "replacement text, may be empty"}
                GIT_STATUS: {"workingDirectory": "."}
                GIT_DIFF: {"workingDirectory": "."}
                GIT_ADD: {"workingDirectory": ".", "paths": ["relative/path"]}
                GIT_COMMIT: {"workingDirectory": ".", "message": "concise commit message"}
                GIT_PUSH: {"workingDirectory": ".", "remote": "origin", "branch": "current-branch-or-empty"}
                GIT_PULL: {"workingDirectory": ".", "remote": "origin", "branch": "current-branch-or-empty"}
                GIT_FETCH: {"workingDirectory": ".", "remote": "origin"}
                GIT_LOG: {"workingDirectory": ".", "maxCount": 5}
                GIT_SHOW: {"workingDirectory": ".", "revision": "HEAD"}
                GIT_BRANCH: {"workingDirectory": "."}
                GIT_CHECKOUT: {"workingDirectory": ".", "ref": "branch-or-revision"}
                RUN_COMMAND: {"executable": "mvn", "arguments": ["test"], "workingDirectory": "."}

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

                Return an empty actions array if no action is needed. The Runtime will reject unsupported types
                and will reject outputs with more than 5 actions.
                Do not use CREATE_FILE to create directories. Use CREATE_DIRECTORY for directory paths.
                Every CREATE_FILE action must include a non-empty content string.
                Use READ_FILE before APPLY_PATCH unless recent tool results already include the exact target content.
                Use RUN_COMMAND for build, test, compile, formatting, code generation, diagnostics, and project scripts.
                Tool path inputs must be real workspace-relative paths. Never use task-reports paths or absolute
                filesystem paths from project memory as tool inputs.
                Command execution is always mediated by Runtime command policy and approval. Do not use shell
                metacharacters; pass arguments as an array.

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
                relatedFiles must contain only real workspace-relative files. Do not use task-reports paths;
                those are historical memory references, not editable workspace files.

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

    public LlmPrompt finalReport(ReportContext context) {
        return new LlmPrompt("final-report-v1", SHARED_SYSTEM, """
                Return json with this exact shape:
                {
                  "markdown": "# Result\\n\\n..."
                }
                Write for the terminal user, not for an audit log. Give the direct answer or conclusion first.
                Use the workflow summaries, project context, changed files, and git status as evidence, but do not
                dump raw file lists, raw git status, or node-by-node workflow logs unless they are necessary to answer
                the user's request. Do not invent tool results. If the user asked about earlier context, answer from
                the previous conversation prompts below.

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

                Git working tree status:
                %s

                Previous prompts in this conversation, newest first:
                %s
                """.formatted(context.taskSummary(), context.resultSummary(), context.workflowSummaries(),
                context.recentToolObservations(), context.projectContext(), context.changedFiles(),
                context.gitStatusLines(), context.previousConversationPrompts()));
    }

    private String memorySummary(com.nask.agent.memory.MemoryContext context) {
        if (context == null) {
            return "No project memory context retrieved.";
        }
        var profile = context.profile() == null ? "No project profile." : context.profile().languageSummary();
        var results = context.results().stream()
                .limit(8)
                .map(result -> "- %s %.1f %s source=%s snippet=%s".formatted(result.resultType(), result.score(),
                        result.title(), sourceSummary(result), compact(result.snippet(), 180)))
                .toList();
        return "Retrieval %s: %s%nProfile: %s%nResults:%n%s".formatted(
                context.retrievalId(), context.summary(), profile, String.join("\n", results));
    }

    private String sourceSummary(com.nask.agent.memory.MemorySearchResult result) {
        var source = result.source();
        var documentType = result.metadata() == null ? null : result.metadata().get("documentType");
        if ("TASK_REPORT".equals(documentType)) {
            return "historical task report " + source.sourceId() + " (not a workspace file path)";
        }
        return source.path() == null || source.path().isBlank() ? source.sourceType() : source.path();
    }

    private String conversationHistory(TaskContext context) {
        if (context.previousTasks() == null || context.previousTasks().isEmpty()) {
            return "No earlier tasks in this conversation.";
        }
        return context.previousTasks().stream()
                .map(task -> """
                        - Task %s [%s]
                          Prompt: %s
                          Report excerpt: %s
                          Affected files: %s
                        """.formatted(task.taskId(), task.status(), compact(task.prompt(), 300),
                        compact(task.finalReport(), 700), task.affectedFiles()))
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
