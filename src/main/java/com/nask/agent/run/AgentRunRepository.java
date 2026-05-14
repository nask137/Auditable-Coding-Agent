package com.nask.agent.run;

import com.nask.agent.common.Domain;
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
import java.util.Optional;
import java.util.UUID;

import static com.nask.agent.common.DbValues.ts;

/**
 * JDBC repository for agent run rows.
 */
@Repository
public class AgentRunRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final JsonSupport json;

    /**
     * Creates a repository backed by JDBC and JSON helpers.
     */
    public AgentRunRepository(NamedParameterJdbcTemplate jdbc, JsonSupport json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /**
     * Starts the single execution attached to the task.
     */
    public AgentRun insert(AgentRun run) {
        jdbc.update("""
                update task
                   set agent_mode = :agentMode,
                       status = :status,
                       execution_started_at = :startedAt,
                       execution_finished_at = :finishedAt,
                       failure_reason = :failureReason,
                       runtime_metadata = cast(:runtimeMetadata as jsonb),
                       updated_at = :startedAt
                 where id = :taskId
                """, params(run));
        return run;
    }

    /**
     * Looks up a run by id.
     */
    public Optional<AgentRun> findById(UUID id) {
        return jdbc.query("""
                select id,
                       id as task_id,
                       coalesce(agent_mode, 'CODE_EDIT') as agent_mode,
                       status,
                       coalesce(execution_started_at, updated_at) as started_at,
                       execution_finished_at as finished_at,
                       failure_reason,
                       runtime_metadata
                  from task
                 where id = :id
                """, new MapSqlParameterSource("id", id), mapper())
                .stream().findFirst();
    }

    /**
     * Lists all runs newest first for read-only dashboard selection.
     */
    public List<AgentRun> findAll() {
        return jdbc.query("""
                select id,
                       id as task_id,
                       coalesce(agent_mode, 'CODE_EDIT') as agent_mode,
                       status,
                       coalesce(execution_started_at, updated_at) as started_at,
                       execution_finished_at as finished_at,
                       failure_reason,
                       runtime_metadata
                  from task
                 where execution_started_at is not null
                 order by execution_started_at desc, id
                """, mapper());
    }

    /**
     * Lists all runs for a task, newest first.
     */
    public List<AgentRun> findByTask(UUID taskId) {
        return jdbc.query("""
                select id,
                       id as task_id,
                       coalesce(agent_mode, 'CODE_EDIT') as agent_mode,
                       status,
                       coalesce(execution_started_at, updated_at) as started_at,
                       execution_finished_at as finished_at,
                       failure_reason,
                       runtime_metadata
                  from task
                 where id = :taskId
                   and execution_started_at is not null
                 order by execution_started_at desc, id
                """,
                new MapSqlParameterSource("taskId", taskId), mapper());
    }

    /**
     * Updates the run lifecycle and stamps {@code finished_at} for terminal states.
     */
    public void updateStatus(UUID id, Domain.AgentRunStatus status, String failureReason) {
        jdbc.update("""
                update task
                   set status = :status,
                       failure_reason = :failureReason,
                       execution_finished_at = case when :finished then :finishedAt else execution_finished_at end,
                       updated_at = :updatedAt
                 where id = :id
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("status", status.name())
                .addValue("failureReason", failureReason)
                .addValue("finished", status == Domain.AgentRunStatus.COMPLETED
                        || status == Domain.AgentRunStatus.FAILED
                        || status == Domain.AgentRunStatus.CANCELLED)
                .addValue("finishedAt", ts(Instant.now()))
                .addValue("updatedAt", ts(Instant.now())));
    }

    private MapSqlParameterSource params(AgentRun run) {
        return new MapSqlParameterSource()
                .addValue("id", run.id())
                .addValue("taskId", run.taskId())
                .addValue("agentMode", run.agentMode())
                .addValue("status", run.status())
                .addValue("startedAt", ts(run.startedAt()))
                .addValue("finishedAt", ts(run.finishedAt()))
                .addValue("failureReason", run.failureReason())
                .addValue("runtimeMetadata", json.toJson(run.runtimeMetadata()));
    }

    private RowMapper<AgentRun> mapper() {
        return this::mapRow;
    }

    private AgentRun mapRow(ResultSet rs, int rowNum) throws SQLException {
        var finished = rs.getObject("finished_at", OffsetDateTime.class);
        return new AgentRun(
                rs.getObject("id", UUID.class),
                rs.getObject("task_id", UUID.class),
                rs.getString("agent_mode"),
                rs.getString("status"),
                rs.getObject("started_at", OffsetDateTime.class).toInstant(),
                finished == null ? null : finished.toInstant(),
                rs.getString("failure_reason"),
                json.toMap(rs.getString("runtime_metadata")));
    }
}
