package com.nask.agent.plan;

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
 * JDBC repository for plans and plan items.
 */
@Repository
public class PlanRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final JsonSupport json;

    /**
     * Creates a repository backed by JDBC and JSON helpers.
     */
    public PlanRepository(NamedParameterJdbcTemplate jdbc, JsonSupport json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /**
     * Inserts the plan header row.
     */
    public Plan insertPlan(Plan plan) {
        jdbc.update("""
                insert into plan (id, task_id, run_id, status, created_at, updated_at)
                values (:id, :taskId, :runId, :status, :createdAt, :updatedAt)
                """, new MapSqlParameterSource()
                .addValue("id", plan.id())
                .addValue("taskId", plan.taskId())
                .addValue("runId", plan.runId())
                .addValue("status", plan.status())
                .addValue("createdAt", ts(plan.createdAt()))
                .addValue("updatedAt", ts(plan.updatedAt())));
        return plan;
    }

    /**
     * Inserts an ordered plan item.
     */
    public PlanItem insertItem(PlanItem item) {
        jdbc.update("""
                insert into plan_item (id, plan_id, description, status, related_files, notes, order_index, created_at, updated_at)
                values (:id, :planId, :description, :status, cast(:relatedFiles as jsonb), :notes, :orderIndex, :createdAt, :updatedAt)
                """, new MapSqlParameterSource()
                .addValue("id", item.id())
                .addValue("planId", item.planId())
                .addValue("description", item.description())
                .addValue("status", item.status())
                .addValue("relatedFiles", json.toJson(item.relatedFiles()))
                .addValue("notes", item.notes())
                .addValue("orderIndex", item.orderIndex())
                .addValue("createdAt", ts(item.createdAt()))
                .addValue("updatedAt", ts(item.updatedAt())));
        return item;
    }

    /**
     * Finds the newest plan attached to a run.
     */
    public Optional<Plan> findByRun(UUID runId) {
        return jdbc.query("select * from plan where run_id = :runId order by created_at desc limit 1",
                new MapSqlParameterSource("runId", runId), planMapper()).stream().findFirst();
    }

    /**
     * Lists all items for a plan in execution order.
     */
    public List<PlanItem> findItems(UUID planId) {
        return jdbc.query("select * from plan_item where plan_id = :planId order by order_index",
                new MapSqlParameterSource("planId", planId), itemMapper());
    }

    /**
     * Returns the next pending item according to {@code order_index}.
     */
    public Optional<PlanItem> findNextPendingItem(UUID planId) {
        return jdbc.query("""
                        select * from plan_item
                         where plan_id = :planId and status = :status
                         order by order_index
                         limit 1
                        """, new MapSqlParameterSource()
                        .addValue("planId", planId)
                        .addValue("status", Domain.PlanItemStatus.PENDING.name()),
                itemMapper()).stream().findFirst();
    }

    /**
     * Updates a plan item status and refreshes {@code updated_at}.
     */
    public void updateItemStatus(UUID itemId, Domain.PlanItemStatus status) {
        jdbc.update("update plan_item set status = :status, updated_at = :updatedAt where id = :id",
                new MapSqlParameterSource()
                        .addValue("id", itemId)
                        .addValue("status", status.name())
                        .addValue("updatedAt", ts(Instant.now())));
    }

    /**
     * Updates a plan status and refreshes {@code updated_at}.
     */
    public void updatePlanStatus(UUID planId, Domain.PlanStatus status) {
        jdbc.update("update plan set status = :status, updated_at = :updatedAt where id = :id",
                new MapSqlParameterSource()
                        .addValue("id", planId)
                        .addValue("status", status.name())
                        .addValue("updatedAt", ts(Instant.now())));
    }

    private RowMapper<Plan> planMapper() {
        return (rs, rowNum) -> new Plan(
                rs.getObject("id", UUID.class),
                rs.getObject("task_id", UUID.class),
                rs.getObject("run_id", UUID.class),
                rs.getString("status"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getObject("updated_at", OffsetDateTime.class).toInstant());
    }

    private RowMapper<PlanItem> itemMapper() {
        return this::mapItem;
    }

    private PlanItem mapItem(ResultSet rs, int rowNum) throws SQLException {
        return new PlanItem(
                rs.getObject("id", UUID.class),
                rs.getObject("plan_id", UUID.class),
                rs.getString("description"),
                rs.getString("status"),
                json.toStringList(rs.getString("related_files")),
                rs.getString("notes"),
                rs.getInt("order_index"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getObject("updated_at", OffsetDateTime.class).toInstant());
    }
}
