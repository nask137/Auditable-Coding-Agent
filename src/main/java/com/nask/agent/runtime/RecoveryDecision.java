package com.nask.agent.runtime;

import com.nask.agent.common.Domain;

/**
 * Recovery strategy plus remaining budget for audit metadata.
 */
public record RecoveryDecision(Domain.RecoveryStrategy strategy, boolean recoverable, int attempt, int budgetRemaining) {
}
