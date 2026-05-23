package com.nask.agent.llm;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Model decision for how to execute the current plan item.
 */
public record AgentDecision(@NotNull UUID planItemId,
                            @NotNull @Size(max = 5, message = "must contain no more than 5 actions")
                            List<@Valid Action> actions) {
    /**
     * Tool action requested by the model.
     */
    public record Action(@NotBlank String type, @NotBlank String reason, @NotNull Map<String, Object> input) {
    }
}
