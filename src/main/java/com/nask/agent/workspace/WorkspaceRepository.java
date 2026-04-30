package com.nask.agent.workspace;

import com.nask.agent.common.JsonSupport;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.nask.agent.common.DbValues.ts;

/**
 * JDBC repository for the {@code workspace} table.
 */
@Repository
public class WorkspaceRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final JsonSupport json;

    /**
     * Creates a repository using named-parameter JDBC and JSON helpers.
     */
    public WorkspaceRepository(NamedParameterJdbcTemplate jdbc, JsonSupport json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /**
     * Inserts a new workspace row.
     */
    public Workspace insert(Workspace workspace) {
        jdbc.update("""
                insert into workspace (
                  id, name, root_path, trusted, allowed_operations, blocked_paths,
                  sensitive_patterns, created_at, last_used_at
                ) values (
                  :id, :name, :rootPath, :trusted, cast(:allowedOperations as jsonb),
                  cast(:blockedPaths as jsonb), cast(:sensitivePatterns as jsonb),
                  :createdAt, :lastUsedAt
                )
                """, params(workspace));
        return workspace;
    }

    /**
     * Looks up a workspace by id.
     */
    public Optional<Workspace> findById(UUID id) {
        var rows = jdbc.query("select * from workspace where id = :id",
                new MapSqlParameterSource("id", id), mapper());
        return rows.stream().findFirst();
    }

    /**
     * Lists all workspaces newest first.
     */
    public List<Workspace> findAll() {
        return jdbc.query("select * from workspace order by created_at desc", mapper());
    }

    /**
     * Updates the last-used timestamp for a workspace.
     */
    public void touch(UUID id) {
        jdbc.update("update workspace set last_used_at = now() where id = :id",
                new MapSqlParameterSource("id", id));
    }

    private MapSqlParameterSource params(Workspace workspace) {
        return new MapSqlParameterSource()
                .addValue("id", workspace.id())
                .addValue("name", workspace.name())
                .addValue("rootPath", workspace.rootPath())
                .addValue("trusted", workspace.trusted())
                .addValue("allowedOperations", json.toJson(workspace.allowedOperations()))
                .addValue("blockedPaths", json.toJson(workspace.blockedPaths()))
                .addValue("sensitivePatterns", json.toJson(workspace.sensitivePatterns()))
                .addValue("createdAt", ts(workspace.createdAt()))
                .addValue("lastUsedAt", ts(workspace.lastUsedAt()));
    }

    private RowMapper<Workspace> mapper() {
        return this::mapRow;
    }

    private Workspace mapRow(ResultSet rs, int rowNum) throws SQLException {
        var lastUsed = rs.getObject("last_used_at", OffsetDateTime.class);
        return new Workspace(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("root_path"),
                rs.getBoolean("trusted"),
                json.toStringList(rs.getString("allowed_operations")),
                json.toStringList(rs.getString("blocked_paths")),
                json.toStringList(rs.getString("sensitive_patterns")),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                lastUsed == null ? null : lastUsed.toInstant());
    }
}
