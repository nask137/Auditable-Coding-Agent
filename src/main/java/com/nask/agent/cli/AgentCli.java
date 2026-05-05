package com.nask.agent.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import picocli.CommandLine;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
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
                AgentCli.FailuresCommand.class,
                AgentCli.DiffCommand.class,
                AgentCli.ReportCommand.class,
                AgentCli.ApprovalsCommand.class,
                AgentCli.ApproveCommand.class,
                AgentCli.DenyCommand.class,
                AgentCli.InputsCommand.class,
                AgentCli.InputCommand.class,
                AgentCli.AnswerCommand.class,
                AgentCli.CancelInputCommand.class,
                AgentCli.WorkflowsCommand.class,
                AgentCli.WorkflowCommand.class,
                AgentCli.WorkflowStatusCommand.class,
                AgentCli.WorkflowPathCommand.class,
                AgentCli.ScanCommand.class,
                AgentCli.ProfileCommand.class,
                AgentCli.SymbolsCommand.class,
                AgentCli.OutlineCommand.class,
                AgentCli.ContextCommand.class,
                AgentCli.MemoryCommand.class,
                AgentCli.RememberCommand.class,
                AgentCli.MemoryProposalsCommand.class,
                AgentCli.MemoryApproveCommand.class,
                AgentCli.MemoryRejectCommand.class,
                AgentCli.MemoryRetrievalsCommand.class,
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
        @CommandLine.Option(names = "--workflow", defaultValue = "coding-agent") String workflow;

        /**
         * Creates the task, then starts it through the HTTP API.
         */
        @Override
        public Integer call() throws Exception {
            var root = (AgentCli) spec.root().userObject();
            var created = root.post("/api/tasks", Map.of("workspaceId", workspaceId, "title", "CLI task", "userRequest", task));
            var id = root.mapper.readTree(created).get("id").asText();
            System.out.println(root.post("/api/tasks/" + id + "/start?workflow=" + workflow, null));
            return 0;
        }
    }

    /**
     * Lists workflow definitions.
     */
    @CommandLine.Command(name = "workflows")
    static class WorkflowsCommand implements Callable<Integer> {
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;

        @Override
        public Integer call() throws Exception {
            var root = (AgentCli) spec.root().userObject();
            System.out.println(root.get("/api/workflows"));
            return 0;
        }
    }

    /**
     * Displays one workflow definition.
     */
    @CommandLine.Command(name = "workflow")
    static class WorkflowCommand implements Callable<Integer> {
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;
        @CommandLine.Parameters(index = "0") String workflowId;

        @Override
        public Integer call() throws Exception {
            var root = (AgentCli) spec.root().userObject();
            System.out.println(root.get("/api/workflows/" + workflowId));
            return 0;
        }
    }

    /**
     * Displays the workflow definition selected by a run.
     */
    @CommandLine.Command(name = "workflow-status")
    static class WorkflowStatusCommand implements Callable<Integer> {
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;
        @CommandLine.Parameters(index = "0") String runId;

        @Override
        public Integer call() throws Exception {
            var root = (AgentCli) spec.root().userObject();
            System.out.println(root.get("/api/runs/" + runId + "/workflow"));
            return 0;
        }
    }

    /**
     * Displays workflow node and edge history for a run.
     */
    @CommandLine.Command(name = "workflow-path")
    static class WorkflowPathCommand implements Callable<Integer> {
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;
        @CommandLine.Parameters(index = "0") String runId;

        @Override
        public Integer call() throws Exception {
            var root = (AgentCli) spec.root().userObject();
            System.out.println(root.get("/api/runs/" + runId + "/workflow/nodes"));
            System.out.println(root.get("/api/runs/" + runId + "/workflow/edges"));
            return 0;
        }
    }

    /**
     * Triggers a bounded project scan for a workspace.
     */
    @CommandLine.Command(name = "scan")
    static class ScanCommand implements Callable<Integer> {
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;
        @CommandLine.Parameters(index = "0") String workspaceId;

        @Override
        public Integer call() throws Exception {
            var root = (AgentCli) spec.root().userObject();
            System.out.println(root.post("/api/workspaces/" + workspaceId + "/scan", null));
            return 0;
        }
    }

    /**
     * Displays the latest project profile for a workspace.
     */
    @CommandLine.Command(name = "profile")
    static class ProfileCommand implements Callable<Integer> {
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;
        @CommandLine.Parameters(index = "0") String workspaceId;

        @Override
        public Integer call() throws Exception {
            var root = (AgentCli) spec.root().userObject();
            System.out.println(root.get("/api/workspaces/" + workspaceId + "/profile"));
            return 0;
        }
    }

    /**
     * Searches indexed code symbols for a workspace.
     */
    @CommandLine.Command(name = "symbols")
    static class SymbolsCommand implements Callable<Integer> {
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;
        @CommandLine.Parameters(index = "0") String workspaceId;
        @CommandLine.Option(names = "--query", defaultValue = "") String query;
        @CommandLine.Option(names = "--type", defaultValue = "") String type;

        @Override
        public Integer call() throws Exception {
            var root = (AgentCli) spec.root().userObject();
            System.out.println(root.get("/api/workspaces/" + workspaceId + "/symbols?query="
                    + encode(query) + "&type=" + encode(type)));
            return 0;
        }
    }

    /**
     * Displays indexed symbols for one workspace-relative file.
     */
    @CommandLine.Command(name = "outline")
    static class OutlineCommand implements Callable<Integer> {
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;
        @CommandLine.Parameters(index = "0") String workspaceId;
        @CommandLine.Option(names = "--path", required = true) String path;

        @Override
        public Integer call() throws Exception {
            var root = (AgentCli) spec.root().userObject();
            System.out.println(root.get("/api/workspaces/" + workspaceId + "/outline?path=" + encode(path)));
            return 0;
        }
    }

    /**
     * Retrieves project context using the unified memory search service.
     */
    @CommandLine.Command(name = "context")
    static class ContextCommand implements Callable<Integer> {
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;
        @CommandLine.Parameters(index = "0") String workspaceId;
        @CommandLine.Option(names = "--query", defaultValue = "") String query;
        @CommandLine.Option(names = "--memory-type", defaultValue = "") String memoryType;
        @CommandLine.Option(names = "--document-type", defaultValue = "") String documentType;
        @CommandLine.Option(names = "--symbol-type", defaultValue = "") String symbolType;
        @CommandLine.Option(names = "--limit", defaultValue = "10") int limit;

        @Override
        public Integer call() throws Exception {
            var root = (AgentCli) spec.root().userObject();
            System.out.println(root.get("/api/workspaces/" + workspaceId + "/search-context?q="
                    + encode(query) + optional("memoryType", memoryType)
                    + optional("documentType", documentType) + optional("symbolType", symbolType)
                    + "&limit=" + limit));
            return 0;
        }
    }

    /**
     * Lists project memory items for a workspace.
     */
    @CommandLine.Command(name = "memory")
    static class MemoryCommand implements Callable<Integer> {
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;
        @CommandLine.Parameters(index = "0") String workspaceId;

        @Override
        public Integer call() throws Exception {
            var root = (AgentCli) spec.root().userObject();
            System.out.println(root.get("/api/workspaces/" + workspaceId + "/memory"));
            return 0;
        }
    }

    /**
     * Manually records an approved project memory item.
     */
    @CommandLine.Command(name = "remember")
    static class RememberCommand implements Callable<Integer> {
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;
        @CommandLine.Parameters(index = "0") String workspaceId;
        @CommandLine.Option(names = "--type", required = true) String type;
        @CommandLine.Option(names = "--title", required = true) String title;
        @CommandLine.Option(names = "--content", required = true) String content;
        @CommandLine.Option(names = "--scope", defaultValue = "workspace") String scope;
        @CommandLine.Option(names = "--status", defaultValue = "APPROVED") String status;
        @CommandLine.Option(names = "--confidence", defaultValue = "1.0") double confidence;
        @CommandLine.Option(names = "--source-path", defaultValue = "") String sourcePath;

        @Override
        public Integer call() throws Exception {
            var root = (AgentCli) spec.root().userObject();
            var body = new LinkedHashMap<String, Object>();
            body.put("memoryType", type);
            body.put("title", title);
            body.put("content", content);
            body.put("scope", scope);
            body.put("status", status);
            body.put("confidence", confidence);
            body.put("createdBy", "cli");
            if (sourcePath != null && !sourcePath.isBlank()) {
                body.put("sourcePath", sourcePath);
                body.put("sourceType", "USER");
            }
            System.out.println(root.post("/api/workspaces/" + workspaceId + "/memory", body));
            return 0;
        }
    }

    /**
     * Lists memory write proposals for a workspace.
     */
    @CommandLine.Command(name = "memory-proposals")
    static class MemoryProposalsCommand implements Callable<Integer> {
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;
        @CommandLine.Parameters(index = "0") String workspaceId;

        @Override
        public Integer call() throws Exception {
            var root = (AgentCli) spec.root().userObject();
            System.out.println(root.get("/api/workspaces/" + workspaceId + "/memory-proposals"));
            return 0;
        }
    }

    /**
     * Approves a memory write proposal.
     */
    @CommandLine.Command(name = "memory-approve")
    static class MemoryApproveCommand implements Callable<Integer> {
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;
        @CommandLine.Parameters(index = "0") String proposalId;

        @Override
        public Integer call() throws Exception {
            var root = (AgentCli) spec.root().userObject();
            System.out.println(root.post("/api/memory-proposals/" + proposalId + "/approve",
                    Map.of("resolvedBy", "cli")));
            return 0;
        }
    }

    /**
     * Rejects a memory write proposal.
     */
    @CommandLine.Command(name = "memory-reject")
    static class MemoryRejectCommand implements Callable<Integer> {
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;
        @CommandLine.Parameters(index = "0") String proposalId;
        @CommandLine.Option(names = "--reason", defaultValue = "Rejected from CLI") String reason;

        @Override
        public Integer call() throws Exception {
            var root = (AgentCli) spec.root().userObject();
            System.out.println(root.post("/api/memory-proposals/" + proposalId + "/reject",
                    Map.of("resolvedBy", "cli", "reason", reason)));
            return 0;
        }
    }

    /**
     * Lists persisted memory retrieval records for a workspace.
     */
    @CommandLine.Command(name = "memory-retrievals")
    static class MemoryRetrievalsCommand implements Callable<Integer> {
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;
        @CommandLine.Parameters(index = "0") String workspaceId;

        @Override
        public Integer call() throws Exception {
            var root = (AgentCli) spec.root().userObject();
            System.out.println(root.get("/api/workspaces/" + workspaceId + "/memory-retrievals"));
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
     * Displays task runtime failures.
     */
    @CommandLine.Command(name = "failures")
    static class FailuresCommand implements Callable<Integer> {
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;
        @CommandLine.Parameters(index = "0") String taskId;

        @Override
        public Integer call() throws Exception {
            var root = (AgentCli) spec.root().userObject();
            System.out.println(root.get("/api/tasks/" + taskId + "/failures"));
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
     * Lists pending user-input requests.
     */
    @CommandLine.Command(name = "inputs")
    static class InputsCommand implements Callable<Integer> {
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;

        @Override
        public Integer call() throws Exception {
            var root = (AgentCli) spec.root().userObject();
            System.out.println(root.get("/api/user-input-requests?status=PENDING"));
            return 0;
        }
    }

    /**
     * Displays one user-input request.
     */
    @CommandLine.Command(name = "input")
    static class InputCommand implements Callable<Integer> {
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;
        @CommandLine.Parameters(index = "0") String requestId;

        @Override
        public Integer call() throws Exception {
            var root = (AgentCli) spec.root().userObject();
            System.out.println(root.get("/api/user-input-requests/" + requestId));
            return 0;
        }
    }

    /**
     * Answers a pending user-input request and resumes the run.
     */
    @CommandLine.Command(name = "answer")
    static class AnswerCommand implements Callable<Integer> {
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;
        @CommandLine.Parameters(index = "0") String requestId;
        @CommandLine.Option(names = "--text", required = true) String answer;

        @Override
        public Integer call() throws Exception {
            var root = (AgentCli) spec.root().userObject();
            System.out.println(root.post("/api/user-input-requests/" + requestId + "/answer", Map.of("answer", answer)));
            return 0;
        }
    }

    /**
     * Cancels a pending user-input request and fails the run.
     */
    @CommandLine.Command(name = "cancel-input")
    static class CancelInputCommand implements Callable<Integer> {
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;
        @CommandLine.Parameters(index = "0") String requestId;

        @Override
        public Integer call() throws Exception {
            var root = (AgentCli) spec.root().userObject();
            System.out.println(root.post("/api/user-input-requests/" + requestId + "/cancel", null));
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

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String optional(String name, String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return "&" + name + "=" + encode(value);
    }
}
