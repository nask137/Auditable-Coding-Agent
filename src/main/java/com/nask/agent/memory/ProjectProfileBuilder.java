package com.nask.agent.memory;

import com.nask.agent.common.Domain;
import com.nask.agent.workspace.Workspace;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Builds a stable project profile from scanner observations.
 */
@Component
public class ProjectProfileBuilder {
    /**
     * Converts scanner observations into a workspace-level project profile.
     */
    public ProjectProfile build(Workspace workspace, UUID scanRunId, ProjectScanResult scanResult) {
        var languages = new LinkedHashSet<String>();
        var frameworks = new LinkedHashSet<String>();
        var buildTools = new LinkedHashSet<String>();
        var testTools = new LinkedHashSet<String>();
        var packageManagers = new LinkedHashSet<String>();
        var entrypoints = new LinkedHashSet<String>();
        var importantPaths = new LinkedHashSet<String>();
        var docsPaths = new LinkedHashSet<String>();
        var configPaths = new LinkedHashSet<String>();

        for (var observation : scanResult.observations()) {
            var path = observation.path();
            var lower = path.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".java") || lower.equals("pom.xml") || lower.startsWith("src/main/java/")) {
                languages.add("Java");
            }
            if (lower.equals("pom.xml")) {
                buildTools.add("Maven");
                packageManagers.add("Maven");
                importantPaths.add(path);
            }
            if (observation.fileType() == Domain.ProjectFileType.DOCS) {
                docsPaths.add(path);
            }
            if (observation.fileType() == Domain.ProjectFileType.CONFIG
                    || observation.fileType() == Domain.ProjectFileType.BUILD_FILE) {
                configPaths.add(path);
            }
            if (lower.startsWith("src/main/java/")) {
                importantPaths.add("src/main/java");
            }
            if (lower.startsWith("src/test/java/")) {
                importantPaths.add("src/test/java");
                testTools.add("JUnit");
            }
            if (lower.startsWith("src/main/resources/db/migration/")) {
                importantPaths.add("src/main/resources/db/migration");
                frameworks.add("Flyway");
            }
            if (looksLikeSpringBootEntrypoint(observation)) {
                frameworks.add("Spring Boot");
                entrypoints.add(path);
            }
            if (contains(observation, "org.springframework.boot")) {
                frameworks.add("Spring Boot");
            }
            if (contains(observation, "org.junit") || contains(observation, "junit-jupiter")
                    || contains(observation, "spring-boot-starter-test")) {
                testTools.add("JUnit");
            }
        }

        var languageSummary = languages.isEmpty() ? "Unknown" : String.join(", ", languages);
        var confidence = confidence(languages, buildTools, frameworks, testTools, docsPaths);
        var now = Instant.now();
        return new ProjectProfile(UUID.randomUUID(), workspace.id(), languageSummary, list(frameworks),
                list(buildTools), list(testTools), list(packageManagers), list(entrypoints), list(importantPaths),
                list(docsPaths), list(configPaths), scanRunId, confidence, now, now);
    }

    private boolean looksLikeSpringBootEntrypoint(ProjectScanObservation observation) {
        return observation.path().endsWith(".java") && contains(observation, "@SpringBootApplication");
    }

    private boolean contains(ProjectScanObservation observation, String needle) {
        return observation.contentSample() != null && observation.contentSample().contains(needle);
    }

    private double confidence(LinkedHashSet<String> languages, LinkedHashSet<String> buildTools,
                              LinkedHashSet<String> frameworks, LinkedHashSet<String> testTools,
                              LinkedHashSet<String> docsPaths) {
        var score = 0.25;
        if (!languages.isEmpty()) {
            score += 0.2;
        }
        if (!buildTools.isEmpty()) {
            score += 0.2;
        }
        if (!frameworks.isEmpty()) {
            score += 0.15;
        }
        if (!testTools.isEmpty()) {
            score += 0.1;
        }
        if (!docsPaths.isEmpty()) {
            score += 0.1;
        }
        return Math.min(0.95, score);
    }

    private List<String> list(LinkedHashSet<String> values) {
        return values.stream().toList();
    }
}
