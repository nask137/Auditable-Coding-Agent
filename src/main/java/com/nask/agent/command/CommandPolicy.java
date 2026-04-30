package com.nask.agent.command;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CommandPolicy(
        UUID id,
        UUID workspaceId,
        String policyType,
        String executable,
        List<String> argsPattern,
        String cwdScope,
        boolean allowPipe,
        boolean allowRedirect,
        boolean allowBackground,
        Map<String, Object> envPolicy,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt) {
}
