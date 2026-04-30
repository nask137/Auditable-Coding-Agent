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

@Service
public class PlanService {
    private final PlanRepository repository;
    private final AuditService auditService;

    public PlanService(PlanRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    @Transactional
    public PlanView create(UUID taskId, UUID runId, PlanDraft draft) {
        var now = Instant.now();
        var plan = repository.insertPlan(new Plan(UUID.randomUUID(), taskId, runId,
                Domain.PlanStatus.ACTIVE.name(), now, now));
        var items = new ArrayList<PlanItem>();
        for (int i = 0; i < draft.items().size(); i++) {
            var draftItem = draft.items().get(i);
            items.add(repository.insertItem(new PlanItem(UUID.randomUUID(), plan.id(), draftItem.description(),
                    Domain.PlanItemStatus.PENDING.name(), draftItem.relatedFiles(), draftItem.notes(), i + 1, now, now)));
        }
        auditService.append(new AuditEventDraft(taskId, runId, null, null, Domain.AuditEventType.PlanCreated,
                Domain.AuditActor.AGENT, Domain.AuditLevel.INFO, "Create plan",
                "Created " + items.size() + " plan items", List.of(), null, null, null, null,
                null, null, null, true, null, null, java.util.Map.of("planId", plan.id().toString())));
        return new PlanView(plan, items);
    }

    public PlanView getByRun(UUID runId) {
        var plan = repository.findByRun(runId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PLAN_NOT_FOUND", "Plan not found for run: " + runId));
        return new PlanView(plan, repository.findItems(plan.id()));
    }

    public PlanItem nextPending(UUID planId) {
        return repository.findNextPendingItem(planId).orElse(null);
    }

    public void updateItemStatus(UUID itemId, Domain.PlanItemStatus status) {
        repository.updateItemStatus(itemId, status);
    }

    public void updatePlanStatus(UUID planId, Domain.PlanStatus status) {
        repository.updatePlanStatus(planId, status);
    }
}
