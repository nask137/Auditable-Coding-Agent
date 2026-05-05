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
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.flyway.enabled=false",
                "agent.llm.provider=stub",
                "agent.llm.api-key="
        })
@ActiveProfiles("test")
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
                create table if not exists workflow_definition (
                  id uuid primary key,
                  name text not null,
                  version int not null,
                  description text not null,
                  mode text not null,
                  enabled boolean not null,
                  definition_json jsonb not null,
                  created_at timestamptz not null,
                  updated_at timestamptz not null,
                  constraint uq_workflow_definition_name_version unique (name, version)
                )
                """);
        jdbc.execute("""
                create table if not exists workflow_node_execution (
                  id uuid primary key,
                  task_id uuid not null references task(id),
                  run_id uuid not null references agent_run(id),
                  workflow_definition_id uuid not null references workflow_definition(id),
                  node_id text not null,
                  node_type text not null,
                  agent_step_id uuid references agent_step(id),
                  status text not null,
                  input_summary text,
                  output_summary text,
                  failure_id uuid references runtime_failure(id),
                  started_at timestamptz not null,
                  completed_at timestamptz,
                  metadata_json jsonb not null
                )
                """);
        jdbc.execute("""
                create table if not exists workflow_edge_decision (
                  id uuid primary key,
                  task_id uuid not null references task(id),
                  run_id uuid not null references agent_run(id),
                  workflow_definition_id uuid not null references workflow_definition(id),
                  from_node_id text not null,
                  to_node_id text not null,
                  edge_type text not null,
                  condition_summary text,
                  decision_reason text not null,
                  selected boolean not null,
                  created_at timestamptz not null,
                  metadata_json jsonb not null
                )
                """);
        jdbc.execute("""
                create table if not exists project_scan_run (
                  id uuid primary key,
                  workspace_id uuid not null references workspace(id),
                  task_id uuid references task(id),
                  run_id uuid references agent_run(id),
                  status text not null,
                  scan_reason text not null,
                  started_at timestamptz not null,
                  completed_at timestamptz,
                  files_seen int not null,
                  files_indexed int not null,
                  files_skipped int not null,
                  summary text not null,
                  metadata_json jsonb not null
                )
                """);
        jdbc.execute("""
                create table if not exists project_profile (
                  id uuid primary key,
                  workspace_id uuid not null references workspace(id),
                  language_summary text not null,
                  frameworks_json jsonb not null,
                  build_tools_json jsonb not null,
                  test_tools_json jsonb not null,
                  package_managers_json jsonb not null,
                  entrypoints_json jsonb not null,
                  important_paths_json jsonb not null,
                  docs_paths_json jsonb not null,
                  config_paths_json jsonb not null,
                  last_scan_run_id uuid references project_scan_run(id),
                  confidence numeric(5, 2) not null,
                  created_at timestamptz not null,
                  updated_at timestamptz not null,
                  constraint uq_project_profile_workspace unique (workspace_id)
                )
                """);
        jdbc.execute("""
                create table if not exists indexed_document (
                  id uuid primary key,
                  workspace_id uuid not null references workspace(id),
                  scan_run_id uuid references project_scan_run(id),
                  path text not null,
                  document_type text not null,
                  title text not null,
                  chunk_index int not null,
                  content text not null,
                  content_hash text not null,
                  line_start int not null,
                  line_end int not null,
                  token_count int not null,
                  metadata_json jsonb not null,
                  created_at timestamptz not null,
                  constraint uq_indexed_document_content unique (workspace_id, document_type, path, chunk_index, content_hash)
                )
                """);
        jdbc.execute("""
                create table if not exists code_symbol (
                  id uuid primary key,
                  workspace_id uuid not null references workspace(id),
                  scan_run_id uuid references project_scan_run(id),
                  path text not null,
                  language text not null,
                  symbol_type text not null,
                  symbol_name text not null,
                  container_name text,
                  signature text not null,
                  line_start int not null,
                  line_end int not null,
                  visibility text,
                  metadata_json jsonb not null,
                  created_at timestamptz not null
                )
                """);
        jdbc.execute("""
                create table if not exists project_memory_item (
                  id uuid primary key,
                  workspace_id uuid not null references workspace(id),
                  memory_type text not null,
                  scope text not null,
                  title text not null,
                  content text not null,
                  source_type text not null,
                  source_id uuid,
                  source_path text,
                  source_line_start int,
                  source_line_end int,
                  status text not null,
                  confidence numeric(5, 2) not null,
                  expires_at timestamptz,
                  created_by text not null,
                  created_at timestamptz not null,
                  approved_by text,
                  approved_at timestamptz,
                  metadata_json jsonb not null
                )
                """);
        jdbc.execute("""
                create table if not exists memory_retrieval (
                  id uuid primary key,
                  workspace_id uuid not null references workspace(id),
                  task_id uuid references task(id),
                  run_id uuid references agent_run(id),
                  workflow_node_execution_id uuid references workflow_node_execution(id),
                  query_text text not null,
                  filters_json jsonb not null,
                  result_refs_json jsonb not null,
                  summary text not null,
                  created_at timestamptz not null
                )
                """);
        jdbc.execute("""
                create table if not exists memory_write_proposal (
                  id uuid primary key,
                  workspace_id uuid not null references workspace(id),
                  task_id uuid references task(id),
                  run_id uuid references agent_run(id),
                  proposal_type text not null,
                  title text not null,
                  content text not null,
                  source_refs_json jsonb not null,
                  status text not null,
                  approval_request_id uuid references approval_request(id),
                  project_memory_item_id uuid references project_memory_item(id),
                  created_at timestamptz not null,
                  resolved_at timestamptz,
                  metadata_json jsonb not null
                )
                """);
        jdbc.execute("""
                truncate table
                  memory_write_proposal,
                  memory_retrieval,
                  project_memory_item,
                  code_symbol,
                  indexed_document,
                  project_profile,
                  project_scan_run,
                  workflow_edge_decision,
                  workflow_node_execution,
                  workflow_definition,
                  user_input_request,
                  runtime_failure,
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
        assertThat(report.get("contentMd").toString()).contains("## Project Context");
        assertThat(report.get("contentMd").toString()).contains("## Workflow");

        var runId = run.get("id").toString();
        var workflow = getMap("/api/runs/" + runId + "/workflow");
        assertThat(workflow.get("name")).isEqualTo("coding-agent");
        var workflowNodes = getList("/api/runs/" + runId + "/workflow/nodes");
        assertThat(workflowNodes).extracting(node -> node.get("nodeId"))
                .contains("understand_task", "inspect_workspace", "project_memory", "code_understanding",
                        "create_plan", "execute_plan_item", "validate", "task_summary_memory", "finish");
        var workflowEdges = getList("/api/runs/" + runId + "/workflow/edges");
        assertThat(workflowEdges).isNotEmpty();
        var proposals = getList("/api/workspaces/" + workspaceId + "/memory-proposals");
        assertThat(proposals).hasSize(1);
        var proposalId = proposals.getFirst().get("id").toString();
        var approvalId = proposals.getFirst().get("approvalRequestId").toString();
        assertThat(proposals.getFirst().get("status")).isEqualTo("WAITING_APPROVAL");
        post("/api/approvals/" + approvalId + "/approve", Map.of("resolvedBy", "test"));
        assertThat(getList("/api/workspaces/" + workspaceId + "/memory-proposals"))
                .filteredOn(proposal -> proposalId.equals(proposal.get("id").toString()))
                .extracting(proposal -> proposal.get("status"))
                .containsExactly("APPROVED");
        assertThat(jdbc.queryForObject("""
                select count(*)
                  from project_memory_item
                 where workspace_id = ?::uuid
                   and status = 'APPROVED'
                   and memory_type = 'TASK_LESSON'
                """, Integer.class, workspaceId)).isEqualTo(1);
        assertThat(getList("/api/workspaces/" + workspaceId + "/memory"))
                .extracting(memory -> memory.get("memoryType"))
                .contains("TASK_LESSON");
    }

    @Test
    void phase4ScanCreatesProjectProfileAndAuditEvents() throws Exception {
        Files.createDirectories(workspaceDir.resolve("src/main/java/com/example"));
        Files.createDirectories(workspaceDir.resolve("src/test/java/com/example"));
        Files.createDirectories(workspaceDir.resolve("src/main/resources/db/migration"));
        Files.createDirectories(workspaceDir.resolve("docs"));
        Files.writeString(workspaceDir.resolve("pom.xml"), """
                <project>
                  <dependencies>
                    <dependency>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot-starter-test</artifactId>
                    </dependency>
                  </dependencies>
                </project>
                """);
        Files.writeString(workspaceDir.resolve("README.md"), "scan me");
        Files.writeString(workspaceDir.resolve("docs/design.md"), "design");
        Files.writeString(workspaceDir.resolve("src/main/java/com/example/App.java"),
                """
                import org.springframework.boot.autoconfigure.SpringBootApplication;
                @SpringBootApplication
                public class App {
                    private static final String NAME = "app";
                    public App() {
                    }
                    public String name() {
                        return NAME;
                    }
                }
                """);
        Files.writeString(workspaceDir.resolve("src/test/java/com/example/AppTests.java"),
                "import org.junit.jupiter.api.Test; class AppTests {}");
        Files.writeString(workspaceDir.resolve("src/main/resources/db/migration/V1__init.sql"),
                "create table example(id int);");

        var workspace = post("/api/workspaces", Map.of(
                "name", "phase4",
                "rootPath", workspaceDir.toString(),
                "trusted", true));
        var workspaceId = workspace.get("id").toString();
        var taskId = UUID.randomUUID();
        var runId = UUID.randomUUID();
        jdbc.update("""
                insert into task (id, workspace_id, title, user_request, status, created_at, updated_at)
                values (?, ?::uuid, 'historical task', 'summarize project', 'COMPLETED', now(), now())
                """, taskId, workspaceId);
        jdbc.update("""
                insert into agent_run (id, task_id, agent_mode, status, started_at, finished_at, failure_reason, runtime_metadata)
                values (?, ?, 'CODE_EDIT', 'COMPLETED', now(), now(), null, '{}'::jsonb)
                """, runId, taskId);
        jdbc.update("""
                insert into task_report (id, task_id, run_id, content_md, created_at)
                values (?, ?, ?, '## Historical Report\n\nUse mvn test for validation.', now())
                """, UUID.randomUUID(), taskId, runId);

        var scan = post("/api/workspaces/" + workspaceId + "/scan", null);
        assertThat(scan.get("status")).isEqualTo("COMPLETED");
        assertThat(((Number) scan.get("filesIndexed")).intValue()).isGreaterThanOrEqualTo(5);

        var profile = getMap("/api/workspaces/" + workspaceId + "/profile");
        assertThat(profile.get("languageSummary").toString()).contains("Java");
        assertThat(stringList(profile.get("frameworks"))).contains("Spring Boot", "Flyway");
        assertThat(stringList(profile.get("buildTools"))).contains("Maven");
        assertThat(stringList(profile.get("testTools"))).contains("JUnit");
        assertThat(stringList(profile.get("docsPaths"))).contains("README.md", "docs/design.md");

        var scanRuns = getList("/api/workspaces/" + workspaceId + "/scan-runs");
        assertThat(scanRuns).hasSize(1);
        assertThat(scanRuns.getFirst().get("status")).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForList("""
                select distinct document_type
                  from indexed_document
                 where workspace_id = ?::uuid
                 order by document_type
                """, String.class, workspaceId)).contains("README", "DOCS", "BUILD_FILE", "MIGRATION", "TASK_REPORT");
        assertThat(jdbc.queryForObject("""
                select count(*)
                  from indexed_document
                 where workspace_id = ?::uuid
                   and document_type = 'TASK_REPORT'
                   and content like '%mvn test%'
                """, Integer.class, workspaceId)).isEqualTo(1);
        Files.writeString(workspaceDir.resolve("README.md"), "scan me after update");
        post("/api/workspaces/" + workspaceId + "/scan", null);
        assertThat(jdbc.queryForObject("""
                select count(*)
                  from indexed_document
                 where workspace_id = ?::uuid
                   and path = 'README.md'
                   and content like '%scan me%'
                """, Integer.class, workspaceId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                select count(*)
                  from indexed_document
                 where workspace_id = ?::uuid
                   and path = 'README.md'
                   and content = 'scan me'
                """, Integer.class, workspaceId)).isZero();
        assertThat(jdbc.queryForList("""
                select symbol_type
                  from code_symbol
                 where workspace_id = ?::uuid
                   and path = 'src/main/java/com/example/App.java'
                 order by line_start
                """, String.class, workspaceId)).contains("CLASS", "CONSTANT", "CONSTRUCTOR", "METHOD");
        var symbols = getList("/api/workspaces/" + workspaceId + "/symbols?query=name&type=METHOD");
        assertThat(symbols).hasSize(1);
        assertThat(symbols.getFirst().get("symbolName")).isEqualTo("name");
        var outline = getList("/api/workspaces/" + workspaceId
                + "/outline?path=src/main/java/com/example/App.java");
        assertThat(outline).extracting(symbol -> symbol.get("symbolName")).contains("App", "NAME", "name");
        jdbc.update("""
                insert into project_memory_item (
                  id, workspace_id, memory_type, scope, title, content, source_type, source_id,
                  source_path, source_line_start, source_line_end, status, confidence, expires_at,
                  created_by, created_at, approved_by, approved_at, metadata_json
                ) values (
                  ?, ?::uuid, 'COMMON_COMMAND', 'workspace', 'Maven test command',
                  'Use mvn test for validation in this repository.', 'USER', null,
                  'README.md', 1, 1, 'APPROVED', 0.95, null, 'test', now(), 'test', now(), '{}'::jsonb
                )
                """, UUID.randomUUID(), workspaceId);
        var context = getMap("/api/workspaces/" + workspaceId
                + "/search-context?q=mvn%20test%20App&documentType=TASK_REPORT&symbolType=CLASS&limit=5");
        assertThat(context.get("retrievalId")).isNotNull();
        assertThat(((List<?>) context.get("results"))).isNotEmpty();
        assertThat(context.get("summary").toString()).contains("Retrieved");
        assertThat(jdbc.queryForObject("""
                select count(*)
                  from memory_retrieval
                 where workspace_id = ?::uuid
                   and query_text = 'mvn test App'
                """, Integer.class, workspaceId)).isEqualTo(1);
        assertThat(getList("/api/workspaces/" + workspaceId + "/memory-retrievals")).hasSize(1);
        var manualMemory = post("/api/workspaces/" + workspaceId + "/memory", Map.of(
                "memoryType", "PROJECT_RULE",
                "title", "Keep changes small",
                "content", "Prefer small auditable patches.",
                "createdBy", "test"));
        assertThat(manualMemory.get("status")).isEqualTo("APPROVED");
        assertThat(getList("/api/workspaces/" + workspaceId + "/memory"))
                .extracting(memory -> memory.get("memoryType"))
                .contains("PROJECT_RULE");
        assertThat(jdbc.queryForObject("""
                select count(*)
                  from audit_event
                 where event_type in ('ProjectScanStarted', 'ProjectScanCompleted')
                   and metadata ->> 'workspaceId' = ?
                """, Integer.class, workspaceId)).isEqualTo(4);
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
        assertThat(jdbc.queryForObject("""
                select status
                  from workflow_node_execution
                 where run_id = ?
                   and node_id = 'validate'
                """, String.class, java.util.UUID.fromString(runId))).isEqualTo("SUCCESS");
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

    @Test
    void denyingValidationCommandFailsPausedStep() throws Exception {
        Files.writeString(workspaceDir.resolve("README.md"), "approval deny workspace");

        var workspace = post("/api/workspaces", Map.of(
                "name", "approval-deny",
                "rootPath", workspaceDir.toString(),
                "trusted", true));
        var workspaceId = workspace.get("id").toString();

        var task = post("/api/tasks", Map.of(
                "workspaceId", workspaceId,
                "title", "approval deny task",
                "userRequest", "create an audited note and validate"));
        var taskId = task.get("id").toString();

        var run = post("/api/tasks/" + taskId + "/start", null);
        var runId = run.get("id").toString();

        assertThat(run.get("status")).isEqualTo("WAITING_APPROVAL");
        assertThat(jdbc.queryForObject(
                "select status from agent_step where run_id = ? and step_type = 'VALIDATE'",
                String.class, java.util.UUID.fromString(runId))).isEqualTo("WAITING_APPROVAL");

        var approvals = getList("/api/approvals?status=PENDING");
        assertThat(approvals).hasSize(1);
        var approvalId = approvals.getFirst().get("id").toString();

        post("/api/approvals/" + approvalId + "/deny", Map.of("resolvedBy", "test", "reason", "not needed"));

        var failedRun = getMap("/api/runs/" + runId);
        assertThat(failedRun.get("status")).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject(
                "select status from agent_step where run_id = ? and step_type = 'VALIDATE'",
                String.class, java.util.UUID.fromString(runId))).isEqualTo("FAILED");
    }

    @Test
    void reviewWorkflowCompletesWithoutFileChanges() throws Exception {
        Files.writeString(workspaceDir.resolve("README.md"), "review workflow workspace");

        var workspace = post("/api/workspaces", Map.of(
                "name", "review-workflow",
                "rootPath", workspaceDir.toString(),
                "trusted", true));
        var workspaceId = workspace.get("id").toString();

        var task = post("/api/tasks", Map.of(
                "workspaceId", workspaceId,
                "title", "review workflow task",
                "userRequest", "review this project"));
        var taskId = task.get("id").toString();

        var run = post("/api/tasks/" + taskId + "/start?workflow=review-agent", null);
        var runId = run.get("id").toString();

        assertThat(run.get("status")).isEqualTo("COMPLETED");
        assertThat(Files.exists(workspaceDir.resolve("AGENT_TASK_NOTE.md"))).isFalse();
        assertThat(getList("/api/tasks/" + taskId + "/changes")).isEmpty();
        assertThat(getMap("/api/runs/" + runId + "/workflow").get("name")).isEqualTo("review-agent");
        assertThat(getList("/api/runs/" + runId + "/workflow/nodes"))
                .extracting(node -> node.get("nodeType"))
                .contains("WORKSPACE_INSPECTION", "CODE_UNDERSTANDING", "REPORT", "FINISH");
    }

    @Test
    void testWorkflowRunsValidationOnly() throws Exception {
        Files.writeString(workspaceDir.resolve("README.md"), "test workflow workspace");

        var workspace = post("/api/workspaces", Map.of(
                "name", "test-workflow",
                "rootPath", workspaceDir.toString(),
                "trusted", true));
        var workspaceId = workspace.get("id").toString();

        post("/api/workspaces/" + workspaceId + "/command-policies", Map.of(
                "policyType", "ALLOWLIST",
                "executable", "java",
                "argsPattern", List.of("-version")));

        var task = post("/api/tasks", Map.of(
                "workspaceId", workspaceId,
                "title", "test workflow task",
                "userRequest", "run tests"));
        var taskId = task.get("id").toString();

        var run = post("/api/tasks/" + taskId + "/start?workflow=test-agent", null);
        var runId = run.get("id").toString();

        assertThat(run.get("status")).isEqualTo("COMPLETED");
        assertThat(Files.exists(workspaceDir.resolve("AGENT_TASK_NOTE.md"))).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from validation_result where run_id = ?",
                Integer.class, java.util.UUID.fromString(runId))).isEqualTo(1);
        assertThat(getMap("/api/runs/" + runId + "/workflow").get("name")).isEqualTo("test-agent");
        assertThat(getList("/api/runs/" + runId + "/workflow/nodes"))
                .extracting(node -> node.get("nodeId"))
                .contains("validate", "report", "finish");
        assertThat(getList("/api/runs/" + runId + "/workflow/edges"))
                .extracting(edge -> edge.get("toNodeId"))
                .contains("report", "finish");
    }

    @Test
    void unknownWorkflowIsRejectedBeforeRunCreation() throws Exception {
        Files.writeString(workspaceDir.resolve("README.md"), "unknown workflow workspace");

        var workspace = post("/api/workspaces", Map.of(
                "name", "unknown-workflow",
                "rootPath", workspaceDir.toString(),
                "trusted", true));
        var workspaceId = workspace.get("id").toString();

        var task = post("/api/tasks", Map.of(
                "workspaceId", workspaceId,
                "title", "unknown workflow task",
                "userRequest", "run tests"));
        var taskId = task.get("id").toString();

        postExpectStatus("/api/tasks/" + taskId + "/start?workflow=missing-agent", null, 404);

        var currentTask = getMap("/api/tasks/" + taskId);
        assertThat(currentTask.get("status")).isEqualTo("CREATED");
        assertThat(jdbc.queryForObject("select count(*) from agent_run where task_id = ?",
                Integer.class, java.util.UUID.fromString(taskId))).isZero();
    }

    @Test
    void approvingTestWorkflowValidationResumesExistingCommand() throws Exception {
        Files.writeString(workspaceDir.resolve("README.md"), "test workflow approval workspace");

        var workspace = post("/api/workspaces", Map.of(
                "name", "test-workflow-approval",
                "rootPath", workspaceDir.toString(),
                "trusted", true));
        var workspaceId = workspace.get("id").toString();

        var task = post("/api/tasks", Map.of(
                "workspaceId", workspaceId,
                "title", "test workflow approval task",
                "userRequest", "run tests"));
        var taskId = task.get("id").toString();

        var run = post("/api/tasks/" + taskId + "/start?workflow=test-agent", null);
        var runId = run.get("id").toString();

        assertThat(run.get("status")).isEqualTo("WAITING_APPROVAL");
        assertThat(jdbc.queryForObject("select count(*) from command_execution where run_id = ?",
                Integer.class, java.util.UUID.fromString(runId))).isEqualTo(1);
        assertThat(jdbc.queryForObject("select status from command_execution where run_id = ?",
                String.class, java.util.UUID.fromString(runId))).isEqualTo("WAITING_APPROVAL");

        var approvals = getList("/api/approvals?status=PENDING");
        var approvalId = approvals.getFirst().get("id").toString();
        post("/api/approvals/" + approvalId + "/approve", Map.of("resolvedBy", "test"));

        var completedRun = getMap("/api/runs/" + runId);
        assertThat(completedRun.get("status")).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("select count(*) from command_execution where run_id = ?",
                Integer.class, java.util.UUID.fromString(runId))).isEqualTo(1);
        assertThat(jdbc.queryForObject("select status from command_execution where run_id = ?",
                String.class, java.util.UUID.fromString(runId))).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("select status from agent_step where run_id = ? and step_type = 'VALIDATE'",
                String.class, java.util.UUID.fromString(runId))).isEqualTo("COMPLETED");
        assertThat(getList("/api/runs/" + runId + "/workflow/nodes"))
                .extracting(node -> node.get("nodeId"))
                .contains("validate", "report", "finish");
        assertThat(jdbc.queryForObject("""
                select count(*)
                  from workflow_node_execution
                 where run_id = ?
                   and node_id = 'validate'
                """, Integer.class, java.util.UUID.fromString(runId))).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                select status
                  from workflow_node_execution
                 where run_id = ?
                   and node_id = 'validate'
                """, String.class, java.util.UUID.fromString(runId))).isEqualTo("SUCCESS");
    }

    @Test
    void denyingTestWorkflowValidationUpdatesWorkflowNode() throws Exception {
        Files.writeString(workspaceDir.resolve("README.md"), "test workflow deny workspace");

        var workspace = post("/api/workspaces", Map.of(
                "name", "test-workflow-deny",
                "rootPath", workspaceDir.toString(),
                "trusted", true));
        var workspaceId = workspace.get("id").toString();

        var task = post("/api/tasks", Map.of(
                "workspaceId", workspaceId,
                "title", "test workflow deny task",
                "userRequest", "run tests"));
        var taskId = task.get("id").toString();

        var run = post("/api/tasks/" + taskId + "/start?workflow=test-agent", null);
        var runId = run.get("id").toString();

        assertThat(run.get("status")).isEqualTo("WAITING_APPROVAL");
        assertThat(jdbc.queryForObject("""
                select status
                  from workflow_node_execution
                 where run_id = ?
                   and node_id = 'validate'
                """, String.class, java.util.UUID.fromString(runId))).isEqualTo("WAITING_APPROVAL");

        var approvals = getList("/api/approvals?status=PENDING");
        var approvalId = approvals.getFirst().get("id").toString();
        post("/api/approvals/" + approvalId + "/deny", Map.of("resolvedBy", "test", "reason", "not needed"));

        var failedRun = getMap("/api/runs/" + runId);
        assertThat(failedRun.get("status")).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("""
                select count(*)
                  from workflow_node_execution
                 where run_id = ?
                   and node_id = 'validate'
                """, Integer.class, java.util.UUID.fromString(runId))).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                select status
                  from workflow_node_execution
                 where run_id = ?
                   and node_id = 'validate'
                """, String.class, java.util.UUID.fromString(runId))).isEqualTo("FAILURE");
    }

    @Test
    void blockedTestWorkflowValidationDoesNotRecordExecutionResult() throws Exception {
        Files.writeString(workspaceDir.resolve("README.md"), "test workflow blocked workspace");

        var workspace = post("/api/workspaces", Map.of(
                "name", "test-workflow-blocked",
                "rootPath", workspaceDir.toString(),
                "trusted", true));
        var workspaceId = workspace.get("id").toString();

        post("/api/workspaces/" + workspaceId + "/command-policies", Map.of(
                "policyType", "BLOCKED",
                "executable", "java",
                "argsPattern", List.of("-version")));

        var task = post("/api/tasks", Map.of(
                "workspaceId", workspaceId,
                "title", "test workflow blocked task",
                "userRequest", "run tests"));
        var taskId = task.get("id").toString();

        var run = post("/api/tasks/" + taskId + "/start?workflow=test-agent", null);
        var runId = run.get("id").toString();

        assertThat(run.get("status")).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("select count(*) from validation_result where run_id = ?",
                Integer.class, java.util.UUID.fromString(runId))).isZero();
        assertThat(jdbc.queryForObject("select status from command_execution where run_id = ?",
                String.class, java.util.UUID.fromString(runId))).isEqualTo("BLOCKED");
        assertThat(jdbc.queryForObject("select status from agent_step where run_id = ? and step_type = 'VALIDATE'",
                String.class, java.util.UUID.fromString(runId))).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("""
                select status
                  from workflow_node_execution
                 where run_id = ?
                   and node_id = 'validate'
                """, String.class, java.util.UUID.fromString(runId))).isEqualTo("BLOCKED");
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

    private void postExpectStatus(String path, Object body, int expectedStatus) {
        try {
            var json = body == null ? "" : objectMapper.writeValueAsString(body);
            var response = http.send(HttpRequest.newBuilder(uri(path))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(json))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode())
                    .describedAs("POST %s response body: %s", path, response.body())
                    .isEqualTo(expectedStatus);
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

    private List<String> stringList(Object value) {
        return ((List<?>) value).stream().map(Object::toString).toList();
    }
}
