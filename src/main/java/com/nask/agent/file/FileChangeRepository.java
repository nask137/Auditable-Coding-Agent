package com.nask.agent.file;

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
import java.util.UUID;

import static com.nask.agent.common.DbValues.ts;

@Repository
public class FileChangeRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public FileChangeRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public FileChange insert(FileChange change) {
        jdbc.update("""
                insert into file_change (
                  id, workspace_id, task_id, run_id, step_id, action_id, path, change_type, reason,
                  diff, before_hash, after_hash, base_revision, observed_at, patch_apply_status,
                  line_added, line_deleted, risk_level, approval_id, created_at
                ) values (
                  :id, :workspaceId, :taskId, :runId, :stepId, :actionId, :path, :changeType, :reason,
                  :diff, :beforeHash, :afterHash, :baseRevision, :observedAt, :patchApplyStatus,
                  :lineAdded, :lineDeleted, :riskLevel, :approvalId, :createdAt
                )
                """, params(change));
        return change;
    }

    public List<FileChange> findByTask(UUID taskId) {
        return jdbc.query("select * from file_change where task_id = :taskId order by created_at, id",
                new MapSqlParameterSource("taskId", taskId), mapper());
    }

    public long countByRun(UUID runId) {
        return jdbc.queryForObject("select count(*) from file_change where run_id = :runId",
                new MapSqlParameterSource("runId", runId), Long.class);
    }

    private MapSqlParameterSource params(FileChange change) {
        return new MapSqlParameterSource()
                .addValue("id", change.id())
                .addValue("workspaceId", change.workspaceId())
                .addValue("taskId", change.taskId())
                .addValue("runId", change.runId())
                .addValue("stepId", change.stepId())
                .addValue("actionId", change.actionId())
                .addValue("path", change.path())
                .addValue("changeType", change.changeType())
                .addValue("reason", change.reason())
                .addValue("diff", change.diff())
                .addValue("beforeHash", change.beforeHash())
                .addValue("afterHash", change.afterHash())
                .addValue("baseRevision", change.baseRevision())
                .addValue("observedAt", ts(change.observedAt()))
                .addValue("patchApplyStatus", change.patchApplyStatus())
                .addValue("lineAdded", change.lineAdded())
                .addValue("lineDeleted", change.lineDeleted())
                .addValue("riskLevel", change.riskLevel())
                .addValue("approvalId", change.approvalId())
                .addValue("createdAt", ts(change.createdAt()));
    }

    private RowMapper<FileChange> mapper() {
        return this::mapRow;
    }

    private FileChange mapRow(ResultSet rs, int rowNum) throws SQLException {
        var observed = rs.getObject("observed_at", OffsetDateTime.class);
        return new FileChange(
                rs.getObject("id", UUID.class),
                rs.getObject("workspace_id", UUID.class),
                rs.getObject("task_id", UUID.class),
                rs.getObject("run_id", UUID.class),
                rs.getObject("step_id", UUID.class),
                rs.getObject("action_id", UUID.class),
                rs.getString("path"),
                rs.getString("change_type"),
                rs.getString("reason"),
                rs.getString("diff"),
                rs.getString("before_hash"),
                rs.getString("after_hash"),
                rs.getString("base_revision"),
                observed == null ? null : observed.toInstant(),
                rs.getString("patch_apply_status"),
                rs.getInt("line_added"),
                rs.getInt("line_deleted"),
                rs.getString("risk_level"),
                rs.getObject("approval_id", UUID.class),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }
}
