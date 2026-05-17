package com.nask.agent.workspace;

import com.nask.agent.TestFiles;
import com.nask.agent.common.AgentSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WorkspaceIgnoreServiceTests {
    private final WorkspaceIgnoreService service = new WorkspaceIgnoreService(
            new AgentSettings(10, 20, 1000, 300, 3, 2, 2, 3, 120, 200000));

    private Path workspaceRoot;

    @BeforeEach
    void setup() throws Exception {
        workspaceRoot = TestFiles.createTempDirectory("agent-ignore-service-");
    }

    @AfterEach
    void cleanup() {
        TestFiles.deleteRecursivelyQuietly(workspaceRoot);
    }

    @Test
    void computesIgnoredPrefixesFromGitExcludeStandard() throws Exception {
        requireGit();
        runGit("init");
        Files.writeString(workspaceRoot.resolve(".gitignore"), "target/\nnode_modules/\n");
        Files.createDirectories(workspaceRoot.resolve("target/classes"));
        Files.createDirectories(workspaceRoot.resolve("web/node_modules/pkg"));
        Files.writeString(workspaceRoot.resolve("target/classes/App.class"), "compiled");
        Files.writeString(workspaceRoot.resolve("web/node_modules/pkg/index.js"), "dependency");
        Files.writeString(workspaceRoot.resolve("README.md"), "read me");

        var view = service.ignoreView(workspace());

        assertThat(view.source()).isEqualTo("git_ls_files");
        assertThat(view.ignoredPrefixes()).contains("target/classes/App.class/", "web/node_modules/pkg/index.js/");
        assertThat(view.ignoredPrefixes()).doesNotContain("README.md/");
    }

    @Test
    void returnsEmptyViewOutsideGitWorkspace() {
        var view = service.ignoreView(workspace());

        assertThat(view.ignoredPrefixes()).isEmpty();
        assertThat(view.source()).isEqualTo("not_a_git_workspace");
    }

    private Workspace workspace() {
        return new Workspace(UUID.randomUUID(), "workspace", workspaceRoot.toString(), true,
                List.of("FILE_READ", "FILE_CREATE", "FILE_MODIFY"), List.of(".git"),
                List.of(".env", ".env.*", "*.pem", "*.key", "id_rsa", "id_ed25519"),
                Instant.now(), null);
    }

    private void requireGit() throws Exception {
        var process = new ProcessBuilder("git", "--version").redirectErrorStream(true).start();
        Assumptions.assumeTrue(process.waitFor() == 0, "git is unavailable");
    }

    private void runGit(String... args) throws Exception {
        var command = new java.util.ArrayList<String>();
        command.add("git");
        command.addAll(List.of(args));
        var process = new ProcessBuilder(command).directory(workspaceRoot.toFile()).redirectErrorStream(true).start();
        var output = new String(process.getInputStream().readAllBytes());
        assertThat(process.waitFor()).describedAs(output).isZero();
    }
}
