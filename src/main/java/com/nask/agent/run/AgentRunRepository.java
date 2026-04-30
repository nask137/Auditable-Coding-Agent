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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.nask.agent.common.DbValues.ts;

@Repository
public class AgentRunRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final JsonSupport json;

    public AgentRunRepository(NamedParameterJdbcTemplate jdbc, JsonSupport json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public AgentRun insert(AgentRun run) {
        jdbc.update("""
                insert into agent_run (id, task_id, agent_mode, status, started_at, finished_at, failure_reason, runtime_metadata)
                values (:id, :taskId, :agentMode, :status, :startedAt, :finishedAt, :failureReason, cast(:runtimeMetadata as jsonb))
                """, params(run));
        return run;
    }

    public Optional<AgentRun> findById(UUID id) {
        return jdbc.query("select * from agent_run where id = :id", new MapSqlParameterSource("id", id), mapper())
                .stream().findFirst();
    }

    public List<AgentRun> findByTask(UUID taskId) {
        return jdbc.query("select * from agent_run where task_id = :taskId order by started_at desc",
                new MapSqlParameterSource("taskId", taskId), mapper());
    }

    public void updateStatus(UUID id, Domain.AgentRunStatus status, String failureReason) {
        jdbc.update("""
                update agent_run
                   set status = :status,
                       failure_reason = :failureReason,
                       finished_at = case when :finished then :finishedAt else finished_at end
                 where id = :id
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("status", status.name())
                .addValue("failureReason", failureReason)
                .addValue("finished", status == Domain.AgentRunStatus.COMPLETED
                        || status == Domain.AgentRunStatus.FAILED
                        || status == Domain.AgentRunStatus.CANCELLED)
                .addValue("finishedAt", ts(Instant.now())));
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
