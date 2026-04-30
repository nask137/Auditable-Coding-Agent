package com.nask.agent.task;

import com.nask.agent.common.Domain;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static com.nask.agent.common.DbValues.ts;

@Repository
public class TaskRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public TaskRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public CodingTask insert(CodingTask task) {
        jdbc.update("""
                insert into task (id, workspace_id, title, user_request, status, created_at, updated_at)
                values (:id, :workspaceId, :title, :userRequest, :status, :createdAt, :updatedAt)
                """, params(task));
        return task;
    }

    public Optional<CodingTask> findById(UUID id) {
        return jdbc.query("select * from task where id = :id", new MapSqlParameterSource("id", id), mapper())
                .stream().findFirst();
    }

    public void updateStatus(UUID id, Domain.TaskStatus status) {
        jdbc.update("update task set status = :status, updated_at = :updatedAt where id = :id",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("status", status.name())
                        .addValue("updatedAt", ts(Instant.now())));
    }

    private MapSqlParameterSource params(CodingTask task) {
        return new MapSqlParameterSource()
                .addValue("id", task.id())
                .addValue("workspaceId", task.workspaceId())
                .addValue("title", task.title())
                .addValue("userRequest", task.userRequest())
                .addValue("status", task.status())
                .addValue("createdAt", ts(task.createdAt()))
                .addValue("updatedAt", ts(task.updatedAt()));
    }

    private RowMapper<CodingTask> mapper() {
        return this::mapRow;
    }

    private CodingTask mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new CodingTask(
                rs.getObject("id", UUID.class),
                rs.getObject("workspace_id", UUID.class),
                rs.getString("title"),
                rs.getString("user_request"),
                rs.getString("status"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getObject("updated_at", OffsetDateTime.class).toInstant());
    }
}
