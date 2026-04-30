package com.nask.agent.workspace;

import com.nask.agent.TestFiles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WorkspacePathGuardTests {
    private final WorkspacePathGuard guard = new WorkspacePathGuard();

    Path tempDir;

    @BeforeEach
    void createWorkspaceRoot() throws IOException {
        tempDir = TestFiles.createTempDirectory("agent-path-guard-");
    }

    @AfterEach
    void deleteWorkspaceRoot() {
        TestFiles.deleteRecursivelyQuietly(tempDir);
    }

    @Test
    void allowsPathInsideWorkspace() {
        var check = guard.check(workspace(), "src/Main.java", false);

        assertThat(check.allowed()).isTrue();
        assertThat(check.relativePath()).isEqualTo("src/Main.java");
    }

    @Test
    void blocksPathOutsideWorkspace() {
        var check = guard.check(workspace(), "../outside.txt", false);

        assertThat(check.allowed()).isFalse();
        assertThat(check.reason()).contains("outside trusted workspace");
    }

    @Test
    void blocksGitDirectoryWrite() {
        var check = guard.check(workspace(), ".git/config", true);

        assertThat(check.allowed()).isFalse();
        assertThat(check.reason()).contains(".git");
    }

    @Test
    void marksSensitiveAndBlockedSensitiveFiles() {
        var env = guard.check(workspace(), ".env", false);
        var key = guard.check(workspace(), "id_rsa", false);

        assertThat(env.sensitive()).isTrue();
        assertThat(env.blockedSensitive()).isFalse();
        assertThat(key.sensitive()).isTrue();
        assertThat(key.blockedSensitive()).isTrue();
    }

    @Test
    void blocksSymlinkEscapeForExistingTarget() throws Exception {
        var sandbox = TestFiles.createTempDirectory("agent-symlink-file-");
        var workspaceRoot = sandbox.resolve("workspace");
        var outsideDir = sandbox.resolve("outside");
        Files.createDirectories(workspaceRoot);
        Files.createDirectories(outsideDir);
        var outsideFile = outsideDir.resolve("secret.txt");
        var link = workspaceRoot.resolve("link.txt");
        try {
            Files.writeString(outsideFile, "secret");
            Files.createSymbolicLink(link, outsideFile);
        } catch (UnsupportedOperationException | IOException | SecurityException e) {
            TestFiles.deleteRecursivelyQuietly(sandbox);
            Assumptions.assumeTrue(false, "Symbolic link creation is unavailable: " + e.getMessage());
        }

        try {
            var check = guard.check(workspace(workspaceRoot), "link.txt", false);

            assertThat(check.allowed()).isFalse();
            assertThat(check.reason()).contains("resolves outside trusted workspace");
        } finally {
            Files.deleteIfExists(link);
            TestFiles.deleteRecursivelyQuietly(sandbox);
        }
    }

    @Test
    void blocksWriteThroughSymlinkedParent() throws Exception {
        var sandbox = TestFiles.createTempDirectory("agent-symlink-write-");
        var workspaceRoot = sandbox.resolve("workspace");
        var outsideDir = sandbox.resolve("outside");
        Files.createDirectories(workspaceRoot);
        Files.createDirectories(outsideDir);
        var outsideFile = outsideDir.resolve("outside.txt");
        var link = workspaceRoot.resolve("linked-file.txt");
        try {
            Files.writeString(outsideFile, "outside");
            Files.createSymbolicLink(link, outsideFile);
        } catch (UnsupportedOperationException | IOException | SecurityException e) {
            TestFiles.deleteRecursivelyQuietly(sandbox);
            Assumptions.assumeTrue(false, "Symbolic link creation is unavailable: " + e.getMessage());
        }

        try {
            var check = guard.check(workspace(workspaceRoot), "linked-file.txt", true);

            assertThat(check.allowed()).isFalse();
            assertThat(check.reason()).contains("outside trusted workspace");
        } finally {
            Files.deleteIfExists(link);
            TestFiles.deleteRecursivelyQuietly(sandbox);
        }
    }

    private Workspace workspace() {
        return workspace(tempDir);
    }

    private Workspace workspace(Path root) {
        return new Workspace(UUID.randomUUID(), "test", root.toString(), true,
                List.of("FILE_READ", "FILE_CREATE", "FILE_MODIFY"),
                List.of(".git"),
                List.of(".env", ".env.*", "*.pem", "*.key", "id_rsa", "id_ed25519"),
                Instant.now(), null);
    }
}
