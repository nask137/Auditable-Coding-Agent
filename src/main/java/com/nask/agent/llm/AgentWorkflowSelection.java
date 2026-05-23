package com.nask.agent.llm;

import jakarta.validation.constraints.NotBlank;

/**
 * Structured model decision for selecting the agent workflow before a task starts.
 */
public record AgentWorkflowSelection(
        @NotBlank String agent,
        @NotBlank String workflow,
        @NotBlank String rationale) {
}
