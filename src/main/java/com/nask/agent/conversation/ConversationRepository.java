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

    public int nextTaskIndex(UUID conversationId) {
        return jdbc.queryForObject("""
                select coalesce(max(prompt_index), 0) + 1
                  from task
                 where conversation_id = :conversationId
                """, new MapSqlParameterSource("conversationId", conversationId), Integer.class);
    }

    public List<ConversationTaskContext> previousTaskContext(UUID conversationId, UUID currentTaskId, int limit) {
        return jdbc.query("""
                select t.id,
                       t.user_request,
                       t.status,
                       t.created_at,
                       r.content_md,
                       coalesce(
                         jsonb_agg(distinct fc.path) filter (where fc.path is not null),
                         '[]'::jsonb
                       ) as affected_files
                  from task t
                  left join lateral (
                    select content_md
                      from task_report tr
                     where tr.task_id = t.id
                     order by tr.created_at desc
                     limit 1
                  ) r on true
                  left join file_change fc on fc.task_id = t.id
                 where t.conversation_id = :conversationId
                   and t.id <> :currentTaskId
                 group by t.id, t.user_request, t.status, t.created_at, r.content_md, t.prompt_index
                 order by t.prompt_index desc, t.created_at desc
                 limit :limit
                """, new MapSqlParameterSource()
                .addValue("conversationId", conversationId)
                .addValue("currentTaskId", currentTaskId)
                .addValue("limit", limit), contextMapper());
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
