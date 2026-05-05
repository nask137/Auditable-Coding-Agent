package com.nask.agent.memory;

import com.nask.agent.common.JsonSupport;
import com.nask.agent.report.TaskReport;
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
 * JDBC repository for phase 4 project profiles and scan records.
 */
@Repository
public class ProjectMemoryRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final JsonSupport json;

    public ProjectMemoryRepository(NamedParameterJdbcTemplate jdbc, JsonSupport json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public ProjectScanRun insertScanRun(ProjectScanRun scanRun) {
        jdbc.update("""
                insert into project_scan_run (
                  id, workspace_id, task_id, run_id, status, scan_reason, started_at, completed_at,
                  files_seen, files_indexed, files_skipped, summary, metadata_json
                ) values (
                  :id, :workspaceId, :taskId, :runId, :status, :scanReason, :startedAt, :completedAt,
                  :filesSeen, :filesIndexed, :filesSkipped, :summary, cast(:metadataJson as jsonb)
                )
                """, params(scanRun));
        return scanRun;
    }

    public ProjectScanRun updateScanRun(ProjectScanRun scanRun) {
        jdbc.update("""
                update project_scan_run
                   set status = :status,
                       completed_at = :completedAt,
                       files_seen = :filesSeen,
                       files_indexed = :filesIndexed,
                       files_skipped = :filesSkipped,
                       summary = :summary,
                       metadata_json = cast(:metadataJson as jsonb)
                 where id = :id
                """, params(scanRun));
        return scanRun;
    }

    public ProjectProfile upsertProfile(ProjectProfile profile) {
        jdbc.update("""
                insert into project_profile (
                  id, workspace_id, language_summary, frameworks_json, build_tools_json, test_tools_json,
                  package_managers_json, entrypoints_json, important_paths_json, docs_paths_json,
                  config_paths_json, last_scan_run_id, confidence, created_at, updated_at
                ) values (
                  :id, :workspaceId, :languageSummary, cast(:frameworks as jsonb), cast(:buildTools as jsonb),
                  cast(:testTools as jsonb), cast(:packageManagers as jsonb), cast(:entrypoints as jsonb),
                  cast(:importantPaths as jsonb), cast(:docsPaths as jsonb), cast(:configPaths as jsonb),
                  :lastScanRunId, :confidence, :createdAt, :updatedAt
                )
                on conflict (workspace_id) do update set
                  language_summary = excluded.language_summary,
                  frameworks_json = excluded.frameworks_json,
                  build_tools_json = excluded.build_tools_json,
                  test_tools_json = excluded.test_tools_json,
                  package_managers_json = excluded.package_managers_json,
                  entrypoints_json = excluded.entrypoints_json,
                  important_paths_json = excluded.important_paths_json,
                  docs_paths_json = excluded.docs_paths_json,
                  config_paths_json = excluded.config_paths_json,
                  last_scan_run_id = excluded.last_scan_run_id,
                  confidence = excluded.confidence,
                  updated_at = excluded.updated_at
                """, profileParams(profile));
        return findProfileByWorkspace(profile.workspaceId()).orElse(profile);
    }

    public int insertIndexedDocuments(List<IndexedDocument> documents) {
        var inserted = 0;
        for (var document : documents) {
            inserted += jdbc.update("""
                    insert into indexed_document (
                      id, workspace_id, scan_run_id, path, document_type, title, chunk_index, content,
                      content_hash, line_start, line_end, token_count, metadata_json, created_at
                    ) values (
                      :id, :workspaceId, :scanRunId, :path, :documentType, :title, :chunkIndex, :content,
                      :contentHash, :lineStart, :lineEnd, :tokenCount, cast(:metadataJson as jsonb), :createdAt
                    )
                    on conflict (workspace_id, document_type, path, chunk_index, content_hash) do nothing
                    """, documentParams(document));
        }
        return inserted;
    }

    public int replaceIndexedDocuments(UUID workspaceId, String source, List<IndexedDocument> documents) {
        jdbc.update("""
                delete from indexed_document
                 where workspace_id = :workspaceId
                   and metadata_json ->> 'source' = :source
                """, new MapSqlParameterSource()
                .addValue("workspaceId", workspaceId)
                .addValue("source", source));
        return insertIndexedDocuments(documents);
    }

    public int replaceCodeSymbols(UUID workspaceId, List<CodeSymbol> symbols) {
        jdbc.update("delete from code_symbol where workspace_id = :workspaceId",
                new MapSqlParameterSource("workspaceId", workspaceId));
        var inserted = 0;
        for (var symbol : symbols) {
            inserted += jdbc.update("""
                    insert into code_symbol (
                      id, workspace_id, scan_run_id, path, language, symbol_type, symbol_name, container_name,
                      signature, line_start, line_end, visibility, metadata_json, created_at
                    ) values (
                      :id, :workspaceId, :scanRunId, :path, :language, :symbolType, :symbolName, :containerName,
                      :signature, :lineStart, :lineEnd, :visibility, cast(:metadataJson as jsonb), :createdAt
                    )
                    """, symbolParams(symbol));
        }
        return inserted;
    }

    public ProjectMemoryItem insertProjectMemoryItem(ProjectMemoryItem item) {
        jdbc.update("""
                insert into project_memory_item (
                  id, workspace_id, memory_type, scope, title, content, source_type, source_id,
                  source_path, source_line_start, source_line_end, status, confidence, expires_at,
                  created_by, created_at, approved_by, approved_at, metadata_json
                ) values (
                  :id, :workspaceId, :memoryType, :scope, :title, :content, :sourceType, :sourceId,
                  :sourcePath, :sourceLineStart, :sourceLineEnd, :status, :confidence, :expiresAt,
                  :createdBy, :createdAt, :approvedBy, :approvedAt, cast(:metadataJson as jsonb)
                )
                """, memoryItemParams(item));
        return item;
    }

    public MemoryRetrieval insertMemoryRetrieval(MemoryRetrieval retrieval) {
        jdbc.update("""
                insert into memory_retrieval (
                  id, workspace_id, task_id, run_id, workflow_node_execution_id, query_text,
                  filters_json, result_refs_json, summary, created_at
                ) values (
                  :id, :workspaceId, :taskId, :runId, :workflowNodeExecutionId, :queryText,
                  cast(:filtersJson as jsonb), cast(:resultRefsJson as jsonb), :summary, :createdAt
                )
                """, retrievalParams(retrieval));
        return retrieval;
    }

    public MemoryWriteProposal insertMemoryWriteProposal(MemoryWriteProposal proposal) {
        jdbc.update("""
                insert into memory_write_proposal (
                  id, workspace_id, task_id, run_id, proposal_type, title, content, source_refs_json,
                  status, approval_request_id, project_memory_item_id, created_at, resolved_at, metadata_json
                ) values (
                  :id, :workspaceId, :taskId, :runId, :proposalType, :title, :content, cast(:sourceRefsJson as jsonb),
                  :status, :approvalRequestId, :projectMemoryItemId, :createdAt, :resolvedAt, cast(:metadataJson as jsonb)
                )
                """, proposalParams(proposal));
        return proposal;
    }

    public void attachApprovalToMemoryWriteProposal(UUID proposalId, UUID approvalRequestId) {
        jdbc.update("""
                update memory_write_proposal
                   set approval_request_id = :approvalRequestId
                 where id = :proposalId
                """, new MapSqlParameterSource()
                .addValue("proposalId", proposalId)
                .addValue("approvalRequestId", approvalRequestId));
    }

    public void resolveMemoryWriteProposal(UUID proposalId, String status, UUID projectMemoryItemId) {
        jdbc.update("""
                update memory_write_proposal
                   set status = :status,
                       project_memory_item_id = :projectMemoryItemId,
                       resolved_at = now()
                 where id = :proposalId
                """, new MapSqlParameterSource()
                .addValue("proposalId", proposalId)
                .addValue("status", status)
                .addValue("projectMemoryItemId", projectMemoryItemId));
    }

    public Optional<ProjectProfile> findProfileByWorkspace(UUID workspaceId) {
        return jdbc.query("select * from project_profile where workspace_id = :workspaceId",
                new MapSqlParameterSource("workspaceId", workspaceId), profileMapper()).stream().findFirst();
    }

    public List<ProjectScanRun> findScanRunsByWorkspace(UUID workspaceId) {
        return jdbc.query("""
                select * from project_scan_run
                 where workspace_id = :workspaceId
                 order by started_at desc, id
                """, new MapSqlParameterSource("workspaceId", workspaceId), scanMapper());
    }

    public List<IndexedDocument> findIndexedDocumentsByWorkspace(UUID workspaceId) {
        return jdbc.query("""
                select * from indexed_document
                 where workspace_id = :workspaceId
                 order by document_type, path, chunk_index, created_at
                """, new MapSqlParameterSource("workspaceId", workspaceId), indexedDocumentMapper());
    }

    public List<ProjectMemoryItem> findProjectMemoryItemsByWorkspace(UUID workspaceId) {
        return jdbc.query("""
                select * from project_memory_item
                 where workspace_id = :workspaceId
                   and status in ('APPROVED', 'PROPOSED')
                   and (expires_at is null or expires_at > now())
                 order by status, confidence desc, created_at desc, id
                """, new MapSqlParameterSource("workspaceId", workspaceId), memoryItemMapper());
    }

    public List<TaskReport> findTaskReportsByWorkspace(UUID workspaceId) {
        return jdbc.query("""
                select r.*
                  from task_report r
                  join task t on t.id = r.task_id
                 where t.workspace_id = :workspaceId
                 order by r.created_at desc, r.id
                """, new MapSqlParameterSource("workspaceId", workspaceId), taskReportMapper());
    }

    public List<CodeSymbol> searchCodeSymbols(UUID workspaceId, String query, String symbolType) {
        var sql = new StringBuilder("""
                select * from code_symbol
                 where workspace_id = :workspaceId
                """);
        var params = new MapSqlParameterSource("workspaceId", workspaceId);
        if (query != null && !query.isBlank()) {
            sql.append(" and lower(symbol_name) like :query");
            params.addValue("query", "%" + query.toLowerCase(java.util.Locale.ROOT) + "%");
        }
        if (symbolType != null && !symbolType.isBlank()) {
            sql.append(" and symbol_type = :symbolType");
            params.addValue("symbolType", symbolType);
        }
        sql.append(" order by path, line_start, symbol_name");
        return jdbc.query(sql.toString(), params, codeSymbolMapper());
    }

    public List<CodeSymbol> findCodeSymbolsByPath(UUID workspaceId, String path) {
        return jdbc.query("""
                select * from code_symbol
                 where workspace_id = :workspaceId
                   and path = :path
                 order by line_start, symbol_name
                """, new MapSqlParameterSource()
                .addValue("workspaceId", workspaceId)
                .addValue("path", path), codeSymbolMapper());
    }

    public List<MemoryRetrieval> findMemoryRetrievalsByWorkspace(UUID workspaceId) {
        return jdbc.query("""
                select * from memory_retrieval
                 where workspace_id = :workspaceId
                 order by created_at desc, id
                """, new MapSqlParameterSource("workspaceId", workspaceId), memoryRetrievalMapper());
    }

    public List<MemoryRetrieval> findMemoryRetrievalsByRun(UUID runId) {
        return jdbc.query("""
                select * from memory_retrieval
                 where run_id = :runId
                 order by created_at desc, id
                """, new MapSqlParameterSource("runId", runId), memoryRetrievalMapper());
    }

    public Optional<MemoryWriteProposal> findMemoryWriteProposalById(UUID proposalId) {
        return jdbc.query("select * from memory_write_proposal where id = :proposalId",
                new MapSqlParameterSource("proposalId", proposalId), memoryWriteProposalMapper())
                .stream().findFirst();
    }

    public Optional<MemoryWriteProposal> findMemoryWriteProposalByApprovalRequestId(UUID approvalRequestId) {
        return jdbc.query("""
                select * from memory_write_proposal
                 where approval_request_id = :approvalRequestId
                """, new MapSqlParameterSource("approvalRequestId", approvalRequestId),
                memoryWriteProposalMapper()).stream().findFirst();
    }

    public List<MemoryWriteProposal> findMemoryWriteProposalsByWorkspace(UUID workspaceId) {
        return jdbc.query("""
                select * from memory_write_proposal
                 where workspace_id = :workspaceId
                 order by created_at desc, id
                """, new MapSqlParameterSource("workspaceId", workspaceId), memoryWriteProposalMapper());
    }

    public List<MemoryWriteProposal> findMemoryWriteProposalsByRun(UUID runId) {
        return jdbc.query("""
                select * from memory_write_proposal
                 where run_id = :runId
                 order by created_at desc, id
                """, new MapSqlParameterSource("runId", runId), memoryWriteProposalMapper());
    }

    public boolean existsMemoryWriteProposalForRun(UUID runId, String proposalType, String title, String content) {
        var count = jdbc.queryForObject("""
                select count(*)
                  from memory_write_proposal
                 where run_id = :runId
                   and proposal_type = :proposalType
                   and title = :title
                   and content = :content
                """, new MapSqlParameterSource()
                .addValue("runId", runId)
                .addValue("proposalType", proposalType)
                .addValue("title", title)
                .addValue("content", content), Integer.class);
        return count != null && count > 0;
    }

    private MapSqlParameterSource params(ProjectScanRun scanRun) {
        return new MapSqlParameterSource()
                .addValue("id", scanRun.id())
                .addValue("workspaceId", scanRun.workspaceId())
                .addValue("taskId", scanRun.taskId())
                .addValue("runId", scanRun.runId())
                .addValue("status", scanRun.status())
                .addValue("scanReason", scanRun.scanReason())
                .addValue("startedAt", ts(scanRun.startedAt()))
                .addValue("completedAt", ts(scanRun.completedAt()))
                .addValue("filesSeen", scanRun.filesSeen())
                .addValue("filesIndexed", scanRun.filesIndexed())
                .addValue("filesSkipped", scanRun.filesSkipped())
                .addValue("summary", scanRun.summary())
                .addValue("metadataJson", json.toJson(scanRun.metadata()));
    }

    private MapSqlParameterSource profileParams(ProjectProfile profile) {
        return new MapSqlParameterSource()
                .addValue("id", profile.id())
                .addValue("workspaceId", profile.workspaceId())
                .addValue("languageSummary", profile.languageSummary())
                .addValue("frameworks", json.toJson(profile.frameworks()))
                .addValue("buildTools", json.toJson(profile.buildTools()))
                .addValue("testTools", json.toJson(profile.testTools()))
                .addValue("packageManagers", json.toJson(profile.packageManagers()))
                .addValue("entrypoints", json.toJson(profile.entrypoints()))
                .addValue("importantPaths", json.toJson(profile.importantPaths()))
                .addValue("docsPaths", json.toJson(profile.docsPaths()))
                .addValue("configPaths", json.toJson(profile.configPaths()))
                .addValue("lastScanRunId", profile.lastScanRunId())
                .addValue("confidence", profile.confidence())
                .addValue("createdAt", ts(profile.createdAt()))
                .addValue("updatedAt", ts(profile.updatedAt()));
    }

    private MapSqlParameterSource documentParams(IndexedDocument document) {
        return new MapSqlParameterSource()
                .addValue("id", document.id())
                .addValue("workspaceId", document.workspaceId())
                .addValue("scanRunId", document.scanRunId())
                .addValue("path", document.path())
                .addValue("documentType", document.documentType())
                .addValue("title", document.title())
                .addValue("chunkIndex", document.chunkIndex())
                .addValue("content", document.content())
                .addValue("contentHash", document.contentHash())
                .addValue("lineStart", document.lineStart())
                .addValue("lineEnd", document.lineEnd())
                .addValue("tokenCount", document.tokenCount())
                .addValue("metadataJson", json.toJson(document.metadata()))
                .addValue("createdAt", ts(document.createdAt()));
    }

    private MapSqlParameterSource symbolParams(CodeSymbol symbol) {
        return new MapSqlParameterSource()
                .addValue("id", symbol.id())
                .addValue("workspaceId", symbol.workspaceId())
                .addValue("scanRunId", symbol.scanRunId())
                .addValue("path", symbol.path())
                .addValue("language", symbol.language())
                .addValue("symbolType", symbol.symbolType())
                .addValue("symbolName", symbol.symbolName())
                .addValue("containerName", symbol.containerName())
                .addValue("signature", symbol.signature())
                .addValue("lineStart", symbol.lineStart())
                .addValue("lineEnd", symbol.lineEnd())
                .addValue("visibility", symbol.visibility())
                .addValue("metadataJson", json.toJson(symbol.metadata()))
                .addValue("createdAt", ts(symbol.createdAt()));
    }

    private MapSqlParameterSource memoryItemParams(ProjectMemoryItem item) {
        return new MapSqlParameterSource()
                .addValue("id", item.id())
                .addValue("workspaceId", item.workspaceId())
                .addValue("memoryType", item.memoryType())
                .addValue("scope", item.scope())
                .addValue("title", item.title())
                .addValue("content", item.content())
                .addValue("sourceType", item.sourceType())
                .addValue("sourceId", item.sourceId())
                .addValue("sourcePath", item.sourcePath())
                .addValue("sourceLineStart", item.sourceLineStart())
                .addValue("sourceLineEnd", item.sourceLineEnd())
                .addValue("status", item.status())
                .addValue("confidence", item.confidence())
                .addValue("expiresAt", ts(item.expiresAt()))
                .addValue("createdBy", item.createdBy())
                .addValue("createdAt", ts(item.createdAt()))
                .addValue("approvedBy", item.approvedBy())
                .addValue("approvedAt", ts(item.approvedAt()))
                .addValue("metadataJson", json.toJson(item.metadata()));
    }

    private MapSqlParameterSource retrievalParams(MemoryRetrieval retrieval) {
        return new MapSqlParameterSource()
                .addValue("id", retrieval.id())
                .addValue("workspaceId", retrieval.workspaceId())
                .addValue("taskId", retrieval.taskId())
                .addValue("runId", retrieval.runId())
                .addValue("workflowNodeExecutionId", retrieval.workflowNodeExecutionId())
                .addValue("queryText", retrieval.queryText())
                .addValue("filtersJson", json.toJson(retrieval.filters()))
                .addValue("resultRefsJson", json.toJson(retrieval.resultRefs()))
                .addValue("summary", retrieval.summary())
                .addValue("createdAt", ts(retrieval.createdAt()));
    }

    private MapSqlParameterSource proposalParams(MemoryWriteProposal proposal) {
        return new MapSqlParameterSource()
                .addValue("id", proposal.id())
                .addValue("workspaceId", proposal.workspaceId())
                .addValue("taskId", proposal.taskId())
                .addValue("runId", proposal.runId())
                .addValue("proposalType", proposal.proposalType())
                .addValue("title", proposal.title())
                .addValue("content", proposal.content())
                .addValue("sourceRefsJson", json.toJson(proposal.sourceRefs()))
                .addValue("status", proposal.status())
                .addValue("approvalRequestId", proposal.approvalRequestId())
                .addValue("projectMemoryItemId", proposal.projectMemoryItemId())
                .addValue("createdAt", ts(proposal.createdAt()))
                .addValue("resolvedAt", ts(proposal.resolvedAt()))
                .addValue("metadataJson", json.toJson(proposal.metadata()));
    }

    private RowMapper<ProjectScanRun> scanMapper() {
        return this::mapScan;
    }

    private RowMapper<ProjectProfile> profileMapper() {
        return this::mapProfile;
    }

    private RowMapper<IndexedDocument> indexedDocumentMapper() {
        return this::mapIndexedDocument;
    }

    private RowMapper<TaskReport> taskReportMapper() {
        return this::mapTaskReport;
    }

    private RowMapper<CodeSymbol> codeSymbolMapper() {
        return this::mapCodeSymbol;
    }

    private RowMapper<ProjectMemoryItem> memoryItemMapper() {
        return this::mapMemoryItem;
    }

    private RowMapper<MemoryRetrieval> memoryRetrievalMapper() {
        return this::mapMemoryRetrieval;
    }

    private RowMapper<MemoryWriteProposal> memoryWriteProposalMapper() {
        return this::mapMemoryWriteProposal;
    }

    private ProjectScanRun mapScan(ResultSet rs, int rowNum) throws SQLException {
        var completed = rs.getObject("completed_at", OffsetDateTime.class);
        return new ProjectScanRun(rs.getObject("id", UUID.class), rs.getObject("workspace_id", UUID.class),
                rs.getObject("task_id", UUID.class), rs.getObject("run_id", UUID.class), rs.getString("status"),
                rs.getString("scan_reason"), rs.getObject("started_at", OffsetDateTime.class).toInstant(),
                completed == null ? null : completed.toInstant(), rs.getInt("files_seen"),
                rs.getInt("files_indexed"), rs.getInt("files_skipped"), rs.getString("summary"),
                json.toMap(rs.getString("metadata_json")));
    }

    private ProjectProfile mapProfile(ResultSet rs, int rowNum) throws SQLException {
        return new ProjectProfile(rs.getObject("id", UUID.class), rs.getObject("workspace_id", UUID.class),
                rs.getString("language_summary"), json.toStringList(rs.getString("frameworks_json")),
                json.toStringList(rs.getString("build_tools_json")),
                json.toStringList(rs.getString("test_tools_json")),
                json.toStringList(rs.getString("package_managers_json")),
                json.toStringList(rs.getString("entrypoints_json")),
                json.toStringList(rs.getString("important_paths_json")),
                json.toStringList(rs.getString("docs_paths_json")),
                json.toStringList(rs.getString("config_paths_json")),
                rs.getObject("last_scan_run_id", UUID.class),
                rs.getBigDecimal("confidence").doubleValue(),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getObject("updated_at", OffsetDateTime.class).toInstant());
    }

    private IndexedDocument mapIndexedDocument(ResultSet rs, int rowNum) throws SQLException {
        return new IndexedDocument(rs.getObject("id", UUID.class), rs.getObject("workspace_id", UUID.class),
                rs.getObject("scan_run_id", UUID.class), rs.getString("path"), rs.getString("document_type"),
                rs.getString("title"), rs.getInt("chunk_index"), rs.getString("content"),
                rs.getString("content_hash"), rs.getInt("line_start"), rs.getInt("line_end"),
                rs.getInt("token_count"), json.toMap(rs.getString("metadata_json")),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }

    private TaskReport mapTaskReport(ResultSet rs, int rowNum) throws SQLException {
        return new TaskReport(rs.getObject("id", UUID.class), rs.getObject("task_id", UUID.class),
                rs.getObject("run_id", UUID.class), rs.getString("content_md"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }

    private CodeSymbol mapCodeSymbol(ResultSet rs, int rowNum) throws SQLException {
        return new CodeSymbol(rs.getObject("id", UUID.class), rs.getObject("workspace_id", UUID.class),
                rs.getObject("scan_run_id", UUID.class), rs.getString("path"), rs.getString("language"),
                rs.getString("symbol_type"), rs.getString("symbol_name"), rs.getString("container_name"),
                rs.getString("signature"), rs.getInt("line_start"), rs.getInt("line_end"),
                rs.getString("visibility"), json.toMap(rs.getString("metadata_json")),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }

    private ProjectMemoryItem mapMemoryItem(ResultSet rs, int rowNum) throws SQLException {
        var expiresAt = rs.getObject("expires_at", OffsetDateTime.class);
        var approvedAt = rs.getObject("approved_at", OffsetDateTime.class);
        return new ProjectMemoryItem(rs.getObject("id", UUID.class), rs.getObject("workspace_id", UUID.class),
                rs.getString("memory_type"), rs.getString("scope"), rs.getString("title"),
                rs.getString("content"), rs.getString("source_type"), rs.getObject("source_id", UUID.class),
                rs.getString("source_path"), nullableInt(rs, "source_line_start"),
                nullableInt(rs, "source_line_end"), rs.getString("status"),
                rs.getBigDecimal("confidence").doubleValue(),
                expiresAt == null ? null : expiresAt.toInstant(), rs.getString("created_by"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(), rs.getString("approved_by"),
                approvedAt == null ? null : approvedAt.toInstant(), json.toMap(rs.getString("metadata_json")));
    }

    private MemoryRetrieval mapMemoryRetrieval(ResultSet rs, int rowNum) throws SQLException {
        return new MemoryRetrieval(rs.getObject("id", UUID.class), rs.getObject("workspace_id", UUID.class),
                rs.getObject("task_id", UUID.class), rs.getObject("run_id", UUID.class),
                rs.getObject("workflow_node_execution_id", UUID.class), rs.getString("query_text"),
                json.toMap(rs.getString("filters_json")), sourceRefs(rs.getString("result_refs_json")),
                rs.getString("summary"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }

    private MemoryWriteProposal mapMemoryWriteProposal(ResultSet rs, int rowNum) throws SQLException {
        var resolvedAt = rs.getObject("resolved_at", OffsetDateTime.class);
        return new MemoryWriteProposal(rs.getObject("id", UUID.class), rs.getObject("workspace_id", UUID.class),
                rs.getObject("task_id", UUID.class), rs.getObject("run_id", UUID.class),
                rs.getString("proposal_type"), rs.getString("title"), rs.getString("content"),
                sourceRefs(rs.getString("source_refs_json")), rs.getString("status"),
                rs.getObject("approval_request_id", UUID.class),
                rs.getObject("project_memory_item_id", UUID.class),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                resolvedAt == null ? null : resolvedAt.toInstant(), json.toMap(rs.getString("metadata_json")));
    }

    private List<SourceReference> sourceRefs(String jsonValue) {
        return json.toObjectList(jsonValue).stream()
                .map(this::sourceRef)
                .toList();
    }

    private SourceReference sourceRef(Map<String, Object> value) {
        return new SourceReference(string(value.get("sourceType")), uuid(value.get("sourceId")),
                string(value.get("path")), integer(value.get("lineStart")), integer(value.get("lineEnd")),
                string(value.get("symbolName")), uuid(value.get("scanRunId")), uuid(value.get("taskId")),
                uuid(value.get("runId")));
    }

    private UUID uuid(Object value) {
        return value == null || value.toString().isBlank() ? null : UUID.fromString(value.toString());
    }

    private Integer integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return value == null || value.toString().isBlank() ? null : Integer.parseInt(value.toString());
    }

    private String string(Object value) {
        return value == null ? null : value.toString();
    }

    private Integer nullableInt(ResultSet rs, String column) throws SQLException {
        var value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}
