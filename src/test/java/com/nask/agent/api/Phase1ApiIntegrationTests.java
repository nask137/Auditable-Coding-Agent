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
        properties = "spring.flyway.enabled=false")
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
