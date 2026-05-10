package com.nask.agent.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.util.Map;
import java.util.Scanner;
import java.util.UUID;

/**
 * Line-oriented interactive terminal session for local development.
 */
class InteractiveTerminalSession {
    private static final java.util.Set<String> TERMINAL = java.util.Set.of("COMPLETED", "FAILED", "CANCELLED");
    private static final String CUSTOM_USER_INPUT_OPTION = "None of these; answer manually";

    private final AgentCli cli;
    private final ObjectMapper mapper;
    private final CliLocalConfig config;
    private final CliSessionStore sessions;
    private final CliOutputFormatter formatter;
    private final Scanner scanner = new Scanner(System.in);
    private String workspaceId = "";
    private String taskId = "";
    private String runId = "";
    private int renderedEvents;

    InteractiveTerminalSession(AgentCli cli, boolean rawJson) {
        this.cli = cli;
        this.mapper = cli.mapper;
        this.config = CliLocalConfig.load();
        if (cli.baseUrl != null && !cli.baseUrl.isBlank()) {
            config.set("base_url", cli.baseUrl);
        }
        this.sessions = new CliSessionStore(mapper, config.sessionsDir());
        this.formatter = new CliOutputFormatter(mapper, rawJson);
    }

    void run(String initialPrompt) throws Exception {
        System.out.println("Auditable Agent TUI. Type /status, /workspace, /plan, /diff, /permissions, /resume, /new, /exit.");
        sessions.append("session_started", workspaceId, taskId, runId, "", "session started");
        if (initialPrompt != null && !initialPrompt.isBlank()) {
            submitPrompt(initialPrompt);
        }
        while (true) {
            System.out.print("agent> ");
            if (!scanner.hasNextLine()) {
                return;
            }
            var line = scanner.nextLine().strip();
            if (line.isBlank()) {
                continue;
            }
            var command = SlashCommand.parse(line);
            if (command != null) {
                if (handle(command)) {
                    return;
                }
            } else {
                submitPrompt(line);
            }
        }
    }

    private boolean handle(SlashCommand command) throws Exception {
        return switch (command.name()) {
            case "exit", "quit" -> true;
            case "clear" -> {
                System.out.print("\033[H\033[2J");
                System.out.flush();
                yield false;
            }
            case "new" -> {
                taskId = "";
                runId = "";
                renderedEvents = 0;
                sessions.append("new", workspaceId, taskId, runId, "", "new conversation");
                System.out.println("Started a new conversation in this terminal session.");
                yield false;
            }
            case "status" -> {
                printStatus();
                yield false;
            }
            case "plan" -> {
                withTimeline(timeline -> System.out.println(formatter.plan(timeline)));
                yield false;
            }
            case "diff" -> {
                withTimeline(timeline -> System.out.println(formatter.diff(timeline)));
                yield false;
            }
            case "approvals" -> {
                var timeline = currentTimeline();
                if (timeline == null) {
                    System.out.println(formatter.approvals(mapper.readTree(cli.get("/api/approvals?status=PENDING"))));
                } else {
                    System.out.println(formatter.approvals(timeline.path("approvals")));
                }
                yield false;
            }
            case "permissions" -> {
                updatePermissions(command.argument());
                yield false;
            }
            case "workspace", "workspaceid" -> {
                updateWorkspace(command.argument());
                yield false;
            }
            case "resume" -> {
                resume(command.argument());
                yield false;
            }
            default -> {
                System.out.println("Unknown slash command: /" + command.name());
                yield false;
            }
        };
    }

    private void submitPrompt(String prompt) throws Exception {
        var workspaceId = ensureWorkspace();
        var workflow = workflowForPromptAndPermission(config.get("workflow"), prompt);
        var created = mapper.readTree(cli.post("/api/tasks",
                Map.of("workspaceId", workspaceId, "title", "CLI interactive task", "userRequest", prompt)));
        taskId = created.path("id").asText();
        var run = mapper.readTree(cli.post("/api/tasks/" + taskId + "/start-async?workflow=" + workflow, null));
        runId = run.path("id").asText();
        renderedEvents = 0;
        sessions.append("prompt", workspaceId, taskId, runId, run.path("status").asText(), prompt);
        config.save();
        pollUntilBlockedOrDone();
    }

