package com.nask.agent.llm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Structured interpretation of a user task.
 */
public record TaskUnderstanding(
        @NotBlank String summary,
        @NotBlank String taskType,
        @NotNull @Size(max = 10) List<String> constraints,
        @NotNull @Size(max = 10) List<String> initialSearchHints) {
}
