package com.nask.agent.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.InputMismatchException;
import java.util.Map;
import java.util.Scanner;
import java.util.UUID;

/**
 * Line-oriented interactive terminal session for local development.
 */
class InteractiveTerminalSession {
    private static final java.util.Set<String> TERMINAL = java.util.Set.of("COMPLETED", "FAILED", "CANCELLED");
    private static final String CUSTOM_USER_INPUT_OPTION = "None of these; answer manually";
    private static final String DEFAULT_CONVERSATION_TITLE = "Conversation";

    private final AgentCli cli;
    private final ObjectMapper mapper;
    private final CliLocalConfig config;
    private final CliOutputFormatter formatter;
    private final Scanner scanner;
    private String workspaceId = "";
    private String conversationId = "";
    private String conversationTitle = "";
    private String taskId = "";
    private int renderedEvents;

    InteractiveTerminalSession(AgentCli cli, boolean rawJson) {
        this(cli, rawJson, new Scanner(System.in, inputCharset()));
    }

    InteractiveTerminalSession(AgentCli cli, boolean rawJson, Scanner scanner) {
        this.cli = cli;
        this.mapper = cli.mapper;
        this.config = CliLocalConfig.load();
        if (cli.baseUrl != null && !cli.baseUrl.isBlank()) {
            config.set("base_url", cli.baseUrl);
        }
        this.formatter = new CliOutputFormatter(mapper, rawJson);
        this.scanner = scanner;
    }

