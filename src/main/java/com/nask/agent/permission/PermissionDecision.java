package com.nask.agent.permission;

import com.nask.agent.common.Domain;

/**
 * Result of a permission policy check.
 */
public record PermissionDecision(
        Domain.PermissionDecisionType decision,
        Domain.RiskLevel riskLevel,
        String reason,
        Domain.ApprovalType approvalType) {
    /**
     * Creates an allow decision.
     */
    public static PermissionDecision allow(Domain.RiskLevel riskLevel, String reason) {
        return new PermissionDecision(Domain.PermissionDecisionType.ALLOW, riskLevel, reason, null);
    }

    /**
     * Creates a decision that requires user approval before continuing.
     */
    public static PermissionDecision approval(Domain.RiskLevel riskLevel, String reason, Domain.ApprovalType approvalType) {
        return new PermissionDecision(Domain.PermissionDecisionType.REQUIRE_APPROVAL, riskLevel, reason, approvalType);
    }

    /**
     * Creates a hard block decision.
     */
    public static PermissionDecision block(Domain.RiskLevel riskLevel, String reason) {
        return new PermissionDecision(Domain.PermissionDecisionType.BLOCK, riskLevel, reason, null);
    }
}
