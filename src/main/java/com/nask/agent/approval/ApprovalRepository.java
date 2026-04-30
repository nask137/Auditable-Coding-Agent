package com.nask.agent.approval;

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

@Repository
public class ApprovalRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final JsonSupport json;

    public ApprovalRepository(NamedParameterJdbcTemplate jdbc, JsonSupport json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public ApprovalRequestRecord insert(ApprovalRequestRecord approval) {
        jdbc.update("""
                insert into approval_request (
                  id, task_id, run_id, step_id, action_id, approval_type, reason, risk_level,
                  affected_files, command, working_directory, patch_preview, status, created_at,
                  resolved_at, resolved_by, resolution_reason
                ) values (
                  :id, :taskId, :runId, :stepId, :actionId, :approvalType, :reason, :riskLevel,
                  cast(:affectedFiles as jsonb), :command, :workingDirectory, :patchPreview, :status, :createdAt,
                  :resolvedAt, :resolvedBy, :resolutionReason
                )
                """, params(approval));
        return approval;
    }

    public Optional<ApprovalRequestRecord> findById(UUID id) {
        return jdbc.query("select * from approval_request where id = :id",
                new MapSqlParameterSource("id", id), mapper()).stream().findFirst();
    }

    public List<ApprovalRequestRecord> findByStatus(Domain.ApprovalStatus status) {
        return jdbc.query("select * from approval_request where status = :status order by created_at",
                new MapSqlParameterSource("status", status.name()), mapper());
    }

    public List<ApprovalRequestRecord> findAll() {
        return jdbc.query("select * from approval_request order by created_at desc", mapper());
    }

    public List<ApprovalRequestRecord> findApprovedCandidates(UUID runId, Domain.ApprovalType type) {
        return jdbc.query("""
                select * from approval_request
                 where run_id = :runId
                   and approval_type = :approvalType
                   and status = :status
                 order by resolved_at nulls last, created_at
                """, new MapSqlParameterSource()
                .addValue("runId", runId)
                .addValue("approvalType", type.name())
                .addValue("status", Domain.ApprovalStatus.APPROVED.name()), mapper());
    }

    public void resolve(UUID id, Domain.ApprovalStatus status, String resolvedBy, String reason) {
        jdbc.update("""
                update approval_request
                   set status = :status,
                       resolved_at = :resolvedAt,
                       resolved_by = :resolvedBy,
                       resolution_reason = :reason
                 where id = :id
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("status", status.name())
                .addValue("resolvedAt", ts(Instant.now()))
                .addValue("resolvedBy", resolvedBy)
                .addValue("reason", reason));
    }

    public void consume(UUID id) {
        jdbc.update("""
                update approval_request
                   set status = :status,
                       resolution_reason = coalesce(resolution_reason, '') || ' consumed'
                 where id = :id and status = :approved
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("status", Domain.ApprovalStatus.CONSUMED.name())
                .addValue("approved", Domain.ApprovalStatus.APPROVED.name()));
    }

    private MapSqlParameterSource params(ApprovalRequestRecord approval) {
        return new MapSqlParameterSource()
                .addValue("id", approval.id())
                .addValue("taskId", approval.taskId())
                .addValue("runId", approval.runId())
                .addValue("stepId", approval.stepId())
                .addValue("actionId", approval.actionId())
                .addValue("approvalType", approval.approvalType())
                .addValue("reason", approval.reason())
                .addValue("riskLevel", approval.riskLevel())
                .addValue("affectedFiles", json.toJson(approval.affectedFiles()))
                .addValue("command", approval.command())
                .addValue("workingDirectory", approval.workingDirectory())
                .addValue("patchPreview", approval.patchPreview())
                .addValue("status", approval.status())
                .addValue("createdAt", ts(approval.createdAt()))
                .addValue("resolvedAt", ts(approval.resolvedAt()))
                .addValue("resolvedBy", approval.resolvedBy())
                .addValue("resolutionReason", approval.resolutionReason());
    }

    private RowMapper<ApprovalRequestRecord> mapper() {
        return this::mapRow;
    }

    private ApprovalRequestRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        var resolved = rs.getObject("resolved_at", OffsetDateTime.class);
        return new ApprovalRequestRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("task_id", UUID.class),
                rs.getObject("run_id", UUID.class),
                rs.getObject("step_id", UUID.class),
                rs.getObject("action_id", UUID.class),
                rs.getString("approval_type"),
                rs.getString("reason"),
                rs.getString("risk_level"),
                json.toStringList(rs.getString("affected_files")),
                rs.getString("command"),
                rs.getString("working_directory"),
                rs.getString("patch_preview"),
                rs.getString("status"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                resolved == null ? null : resolved.toInstant(),
                rs.getString("resolved_by"),
                rs.getString("resolution_reason"));
    }
}
