package com.nask.agent.permission;

import com.nask.agent.common.Domain;

public record PermissionDecision(
        Domain.PermissionDecisionType decision,
        Domain.RiskLevel riskLevel,
        String reason,
        Domain.ApprovalType approvalType) {
    public static PermissionDecision allow(Domain.RiskLevel riskLevel, String reason) {
        return new PermissionDecision(Domain.PermissionDecisionType.ALLOW, riskLevel, reason, null);
    }

    public static PermissionDecision approval(Domain.RiskLevel riskLevel, String reason, Domain.ApprovalType approvalType) {
        return new PermissionDecision(Domain.PermissionDecisionType.REQUIRE_APPROVAL, riskLevel, reason, approvalType);
    }

    public static PermissionDecision block(Domain.RiskLevel riskLevel, String reason) {
        return new PermissionDecision(Domain.PermissionDecisionType.BLOCK, riskLevel, reason, null);
    }
}
