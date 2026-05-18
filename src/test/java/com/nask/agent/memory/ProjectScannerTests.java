package com.nask.agent.memory;

import com.nask.agent.TestFiles;
import com.nask.agent.common.AgentSettings;
import com.nask.agent.workspace.Workspace;
import com.nask.agent.workspace.WorkspaceIgnoreService;
import com.nask.agent.workspace.WorkspacePathGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectScannerTests {
    private Path workspaceDir;

    @AfterEach
    void cleanup() {
        TestFiles.deleteRecursivelyQuietly(workspaceDir);
    }

    @Test
    void appliesIgnoreRulesAndReadBudgetsWithoutFailingScan() throws Exception {
        workspaceDir = TestFiles.createTempDirectory("project-scanner-");
        Files.createDirectories(workspaceDir.resolve("src/main/java/app"));
        Files.createDirectories(workspaceDir.resolve("target/generated"));
        Files.writeString(workspaceDir.resolve("pom.xml"), "<project><groupId>org.springframework.boot</groupId></project>");
        Files.writeString(workspaceDir.resolve("src/main/java/app/App.java"), "@SpringBootApplication class App {}");
        Files.writeString(workspaceDir.resolve("target/generated/Ignored.java"), "class Ignored {}");
        Files.writeString(workspaceDir.resolve("large.properties"), "x".repeat(64));
        var settings = new AgentSettings(10, 20, 5, 300, 3, 2, 2, 3, 120, 200000,
                2000, 16, 128);
        var ignoreService = mock(WorkspaceIgnoreService.class);
        when(ignoreService.ignoreView(any())).thenReturn(new WorkspaceIgnoreService.IgnoreView(
                List.of("target/generated/Ignored.java"), List.of(), "test", 0));
        var scanner = new ProjectScanner(new WorkspacePathGuard(), ignoreService, settings, new FileClassifier());

        var result = scanner.scan(workspace());

        assertThat(result.observations()).extracting(ProjectScanObservation::path)
                .contains("pom.xml", "src/main/java/app/App.java")
                .doesNotContain("target/generated/Ignored.java");
        assertThat(result.filesSkipped()).isGreaterThanOrEqualTo(2);
        assertThat(result.metadata().get("skippedReasons").toString())
                .contains("file_too_large")
                .contains("target");
    }

    @Test
    void stopsAtMaxFileBudget() throws Exception {
        workspaceDir = TestFiles.createTempDirectory("project-scanner-budget-");
        Files.writeString(workspaceDir.resolve("a.properties"), "a");
        Files.writeString(workspaceDir.resolve("b.properties"), "b");
        Files.writeString(workspaceDir.resolve("c.properties"), "c");
        var settings = new AgentSettings(10, 20, 5, 300, 3, 2, 2, 3, 120, 200000,
                2, 1024, 4096);
        var ignoreService = mock(WorkspaceIgnoreService.class);
        when(ignoreService.ignoreView(any())).thenReturn(new WorkspaceIgnoreService.IgnoreView(List.of(), "test", 0));
        var scanner = new ProjectScanner(new WorkspacePathGuard(), ignoreService, settings, new FileClassifier());

        var result = scanner.scan(workspace());

        assertThat(result.filesSeen()).isEqualTo(2);
        assertThat(result.observations()).hasSize(2);
    }

    private Workspace workspace() {
        return new Workspace(java.util.UUID.randomUUID(), "workspace", workspaceDir.toString(), true,
                List.of(), List.of(), List.of(), Instant.now(), null);
    }
}
