package com.nask.agent.approval;

/**
 * Request body for approving or denying an approval request.
 */
public record ResolveApprovalRequest(String resolvedBy, String reason) {
}
