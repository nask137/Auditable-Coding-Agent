package com.nask.agent.file;

import com.nask.agent.TestFiles;
import com.nask.agent.approval.ApprovalService;
import com.nask.agent.audit.AuditService;
import com.nask.agent.common.AgentSettings;
import com.nask.agent.common.Domain;
import com.nask.agent.permission.PermissionService;
import com.nask.agent.tool.ToolCallRecord;
import com.nask.agent.tool.ToolExecutionContext;
import com.nask.agent.tool.ToolRecordRepository;
import com.nask.agent.workspace.Workspace;
import com.nask.agent.workspace.WorkspaceIgnoreService;
import com.nask.agent.workspace.WorkspacePathGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileToolServiceTests {
    private final ToolRecordRepository toolRecords = mock(ToolRecordRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final WorkspaceIgnoreService ignoreService = mock(WorkspaceIgnoreService.class);
    private final FileToolService service = new FileToolService(new WorkspacePathGuard(), ignoreService,
            new PermissionService(),
            mock(ApprovalService.class), toolRecords, mock(FileChangeRepository.class), auditService,
            new DiffSupport(), new AgentSettings(10, 20, 1000, 300, 3, 2, 2, 3, 120, 200000));

    private Path workspaceRoot;

    @BeforeEach
    void setup() throws Exception {
        workspaceRoot = TestFiles.createTempDirectory("agent-file-list-");
        Files.createDirectories(workspaceRoot.resolve("src/main/java"));
        Files.createDirectories(workspaceRoot.resolve("target/classes"));
        Files.createDirectories(workspaceRoot.resolve("web-dashboard/node_modules/package"));
        Files.writeString(workspaceRoot.resolve("README.md"), "read me");
        Files.writeString(workspaceRoot.resolve("pom.xml"), "<project/>");
        Files.writeString(workspaceRoot.resolve("src/main/java/App.java"), "class App {}");
        Files.writeString(workspaceRoot.resolve("src/main/java/secret.txt"), "secret");
        Files.writeString(workspaceRoot.resolve(".env"), "secret");
        Files.writeString(workspaceRoot.resolve("target/classes/App.class"), "compiled");
        Files.writeString(workspaceRoot.resolve("web-dashboard/node_modules/package/index.js"), "dependency");
        Files.createDirectories(workspaceRoot.resolve(".git"));
        Files.writeString(workspaceRoot.resolve(".git/config"), "secret repo internals");
        when(toolRecords.insertCall(any(), any(), any(), any(), any())).thenReturn(new ToolCallRecord(
                UUID.randomUUID(), UUID.randomUUID(), "list_files", Domain.PermissionLevel.READ_ONLY.name(),
                "List files", java.util.Map.of(), Domain.ToolCallStatus.RUNNING.name(), Instant.now(), null));
        when(ignoreService.ignoreView(any())).thenReturn(new WorkspaceIgnoreService.IgnoreView(
                List.of(".env", "src/main/java/secret.txt"), List.of("target/", "web-dashboard/node_modules/"), "test", 0));
    }

    @AfterEach
    void cleanup() {
        TestFiles.deleteRecursivelyQuietly(workspaceRoot);
    }

    @Test
    void listsReadableFilesUnderWorkspaceRoot() {
        var result = service.listFiles(context(), ".", 4);

        assertThat(result.success()).isTrue();
        assertThat(result.payload().get("files"))
                .asList()
                .contains("README.md", "pom.xml", "src/main/java/App.java")
                .doesNotContain(".env", ".git/config", "src/main/java/secret.txt", "target/classes/App.class",
                        "web-dashboard/node_modules/package/index.js");
    }

    private ToolExecutionContext context() {
        var workspace = new Workspace(UUID.randomUUID(), "workspace", workspaceRoot.toString(), true,
                List.of("FILE_READ", "FILE_CREATE", "FILE_MODIFY"), List.of(".git"),
                List.of(".env", ".env.*", "*.pem", "*.key", "id_rsa", "id_ed25519"),
                Instant.now(), null);
        var taskId = UUID.randomUUID();
        return new ToolExecutionContext(taskId, taskId, UUID.randomUUID(), UUID.randomUUID(), workspace);
    }
}