    void run(String initialPrompt) throws Exception {
        System.out.println("Auditable Agent TUI. Type /status, /workspace, /plan, /diff, /permissions, /resume, /new, /rename, /exit.");
        try {
            initializeWorkspace();
        } catch (IllegalStateException e) {
            System.out.println("Request failed: " + e.getMessage());
        } catch (java.net.ConnectException e) {
            System.out.println("Cannot connect to " + cli.effectiveBaseUrl()
                    + ". Start the backend service or update --base-url.");
        }
        if (initialPrompt != null && !initialPrompt.isBlank()) {
            try {
                submitPrompt(initialPrompt);
            } catch (IllegalStateException e) {
                System.out.println("Request failed: " + e.getMessage());
            } catch (java.net.ConnectException e) {
                System.out.println("Cannot connect to " + cli.effectiveBaseUrl()
                        + ". Start the backend service or update --base-url.");
            }
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
            try {
                if (command != null) {
                    if (handle(command)) {
                        return;
                    }
                } else {
                    submitPrompt(line);
                }
            } catch (IllegalStateException e) {
                System.out.println("Request failed: " + e.getMessage());
            } catch (java.net.ConnectException e) {
                System.out.println("Cannot connect to " + cli.effectiveBaseUrl()
                        + ". Start the backend service or update --base-url.");
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
                startNewConversation();
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
            case "rename" -> {
                renameConversation(command.argument());
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
        var workflow = workflowForPermission(config.get("workflow"));
        var request = new java.util.LinkedHashMap<String, Object>();
        request.put("workspaceId", workspaceId);
        if (conversationId != null && !conversationId.isBlank()) {
            if (shouldAutoTitleConversation()) {
                renameConversationTo(promptTitle(prompt));
            }
            request.put("conversationId", conversationId);
        }
        request.put("title", promptTitle(prompt));
        request.put("userRequest", prompt);
        var created = mapper.readTree(cli.post("/api/tasks", request));
        conversationId = created.path("conversationId").asText(conversationId);
        if (conversationTitle == null || conversationTitle.isBlank()) {
            conversationTitle = promptTitle(prompt);
        }
        taskId = created.path("id").asText();
        var startPath = "/api/tasks/" + taskId + "/start-async";
        if (workflow != null && !workflow.isBlank()) {
            startPath += "?workflow=" + workflow;
        }
        var started = mapper.readTree(cli.post(startPath, null));
        renderedEvents = 0;
        config.save();
        pollUntilBlockedOrDone();
    }

    String initializeWorkspace() throws Exception {
        if (!isUuid(workspaceId)) {
            workspaceId = resolveOrRegisterCurrentWorkspace();
        }
        return workspaceId;
    }

    String startNewConversation() throws Exception {
        var workspace = ensureWorkspace();
        var created = mapper.readTree(cli.post("/api/conversations",
                Map.of("workspaceId", workspace, "title", DEFAULT_CONVERSATION_TITLE)));
        conversationId = created.path("id").asText("");
        conversationTitle = created.path("title").asText(DEFAULT_CONVERSATION_TITLE);
        taskId = "";
        renderedEvents = 0;
        System.out.println("Started new conversation \"" + conversationTitle + "\" (" + shortId(conversationId)
                + ") in workspace " + workspaceId + ".");
        return conversationId;
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
            var status = timeline.path("task").path("status").asText("");
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
        System.out.println(formatter.finalSummary(timeline));
    }

    private void printStatus() throws Exception {
        var timeline = currentTimeline();
        if (timeline == null) {
            System.out.println("""
                    Base URL: %s
                    Permission: %s
                    Workspace ID: %s
                    Conversation: %s
                    Conversation title: %s
                    Workspace root: %s
                    Task: none
                    """.formatted(cli.effectiveBaseUrl(), config.get("permission_preset"),
                    unresolvedWorkspaceId(), valueOrUnset(conversationId), valueOrUnset(conversationTitle),
                    currentWorkspaceRoot()));
            return;
        }
        System.out.println(formatter.status(timeline, cli.effectiveBaseUrl(), config.get("permission_preset")));
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
        ensureWorkspace();
        JsonNode conversation;
        if (argument == null || argument.isBlank()) {
            conversation = chooseConversation();
        } else if ("last".equalsIgnoreCase(argument)) {
            conversation = latestConversation();
        } else {
            conversation = mapper.readTree(cli.get("/api/conversations/" + argument));
        }
        if (conversation == null || conversation.path("id").asText("").isBlank()) {
            System.out.println("No backend conversation found for this workspace.");
            return;
        }
        conversationId = conversation.path("id").asText("");
        conversationTitle = conversation.path("title").asText("");
        workspaceId = conversation.path("workspaceId").asText(workspaceId);
        taskId = "";
        renderedEvents = 0;
        System.out.println("Resumed conversation \"" + conversationTitle + "\" (" + shortId(conversationId) + ").");
        System.out.println("Submit a prompt to add a new task to this conversation.");
    }

    private JsonNode chooseConversation() throws Exception {
        var conversations = conversationsForWorkspace();
        if (!conversations.isArray() || conversations.isEmpty()) {
            return null;
        }
        var choices = new java.util.ArrayList<String>();
        for (var conversation : conversations) {
            choices.add("%s (%s) updated %s".formatted(conversation.path("title").asText("(untitled)"),
                    shortId(conversation.path("id").asText("")), conversation.path("updatedAt").asText("-")));
        }
        var selected = selectChoice(choices);
        return conversations.get(selected);
    }

    private JsonNode latestConversation() throws Exception {
        var conversations = conversationsForWorkspace();
        return conversations.isArray() && !conversations.isEmpty() ? conversations.get(0) : null;
    }

    private JsonNode conversationsForWorkspace() throws Exception {
        return mapper.readTree(cli.get("/api/conversations?workspaceId=" + ensureWorkspace()));
    }

    private void renameConversation(String title) throws Exception {
        if (conversationId == null || conversationId.isBlank()) {
            System.out.println("No active conversation. Use /new or /resume first.");
            return;
        }
        if (title == null || title.isBlank()) {
            System.out.println("Usage: /rename <conversation title>");
            return;
        }
        renameConversationTo(title);
        System.out.println("Renamed conversation to \"" + conversationTitle + "\".");
    }

    private void renameConversationTo(String title) throws Exception {
        var renamed = mapper.readTree(cli.post("/api/conversations/" + conversationId + "/rename",
                Map.of("title", truncate(title.strip(), 120))));
        conversationTitle = renamed.path("title").asText(conversationTitle);
    }

    private void withTimeline(TimelineConsumer consumer) throws Exception {
        var timeline = currentTimeline();
        if (timeline == null) {
            System.out.println("No active task. Submit a prompt first or use /resume.");
            return;
        }
        consumer.accept(timeline);
    }

    private JsonNode currentTimeline() throws Exception {
        if (taskId == null || taskId.isBlank()) {
            return null;
        }
        return mapper.readTree(cli.get("/api/tasks/" + taskId + "/timeline"));
    }

    private String ensureWorkspace() throws Exception {
        if (isUuid(workspaceId)) {
            return workspaceId;
        }
        workspaceId = resolveOrRegisterCurrentWorkspace();
        return workspaceId;
    }

    private String resolveOrRegisterCurrentWorkspace() throws Exception {
        var root = currentWorkspaceRoot();
        var workspaces = mapper.readTree(cli.get("/api/workspaces"));
        var registered = findWorkspaceIdByRoot(workspaces, root);
        if (registered != null) {
            return registered;
        }
        confirmTrustedWorkspaceRegistration(root);
        var created = mapper.readTree(cli.post("/api/workspaces",
                Map.of("rootPath", root.toString(), "trusted", true)));
        return created.path("id").asText();
    }

    private void confirmTrustedWorkspaceRegistration(Path root) {
        System.out.println("No workspace is registered for the current directory:");
        System.out.println(root);
        System.out.print("Register this directory as a trusted workspace? Type yes to continue: ");
        if (!scanner.hasNextLine()) {
            throw new InputMismatchException("Workspace registration requires explicit confirmation");
        }
        var answer = scanner.nextLine().strip();
        if (!"yes".equalsIgnoreCase(answer)) {
            throw new InputMismatchException("Workspace registration cancelled");
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

    String workflowForPermission(String configured) {
        return workflowForPermission(configured, config.get("permission_preset"));
    }

    static String workflowForPermission(String configured, String preset) {
        if ("read-only".equals(preset)
                && (configured == null || configured.isBlank() || "auto".equals(configured))) {
            return "review-agent";
        }
        return configured == null || configured.isBlank() || "auto".equals(configured) ? null : configured;
    }

    static String promptTitle(String prompt) {
        var text = prompt == null ? "" : prompt.replaceAll("\\s+", " ").strip();
        if (text.isBlank()) {
            return "CLI task";
        }
        return truncate(text, 80);
    }

    private boolean shouldAutoTitleConversation() {
        return conversationTitle == null
                || conversationTitle.isBlank()
                || DEFAULT_CONVERSATION_TITLE.equals(conversationTitle)
                || "CLI conversation".equals(conversationTitle);
    }

    static Charset inputCharset() {
        var console = System.console();
        if (console != null) {
            return console.charset();
        }
        return Charset.defaultCharset();
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

    private static String shortId(String value) {
        return value == null || value.length() < 8 ? valueOrUnset(value) : value.substring(0, 8);
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String unresolvedWorkspaceId() {
        return workspaceId == null || workspaceId.isBlank()
                ? "<unresolved; submit a prompt to resolve/register current directory>"
                : workspaceId;
    }

    @FunctionalInterface
    private interface TimelineConsumer {
        void accept(JsonNode timeline) throws Exception;
    }
}
