package com.nask.agent.git;

import com.nask.agent.TestFiles;
import com.nask.agent.audit.AuditService;
import com.nask.agent.common.AgentSettings;
import com.nask.agent.common.Domain;
import com.nask.agent.permission.PermissionService;
import com.nask.agent.tool.ToolCallRecord;
import com.nask.agent.tool.ToolExecutionContext;
import com.nask.agent.tool.ToolRecordRepository;
import com.nask.agent.workspace.Workspace;
import com.nask.agent.workspace.WorkspacePathGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GitToolServiceTests {
    private Path workspaceDir;

    @AfterEach
    void cleanup() {
        TestFiles.deleteRecursivelyQuietly(workspaceDir);
    }

    @Test
    void gitDiffFiltersSensitiveFilesThroughPermissionService() throws Exception {
        assumeTrue(run(null, "git", "--version") == 0);
        workspaceDir = TestFiles.createTempDirectory("agent-git-tool-");
        Files.writeString(workspaceDir.resolve("App.java"), "class App { String value() { return \"old\"; } }\n");
        Files.writeString(workspaceDir.resolve("secret.env"), "TOKEN=old\n");
        assertThat(run(workspaceDir, "git", "init")).isZero();
        assertThat(run(workspaceDir, "git", "config", "user.email", "test@example.com")).isZero();
        assertThat(run(workspaceDir, "git", "config", "user.name", "Test User")).isZero();
        assertThat(run(workspaceDir, "git", "add", ".")).isZero();
        assertThat(run(workspaceDir, "git", "commit", "-m", "init")).isZero();

        Files.writeString(workspaceDir.resolve("App.java"), "class App { String value() { return \"new\"; } }\n");
        Files.writeString(workspaceDir.resolve("secret.env"), "TOKEN=new\n");

        var service = service();
        var result = service.diff(context(workspaceDir, List.of("*.env")), ".");

        assertThat(result.success()).isTrue();
        assertThat(result.payload().get("includedFiles")).isEqualTo(List.of("App.java"));
        assertThat(result.payload().get("filteredFiles")).isEqualTo(List.of("secret.env"));
        var output = result.payload().get("output").toString();
        assertThat(output).contains("App.java");
        assertThat(output).contains("return \"new\"");
        assertThat(output).doesNotContain("TOKEN=old");
        assertThat(output).doesNotContain("TOKEN=new");
    }

    @Test
    void gitStatusBlocksOnNonGitWorkspace() throws Exception {
        workspaceDir = TestFiles.createTempDirectory("agent-git-tool-");

        var result = service().status(context(workspaceDir, List.of()), ".");

        assertThat(result.blocked()).isTrue();
    }

    @Test
    void gitDiffKeepsPatchContentWhenWorkingDirectoryIsSubdirectory() throws Exception {
        assumeTrue(run(null, "git", "--version") == 0);
        workspaceDir = TestFiles.createTempDirectory("agent-git-tool-");
        var sourceDir = Files.createDirectories(workspaceDir.resolve("src/main/java"));
        Files.writeString(sourceDir.resolve("App.java"), "class App { String value() { return \"old\"; } }\n");
        assertThat(run(workspaceDir, "git", "init")).isZero();
        assertThat(run(workspaceDir, "git", "config", "user.email", "test@example.com")).isZero();
        assertThat(run(workspaceDir, "git", "config", "user.name", "Test User")).isZero();
        assertThat(run(workspaceDir, "git", "add", ".")).isZero();
        assertThat(run(workspaceDir, "git", "commit", "-m", "init")).isZero();

        Files.writeString(sourceDir.resolve("App.java"), "class App { String value() { return \"new\"; } }\n");

        var result = service().diff(context(workspaceDir, List.of()), "src/main/java");

        assertThat(result.success()).isTrue();
        assertThat(result.payload().get("includedFiles")).isEqualTo(List.of("src/main/java/App.java"));
        var output = result.payload().get("output").toString();
        assertThat(output).contains("src/main/java/App.java");
        assertThat(output).contains("-class App { String value() { return \"old\"; } }");
        assertThat(output).contains("+class App { String value() { return \"new\"; } }");
    }

    @Test
    void gitDiffIgnoresSuccessfulStderrWarningsWhenParsingChangedPaths() throws Exception {
        assumeTrue(run(null, "git", "--version") == 0);
        workspaceDir = TestFiles.createTempDirectory("agent-git-tool-");
        var source = workspaceDir.resolve("src/main/java/cdu/wangnan/App.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "class App { String value() { return \"old\"; } }\n");
        assertThat(run(workspaceDir, "git", "init")).isZero();
        assertThat(run(workspaceDir, "git", "config", "user.email", "test@example.com")).isZero();
        assertThat(run(workspaceDir, "git", "config", "user.name", "Test User")).isZero();
        assertThat(run(workspaceDir, "git", "add", ".")).isZero();
        assertThat(run(workspaceDir, "git", "commit", "-m", "init")).isZero();
        assertThat(run(workspaceDir, "git", "config", "core.safecrlf", "warn")).isZero();
        assertThat(run(workspaceDir, "git", "config", "core.autocrlf", "true")).isZero();

        Files.writeString(source, "class App { String value() { return \"new\"; } }\r\n");

        var result = service().diff(context(workspaceDir, List.of()), ".");

        assertThat(result.success()).isTrue();
        assertThat(result.payload().get("includedFiles")).isEqualTo(List.of("src/main/java/cdu/wangnan/App.java"));
    }

    @Test
    void gitShowRejectsOptionShapedRevision() throws Exception {
        assumeTrue(run(null, "git", "--version") == 0);
        workspaceDir = TestFiles.createTempDirectory("agent-git-tool-");
        assertThat(run(workspaceDir, "git", "init")).isZero();

        var result = service().show(context(workspaceDir, List.of()), ".", "--output=AGENT_TASK_NOTE.md");

        assertThat(result.blocked()).isTrue();
        assertThat(result.summary()).contains("must not be an option");
        assertThat(workspaceDir.resolve("AGENT_TASK_NOTE.md")).doesNotExist();
    }

    @Test
    void gitCommitBypassesRepositoryHooks() throws Exception {
        assumeTrue(run(null, "git", "--version") == 0);
        workspaceDir = TestFiles.createTempDirectory("agent-git-tool-");
        assertThat(run(workspaceDir, "git", "init")).isZero();
        assertThat(run(workspaceDir, "git", "config", "user.email", "test@example.com")).isZero();
        assertThat(run(workspaceDir, "git", "config", "user.name", "Test User")).isZero();
        Files.writeString(workspaceDir.resolve("App.java"), "class App {}\n");
        assertThat(run(workspaceDir, "git", "add", ".")).isZero();
        var hook = workspaceDir.resolve(".git/hooks/pre-commit");
        Files.writeString(hook, "#!/bin/sh\nexit 1\n");

        var result = service().commit(context(workspaceDir, List.of()), ".", "init");

        assertThat(result.success()).isTrue();
        assertThat(result.payload().get("command")).isEqualTo("git commit --no-verify -m init");
        assertThat(run(workspaceDir, "git", "log", "--oneline", "--max-count=1")).isZero();
    }

    private GitToolService service() {
        var toolRecords = mock(ToolRecordRepository.class);
        when(toolRecords.insertCall(any(), anyString(), any(), anyString(), any()))
                .thenReturn(new ToolCallRecord(UUID.randomUUID(), UUID.randomUUID(), "git_diff",
                        Domain.PermissionLevel.GIT_READ.name(), "Git diff", Map.of(),
                        Domain.ToolCallStatus.RUNNING.name(), Instant.now(), null));
        when(toolRecords.insertResult(any(), anyBoolean(), anyString(), any(), anyString(), any()))
                .thenReturn(null);
        return new GitToolService(new WorkspacePathGuard(), new PermissionService(), toolRecords,
                mock(AuditService.class), new AgentSettings(20, 50, 5, 300, 3, 30, 200000));
    }

    private ToolExecutionContext context(Path root, List<String> sensitivePatterns) {
        var workspace = new Workspace(UUID.randomUUID(), "test", root.toString(), true, List.of(),
                List.of(), sensitivePatterns, Instant.now(), null);
        return new ToolExecutionContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), workspace);
    }

    private int run(Path cwd, String... command) throws Exception {
        var builder = new ProcessBuilder(command).redirectErrorStream(true);
        if (cwd != null) {
            builder.directory(cwd.toFile());
        }
        var process = builder.start();
        process.getInputStream().readAllBytes();
        return process.waitFor();
    }
}
