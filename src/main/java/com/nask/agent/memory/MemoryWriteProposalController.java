package com.nask.agent.memory;

import com.nask.agent.approval.ResolveApprovalRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST API for resolving memory write proposals without rerunning completed tasks.
 */
@RestController
@RequestMapping("/api")
public class MemoryWriteProposalController {
    private final MemoryWriteProposalService service;

    public MemoryWriteProposalController(MemoryWriteProposalService service) {
        this.service = service;
    }

    /**
     * Lists memory write proposals for a workspace.
     */
    @GetMapping("/workspaces/{workspaceId}/memory-proposals")
    List<MemoryWriteProposal> list(@PathVariable UUID workspaceId) {
        return service.list(workspaceId);
    }

    /**
     * Approves a memory write proposal and persists the project memory item.
     */
    @PostMapping("/memory-proposals/{proposalId}/approve")
    MemoryWriteProposal approve(@PathVariable UUID proposalId,
                                @RequestBody(required = false) ResolveApprovalRequest request) {
        return service.approve(proposalId, request);
    }

    /**
     * Rejects a memory write proposal without failing the associated run.
     */
    @PostMapping("/memory-proposals/{proposalId}/reject")
    MemoryWriteProposal reject(@PathVariable UUID proposalId,
                               @RequestBody(required = false) ResolveApprovalRequest request) {
        return service.reject(proposalId, request);
    }
}
