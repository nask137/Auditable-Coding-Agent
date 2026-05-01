package com.nask.agent.llm;

import jakarta.validation.constraints.NotBlank;

/**
 * Model-authored report body before deterministic audit sections are appended.
 */
public record FinalReportDraft(@NotBlank String markdown) {
}
