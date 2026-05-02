package com.nask.agent.runtime;

import com.nask.agent.common.DbValues;
import com.nask.agent.common.Domain;
import com.nask.agent.common.JsonSupport;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC repository for user-input requests.
 */
@Repository
public class UserInputRequestRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final JsonSupport json;

    public UserInputRequestRepository(NamedParameterJdbcTemplate jdbc, JsonSupport json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public UserInputRequestRecord insert(UserInputRequestRecord request) {
        jdbc.update("""
                insert into user_input_request (
                  id, task_id, run_id, step_id, plan_item_id, status, question, context_summary,
                  suggested_options, answer, created_at, answered_at
                ) values (
                  :id, :taskId, :runId, :stepId, :planItemId, :status, :question, :contextSummary,
                  cast(:suggestedOptions as jsonb), :answer, :createdAt, :answeredAt
                )
                """, params(request));
        return request;
    }

    public Optional<UserInputRequestRecord> findById(UUID id) {
        return jdbc.query("select * from user_input_request where id = :id",
                new MapSqlParameterSource("id", id), mapper()).stream().findFirst();
    }

    public List<UserInputRequestRecord> findAll() {
        return jdbc.query("select * from user_input_request order by created_at", mapper());
    }

    public List<UserInputRequestRecord> findByStatus(Domain.UserInputStatus status) {
        return jdbc.query("select * from user_input_request where status = :status order by created_at",
                new MapSqlParameterSource("status", status.name()), mapper());
    }

    public Optional<UserInputRequestRecord> findPendingByRun(UUID runId) {
        return jdbc.query("""
                select * from user_input_request
                 where run_id = :runId and status = :status
                 order by created_at desc limit 1
                """, new MapSqlParameterSource()
                .addValue("runId", runId)
                .addValue("status", Domain.UserInputStatus.PENDING.name()), mapper()).stream().findFirst();
    }

    public int countByRun(UUID runId) {
        return jdbc.queryForObject("select count(*) from user_input_request where run_id = :runId",
                new MapSqlParameterSource("runId", runId), Integer.class);
    }

    public void answer(UUID id, String answer) {
        jdbc.update("""
                update user_input_request
                   set status = :status,
                       answer = :answer,
                       answered_at = :answeredAt
                 where id = :id
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("status", Domain.UserInputStatus.ANSWERED.name())
                .addValue("answer", answer)
                .addValue("answeredAt", DbValues.ts(java.time.Instant.now())));
    }

    public void cancel(UUID id) {
        jdbc.update("""
                update user_input_request
                   set status = :status,
                       answered_at = :answeredAt
                 where id = :id
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("status", Domain.UserInputStatus.CANCELLED.name())
                .addValue("answeredAt", DbValues.ts(java.time.Instant.now())));
    }

    private MapSqlParameterSource params(UserInputRequestRecord request) {
        return new MapSqlParameterSource()
                .addValue("id", request.id())
                .addValue("taskId", request.taskId())
                .addValue("runId", request.runId())
                .addValue("stepId", request.stepId())
                .addValue("planItemId", request.planItemId())
                .addValue("status", request.status())
                .addValue("question", request.question())
                .addValue("contextSummary", request.contextSummary())
                .addValue("suggestedOptions", json.toJson(request.suggestedOptions()))
                .addValue("answer", request.answer())
                .addValue("createdAt", DbValues.ts(request.createdAt()))
                .addValue("answeredAt", DbValues.ts(request.answeredAt()));
    }

    private RowMapper<UserInputRequestRecord> mapper() {
        return (rs, rowNum) -> new UserInputRequestRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("task_id", UUID.class),
                rs.getObject("run_id", UUID.class),
                rs.getObject("step_id", UUID.class),
                rs.getObject("plan_item_id", UUID.class),
                rs.getString("status"),
                rs.getString("question"),
                rs.getString("context_summary"),
                json.toStringList(rs.getString("suggested_options")),
                rs.getString("answer"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getObject("answered_at", OffsetDateTime.class) == null
                        ? null : rs.getObject("answered_at", OffsetDateTime.class).toInstant());
    }
}
