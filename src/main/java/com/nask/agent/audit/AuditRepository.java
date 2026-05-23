package com.nask.agent.audit;

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
import java.util.UUID;

import static com.nask.agent.common.DbValues.ts;

/**
 * JDBC repository for append-only audit events.
 */
@Repository
public class AuditRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final JsonSupport json;

    /**
     * Creates a repository backed by JDBC and JSON helpers.
     */
    public AuditRepository(NamedParameterJdbcTemplate jdbc, JsonSupport json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /**
     * Appends an audit event and returns its id.
     */
    public UUID insert(AuditEvent event) {
        jdbc.update("""
                insert into audit_event (
                  id, task_id, run_id, step_id, action_id, event_type, actor, level, occurred_at,
                  input_summary, output_summary, related_files, related_tool_call_id, related_approval_id,
                  related_command_id, related_file_change_id, permission_level, risk_level, approval_status,
                  success, error_code, error_message, metadata
                ) values (
                  :id, :taskId, :runId, :stepId, :actionId, :eventType, :actor, :level, :occurredAt,
                  :inputSummary, :outputSummary, cast(:relatedFiles as jsonb), :relatedToolCallId,
                  :relatedApprovalId, :relatedCommandId, :relatedFileChangeId, :permissionLevel, :riskLevel,
                  :approvalStatus, :success, :errorCode, :errorMessage, cast(:metadata as jsonb)
                )
                """, new MapSqlParameterSource()
                .addValue("id", event.id())
                .addValue("taskId", event.taskId())
                .addValue("runId", event.runId())
                .addValue("stepId", event.stepId())
                .addValue("actionId", event.actionId())
                .addValue("eventType", event.eventType())
                .addValue("actor", event.actor())
                .addValue("level", event.level())
                .addValue("occurredAt", ts(event.occurredAt()))
                .addValue("inputSummary", event.inputSummary())
                .addValue("outputSummary", event.outputSummary())
                .addValue("relatedFiles", json.toJson(event.relatedFiles()))
                .addValue("relatedToolCallId", event.relatedToolCallId())
                .addValue("relatedApprovalId", event.relatedApprovalId())
                .addValue("relatedCommandId", event.relatedCommandId())
                .addValue("relatedFileChangeId", event.relatedFileChangeId())
                .addValue("permissionLevel", event.permissionLevel())
                .addValue("riskLevel", event.riskLevel())
                .addValue("approvalStatus", event.approvalStatus())
                .addValue("success", event.success())
                .addValue("errorCode", event.errorCode())
                .addValue("errorMessage", event.errorMessage())
                .addValue("metadata", json.toJson(event.metadata())));
        return event.id();
    }

    /**
     * Lists audit events for a task in chronological order.
     */
    public List<AuditEvent> findByTask(UUID taskId) {
        return jdbc.query("select * from audit_event where task_id = :taskId order by event_sequence",
                new MapSqlParameterSource("taskId", taskId), mapper());
    }

    /**
     * Lists audit events for a run in chronological order.
     */
    public List<AuditEvent> findByRun(UUID runId) {
        return jdbc.query("select * from audit_event where run_id = :runId order by event_sequence",
                new MapSqlParameterSource("runId", runId), mapper());
    }

    private RowMapper<AuditEvent> mapper() {
        return this::mapRow;
    }

    private AuditEvent mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new AuditEvent(
                rs.getObject("id", UUID.class),
                rs.getObject("task_id", UUID.class),
                rs.getObject("run_id", UUID.class),
                rs.getObject("step_id", UUID.class),
                rs.getObject("action_id", UUID.class),
                rs.getString("event_type"),
                rs.getString("actor"),
                rs.getString("level"),
                rs.getObject("occurred_at", OffsetDateTime.class).toInstant(),
                rs.getString("input_summary"),
                rs.getString("output_summary"),
                json.toStringList(rs.getString("related_files")),
                rs.getObject("related_tool_call_id", UUID.class),
                rs.getObject("related_approval_id", UUID.class),
                rs.getObject("related_command_id", UUID.class),
                rs.getObject("related_file_change_id", UUID.class),
                rs.getString("permission_level"),
                rs.getString("risk_level"),
                rs.getString("approval_status"),
                (Boolean) rs.getObject("success"),
                rs.getString("error_code"),
                rs.getString("error_message"),
                json.toMap(rs.getString("metadata")));
    }

    /**
     * Converts a service-layer draft into a persistent event.
     */
    public static AuditEvent fromDraft(UUID id, Instant now, AuditEventDraft draft) {
        return new AuditEvent(
                id,
                draft.taskId(),
                draft.runId(),
                draft.stepId(),
                draft.actionId(),
                draft.eventType().name(),
                draft.actor().name(),
                draft.level().name(),
                now,
                draft.inputSummary(),
                draft.outputSummary(),
                draft.relatedFiles() == null ? List.of() : draft.relatedFiles(),
                draft.relatedToolCallId(),
                draft.relatedApprovalId(),
                draft.relatedCommandId(),
                draft.relatedFileChangeId(),
                draft.permissionLevel() == null ? null : draft.permissionLevel().name(),
                draft.riskLevel() == null ? null : draft.riskLevel().name(),
                draft.approvalStatus() == null ? null : draft.approvalStatus().name(),
                draft.success(),
                draft.errorCode(),
                draft.errorMessage(),
                draft.metadata() == null ? java.util.Map.of() : draft.metadata());
    }
}
