package com.nask.agent.llm;

import java.util.List;

/**
 * Ordered plan returned by the model before persistence.
 */
public record PlanDraft(List<Item> items) {
    /**
     * Single model-drafted plan item.
     */
    public record Item(String description, List<String> relatedFiles, String notes) {
    }
}
