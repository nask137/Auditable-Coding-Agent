package com.nask.agent.approval;

import com.nask.agent.run.AgentLoopExecutor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {
    private final ApprovalService approvalService;
    private final AgentLoopExecutor loopExecutor;

    public ApprovalController(ApprovalService approvalService, AgentLoopExecutor loopExecutor) {
        this.approvalService = approvalService;
        this.loopExecutor = loopExecutor;
    }

    @GetMapping
    List<ApprovalRequestRecord> list(@RequestParam(required = false) String status) {
        return approvalService.list(status);
    }

    @GetMapping("/{approvalId}")
    ApprovalRequestRecord get(@PathVariable UUID approvalId) {
        return approvalService.getRequired(approvalId);
    }

    @PostMapping("/{approvalId}/approve")
    ApprovalRequestRecord approve(@PathVariable UUID approvalId, @RequestBody(required = false) ResolveApprovalRequest request) {
        var approval = approvalService.approve(approvalId, request);
        loopExecutor.execute(approval.runId());
        return approvalService.getRequired(approvalId);
    }

    @PostMapping("/{approvalId}/deny")
    ApprovalRequestRecord deny(@PathVariable UUID approvalId, @RequestBody(required = false) ResolveApprovalRequest request) {
        return approvalService.deny(approvalId, request);
    }
}
