package com.nask.agent.runtime;

import jakarta.validation.constraints.NotBlank;

/**
 * API request body used to answer a pending user-input request.
 */
public record AnswerUserInputRequest(@NotBlank String answer) {
}
