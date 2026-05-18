package com.nask.agent.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * File-backed CLI settings/session state for local dashboard visibility.
 */
@Service
public class CliRuntimeStateService {
    private final ObjectMapper mapper;
    private final NamedParameterJdbcTemplate jdbc;
    private final Path home;

    public CliRuntimeStateService(ObjectMapper mapper, NamedParameterJdbcTemplate jdbc) {
        this.mapper = mapper;
        this.jdbc = jdbc;
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
        return jdbc.query("""
                select c.id as conversation_id,
                       c.title as conversation_title,
                       c.workspace_id,
                       count(t.id)::int as task_count,
                       latest.id as latest_task_id,
                       latest.status as latest_task_status,
                       c.updated_at
                  from conversation c
                  left join task t on t.conversation_id = c.id
                  left join lateral (
                    select id, status
                      from task latest_task
                     where latest_task.conversation_id = c.id
                     order by latest_task.prompt_index desc, latest_task.created_at desc
                     limit 1
                  ) latest on true
                 group by c.id, c.title, c.workspace_id, latest.id, latest.status, c.updated_at
                 order by c.updated_at desc
                 limit 50
                """, (rs, rowNum) -> new CliSessionSummary(
                rs.getObject("conversation_id", java.util.UUID.class).toString(),
                rs.getString("conversation_title"),
                rs.getObject("workspace_id", java.util.UUID.class).toString(),
                rs.getInt("task_count"),
                rs.getObject("latest_task_id", java.util.UUID.class) == null
                        ? "" : rs.getObject("latest_task_id", java.util.UUID.class).toString(),
                rs.getString("latest_task_status") == null ? "" : rs.getString("latest_task_status"),
                rs.getObject("updated_at", java.time.OffsetDateTime.class).toInstant()));
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
