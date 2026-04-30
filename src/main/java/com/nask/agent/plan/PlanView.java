package com.nask.agent.plan;

import java.util.List;

/**
 * API projection that returns a plan together with its ordered items.
 */
public record PlanView(Plan plan, List<PlanItem> items) {
}
