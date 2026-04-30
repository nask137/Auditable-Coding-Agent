package com.nask.agent.tool;

import com.nask.agent.common.Domain;
import com.nask.agent.common.JsonSupport;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static com.nask.agent.common.DbValues.ts;

/**
 * JDBC repository for tool call and tool result records.
 */
@Repository
public class ToolRecordRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final JsonSupport json;

    /**
     * Creates a repository backed by JDBC and JSON helpers.
     */
    public ToolRecordRepository(NamedParameterJdbcTemplate jdbc, JsonSupport json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /**
     * Starts a tool call record in {@code RUNNING} status.
     */
    public ToolCallRecord insertCall(UUID actionId, String toolName, Domain.PermissionLevel permissionLevel,
                                     String inputSummary, Map<String, Object> inputPayload) {
        var record = new ToolCallRecord(UUID.randomUUID(), actionId, toolName, permissionLevel.name(), inputSummary,
                inputPayload, Domain.ToolCallStatus.RUNNING.name(), Instant.now(), null);
        jdbc.update("""
                insert into tool_call (id, action_id, tool_name, permission_level, input_summary, input_payload, status, started_at, finished_at)
                values (:id, :actionId, :toolName, :permissionLevel, :inputSummary, cast(:inputPayload as jsonb), :status, :startedAt, :finishedAt)
                """, new MapSqlParameterSource()
                .addValue("id", record.id())
                .addValue("actionId", record.actionId())
                .addValue("toolName", record.toolName())
                .addValue("permissionLevel", record.permissionLevel())
                .addValue("inputSummary", record.inputSummary())
                .addValue("inputPayload", json.toJson(record.inputPayload()))
                .addValue("status", record.status())
                .addValue("startedAt", ts(record.startedAt()))
                .addValue("finishedAt", ts(record.finishedAt())));
        return record;
    }

    /**
     * Completes or blocks a tool call.
     */
    public void completeCall(UUID toolCallId, Domain.ToolCallStatus status) {
        jdbc.update("update tool_call set status = :status, finished_at = :finishedAt where id = :id",
                new MapSqlParameterSource()
                        .addValue("id", toolCallId)
                        .addValue("status", status.name())
                        .addValue("finishedAt", ts(Instant.now())));
    }

    /**
     * Inserts the output record for a completed tool call.
     */
    public ToolResultRecord insertResult(UUID toolCallId, boolean success, String outputSummary,
                                         Map<String, Object> outputPayload, String errorMessage,
                                         Map<String, Object> metadata) {
        var result = new ToolResultRecord(UUID.randomUUID(), toolCallId, success, outputSummary, outputPayload,
                errorMessage, metadata, Instant.now());
        jdbc.update("""
                insert into tool_result (id, tool_call_id, success, output_summary, output_payload, error_message, metadata, created_at)
                values (:id, :toolCallId, :success, :outputSummary, cast(:outputPayload as jsonb), :errorMessage, cast(:metadata as jsonb), :createdAt)
                """, new MapSqlParameterSource()
                .addValue("id", result.id())
                .addValue("toolCallId", result.toolCallId())
                .addValue("success", result.success())
                .addValue("outputSummary", result.outputSummary())
                .addValue("outputPayload", json.toJson(result.outputPayload()))
                .addValue("errorMessage", result.errorMessage())
                .addValue("metadata", json.toJson(result.metadata()))
                .addValue("createdAt", ts(result.createdAt())));
        return result;
    }
}
