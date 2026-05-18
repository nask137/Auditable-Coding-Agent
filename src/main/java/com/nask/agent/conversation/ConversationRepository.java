package com.nask.agent.conversation;

import com.nask.agent.common.JsonSupport;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.nask.agent.common.DbValues.ts;

/**
 * JDBC repository for conversation rows and compact task history.
 */
@Repository
public class ConversationRepository {

    private static final int DEFAULT_REPORT_EXCERPT_BYTES = 1200;
    private final NamedParameterJdbcTemplate jdbc;
    private final JsonSupport json;

    public ConversationRepository(NamedParameterJdbcTemplate jdbc, JsonSupport json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public Conversation insert(Conversation conversation) {
        jdbc.update("""
                insert into conversation (id, workspace_id, title, created_at, updated_at)
                values (:id, :workspaceId, :title, :createdAt, :updatedAt)
                """, new MapSqlParameterSource()
                .addValue("id", conversation.id())
                .addValue("workspaceId", conversation.workspaceId())
                .addValue("title", conversation.title())
                .addValue("createdAt", ts(conversation.createdAt()))
                .addValue("updatedAt", ts(conversation.updatedAt())));
        return conversation;
    }

    public Optional<Conversation> findById(UUID id) {
        return jdbc.query("select * from conversation where id = :id",
                new MapSqlParameterSource("id", id), mapper()).stream().findFirst();
    }

    public List<Conversation> findByWorkspace(UUID workspaceId) {
        return jdbc.query("""
                select * from conversation
                 where workspace_id = :workspaceId
                 order by updated_at desc, id
                """, new MapSqlParameterSource("workspaceId", workspaceId), mapper());
    }

    public void touch(UUID id) {
        jdbc.update("update conversation set updated_at = now() where id = :id",
                new MapSqlParameterSource("id", id));
    }

    public Conversation updateTitle(UUID id, String title) {
        jdbc.update("""
                update conversation
                   set title = :title,
                       updated_at = now()
                 where id = :id
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("title", title));
        return findById(id).orElseThrow();
    }

    public int nextTaskIndex(UUID conversationId) {
        return jdbc.queryForObject("""
                select coalesce(max(prompt_index), 0) + 1
                  from task
                 where conversation_id = :conversationId
                """, new MapSqlParameterSource("conversationId", conversationId), Integer.class);
    }

    public List<ConversationTaskContext> previousTaskContext(UUID conversationId, UUID currentTaskId, int limit) {
        return previousTaskContext(conversationId, currentTaskId, limit,
                Math.max(1, limit) * DEFAULT_REPORT_EXCERPT_BYTES);
    }

    public List<ConversationTaskContext> previousTaskContext(UUID conversationId, UUID currentTaskId, int limit,
                                                             int contextFetchMaxBytes) {
        return jdbc.query("""
                with previous_tasks as (
                    select t.id,
                           t.user_request,
                           t.status,
                           t.created_at,
                           t.prompt_index
                      from task t
                     where t.conversation_id = :conversationId
                       and t.id <> :currentTaskId
                     order by t.prompt_index desc, t.created_at desc
                     limit :limit
                ),
                task_context as (
                    select t.id,
                           t.user_request,
                           t.status,
                           t.created_at,
                           t.prompt_index,
                           coalesce(r.content_md, '') as raw_report,
                           coalesce(f.affected_files, '[]'::jsonb) as affected_files,
                           octet_length(coalesce(t.user_request, ''))
                             + octet_length(coalesce(t.status, ''))
                             + octet_length(coalesce(f.affected_files::text, '[]'))
                             + 64 as base_bytes
                      from previous_tasks t
                      left join lateral (
                        select content_md
                          from task_report tr
                         where tr.task_id = t.id
                         order by tr.created_at desc
                         limit 1
                      ) r on true
                      left join lateral (
                        select coalesce(jsonb_agg(distinct fc.path) filter (where fc.path is not null),
                                        '[]'::jsonb) as affected_files
                          from file_change fc
                         where fc.task_id = t.id
                      ) f on true
                ),
                budgeted as (
                    select *,
                           coalesce(sum(base_bytes + octet_length(raw_report)) over (
                             order by prompt_index desc, created_at desc
                             rows between unbounded preceding and 1 preceding
                           ), 0) as bytes_before
                      from task_context
                )
                select id,
                       user_request,
                       status,
                       created_at,
                       case
                         when (:contextFetchMaxBytes - bytes_before - base_bytes) <= 0 then ''
                         else substring(raw_report from 1 for least(
                           octet_length(raw_report),
                           greatest(0, :contextFetchMaxBytes - bytes_before - base_bytes)
                         )::int)
                       end as content_md,
                       affected_files
                  from budgeted
                 where bytes_before < :contextFetchMaxBytes
                 order by prompt_index desc, created_at desc
                """, new MapSqlParameterSource()
                .addValue("conversationId", conversationId)
                .addValue("currentTaskId", currentTaskId)
                .addValue("limit", limit)
                .addValue("contextFetchMaxBytes", Math.max(1, contextFetchMaxBytes)), contextMapper());
    }

    private RowMapper<Conversation> mapper() {
        return this::mapRow;
    }

    private Conversation mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Conversation(
                rs.getObject("id", UUID.class),
                rs.getObject("workspace_id", UUID.class),
                rs.getString("title"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getObject("updated_at", OffsetDateTime.class).toInstant());
    }

    private RowMapper<ConversationTaskContext> contextMapper() {
        return (rs, rowNum) -> new ConversationTaskContext(
                rs.getObject("id", UUID.class),
                rs.getString("user_request"),
                rs.getString("status"),
                rs.getString("content_md"),
                json.toStringList(rs.getString("affected_files")),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }
}