    private void pollUntilBlockedOrDone() throws Exception {
        while (true) {
            Thread.sleep(1000);
            var timeline = currentTimeline();
            if (timeline == null) {
                return;
            }
            var events = timeline.path("events");
            System.out.print(formatter.timelineUpdate(timeline, renderedEvents));
            renderedEvents = events.isArray() ? events.size() : renderedEvents;
            var status = timeline.path("run").path("status").asText("");
            sessions.append("timeline", workspaceId, taskId, runId, status, status);
            if ("WAITING_APPROVAL".equals(status)) {
                resolveApproval(timeline);
                return;
            } else if ("WAITING_USER_INPUT".equals(status)) {
                answerUserInput(timeline);
                return;
            } else if (TERMINAL.contains(status)) {
                renderFinal(timeline);
                return;
            }
        }
    }

    private void resolveApproval(JsonNode timeline) throws Exception {
        var approval = firstPending(timeline.path("approvals"));
        if (approval == null) {
            return;
        }
        System.out.println(formatter.approvals(mapper.createArrayNode().add(approval)));
        System.out.print("Approve this request? [y/N] ");
        var answer = scanner.nextLine().strip();
        try {
            if ("y".equalsIgnoreCase(answer) || "yes".equalsIgnoreCase(answer)) {
                cli.post("/api/approvals/" + approval.path("id").asText() + "/approve",
                        Map.of("resolvedBy", "cli-tui"));
            } else {
                cli.post("/api/approvals/" + approval.path("id").asText() + "/deny",
                        Map.of("resolvedBy", "cli-tui", "reason", "Denied from TUI"));
                pollUntilBlockedOrDone();
                return;
            }
        } catch (IllegalStateException e) {
            System.out.println("Approval request was resolved, but run resume failed: " + e.getMessage());
            System.out.println("Use /status or /resume last after checking the backend logs.");
            return;
        }
        pollUntilBlockedOrDone();
    }

    private void answerUserInput(JsonNode timeline) throws Exception {
        var request = firstPending(timeline.path("userInputs"));
        if (request == null) {
            return;
        }
        System.out.println(request.path("question").asText("Runtime needs guidance."));
        var answer = chooseUserInputAnswer(request);
        if ("cancel task".equalsIgnoreCase(answer.strip())) {
            cli.post("/api/user-input-requests/" + request.path("id").asText() + "/cancel", null);
        } else {
            cli.post("/api/user-input-requests/" + request.path("id").asText() + "/answer",
                    Map.of("answer", answer));
        }
        pollUntilBlockedOrDone();
    }

    private String chooseUserInputAnswer(JsonNode request) throws Exception {
        var choices = new java.util.ArrayList<String>();
        var options = request.path("suggestedOptions");
        if (options.isArray()) {
            for (var option : options) {
                var text = option.asText();
                if (!text.isBlank()) {
                    choices.add(text);
                }
            }
        }
        if (choices.isEmpty()) {
            System.out.print("Answer: ");
            return scanner.nextLine();
        }
        choices.add(CUSTOM_USER_INPUT_OPTION);
        var selected = selectChoice(choices);
        if (selected == choices.size() - 1) {
            System.out.print("Custom answer: ");
            return scanner.nextLine();
        }
        return choices.get(selected);
    }

    private int selectChoice(java.util.List<String> choices) throws Exception {
        var selected = 0;
        System.out.println("Use ↑/↓ then Enter, or type a number.");
        renderChoiceMenu(choices, selected, false);
        while (true) {
            var key = readMenuKey();
            if (key.kind() == MenuKeyKind.UP) {
                selected = selected == 0 ? choices.size() - 1 : selected - 1;
                renderChoiceMenu(choices, selected, true);
            } else if (key.kind() == MenuKeyKind.DOWN) {
                selected = (selected + 1) % choices.size();
                renderChoiceMenu(choices, selected, true);
            } else if (key.kind() == MenuKeyKind.ENTER) {
                System.out.println();
                return selected;
            } else if (key.kind() == MenuKeyKind.NUMBER && key.number() >= 1 && key.number() <= choices.size()) {
                System.out.println();
                return key.number() - 1;
            }
        }
    }

