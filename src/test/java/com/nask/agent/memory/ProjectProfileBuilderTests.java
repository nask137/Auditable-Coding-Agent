package com.nask.agent.memory;

import com.nask.agent.common.Domain;
import com.nask.agent.workspace.Workspace;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectProfileBuilderTests {
    private final ProjectProfileBuilder builder = new ProjectProfileBuilder();

    @Test
    void identifiesJavaSpringBootMavenFlywayJunitAndDocs() {
        var workspace = new Workspace(UUID.randomUUID(), "workspace", "D:/tmp/workspace", true,
                List.of(), List.of(), List.of(), Instant.now(), null);
        var observations = List.of(
                new ProjectScanObservation("pom.xml", Domain.ProjectFileType.BUILD_FILE, 100, true,
                        "<artifactId>spring-boot-starter-test</artifactId><groupId>org.springframework.boot</groupId>"),
                new ProjectScanObservation("README.md", Domain.ProjectFileType.DOCS, 20, true, "readme"),
                new ProjectScanObservation("docs/step4/phase4-work-plan.md", Domain.ProjectFileType.DOCS, 20,
                        false, ""),
                new ProjectScanObservation("src/main/java/com/nask/agent/AgentApplication.java",
                        Domain.ProjectFileType.SOURCE, 100, true, "@SpringBootApplication class AgentApplication {}"),
                new ProjectScanObservation("src/test/java/com/nask/agent/AgentApplicationTests.java",
                        Domain.ProjectFileType.TEST, 100, true, "import org.junit.jupiter.api.Test;"),
                new ProjectScanObservation("src/main/resources/db/migration/V4__phase4_project_memory.sql",
                        Domain.ProjectFileType.MIGRATION, 100, false, ""));
        var scanResult = new ProjectScanResult(observations, observations.size(), observations.size(), 0,
                "summary", Map.of());

        var profile = builder.build(workspace, UUID.randomUUID(), scanResult);

        assertThat(profile.languageSummary()).contains("Java");
        assertThat(profile.buildTools()).contains("Maven");
        assertThat(profile.packageManagers()).contains("Maven");
        assertThat(profile.frameworks()).contains("Spring Boot", "Flyway");
        assertThat(profile.testTools()).contains("JUnit");
        assertThat(profile.docsPaths()).contains("README.md", "docs/step4/phase4-work-plan.md");
        assertThat(profile.entrypoints()).contains("src/main/java/com/nask/agent/AgentApplication.java");
        assertThat(profile.importantPaths()).contains("src/main/java", "src/test/java",
                "src/main/resources/db/migration");
    }
}
