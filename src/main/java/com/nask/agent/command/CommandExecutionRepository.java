package com.nask.agent.command;

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
 * JDBC repository for command execution records.
 */
@Repository
public class CommandExecutionRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final JsonSupport json;

    /**
     * Creates a repository backed by JDBC and JSON helpers.
     */
    public CommandExecutionRepository(NamedParameterJdbcTemplate jdbc, JsonSupport json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /**
     * Inserts a command execution row before the process starts or waits.
     */
    public CommandExecution insert(CommandExecution command) {
        jdbc.update("""
                insert into command_execution (
                  id, workspace_id, task_id, run_id, step_id, action_id, command, executable,
                  arguments, working_directory, policy_type, risk_level, approval_id, status,
                  exit_code, output_summary, started_at, finished_at, created_at
                ) values (
                  :id, :workspaceId, :taskId, :runId, :stepId, :actionId, :command, :executable,
                  cast(:arguments as jsonb), :workingDirectory, :policyType, :riskLevel, :approvalId, :status,
                  :exitCode, :outputSummary, :startedAt, :finishedAt, :createdAt
                )
                """, params(command));
        return command;
    }

    /**
     * Completes a command execution with status, exit code, and output summary.
     */
    public void complete(UUID id, String status, Integer exitCode, String outputSummary) {
        jdbc.update("""
                update command_execution
                   set status = :status, exit_code = :exitCode, output_summary = :outputSummary, finished_at = :finishedAt
                 where id = :id
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("status", status)
                .addValue("exitCode", exitCode)
                .addValue("outputSummary", outputSummary)
                .addValue("finishedAt", ts(Instant.now())));
    }

    /**
     * Links the approval request that can later resume this waiting command.
     */
    public void attachApproval(UUID id, UUID approvalId) {
        jdbc.update("""
                update command_execution
                   set approval_id = :approvalId
                 where id = :id
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("approvalId", approvalId));
    }

    /**
     * Marks a previously waiting command as actively running.
     */
    public void markRunning(UUID id) {
        jdbc.update("""
                update command_execution
                   set status = :status,
                       started_at = :startedAt
                 where id = :id
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("status", Domain.CommandExecutionStatus.RUNNING.name())
                .addValue("startedAt", ts(Instant.now())));
    }

    /**
     * Finds the oldest approved command execution pause for a run.
     */
    public Optional<CommandExecution> findApprovedWaitingByRun(UUID runId) {
        return jdbc.query("""
                select ce.*
                  from command_execution ce
                  join approval_request ar on ar.id = ce.approval_id
                 where ce.run_id = :runId
                   and ce.status = :commandStatus
                   and ar.status = :approvalStatus
                   and ar.approval_type = :approvalType
                 order by ar.resolved_at nulls last, ce.created_at, ce.id
                 limit 1
                """, new MapSqlParameterSource()
                .addValue("runId", runId)
                .addValue("commandStatus", Domain.CommandExecutionStatus.WAITING_APPROVAL.name())
                .addValue("approvalStatus", Domain.ApprovalStatus.APPROVED.name())
                .addValue("approvalType", Domain.ApprovalType.COMMAND_EXECUTION.name()), mapper()).stream().findFirst();
    }

    /**
     * Lists commands requested by a task in creation order.
     */
    public List<CommandExecution> findByTask(UUID taskId) {
        return jdbc.query("select * from command_execution where task_id = :taskId order by created_at, id",
                new MapSqlParameterSource("taskId", taskId), mapper());
    }

    private MapSqlParameterSource params(CommandExecution command) {
        return new MapSqlParameterSource()
                .addValue("id", command.id())
                .addValue("workspaceId", command.workspaceId())
                .addValue("taskId", command.taskId())
                .addValue("runId", command.runId())
                .addValue("stepId", command.stepId())
                .addValue("actionId", command.actionId())
                .addValue("command", command.command())
                .addValue("executable", command.executable())
                .addValue("arguments", json.toJson(command.arguments()))
                .addValue("workingDirectory", command.workingDirectory())
                .addValue("policyType", command.policyType())
                .addValue("riskLevel", command.riskLevel())
                .addValue("approvalId", command.approvalId())
                .addValue("status", command.status())
                .addValue("exitCode", command.exitCode())
                .addValue("outputSummary", command.outputSummary())
                .addValue("startedAt", ts(command.startedAt()))
                .addValue("finishedAt", ts(command.finishedAt()))
                .addValue("createdAt", ts(command.createdAt()));
    }

    private RowMapper<CommandExecution> mapper() {
        return this::mapRow;
    }

    private CommandExecution mapRow(ResultSet rs, int rowNum) throws SQLException {
        var started = rs.getObject("started_at", OffsetDateTime.class);
        var finished = rs.getObject("finished_at", OffsetDateTime.class);
        return new CommandExecution(
                rs.getObject("id", UUID.class),
                rs.getObject("workspace_id", UUID.class),
                rs.getObject("task_id", UUID.class),
                rs.getObject("run_id", UUID.class),
                rs.getObject("step_id", UUID.class),
                rs.getObject("action_id", UUID.class),
                rs.getString("command"),
                rs.getString("executable"),
                json.toStringList(rs.getString("arguments")),
                rs.getString("working_directory"),
                rs.getString("policy_type"),
                rs.getString("risk_level"),
                rs.getObject("approval_id", UUID.class),
                rs.getString("status"),
                (Integer) rs.getObject("exit_code"),
                rs.getString("output_summary"),
                started == null ? null : started.toInstant(),
                finished == null ? null : finished.toInstant(),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }
}
