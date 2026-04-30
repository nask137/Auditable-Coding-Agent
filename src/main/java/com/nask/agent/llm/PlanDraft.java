package com.nask.agent.llm;

import java.util.List;

public record PlanDraft(List<Item> items) {
    public record Item(String description, List<String> relatedFiles, String notes) {
    }
}