    private void renderChoiceMenu(java.util.List<String> choices, int selected, boolean redraw) {
        if (redraw) {
            System.out.print("\033[" + (choices.size() + 1) + "F");
        }
        for (var i = 0; i < choices.size(); i++) {
            var marker = i == selected ? ">" : " ";
            System.out.print("\033[2K");
            System.out.println("%s %d. %s".formatted(marker, i + 1, choices.get(i)));
        }
        System.out.print("\033[2K");
        System.out.print("Choice: ");
    }

    private MenuKey readMenuKey() throws Exception {
        var first = System.in.read();
        if (first == -1 || first == '\n' || first == '\r') {
            consumeLfAfterCr(first);
            return MenuKey.enter();
        }
        if (first >= '1' && first <= '9') {
            discardUntilLineEnd();
            return MenuKey.number(first - '0');
        }
        if (first == 27) {
            var second = System.in.read();
            var third = System.in.read();
            if (second == '[' && third == 'A') {
                return MenuKey.up();
            }
            if (second == '[' && third == 'B') {
                return MenuKey.down();
            }
        }
        discardUntilLineEnd();
        return MenuKey.unknown();
    }

    private void consumeLfAfterCr(int first) throws Exception {
        if (first == '\r' && System.in.available() > 0) {
            System.in.read();
        }
    }

    private void discardUntilLineEnd() throws Exception {
        while (System.in.available() > 0) {
            var value = System.in.read();
            if (value == '\n' || value == '\r') {
                return;
            }
        }
    }

    private enum MenuKeyKind {
        UP, DOWN, ENTER, NUMBER, UNKNOWN
    }

    private record MenuKey(MenuKeyKind kind, int number) {
        static MenuKey up() {
            return new MenuKey(MenuKeyKind.UP, 0);
        }

        static MenuKey down() {
            return new MenuKey(MenuKeyKind.DOWN, 0);
        }

        static MenuKey enter() {
            return new MenuKey(MenuKeyKind.ENTER, 0);
        }

        static MenuKey number(int number) {
            return new MenuKey(MenuKeyKind.NUMBER, number);
        }

        static MenuKey unknown() {
            return new MenuKey(MenuKeyKind.UNKNOWN, 0);
        }
    }

    private void renderFinal(JsonNode timeline) {
        var report = timeline.path("report");
        if (!report.isMissingNode() && !report.isNull()) {
            System.out.println(report.path("contentMd").asText(""));
        }
        System.out.println(formatter.diff(timeline));
    }

    private void printStatus() throws Exception {
        var timeline = currentTimeline();
        if (timeline == null) {
            System.out.println("""
                    Session: %s
                    Base URL: %s
                    Permission: %s
                    Workspace: %s
                    Workspace root: %s
                    Run: none
                    """.formatted(sessions.sessionId(), cli.effectiveBaseUrl(), config.get("permission_preset"),
                    valueOrUnset(workspaceId), currentWorkspaceRoot()));
            return;
        }
        System.out.println(formatter.status(timeline, sessions.sessionId(), cli.effectiveBaseUrl(),
                config.get("permission_preset")));
    }

    private void updatePermissions(String argument) throws Exception {
        if (argument == null || argument.isBlank()) {
            System.out.println("Current permission preset: " + config.get("permission_preset"));
            System.out.println("Allowed: read-only, workspace-write, full-auto");
            return;
        }
        if (!java.util.Set.of("read-only", "workspace-write", "full-auto").contains(argument)) {
            System.out.println("Unsupported permission preset: " + argument);
            return;
        }
        config.set("permission_preset", argument);
        config.save();
        System.out.println("Permission preset set to " + argument);
    }

    private void updateWorkspace(String argument) throws Exception {
        if (argument == null || argument.isBlank()) {
            System.out.println("Current workspace id: " + valueOrUnset(workspaceId));
            System.out.println("TUI resolves the workspace from the current directory: " + currentWorkspaceRoot());
            System.out.println("Usage: /workspace <36-char workspace UUID> to override this session only");
            return;
        }
        if (!isUuid(argument)) {
            System.out.println("Invalid workspace id. Expected a 36-character UUID, got: " + argument);
            return;
        }
        workspaceId = argument;
        System.out.println("Workspace id set to " + argument + " for this terminal session.");
    }

