package com.nask.agent.llm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Model decision describing whether to validate and which command to run.
 */
public record ValidationDecision(
        boolean shouldValidate,
        @NotNull @Size(max = 20) List<String> executableAndArgs,
        @NotBlank String reason) {
}
