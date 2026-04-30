package com.nask.agent.plan;

import java.util.List;

public record PlanView(Plan plan, List<PlanItem> items) {
}
