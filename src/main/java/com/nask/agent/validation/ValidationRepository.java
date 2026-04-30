package com.nask.agent.validation;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static com.nask.agent.common.DbValues.ts;

/**
 * JDBC repository for validation results.
 */
@Repository
public class ValidationRepository {
    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Creates a repository backed by named-parameter JDBC.
     */
    public ValidationRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Inserts a validation result.
     */
    public ValidationResultRecord insert(ValidationResultRecord result) {
        jdbc.update("""
                insert into validation_result (id, task_id, run_id, step_id, command_id, validation_type, success, summary, created_at)
                values (:id, :taskId, :runId, :stepId, :commandId, :validationType, :success, :summary, :createdAt)
                """, new MapSqlParameterSource()
                .addValue("id", result.id())
                .addValue("taskId", result.taskId())
                .addValue("runId", result.runId())
                .addValue("stepId", result.stepId())
                .addValue("commandId", result.commandId())
                .addValue("validationType", result.validationType())
                .addValue("success", result.success())
                .addValue("summary", result.summary())
                .addValue("createdAt", ts(result.createdAt())));
        return result;
    }

    /**
     * Lists validation results for a task.
     */
    public List<ValidationResultRecord> findByTask(UUID taskId) {
        return jdbc.query("select * from validation_result where task_id = :taskId order by created_at",
                new MapSqlParameterSource("taskId", taskId), mapper());
    }

    private RowMapper<ValidationResultRecord> mapper() {
        return this::mapRow;
    }

    private ValidationResultRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new ValidationResultRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("task_id", UUID.class),
                rs.getObject("run_id", UUID.class),
                rs.getObject("step_id", UUID.class),
                rs.getObject("command_id", UUID.class),
                rs.getString("validation_type"),
                rs.getBoolean("success"),
                rs.getString("summary"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }
}
