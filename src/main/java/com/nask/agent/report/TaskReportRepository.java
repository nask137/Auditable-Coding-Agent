package com.nask.agent.report;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static com.nask.agent.common.DbValues.ts;

/**
 * JDBC repository for task reports.
 */
@Repository
public class TaskReportRepository {
    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Creates a repository backed by named-parameter JDBC.
     */
    public TaskReportRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Inserts a generated report.
     */
    public TaskReport insert(TaskReport report) {
        jdbc.update("""
                insert into task_report (id, task_id, run_id, content_md, created_at)
                values (:id, :taskId, :runId, :contentMd, :createdAt)
                """, new MapSqlParameterSource()
                .addValue("id", report.id())
                .addValue("taskId", report.taskId())
                .addValue("runId", report.runId())
                .addValue("contentMd", report.contentMd())
                .addValue("createdAt", ts(report.createdAt())));
        return report;
    }

    /**
     * Returns the latest report for a task.
     */
    public Optional<TaskReport> findLatestByTask(UUID taskId) {
        return jdbc.query("select * from task_report where task_id = :taskId order by created_at desc limit 1",
                new MapSqlParameterSource("taskId", taskId), mapper()).stream().findFirst();
    }

    private RowMapper<TaskReport> mapper() {
        return this::mapRow;
    }

    private TaskReport mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new TaskReport(
                rs.getObject("id", UUID.class),
                rs.getObject("task_id", UUID.class),
                rs.getObject("run_id", UUID.class),
                rs.getString("content_md"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }
}
