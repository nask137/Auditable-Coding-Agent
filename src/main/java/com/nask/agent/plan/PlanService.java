package com.nask.agent.plan;

import com.nask.agent.audit.AuditEventDraft;
import com.nask.agent.audit.AuditService;
import com.nask.agent.common.ApiException;
import com.nask.agent.common.Domain;
import com.nask.agent.llm.PlanDraft;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Coordinates creation and status updates for plans and plan items.
 */
@Service
public class PlanService {
    private final PlanRepository repository;
    private final AuditService auditService;

    /**
     * Creates a plan service.
     */
    public PlanService(PlanRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    /**
     * Persists a model-generated plan and all of its items.
     */
    @Transactional
    public PlanView create(UUID taskId, UUID runId, PlanDraft draft) {
        var now = Instant.now();
        var plan = repository.insertPlan(new Plan(UUID.randomUUID(), taskId, runId,
                Domain.PlanStatus.ACTIVE.name(), now, now));
        var items = new ArrayList<PlanItem>();
        for (int i = 0; i < draft.items().size(); i++) {
            var draftItem = draft.items().get(i);
            // Persist the model's ordering as a 1-based index so API consumers
            // can render a stable plan even if UUID ordering differs.
            items.add(repository.insertItem(new PlanItem(UUID.randomUUID(), plan.id(), draftItem.description(),
                    Domain.PlanItemStatus.PENDING.name(), draftItem.relatedFiles(), draftItem.notes(), i + 1, now, now)));
        }
        auditService.append(new AuditEventDraft(taskId, runId, null, null, Domain.AuditEventType.PlanCreated,
                Domain.AuditActor.AGENT, Domain.AuditLevel.INFO, "Create plan",
                "Created " + items.size() + " plan items", List.of(), null, null, null, null,
                null, null, null, true, null, null, java.util.Map.of("planId", plan.id().toString())));
        return new PlanView(plan, items);
    }

    /**
     * Returns the latest plan for a run.
     */
    public PlanView getByRun(UUID runId) {
        var plan = repository.findByRun(runId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PLAN_NOT_FOUND", "Plan not found for run: " + runId));
        return new PlanView(plan, repository.findItems(plan.id()));
    }

    /**
     * Returns the latest plan for a run, or null when planning has not happened.
     */
    public PlanView findByRun(UUID runId) {
        return repository.findByRun(runId)
                .map(plan -> new PlanView(plan, repository.findItems(plan.id())))
                .orElse(null);
    }

    /**
     * Returns the next pending item or null when the plan is exhausted.
     */
    public PlanItem nextPending(UUID planId) {
        return repository.findNextPendingItem(planId).orElse(null);
    }

    /**
     * Updates one plan item's lifecycle status.
     */
    public void updateItemStatus(UUID itemId, Domain.PlanItemStatus status) {
        repository.updateItemStatus(itemId, status);
    }

    /**
     * Updates the overall plan lifecycle status.
     */
    public void updatePlanStatus(UUID planId, Domain.PlanStatus status) {
        repository.updatePlanStatus(planId, status);
    }

    /**
     * Appends recovery plan items after the current plan tail.
     */
    @Transactional
    public List<PlanItem> appendRecoveryItems(UUID taskId, UUID runId, UUID planId, PlanDraft draft,
                                              UUID failedItemId, String reason, UUID failureId) {
        var now = Instant.now();
        var order = repository.maxOrderIndex(planId);
        var items = new ArrayList<PlanItem>();
        for (var draftItem : draft.items()) {
            items.add(repository.insertItem(new PlanItem(UUID.randomUUID(), planId, draftItem.description(),
                    Domain.PlanItemStatus.PENDING.name(), draftItem.relatedFiles(), draftItem.notes(), ++order, now, now)));
        }
        auditService.append(new AuditEventDraft(taskId, runId, null, null, Domain.AuditEventType.PlanUpdated,
                Domain.AuditActor.RUNTIME, Domain.AuditLevel.INFO, "Append recovery plan items",
                "Appended " + items.size() + " recovery plan items", List.of(), null, null, null, null,
                null, Domain.RiskLevel.MEDIUM, null, true, null, null, java.util.Map.of(
                "planId", planId.toString(),
                "failedItemId", failedItemId == null ? "" : failedItemId.toString(),
                "failureId", failureId == null ? "" : failureId.toString(),
                "reason", reason)));
        auditService.append(new AuditEventDraft(taskId, runId, null, null, Domain.AuditEventType.RecoveryReplanned,
                Domain.AuditActor.RUNTIME, Domain.AuditLevel.INFO, "Replan after failure", reason,
                List.of(), null, null, null, null, null, Domain.RiskLevel.MEDIUM, null,
                true, null, null, java.util.Map.of("newItemCount", items.size())));
        return items;
    }
}