    private void resume(String argument) throws Exception {
        var id = argument == null || argument.isBlank() || "last".equals(argument)
                ? sessions.latestSessionId() : argument;
        if (id == null || id.isBlank()) {
            System.out.println("No previous session found.");
            return;
        }
        var state = sessions.lastState(id);
        sessions.use(id);
        taskId = state.getOrDefault("taskId", "");
        runId = state.getOrDefault("runId", "");
        workspaceId = state.getOrDefault("workspaceId", workspaceId);
        renderedEvents = 0;
        System.out.println("Resumed session " + id);
        printStatus();
    }

    private void withTimeline(TimelineConsumer consumer) throws Exception {
        var timeline = currentTimeline();
        if (timeline == null) {
            System.out.println("No active run. Submit a prompt first or use /resume.");
            return;
        }
        consumer.accept(timeline);
    }

    private JsonNode currentTimeline() throws Exception {
        if (runId == null || runId.isBlank()) {
            return null;
        }
        return mapper.readTree(cli.get("/api/runs/" + runId + "/timeline"));
    }

    private String ensureWorkspace() throws Exception {
        if (isUuid(workspaceId)) {
            return workspaceId;
        }
        var root = currentWorkspaceRoot();
        var workspaces = mapper.readTree(cli.get("/api/workspaces"));
        var registered = findWorkspaceIdByRoot(workspaces, root);
        if (registered != null) {
            workspaceId = registered;
            return workspaceId;
        }
        while (true) {
            System.out.print("Trust and register this directory as a workspace? " + root + " [y/N] ");
            var answer = scanner.nextLine().strip();
            if ("y".equalsIgnoreCase(answer) || "yes".equalsIgnoreCase(answer)) {
                var created = mapper.readTree(cli.post("/api/workspaces",
                        Map.of("rootPath", root.toString(), "trusted", true)));
                workspaceId = created.path("id").asText();
                return workspaceId;
            }
            System.out.println("Workspace registration is required before submitting a task from this directory.");
        }
    }

    static String findWorkspaceIdByRoot(JsonNode workspaces, Path root) {
        if (!workspaces.isArray()) {
            return null;
        }
        var normalizedRoot = normalizePath(root);
        for (var workspace : workspaces) {
            var registeredRoot = workspace.path("rootPath").asText("");
            if (normalizePath(Path.of(registeredRoot)).equals(normalizedRoot)) {
                return workspace.path("id").asText();
            }
        }
        return null;
    }

    private static Path currentWorkspaceRoot() {
        return normalizePath(Path.of("").toAbsolutePath());
    }

    private static Path normalizePath(Path path) {
        return path.toAbsolutePath().normalize();
    }

    private String workflowForPromptAndPermission(String configured, String prompt) {
        var preset = config.get("permission_preset");
        if ("read-only".equals(preset) && !"test-agent".equals(configured)) {
            return "review-agent";
        }
        var workflow = configured == null || configured.isBlank() ? "coding-agent" : configured;
        if ("coding-agent".equals(workflow) && looksLikeReviewOnly(prompt)) {
            return "review-agent";
        }
        return workflow;
    }

    static boolean looksLikeReviewOnly(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return false;
        }
        var text = prompt.toLowerCase(java.util.Locale.ROOT);
        if (text.contains("fix") || text.contains("修复") || text.contains("修改") || text.contains("实现")
                || text.contains("add ") || text.contains("新增")) {
            return false;
        }
        return text.contains("review")
                || text.contains("summarize")
                || text.contains("summary")
                || text.contains("bug")
                || text.contains("总结")
                || text.contains("明显的问题")
                || text.contains("明显bug")
                || text.contains("明显的bug")
                || text.contains("审查")
                || text.contains("检查问题");
    }

    private JsonNode firstPending(JsonNode array) {
        if (!array.isArray()) {
            return null;
        }
        for (var item : array) {
            if ("PENDING".equals(item.path("status").asText())) {
                return item;
            }
        }
        return null;
    }

    private static boolean isUuid(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String valueOrUnset(String value) {
        return value == null || value.isBlank() ? "<unset>" : value;
    }

    @FunctionalInterface
    private interface TimelineConsumer {
        void accept(JsonNode timeline) throws Exception;
    }
}
