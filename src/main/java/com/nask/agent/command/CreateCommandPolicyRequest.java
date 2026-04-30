package com.nask.agent.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateCommandPolicyRequest(
        @NotBlank String policyType,
        @NotBlank String executable,
        @NotNull List<String> argsPattern,
        String cwdScope,
        Boolean allowPipe,
        Boolean allowRedirect,
        Boolean allowBackground) {
}
