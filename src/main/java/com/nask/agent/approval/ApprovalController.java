package com.nask.agent.approval;

import com.nask.agent.task.TaskExecutionExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST API for listing and resolving approval requests.
 */
@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {
    private static final Logger log = LoggerFactory.getLogger(ApprovalController.class);

    private final ApprovalService approvalService;
    private final TaskExecutionExecutor executionExecutor;

    /**
     * Creates an approval controller.
     */
    public ApprovalController(ApprovalService approvalService, TaskExecutionExecutor executionExecutor) {
        this.approvalService = approvalService;
        this.executionExecutor = executionExecutor;
    }

    /**
     * Lists approval requests, optionally filtered by status.
     */
    @GetMapping
    List<ApprovalRequestRecord> list(@RequestParam(required = false) String status) {
        return approvalService.list(status);
    }

    /**
     * Fetches a single approval request.
     */
    @GetMapping("/{approvalId}")
    ApprovalRequestRecord get(@PathVariable UUID approvalId) {
        return approvalService.getRequired(approvalId);
    }

    /**
     * Approves a request and immediately resumes the paused task.
     */
    @PostMapping("/{approvalId}/approve")
    ApprovalRequestRecord approve(@PathVariable UUID approvalId, @RequestBody(required = false) ResolveApprovalRequest request) {
        var approval = approvalService.approve(approvalId, request);
        // Approval resolution only changes domain state. The controller kicks the
        // synchronous Phase 1 loop so the user sees progress immediately.
        try {
            executionExecutor.execute(approval.taskId());
        } catch (RuntimeException e) {
            log.warn("Approval {} was resolved, but task {} did not resume cleanly",
                    approvalId, approval.taskId(), e);
        }
        return approvalService.getRequired(approvalId);
    }

    /**
     * Denies a request and fails the associated task.
     */
    @PostMapping("/{approvalId}/deny")
    ApprovalRequestRecord deny(@PathVariable UUID approvalId, @RequestBody(required = false) ResolveApprovalRequest request) {
        return approvalService.deny(approvalId, request);
    }
}
