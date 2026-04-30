package com.nask.agent.command;

import com.nask.agent.common.JsonSupport;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static com.nask.agent.common.DbValues.ts;

/**
 * JDBC repository for workspace command policies.
 */
@Repository
public class CommandPolicyRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final JsonSupport json;

    /**
     * Creates a repository backed by JDBC and JSON helpers.
     */
    public CommandPolicyRepository(NamedParameterJdbcTemplate jdbc, JsonSupport json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /**
     * Inserts a new enabled policy.
     */
    public CommandPolicy insert(CommandPolicy policy) {
        jdbc.update("""
                insert into command_policy (
                  id, workspace_id, policy_type, executable, args_pattern, cwd_scope, allow_pipe,
                  allow_redirect, allow_background, env_policy, enabled, created_at, updated_at
                ) values (
                  :id, :workspaceId, :policyType, :executable, cast(:argsPattern as jsonb), :cwdScope,
                  :allowPipe, :allowRedirect, :allowBackground, cast(:envPolicy as jsonb), :enabled,
                  :createdAt, :updatedAt
                )
                """, params(policy));
        return policy;
    }

    /**
     * Lists enabled policies for a workspace in creation order.
     */
    public List<CommandPolicy> findByWorkspace(UUID workspaceId) {
        return jdbc.query("select * from command_policy where workspace_id = :workspaceId and enabled = true order by created_at",
                new MapSqlParameterSource("workspaceId", workspaceId), mapper());
    }

    /**
     * Disables a policy without deleting its audit history.
     */
    public void delete(UUID id) {
        jdbc.update("update command_policy set enabled = false, updated_at = :updatedAt where id = :id",
                new MapSqlParameterSource().addValue("id", id).addValue("updatedAt", ts(Instant.now())));
    }

    private MapSqlParameterSource params(CommandPolicy policy) {
        return new MapSqlParameterSource()
                .addValue("id", policy.id())
                .addValue("workspaceId", policy.workspaceId())
                .addValue("policyType", policy.policyType())
                .addValue("executable", policy.executable())
                .addValue("argsPattern", json.toJson(policy.argsPattern()))
                .addValue("cwdScope", policy.cwdScope())
                .addValue("allowPipe", policy.allowPipe())
                .addValue("allowRedirect", policy.allowRedirect())
                .addValue("allowBackground", policy.allowBackground())
                .addValue("envPolicy", json.toJson(policy.envPolicy()))
                .addValue("enabled", policy.enabled())
                .addValue("createdAt", ts(policy.createdAt()))
                .addValue("updatedAt", ts(policy.updatedAt()));
    }

    private RowMapper<CommandPolicy> mapper() {
        return this::mapRow;
    }

    private CommandPolicy mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new CommandPolicy(
                rs.getObject("id", UUID.class),
                rs.getObject("workspace_id", UUID.class),
                rs.getString("policy_type"),
                rs.getString("executable"),
                json.toStringList(rs.getString("args_pattern")),
                rs.getString("cwd_scope"),
                rs.getBoolean("allow_pipe"),
                rs.getBoolean("allow_redirect"),
                rs.getBoolean("allow_background"),
                json.toMap(rs.getString("env_policy")),
                rs.getBoolean("enabled"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getObject("updated_at", OffsetDateTime.class).toInstant());
    }
}
