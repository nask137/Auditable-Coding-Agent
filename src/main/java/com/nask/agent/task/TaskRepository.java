package com.nask.agent.task;

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
 * JDBC repository for task rows.
 */
@Repository
public class TaskRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final JsonSupport json;

    /**
     * Creates a repository backed by named-parameter JDBC.
     */
    public TaskRepository(NamedParameterJdbcTemplate jdbc, JsonSupport json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /**
     * Inserts a new task.
     */
    public CodingTask insert(CodingTask task) {
        jdbc.update("""
                insert into task (
                  id, workspace_id, conversation_id, prompt_index, title, user_request,
                  status, agent_mode, execution_started_at, execution_finished_at, failure_reason,
                  runtime_metadata, created_at, updated_at
                ) values (
                  :id, :workspaceId, :conversationId, :promptIndex, :title, :userRequest,
                  :status, :agentMode, :executionStartedAt, :executionFinishedAt, :failureReason,
                  cast(:runtimeMetadata as jsonb), :createdAt, :updatedAt
                )
                """, params(task));
        return task;
    }

    /**
     * Finds a task by id.
     */
    public Optional<CodingTask> findById(UUID id) {
        return jdbc.query("select * from task where id = :id", new MapSqlParameterSource("id", id), mapper())
                .stream().findFirst();
    }

    /**
     * Lists tasks newest first for read-only dashboard selection.
     */
    public List<CodingTask> findAll() {
        return jdbc.query("select * from task order by updated_at desc", mapper());
    }

    /**
     * Updates task status and refreshes {@code updated_at}.
     */
    public void updateStatus(UUID id, Domain.TaskStatus status) {
        jdbc.update("update task set status = :status, updated_at = :updatedAt where id = :id",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("status", status.name())
                        .addValue("updatedAt", ts(Instant.now())));
    }

    /**
     * Starts the single execution attached to a task.
     */
    public void startExecution(UUID id, String agentMode, String workflowName, Instant startedAt) {
        jdbc.update("""
                update task
                   set agent_mode = :agentMode,
                       status = :status,
                       execution_started_at = :startedAt,
                       execution_finished_at = null,
                       failure_reason = null,
                       runtime_metadata = cast(:runtimeMetadata as jsonb),
                       updated_at = :startedAt
                 where id = :id
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("agentMode", agentMode)
                .addValue("status", Domain.TaskStatus.RUNNING.name())
                .addValue("startedAt", ts(startedAt))
                .addValue("runtimeMetadata", json.toJson(java.util.Map.of(
                        "loop", "phase3-workflow",
                        "workflow", workflowName))));
    }

    /**
     * Updates task execution status and stamps the terminal timestamp when applicable.
     */
    public void updateExecutionStatus(UUID id, Domain.TaskStatus status, String failureReason) {
        var now = Instant.now();
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
                .addValue("finished", status == Domain.TaskStatus.COMPLETED
                        || status == Domain.TaskStatus.FAILED
                        || status == Domain.TaskStatus.CANCELLED)
                .addValue("finishedAt", ts(now))
                .addValue("updatedAt", ts(now)));
    }

    private MapSqlParameterSource params(CodingTask task) {
        return new MapSqlParameterSource()
                .addValue("id", task.id())
                .addValue("workspaceId", task.workspaceId())
                .addValue("conversationId", task.conversationId())
                .addValue("promptIndex", task.promptIndex())
                .addValue("title", task.title())
                .addValue("userRequest", task.userRequest())
                .addValue("status", task.status())
                .addValue("agentMode", task.agentMode())
                .addValue("executionStartedAt", ts(task.executionStartedAt()))
                .addValue("executionFinishedAt", ts(task.executionFinishedAt()))
                .addValue("failureReason", task.failureReason())
                .addValue("runtimeMetadata", json.toJson(task.runtimeMetadata()))
                .addValue("createdAt", ts(task.createdAt()))
                .addValue("updatedAt", ts(task.updatedAt()));
    }

    private RowMapper<CodingTask> mapper() {
        return this::mapRow;
    }

    private CodingTask mapRow(ResultSet rs, int rowNum) throws SQLException {
        var started = rs.getObject("execution_started_at", OffsetDateTime.class);
        var finished = rs.getObject("execution_finished_at", OffsetDateTime.class);
        return new CodingTask(
                rs.getObject("id", UUID.class),
                rs.getObject("workspace_id", UUID.class),
                rs.getObject("conversation_id", UUID.class),
                rs.getInt("prompt_index"),
                rs.getString("title"),
                rs.getString("user_request"),
                rs.getString("status"),
                rs.getString("agent_mode"),
                started == null ? null : started.toInstant(),
                finished == null ? null : finished.toInstant(),
                rs.getString("failure_reason"),
                json.toMap(rs.getString("runtime_metadata")),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getObject("updated_at", OffsetDateTime.class).toInstant());
    }
}
