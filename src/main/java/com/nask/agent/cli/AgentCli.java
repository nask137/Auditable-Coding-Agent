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

    public static void main(String[] args) {
        System.exit(new CommandLine(new AgentCli()).execute(args));
    }

    @Override
    public Integer call() {
        CommandLine.usage(this, System.out);
        return 0;
    }

    String get(String path) throws Exception {
        var response = client.send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    String post(String path, Object body) throws Exception {
        var json = body == null ? "" : mapper.writeValueAsString(body);
        var response = client.send(HttpRequest.newBuilder(URI.create(baseUrl + path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    String delete(String path) throws Exception {
        var response = client.send(HttpRequest.newBuilder(URI.create(baseUrl + path)).DELETE().build(),
                HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    @CommandLine.Command(name = "workspace", subcommands = {WorkspaceAdd.class, WorkspaceList.class})
    static class WorkspaceCommand implements Callable<Integer> {
        @Override
        public Integer call() {
            CommandLine.usage(this, System.out);
            return 0;
        }
    }

    @CommandLine.Command(name = "add")
    static class WorkspaceAdd implements Callable<Integer> {
        @CommandLine.ParentCommand WorkspaceCommand parent;
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;
        @CommandLine.Parameters(index = "0") String path;

        @Override
        public Integer call() throws Exception {
            var root = (AgentCli) spec.root().userObject();
            System.out.println(root.post("/api/workspaces", Map.of("rootPath", path, "trusted", true)));
            return 0;
        }
    }

    @CommandLine.Command(name = "list")
    static class WorkspaceList implements Callable<Integer> {
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;

        @Override
        public Integer call() throws Exception {
            var root = (AgentCli) spec.root().userObject();
            System.out.println(root.get("/api/workspaces"));
            return 0;
        }
    }

    @CommandLine.Command(name = "run")
    static class RunCommand implements Callable<Integer> {
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;
        @CommandLine.Parameters(index = "0") String task;
        @CommandLine.Option(names = "--workspace", required = true) String workspaceId;

        @Override
        public Integer call() throws Exception {
            var root = (AgentCli) spec.root().userObject();
            var created = root.post("/api/tasks", Map.of("workspaceId", workspaceId, "title", "CLI task", "userRequest", task));
            var id = root.mapper.readTree(created).get("id").asText();
            System.out.println(root.post("/api/tasks/" + id + "/start", null));
            return 0;
        }
    }

    @CommandLine.Command(name = "status")
    static class StatusCommand implements Callable<Integer> {
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;
        @CommandLine.Parameters(index = "0") String taskId;

        @Override
        public Integer call() throws Exception {
            var root = (AgentCli) spec.root().userObject();
            System.out.println(root.get("/api/tasks/" + taskId));
            return 0;
        }
    }

    @CommandLine.Command(name = "events")
    static class EventsCommand implements Callable<Integer> {
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;
        @CommandLine.Parameters(index = "0") String taskId;

        @Override
        public Integer call() throws Exception {
            var root = (AgentCli) spec.root().userObject();
            System.out.println(root.get("/api/tasks/" + taskId + "/events"));
            return 0;
        }
    }

    @CommandLine.Command(name = "diff")
    static class DiffCommand implements Callable<Integer> {
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;
        @CommandLine.Parameters(index = "0") String taskId;

        @Override
        public Integer call() throws Exception {
            var root = (AgentCli) spec.root().userObject();
            System.out.println(root.get("/api/tasks/" + taskId + "/changes"));
            return 0;
        }
    }

    @CommandLine.Command(name = "report")
    static class ReportCommand implements Callable<Integer> {
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;
        @CommandLine.Parameters(index = "0") String taskId;

        @Override
        public Integer call() throws Exception {
            var root = (AgentCli) spec.root().userObject();
            System.out.println(root.get("/api/tasks/" + taskId + "/report"));
            return 0;
        }
    }

    @CommandLine.Command(name = "approvals")
    static class ApprovalsCommand implements Callable<Integer> {
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;

        @Override
        public Integer call() throws Exception {
            var root = (AgentCli) spec.root().userObject();
            System.out.println(root.get("/api/approvals?status=PENDING"));
            return 0;
        }
    }

    @CommandLine.Command(name = "approve")
    static class ApproveCommand implements Callable<Integer> {
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;
        @CommandLine.Parameters(index = "0") String approvalId;

        @Override
        public Integer call() throws Exception {
            var root = (AgentCli) spec.root().userObject();
            System.out.println(root.post("/api/approvals/" + approvalId + "/approve", Map.of("resolvedBy", "cli")));
            return 0;
        }
    }

    @CommandLine.Command(name = "deny")
    static class DenyCommand implements Callable<Integer> {
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;
        @CommandLine.Parameters(index = "0") String approvalId;

        @Override
        public Integer call() throws Exception {
            var root = (AgentCli) spec.root().userObject();
            System.out.println(root.post("/api/approvals/" + approvalId + "/deny", Map.of("resolvedBy", "cli", "reason", "Denied from CLI")));
            return 0;
        }
    }

    @CommandLine.Command(name = "command", subcommands = {CommandAllow.class, CommandList.class})
    static class CommandPolicyCommand implements Callable<Integer> {
        @Override
        public Integer call() {
            CommandLine.usage(this, System.out);
            return 0;
        }
    }

    @CommandLine.Command(name = "allow")
    static class CommandAllow implements Callable<Integer> {
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;
        @CommandLine.Option(names = "--workspace", required = true) String workspaceId;
        @CommandLine.Option(names = "--exec", required = true) String executable;
        @CommandLine.Option(names = "--args", split = ",", defaultValue = "") List<String> args;

        @Override
        public Integer call() throws Exception {
            var root = (AgentCli) spec.root().userObject();
            var cleanArgs = args.stream().filter(value -> !value.isBlank()).toList();
            System.out.println(root.post("/api/workspaces/" + workspaceId + "/command-policies",
                    Map.of("policyType", "ALLOWLIST", "executable", executable, "argsPattern", cleanArgs)));
            return 0;
        }
    }

    @CommandLine.Command(name = "list")
    static class CommandList implements Callable<Integer> {
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;
        @CommandLine.Option(names = "--workspace", required = true) String workspaceId;

        @Override
        public Integer call() throws Exception {
            var root = (AgentCli) spec.root().userObject();
            System.out.println(root.get("/api/workspaces/" + workspaceId + "/command-policies"));
            return 0;
        }
    }
}
