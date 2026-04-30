package com.nask.agent.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import picocli.CommandLine;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Command-line client for the local Agent REST API.
 */
@CommandLine.Command(
        name = "agent",
        mixinStandardHelpOptions = true,
        subcommands = {
                AgentCli.WorkspaceCommand.class,
                AgentCli.RunCommand.class,
                AgentCli.StatusCommand.class,
                AgentCli.EventsCommand.class,
                AgentCli.DiffCommand.class,
                AgentCli.ReportCommand.class,
                AgentCli.ApprovalsCommand.class,
                AgentCli.ApproveCommand.class,
                AgentCli.DenyCommand.class,
                AgentCli.CommandPolicyCommand.class
        })
public class AgentCli implements Callable<Integer> {
    @CommandLine.Option(names = "--base-url", defaultValue = "http://localhost:8080", description = "Agent service base URL")
    String baseUrl;

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Runs the CLI entry point.
     */
    public static void main(String[] args) {
        System.exit(new CommandLine(new AgentCli()).execute(args));
    }

    /**
     * Shows top-level usage when no subcommand is provided.
     */
    @Override
    public Integer call() {
        CommandLine.usage(this, System.out);
        return 0;
    }

    /**
     * Sends a GET request to the configured service.
     */
    String get(String path) throws Exception {
        var response = client.send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    /**
     * Sends a JSON POST request to the configured service.
     */
    String post(String path, Object body) throws Exception {
        var json = body == null ? "" : mapper.writeValueAsString(body);
        var response = client.send(HttpRequest.newBuilder(URI.create(baseUrl + path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    /**
     * Sends a DELETE request to the configured service.
     */
    String delete(String path) throws Exception {
        var response = client.send(HttpRequest.newBuilder(URI.create(baseUrl + path)).DELETE().build(),
                HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    /**
     * Parent command for workspace operations.
     */
    @CommandLine.Command(name = "workspace", subcommands = {WorkspaceAdd.class, WorkspaceList.class})
    static class WorkspaceCommand implements Callable<Integer> {
        /**
         * Shows workspace subcommand usage.
         */
        @Override
        public Integer call() {
            CommandLine.usage(this, System.out);
            return 0;
        }
    }

    /**
     * Registers a workspace root with the service.
     */
    @CommandLine.Command(name = "add")
    static class WorkspaceAdd implements Callable<Integer> {
        @CommandLine.ParentCommand WorkspaceCommand parent;
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;
        @CommandLine.Parameters(index = "0") String path;

        /**
         * Calls the workspace creation endpoint.
         */
        @Override
        public Integer call() throws Exception {
            var root = (AgentCli) spec.root().userObject();
            System.out.println(root.post("/api/workspaces", Map.of("rootPath", path, "trusted", true)));
            return 0;
        }
    }

    /**
     * Lists registered workspaces.
     */
    @CommandLine.Command(name = "list")
    static class WorkspaceList implements Callable<Integer> {
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;

        /**
         * Calls the workspace list endpoint.
         */
        @Override
        public Integer call() throws Exception {
            var root = (AgentCli) spec.root().userObject();
            System.out.println(root.get("/api/workspaces"));
            return 0;
        }
    }

    /**
     * Creates a task and starts a run.
     */
    @CommandLine.Command(name = "run")
    static class RunCommand implements Callable<Integer> {
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;
        @CommandLine.Parameters(index = "0") String task;
        @CommandLine.Option(names = "--workspace", required = true) String workspaceId;

        /**
         * Creates the task, then starts it through the HTTP API.
         */
        @Override
        public Integer call() throws Exception {
            var root = (AgentCli) spec.root().userObject();
            var created = root.post("/api/tasks", Map.of("workspaceId", workspaceId, "title", "CLI task", "userRequest", task));
            var id = root.mapper.readTree(created).get("id").asText();
            System.out.println(root.post("/api/tasks/" + id + "/start", null));
            return 0;
        }
    }

    /**
     * Displays task status.
     */
    @CommandLine.Command(name = "status")
    static class StatusCommand implements Callable<Integer> {
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;
        @CommandLine.Parameters(index = "0") String taskId;

        /**
         * Calls the task lookup endpoint.
         */
        @Override
        public Integer call() throws Exception {
            var root = (AgentCli) spec.root().userObject();
            System.out.println(root.get("/api/tasks/" + taskId));
            return 0;
        }
    }

    /**
     * Displays task audit events.
     */
    @CommandLine.Command(name = "events")
    static class EventsCommand implements Callable<Integer> {
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;
        @CommandLine.Parameters(index = "0") String taskId;

        /**
         * Calls the task events endpoint.
         */
        @Override
        public Integer call() throws Exception {
            var root = (AgentCli) spec.root().userObject();
            System.out.println(root.get("/api/tasks/" + taskId + "/events"));
            return 0;
        }
    }

    /**
     * Displays recorded file changes.
     */
    @CommandLine.Command(name = "diff")
    static class DiffCommand implements Callable<Integer> {
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;
        @CommandLine.Parameters(index = "0") String taskId;

        /**
         * Calls the task changes endpoint.
         */
        @Override
        public Integer call() throws Exception {
            var root = (AgentCli) spec.root().userObject();
            System.out.println(root.get("/api/tasks/" + taskId + "/changes"));
            return 0;
        }
    }

    /**
     * Displays the latest task report.
     */
    @CommandLine.Command(name = "report")
    static class ReportCommand implements Callable<Integer> {
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;
        @CommandLine.Parameters(index = "0") String taskId;

        /**
         * Calls the report endpoint.
         */
        @Override
        public Integer call() throws Exception {
            var root = (AgentCli) spec.root().userObject();
            System.out.println(root.get("/api/tasks/" + taskId + "/report"));
            return 0;
        }
    }

    /**
     * Lists pending approval requests.
     */
    @CommandLine.Command(name = "approvals")
    static class ApprovalsCommand implements Callable<Integer> {
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;

        /**
         * Calls the approvals endpoint with a pending-status filter.
         */
        @Override
        public Integer call() throws Exception {
            var root = (AgentCli) spec.root().userObject();
            System.out.println(root.get("/api/approvals?status=PENDING"));
            return 0;
        }
    }

    /**
     * Approves an approval request.
     */
    @CommandLine.Command(name = "approve")
    static class ApproveCommand implements Callable<Integer> {
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;
        @CommandLine.Parameters(index = "0") String approvalId;

        /**
         * Calls the approval endpoint and lets the server resume the run.
         */
        @Override
        public Integer call() throws Exception {
            var root = (AgentCli) spec.root().userObject();
            System.out.println(root.post("/api/approvals/" + approvalId + "/approve", Map.of("resolvedBy", "cli")));
            return 0;
        }
    }

    /**
     * Denies an approval request.
     */
    @CommandLine.Command(name = "deny")
    static class DenyCommand implements Callable<Integer> {
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;
        @CommandLine.Parameters(index = "0") String approvalId;

        /**
         * Calls the denial endpoint.
         */
        @Override
        public Integer call() throws Exception {
            var root = (AgentCli) spec.root().userObject();
            System.out.println(root.post("/api/approvals/" + approvalId + "/deny", Map.of("resolvedBy", "cli", "reason", "Denied from CLI")));
            return 0;
        }
    }

    /**
     * Parent command for command policy operations.
     */
    @CommandLine.Command(name = "command", subcommands = {CommandAllow.class, CommandList.class})
    static class CommandPolicyCommand implements Callable<Integer> {
        /**
         * Shows command-policy subcommand usage.
         */
        @Override
        public Integer call() {
            CommandLine.usage(this, System.out);
            return 0;
        }
    }

    /**
     * Adds an allowlist command policy.
     */
    @CommandLine.Command(name = "allow")
    static class CommandAllow implements Callable<Integer> {
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;
        @CommandLine.Option(names = "--workspace", required = true) String workspaceId;
        @CommandLine.Option(names = "--exec", required = true) String executable;
        @CommandLine.Option(names = "--args", split = ",", defaultValue = "") List<String> args;

        /**
         * Calls the command policy creation endpoint.
         */
        @Override
        public Integer call() throws Exception {
            var root = (AgentCli) spec.root().userObject();
            var cleanArgs = args.stream().filter(value -> !value.isBlank()).toList();
            System.out.println(root.post("/api/workspaces/" + workspaceId + "/command-policies",
                    Map.of("policyType", "ALLOWLIST", "executable", executable, "argsPattern", cleanArgs)));
            return 0;
        }
    }

    /**
     * Lists command policies for a workspace.
     */
    @CommandLine.Command(name = "list")
    static class CommandList implements Callable<Integer> {
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;
        @CommandLine.Option(names = "--workspace", required = true) String workspaceId;

        /**
         * Calls the command policy list endpoint.
         */
        @Override
        public Integer call() throws Exception {
            var root = (AgentCli) spec.root().userObject();
            System.out.println(root.get("/api/workspaces/" + workspaceId + "/command-policies"));
            return 0;
        }
    }
}
