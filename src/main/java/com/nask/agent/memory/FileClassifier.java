package com.nask.agent.memory;

import com.nask.agent.common.Domain;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Classifies workspace-relative paths into coarse project file types.
 */
@Component
public class FileClassifier {
    /**
     * Returns the scanner file type for a workspace-relative path.
     */
    public Domain.ProjectFileType classify(String relativePath) {
        var normalized = normalize(relativePath);
        var lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.equals("pom.xml") || lower.equals("build.gradle") || lower.equals("build.gradle.kts")
                || lower.equals("settings.gradle") || lower.equals("settings.gradle.kts")
                || lower.equals("package.json")) {
            return Domain.ProjectFileType.BUILD_FILE;
        }
        if (lower.startsWith("docs/") || lower.equals("readme.md") || lower.endsWith("/readme.md")
                || lower.endsWith(".md") || lower.endsWith(".adoc")) {
            return Domain.ProjectFileType.DOCS;
        }
        if (lower.contains("/db/migration/") || lower.startsWith("db/migration/")
                || lower.contains("/migrations/")) {
            return Domain.ProjectFileType.MIGRATION;
        }
        if (lower.startsWith("src/test/") || lower.contains("/src/test/") || lower.endsWith("test.java")
                || lower.endsWith("tests.java")) {
            return Domain.ProjectFileType.TEST;
        }
        if (lower.endsWith(".properties") || lower.endsWith(".yml") || lower.endsWith(".yaml")
                || lower.endsWith(".json") || lower.endsWith(".xml") || lower.endsWith(".toml")
                || lower.endsWith(".conf")) {
            return Domain.ProjectFileType.CONFIG;
        }
        if (lower.startsWith("src/main/") || lower.contains("/src/main/") || lower.endsWith(".java")
                || lower.endsWith(".kt") || lower.endsWith(".js") || lower.endsWith(".ts")
                || lower.endsWith(".py")) {
            return Domain.ProjectFileType.SOURCE;
        }
        return Domain.ProjectFileType.OTHER;
    }

    private String normalize(String path) {
        return path == null ? "" : path.replace('\\', '/');
    }
}
