package com.nask.agent.runtime;

import com.nask.agent.common.DbValues;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC repository for phase 2 runtime failure records.
 */
@Repository
public class RuntimeFailureRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public RuntimeFailureRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public RuntimeFailure insert(RuntimeFailure failure) {
        jdbc.update("""
                insert into runtime_failure (
                  id, task_id, run_id, step_id, plan_item_id, failure_type, recoverable, strategy,
                  summary, details, related_event_id, related_tool_call_id, related_command_id,
                  related_file_change_id, attempt, created_at
                ) values (
                  :id, :taskId, :runId, :stepId, :planItemId, :failureType, :recoverable, :strategy,
                  :summary, :details, :relatedEventId, :relatedToolCallId, :relatedCommandId,
                  :relatedFileChangeId, :attempt, :createdAt
                )
                """, params(failure));
        return failure;
    }

    public List<RuntimeFailure> findByTask(UUID taskId) {
        return jdbc.query("select * from runtime_failure where task_id = :taskId order by created_at",
                new MapSqlParameterSource("taskId", taskId), mapper());
    }

    public List<RuntimeFailure> findByRun(UUID runId) {
        return jdbc.query("select * from runtime_failure where run_id = :runId order by created_at",
                new MapSqlParameterSource("runId", runId), mapper());
    }

    public Optional<RuntimeFailure> findLatestByRun(UUID runId) {
        return jdbc.query("select * from runtime_failure where run_id = :runId order by created_at desc limit 1",
                new MapSqlParameterSource("runId", runId), mapper()).stream().findFirst();
    }

    public int countByRunAndType(UUID runId, String failureType) {
        return jdbc.queryForObject("""
                select count(*) from runtime_failure
                 where run_id = :runId and failure_type = :failureType
                """, new MapSqlParameterSource()
                .addValue("runId", runId)
                .addValue("failureType", failureType), Integer.class);
    }

    public int countByRunAndStrategy(UUID runId, String strategy) {
        return jdbc.queryForObject("""
                select count(*) from runtime_failure
                 where run_id = :runId and strategy = :strategy
                """, new MapSqlParameterSource()
                .addValue("runId", runId)
                .addValue("strategy", strategy), Integer.class);
    }

    private MapSqlParameterSource params(RuntimeFailure failure) {
        return new MapSqlParameterSource()
                .addValue("id", failure.id())
                .addValue("taskId", failure.taskId())
                .addValue("runId", failure.runId())
                .addValue("stepId", failure.stepId())
                .addValue("planItemId", failure.planItemId())
                .addValue("failureType", failure.failureType())
                .addValue("recoverable", failure.recoverable())
                .addValue("strategy", failure.strategy())
                .addValue("summary", failure.summary())
                .addValue("details", failure.details())
                .addValue("relatedEventId", failure.relatedEventId())
                .addValue("relatedToolCallId", failure.relatedToolCallId())
                .addValue("relatedCommandId", failure.relatedCommandId())
                .addValue("relatedFileChangeId", failure.relatedFileChangeId())
                .addValue("attempt", failure.attempt())
                .addValue("createdAt", DbValues.ts(failure.createdAt()));
    }

    private RowMapper<RuntimeFailure> mapper() {
        return (rs, rowNum) -> new RuntimeFailure(
                rs.getObject("id", UUID.class),
                rs.getObject("task_id", UUID.class),
                rs.getObject("run_id", UUID.class),
                rs.getObject("step_id", UUID.class),
                rs.getObject("plan_item_id", UUID.class),
                rs.getString("failure_type"),
                rs.getBoolean("recoverable"),
                rs.getString("strategy"),
                rs.getString("summary"),
                rs.getString("details"),
                rs.getObject("related_event_id", UUID.class),
                rs.getObject("related_tool_call_id", UUID.class),
                rs.getObject("related_command_id", UUID.class),
                rs.getObject("related_file_change_id", UUID.class),
                rs.getInt("attempt"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }
}
