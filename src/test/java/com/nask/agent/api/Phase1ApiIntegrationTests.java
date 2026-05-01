package com.nask.agent.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nask.agent.TestFiles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.flyway.enabled=false",
                "agent.llm.provider=stub",
                "agent.llm.api-key="
        })
class Phase1ApiIntegrationTests {
    Path workspaceDir;

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final TypeReference<Map<String, Object>> mapType = new TypeReference<>() {
    };
    private final TypeReference<List<Map<String, Object>>> listType = new TypeReference<>() {
    };

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void setup() throws IOException {
        workspaceDir = TestFiles.createTempDirectory("agent-api-workspace-");
        jdbc.execute("""
                truncate table
                  validation_result,
                  task_report,
                  audit_event,
                  command_execution,
                  file_change,
                  approval_request,
                  tool_result,
                  tool_call,
                  agent_action,
                  agent_step,
                  plan_item,
                  plan,
                  agent_run,
                  task,
                  command_policy,
                  workspace
                restart identity cascade
                """);
    }

    @AfterEach
    void cleanupWorkspace() {
        TestFiles.deleteRecursivelyQuietly(workspaceDir);
    }

    @Test
    void phase1ApiCompletesAuditedRunWithValidation() throws Exception {
        Files.writeString(workspaceDir.resolve("README.md"), "phase1 integration workspace");

        var workspace = post("/api/workspaces", Map.of(
                "name", "integration",
                "rootPath", workspaceDir.toString(),
                "trusted", true));
        var workspaceId = workspace.get("id").toString();

        post("/api/workspaces/" + workspaceId + "/command-policies", Map.of(
                "policyType", "ALLOWLIST",
                "executable", "java",
                "argsPattern", List.of("-version")));

        var task = post("/api/tasks", Map.of(
                "workspaceId", workspaceId,
                "title", "integration task",
                "userRequest", "create an audited note and validate"));
        var taskId = task.get("id").toString();

        var run = post("/api/tasks/" + taskId + "/start", null);

        assertThat(run.get("status")).isEqualTo("COMPLETED");
        assertThat(Files.exists(workspaceDir.resolve("AGENT_TASK_NOTE.md"))).isTrue();

        var events = getList("/api/tasks/" + taskId + "/events");
        assertThat(events).extracting(event -> event.get("eventType"))
                .contains("TaskCreated", "AgentRunStarted", "PlanCreated", "FileCreated",
                        "CommandExecuted", "ValidationCompleted", "AgentFinished");

        var changes = getList("/api/tasks/" + taskId + "/changes");
        assertThat(changes).hasSize(1);
        assertThat(changes.getFirst().get("path")).isEqualTo("AGENT_TASK_NOTE.md");
        assertThat(changes.getFirst().get("afterHash")).isNotNull();

        var report = getMap("/api/tasks/" + taskId + "/report");
        assertThat(report.get("contentMd").toString()).contains("Validation passed");
        assertThat(report.get("contentMd").toString()).contains("AGENT_TASK_NOTE.md");
    }

    @Test
    void approvingValidationCommandResumesWithoutReplanning() throws Exception {
        Files.writeString(workspaceDir.resolve("README.md"), "approval resume workspace");

        var workspace = post("/api/workspaces", Map.of(
                "name", "approval-resume",
                "rootPath", workspaceDir.toString(),
                "trusted", true));
        var workspaceId = workspace.get("id").toString();

        var task = post("/api/tasks", Map.of(
                "workspaceId", workspaceId,
                "title", "approval resume task",
                "userRequest", "create an audited note and validate"));
        var taskId = task.get("id").toString();

        var run = post("/api/tasks/" + taskId + "/start", null);
        var runId = run.get("id").toString();

        assertThat(run.get("status")).isEqualTo("WAITING_APPROVAL");
        assertThat(jdbc.queryForObject("select count(*) from plan where run_id = ?", Integer.class, java.util.UUID.fromString(runId)))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from command_execution where run_id = ?", Integer.class, java.util.UUID.fromString(runId)))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select status from agent_step where run_id = ? and step_type = 'VALIDATE'",
                String.class, java.util.UUID.fromString(runId))).isEqualTo("WAITING_APPROVAL");

        var approvals = getList("/api/approvals?status=PENDING");
        assertThat(approvals).hasSize(1);
        var approvalId = approvals.getFirst().get("id").toString();

        post("/api/approvals/" + approvalId + "/approve", Map.of("resolvedBy", "test"));

        var completedRun = getMap("/api/runs/" + runId);
        assertThat(completedRun.get("status")).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("select count(*) from plan where run_id = ?", Integer.class, java.util.UUID.fromString(runId)))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from command_execution where run_id = ?", Integer.class, java.util.UUID.fromString(runId)))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select status from command_execution where run_id = ?", String.class, java.util.UUID.fromString(runId)))
                .isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("""
                select tc.status
                  from tool_call tc
                  join agent_action aa on aa.id = tc.action_id
                  join agent_step astep on astep.id = aa.step_id
                 where astep.run_id = ?
                   and tc.tool_name = 'run_command'
                """, String.class, java.util.UUID.fromString(runId))).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("""
                select count(*)
                  from tool_result tr
                  join tool_call tc on tc.id = tr.tool_call_id
                  join agent_action aa on aa.id = tc.action_id
                  join agent_step astep on astep.id = aa.step_id
                 where astep.run_id = ?
                   and tc.tool_name = 'run_command'
                   and tr.success = true
                """, Integer.class, java.util.UUID.fromString(runId))).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select status from agent_step where run_id = ? and step_type = 'VALIDATE'",
                String.class, java.util.UUID.fromString(runId))).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("select approval_id is not null from command_execution where run_id = ?", Boolean.class, java.util.UUID.fromString(runId)))
                .isTrue();

        var events = getList("/api/tasks/" + taskId + "/events");
        assertThat(events.stream().filter(event -> "PlanCreated".equals(event.get("eventType"))).count())
                .isEqualTo(1);
        assertThat(events.stream()
                .filter(event -> "StepStarted".equals(event.get("eventType")))
                .filter(event -> "UNDERSTAND_TASK".equals(event.get("outputSummary")))
                .count()).isEqualTo(1);
    }

    private Map<String, Object> post(String path, Object body) {
        try {
            var json = body == null ? "" : objectMapper.writeValueAsString(body);
            var response = http.send(HttpRequest.newBuilder(uri(path))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(json))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode())
                    .describedAs("POST %s response body: %s", path, response.body())
                    .isBetween(200, 299);
            return objectMapper.readValue(response.body(), mapType);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private Map<String, Object> getMap(String path) {
        try {
            var response = http.send(HttpRequest.newBuilder(uri(path)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode())
                    .describedAs("GET %s response body: %s", path, response.body())
                    .isBetween(200, 299);
            return objectMapper.readValue(response.body(), mapType);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private List<Map<String, Object>> getList(String path) {
        try {
            var response = http.send(HttpRequest.newBuilder(uri(path)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode())
                    .describedAs("GET %s response body: %s", path, response.body())
                    .isBetween(200, 299);
            return objectMapper.readValue(response.body(), listType);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
