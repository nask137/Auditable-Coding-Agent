package com.nask.agent.permission;

import com.nask.agent.common.Domain;
import com.nask.agent.workspace.PathCheck;
import org.springframework.stereotype.Service;

@Service
public class PermissionService {
    public PermissionDecision fileDecision(PathCheck pathCheck, Domain.FileOperation operation, boolean highImpact, int patchLines) {
        if (!pathCheck.allowed()) {
            return PermissionDecision.block(Domain.RiskLevel.CRITICAL, pathCheck.reason());
        }
        if (pathCheck.blockedSensitive()) {
            return PermissionDecision.block(Domain.RiskLevel.CRITICAL, "Credential or private key file access is blocked");
        }
        if (pathCheck.sensitive()) {
            var approvalType = operation == Domain.FileOperation.FILE_READ
                    ? Domain.ApprovalType.SENSITIVE_FILE_READ
                    : Domain.ApprovalType.SENSITIVE_FILE_MODIFY;
            return PermissionDecision.approval(Domain.RiskLevel.HIGH, "Sensitive file requires approval", approvalType);
        }
        if (operation == Domain.FileOperation.FILE_DELETE) {
            return PermissionDecision.approval(Domain.RiskLevel.HIGH, "File deletion requires approval", Domain.ApprovalType.FILE_DELETE);
        }
        if (operation == Domain.FileOperation.FILE_MOVE) {
            return PermissionDecision.approval(Domain.RiskLevel.HIGH, "File move requires approval", Domain.ApprovalType.FILE_MOVE);
        }
        if (highImpact) {
            return PermissionDecision.approval(Domain.RiskLevel.HIGH, "High impact file requires approval", Domain.ApprovalType.SENSITIVE_FILE_MODIFY);
        }
        if (patchLines > 300) {
            return PermissionDecision.approval(Domain.RiskLevel.HIGH, "Large patch requires approval", Domain.ApprovalType.LARGE_PATCH);
        }
        var risk = operation == Domain.FileOperation.FILE_READ ? Domain.RiskLevel.LOW : Domain.RiskLevel.MEDIUM;
        return PermissionDecision.allow(risk, "Workspace file operation allowed");
    }
}
