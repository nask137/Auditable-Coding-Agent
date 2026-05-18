package com.nask.agent.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal TOML-like config used by the local CLI.
 */
class CliLocalConfig {
    private final Path home = Path.of(System.getProperty("user.home"), ".auditable-agent");
    private final Map<String, String> values = new LinkedHashMap<>();

    static CliLocalConfig load() {
        var config = new CliLocalConfig();
        var path = config.home.resolve("config.toml");
        if (!Files.exists(path)) {
            config.defaults();
            return config;
        }
        try {
            for (var line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                var trimmed = line.trim();
                if (trimmed.isBlank() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                    continue;
                }
                var parts = trimmed.split("=", 2);
                config.values.put(parts[0].trim(), unquote(parts[1].trim()));
            }
        } catch (IOException e) {
            config.defaults();
        }
        config.defaults();
        return config;
    }

    String get(String key) {
        return values.getOrDefault(key, "");
    }

    void set(String key, String value) {
        values.put(key, value == null ? "" : value);
    }

    void save() throws IOException {
        Files.createDirectories(home);
        var builder = new StringBuilder();
        for (var entry : values.entrySet()) {
            if ("workspace_id".equals(entry.getKey())) {
                continue;
            }
            builder.append(entry.getKey()).append(" = \"")
                    .append(entry.getValue().replace("\\", "\\\\").replace("\"", "\\\""))
                    .append("\"\n");
        }
        Files.writeString(home.resolve("config.toml"), builder.toString(), StandardCharsets.UTF_8);
    }

    private void defaults() {
        values.putIfAbsent("base_url", "http://localhost:8080");
        values.putIfAbsent("workflow", "coding-agent");
        values.putIfAbsent("permission_preset", "workspace-write");
        values.putIfAbsent("model", "");
        values.putIfAbsent("profile", "default");
    }

    private static String unquote(String value) {
        return value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2
                ? value.substring(1, value.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\")
                : value;
    }
}
