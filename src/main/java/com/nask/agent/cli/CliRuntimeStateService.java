package com.nask.agent.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * File-backed CLI settings/session state for local dashboard visibility.
 */
@Service
public class CliRuntimeStateService {
    private final ObjectMapper mapper;
    private final Path home;

    public CliRuntimeStateService(ObjectMapper mapper) {
        this.mapper = mapper;
        this.home = Path.of(System.getProperty("user.home"), ".auditable-agent");
    }

    public CliRuntimeSettings readSettings() {
        var path = home.resolve("config.toml");
        if (!Files.exists(path)) {
            return CliRuntimeSettings.defaults();
        }
        try {
            var values = new java.util.LinkedHashMap<String, String>();
            for (var line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                var trimmed = line.trim();
                if (trimmed.isBlank() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                    continue;
                }
                var parts = trimmed.split("=", 2);
                values.put(parts[0].trim(), unquote(parts[1].trim()));
            }
            var defaults = CliRuntimeSettings.defaults();
            return new CliRuntimeSettings(
                    values.getOrDefault("base_url", defaults.baseUrl()),
                    values.getOrDefault("workspace_id", defaults.workspaceId()),
                    values.getOrDefault("workflow", defaults.workflow()),
                    values.getOrDefault("permission_preset", defaults.permissionPreset()),
                    values.getOrDefault("model", defaults.model()),
                    values.getOrDefault("profile", defaults.profile()));
        } catch (IOException e) {
            return CliRuntimeSettings.defaults();
        }
    }

    public CliRuntimeSettings writeSettings(CliRuntimeSettings settings) throws IOException {
        Files.createDirectories(home);
        var content = """
                base_url = "%s"
                workflow = "%s"
                permission_preset = "%s"
                model = "%s"
                profile = "%s"
                """.formatted(escape(settings.baseUrl()), escape(settings.workflow()),
                escape(settings.permissionPreset()), escape(settings.model()), escape(settings.profile()));
        Files.writeString(home.resolve("config.toml"), content, StandardCharsets.UTF_8);
        return settings;
    }

    public List<CliSessionSummary> sessions() throws IOException {
        var dir = home.resolve("sessions");
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (var stream = Files.list(dir)) {
            return stream.filter(path -> path.getFileName().toString().endsWith(".jsonl"))
                    .map(this::readSession)
                    .filter(java.util.Objects::nonNull)
                    .sorted(Comparator.comparing(CliSessionSummary::updatedAt).reversed())
                    .limit(50)
                    .toList();
        }
    }

    private CliSessionSummary readSession(Path path) {
        try {
            String last = null;
            for (var line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                if (!line.isBlank()) {
                    last = line;
                }
            }
            if (last == null) {
                return null;
            }
            var node = mapper.readTree(last);
            var file = path.getFileName().toString();
            var id = file.substring(0, file.length() - ".jsonl".length());
            return new CliSessionSummary(id, text(node, "workspaceId"), text(node, "conversationId"), text(node, "runId"),
                    text(node, "taskId"), text(node, "status"), Instant.parse(text(node, "timestamp")));
        } catch (Exception e) {
            return null;
        }
    }

    private static String text(com.fasterxml.jackson.databind.JsonNode node, String field) {
        var value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText();
    }

    private static String unquote(String value) {
        return value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2
                ? value.substring(1, value.length() - 1).replace("\\\"", "\"")
                : value;
    }

    private static String escape(String value) {
        return (value == null ? "" : value).replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
