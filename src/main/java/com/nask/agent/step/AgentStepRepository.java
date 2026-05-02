package com.nask.agent.step;

import com.nask.agent.common.Domain;
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
 * JDBC repository for agent step rows.
 */
@Repository
public class AgentStepRepository {
    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Creates a repository backed by named-parameter JDBC.
     */
    public AgentStepRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Inserts a running step.
     */
    public AgentStep insert(AgentStep step) {
        jdbc.update("""
                insert into agent_step (id, run_id, plan_item_id, step_type, status, input_summary, output_summary, started_at, finished_at)
                values (:id, :runId, :planItemId, :stepType, :status, :inputSummary, :outputSummary, :startedAt, :finishedAt)
                """, params(step));
        return step;
    }

    /**
     * Marks a step completed and stores its output summary.
     */
    public void complete(UUID id, String outputSummary) {
        jdbc.update("""
                update agent_step set status = :status, output_summary = :outputSummary, finished_at = :finishedAt where id = :id
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("status", Domain.StepStatus.COMPLETED.name())
                .addValue("outputSummary", outputSummary)
                .addValue("finishedAt", ts(Instant.now())));
    }

    /**
     * Marks a step as paused while waiting for approval.
     */
    public void markWaitingApproval(UUID id, String outputSummary) {
        jdbc.update("""
                update agent_step set status = :status, output_summary = :outputSummary, finished_at = null where id = :id
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("status", Domain.StepStatus.WAITING_APPROVAL.name())
                .addValue("outputSummary", outputSummary));
    }

    /**
     * Marks a step as paused while waiting for user input.
     */
    public void markWaitingUserInput(UUID id, String outputSummary) {
        jdbc.update("""
                update agent_step set status = :status, output_summary = :outputSummary, finished_at = null where id = :id
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("status", Domain.StepStatus.WAITING_USER_INPUT.name())
                .addValue("outputSummary", outputSummary));
    }

    /**
     * Marks a step failed and stores its output summary.
     */
    public void fail(UUID id, String outputSummary) {
        jdbc.update("""
                update agent_step set status = :status, output_summary = :outputSummary, finished_at = :finishedAt where id = :id
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("status", Domain.StepStatus.FAILED.name())
                .addValue("outputSummary", outputSummary)
                .addValue("finishedAt", ts(Instant.now())));
    }

    /**
     * Lists all steps for a run in timeline order.
     */
    public List<AgentStep> findByRun(UUID runId) {
        return jdbc.query("select * from agent_step where run_id = :runId order by started_at, id",
                new MapSqlParameterSource("runId", runId), mapper());
    }

    /**
     * Finds one step by id.
     */
    public Optional<AgentStep> findById(UUID id) {
        return jdbc.query("select * from agent_step where id = :id",
                new MapSqlParameterSource("id", id), mapper()).stream().findFirst();
    }

    private MapSqlParameterSource params(AgentStep step) {
        return new MapSqlParameterSource()
                .addValue("id", step.id())
                .addValue("runId", step.runId())
                .addValue("planItemId", step.planItemId())
                .addValue("stepType", step.stepType())
                .addValue("status", step.status())
                .addValue("inputSummary", step.inputSummary())
                .addValue("outputSummary", step.outputSummary())
                .addValue("startedAt", ts(step.startedAt()))
                .addValue("finishedAt", ts(step.finishedAt()));
    }

    private RowMapper<AgentStep> mapper() {
        return this::mapRow;
    }

    private AgentStep mapRow(ResultSet rs, int rowNum) throws SQLException {
        var finished = rs.getObject("finished_at", OffsetDateTime.class);
        return new AgentStep(
                rs.getObject("id", UUID.class),
                rs.getObject("run_id", UUID.class),
                rs.getObject("plan_item_id", UUID.class),
                rs.getString("step_type"),
                rs.getString("status"),
                rs.getString("input_summary"),
                rs.getString("output_summary"),
                rs.getObject("started_at", OffsetDateTime.class).toInstant(),
                finished == null ? null : finished.toInstant());
    }
}
