package com.nask.agent.action;

import com.nask.agent.common.Domain;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import static com.nask.agent.common.DbValues.ts;

/**
 * JDBC repository for agent actions.
 */
@Repository
public class AgentActionRepository {
    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Creates a repository backed by named-parameter JDBC.
     */
    public AgentActionRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Inserts an action in its initial lifecycle state.
     */
    public AgentAction insert(AgentAction action) {
        jdbc.update("""
                insert into agent_action (id, step_id, action_type, reason, risk_level, status, created_at)
                values (:id, :stepId, :actionType, :reason, :riskLevel, :status, :createdAt)
                """, new MapSqlParameterSource()
                .addValue("id", action.id())
                .addValue("stepId", action.stepId())
                .addValue("actionType", action.actionType())
                .addValue("reason", action.reason())
                .addValue("riskLevel", action.riskLevel())
                .addValue("status", action.status())
                .addValue("createdAt", ts(action.createdAt())));
        return action;
    }

    /**
     * Updates an action status.
     */
    public void updateStatus(UUID actionId, Domain.ActionStatus status) {
        jdbc.update("update agent_action set status = :status where id = :id",
                new MapSqlParameterSource().addValue("id", actionId).addValue("status", status.name()));
    }

    private RowMapper<AgentAction> mapper() {
        return this::mapRow;
    }

    private AgentAction mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new AgentAction(
                rs.getObject("id", UUID.class),
                rs.getObject("step_id", UUID.class),
                rs.getString("action_type"),
                rs.getString("reason"),
                rs.getString("risk_level"),
                rs.getString("status"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }
}
