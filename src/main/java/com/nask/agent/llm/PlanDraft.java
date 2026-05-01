package com.nask.agent.llm;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Ordered plan returned by the model before persistence.
 */
public record PlanDraft(@NotNull @Size(min = 1, max = 10) List<@Valid Item> items) {
    /**
     * Single model-drafted plan item.
     */
    public record Item(
            @NotBlank String description,
            @NotNull @Size(max = 10) List<String> relatedFiles,
            @NotBlank String notes) {
    }
}
