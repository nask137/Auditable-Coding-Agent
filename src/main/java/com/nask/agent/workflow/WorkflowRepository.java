package com.nask.agent.workflow;

import com.nask.agent.common.JsonSupport;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.nask.agent.common.DbValues.ts;

/**
 * JDBC repository for workflow definitions, node executions, and edge decisions.
 */
@Repository
public class WorkflowRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final JsonSupport json;

    public WorkflowRepository(NamedParameterJdbcTemplate jdbc, JsonSupport json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public WorkflowDefinition upsertDefinition(WorkflowDefinition definition) {
        jdbc.update("""
                insert into workflow_definition (
                  id, name, version, description, mode, enabled, definition_json, created_at, updated_at
                ) values (
                  :id, :name, :version, :description, :mode, :enabled, cast(:definitionJson as jsonb), :createdAt, :updatedAt
                )
                on conflict (name, version) do update set
                  description = excluded.description,
                  mode = excluded.mode,
                  enabled = excluded.enabled,
                  definition_json = excluded.definition_json,
                  updated_at = excluded.updated_at
                """, new MapSqlParameterSource()
                .addValue("id", definition.id())
                .addValue("name", definition.name())
                .addValue("version", definition.version())
                .addValue("description", definition.description())
                .addValue("mode", definition.mode())
                .addValue("enabled", definition.enabled())
                .addValue("definitionJson", json.toJson(definition.definition()))
                .addValue("createdAt", ts(definition.createdAt()))
                .addValue("updatedAt", ts(definition.updatedAt())));
        return findByNameAndVersion(definition.name(), definition.version()).orElse(definition);
    }

    public List<WorkflowDefinition> findDefinitions() {
        return jdbc.query("select * from workflow_definition order by name, version", definitionMapper());
    }

    public Optional<WorkflowDefinition> findDefinition(UUID id) {
        return jdbc.query("select * from workflow_definition where id = :id",
                new MapSqlParameterSource("id", id), definitionMapper()).stream().findFirst();
    }

    public Optional<WorkflowDefinition> findLatestEnabledByName(String name) {
        return jdbc.query("""
                select * from workflow_definition
                 where name = :name and enabled = true
                 order by version desc
                 limit 1
                """, new MapSqlParameterSource("name", name), definitionMapper()).stream().findFirst();
    }

    public Optional<WorkflowDefinition> findByNameAndVersion(String name, int version) {
        return jdbc.query("""
                select * from workflow_definition
                 where name = :name and version = :version
                """, new MapSqlParameterSource()
                .addValue("name", name)
                .addValue("version", version), definitionMapper()).stream().findFirst();
    }

    public WorkflowNodeExecution insertNodeExecution(WorkflowNodeExecution execution) {
        jdbc.update("""
                insert into workflow_node_execution (
                  id, task_id, run_id, workflow_definition_id, node_id, node_type, agent_step_id,
                  status, input_summary, output_summary, failure_id, started_at, completed_at, metadata_json
                ) values (
                  :id, :taskId, :runId, :workflowDefinitionId, :nodeId, :nodeType, :agentStepId,
                  :status, :inputSummary, :outputSummary, :failureId, :startedAt, :completedAt, cast(:metadataJson as jsonb)
                )
                """, new MapSqlParameterSource()
                .addValue("id", execution.id())
                .addValue("taskId", execution.taskId())
                .addValue("runId", execution.runId())
                .addValue("workflowDefinitionId", execution.workflowDefinitionId())
                .addValue("nodeId", execution.nodeId())
                .addValue("nodeType", execution.nodeType())
                .addValue("agentStepId", execution.agentStepId())
                .addValue("status", execution.status())
                .addValue("inputSummary", execution.inputSummary())
                .addValue("outputSummary", execution.outputSummary())
                .addValue("failureId", execution.failureId())
                .addValue("startedAt", ts(execution.startedAt()))
                .addValue("completedAt", ts(execution.completedAt()))
                .addValue("metadataJson", json.toJson(execution.metadata())));
        return execution;
    }

    public int updateNodeExecutionForStep(UUID runId, UUID stepId, String status, String inputSummary,
                                           String outputSummary, java.time.Instant completedAt,
                                           Map<String, Object> metadata) {
        return jdbc.update("""
                update workflow_node_execution
                   set status = :status,
                       input_summary = :inputSummary,
                       output_summary = :outputSummary,
                       completed_at = :completedAt,
                       metadata_json = cast(:metadataJson as jsonb)
                 where run_id = :runId
                   and agent_step_id = :stepId
                """, new MapSqlParameterSource()
                .addValue("runId", runId)
                .addValue("stepId", stepId)
                .addValue("status", status)
                .addValue("inputSummary", inputSummary)
                .addValue("outputSummary", outputSummary)
                .addValue("completedAt", ts(completedAt))
                .addValue("metadataJson", json.toJson(metadata == null ? Map.of() : metadata)));
    }

    public WorkflowEdgeDecision insertEdgeDecision(WorkflowEdgeDecision decision) {
        jdbc.update("""
                insert into workflow_edge_decision (
                  id, task_id, run_id, workflow_definition_id, from_node_id, to_node_id, edge_type,
                  condition_summary, decision_reason, selected, created_at, metadata_json
                ) values (
                  :id, :taskId, :runId, :workflowDefinitionId, :fromNodeId, :toNodeId, :edgeType,
                  :conditionSummary, :decisionReason, :selected, :createdAt, cast(:metadataJson as jsonb)
                )
                """, new MapSqlParameterSource()
                .addValue("id", decision.id())
                .addValue("taskId", decision.taskId())
                .addValue("runId", decision.runId())
                .addValue("workflowDefinitionId", decision.workflowDefinitionId())
                .addValue("fromNodeId", decision.fromNodeId())
                .addValue("toNodeId", decision.toNodeId())
                .addValue("edgeType", decision.edgeType())
                .addValue("conditionSummary", decision.conditionSummary())
                .addValue("decisionReason", decision.decisionReason())
                .addValue("selected", decision.selected())
                .addValue("createdAt", ts(decision.createdAt()))
                .addValue("metadataJson", json.toJson(decision.metadata())));
        return decision;
    }

    public List<WorkflowNodeExecution> findNodeExecutionsByRun(UUID runId) {
        return jdbc.query("select * from workflow_node_execution where run_id = :runId order by started_at, id",
                new MapSqlParameterSource("runId", runId), nodeMapper());
    }

    public List<WorkflowEdgeDecision> findEdgeDecisionsByRun(UUID runId) {
        return jdbc.query("select * from workflow_edge_decision where run_id = :runId order by created_at, id",
                new MapSqlParameterSource("runId", runId), edgeMapper());
    }

    private RowMapper<WorkflowDefinition> definitionMapper() {
        return this::mapDefinition;
    }

    private RowMapper<WorkflowNodeExecution> nodeMapper() {
        return this::mapNode;
    }

    private RowMapper<WorkflowEdgeDecision> edgeMapper() {
        return this::mapEdge;
    }

    private WorkflowDefinition mapDefinition(ResultSet rs, int rowNum) throws SQLException {
        return new WorkflowDefinition(rs.getObject("id", UUID.class), rs.getString("name"),
                rs.getInt("version"), rs.getString("description"), rs.getString("mode"),
                rs.getBoolean("enabled"), json.toMap(rs.getString("definition_json")),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getObject("updated_at", OffsetDateTime.class).toInstant());
    }

    private WorkflowNodeExecution mapNode(ResultSet rs, int rowNum) throws SQLException {
        var completed = rs.getObject("completed_at", OffsetDateTime.class);
        return new WorkflowNodeExecution(rs.getObject("id", UUID.class), rs.getObject("task_id", UUID.class),
                rs.getObject("run_id", UUID.class), rs.getObject("workflow_definition_id", UUID.class),
                rs.getString("node_id"), rs.getString("node_type"), rs.getObject("agent_step_id", UUID.class),
                rs.getString("status"), rs.getString("input_summary"), rs.getString("output_summary"),
                rs.getObject("failure_id", UUID.class), rs.getObject("started_at", OffsetDateTime.class).toInstant(),
                completed == null ? null : completed.toInstant(), json.toMap(rs.getString("metadata_json")));
    }

    private WorkflowEdgeDecision mapEdge(ResultSet rs, int rowNum) throws SQLException {
        return new WorkflowEdgeDecision(rs.getObject("id", UUID.class), rs.getObject("task_id", UUID.class),
                rs.getObject("run_id", UUID.class), rs.getObject("workflow_definition_id", UUID.class),
                rs.getString("from_node_id"), rs.getString("to_node_id"), rs.getString("edge_type"),
                rs.getString("condition_summary"), rs.getString("decision_reason"), rs.getBoolean("selected"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(), json.toMap(rs.getString("metadata_json")));
    }
}
