package com.nask.agent.memory;

import com.nask.agent.approval.ApprovalRequestRecord;
import com.nask.agent.approval.ResolveApprovalRequest;
import com.nask.agent.common.ApiException;
import com.nask.agent.common.Domain;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps memory write proposals consistent when their linked approval is
 * resolved through the generic approval API.
 */
@Component
public class MemoryApprovalSynchronizer {
    private final ProjectMemoryRepository repository;

    public MemoryApprovalSynchronizer(ProjectMemoryRepository repository) {
        this.repository = repository;
    }

    public void approve(ApprovalRequestRecord approval, ResolveApprovalRequest request) {
        if (!Domain.ApprovalType.MEMORY_WRITE.name().equals(approval.approvalType())) {
            return;
        }
        var proposal = repository.findMemoryWriteProposalByApprovalRequestId(approval.id()).orElse(null);
        if (proposal == null || Domain.MemoryWriteProposalStatus.APPROVED.name().equals(proposal.status())) {
            return;
        }
        if (Domain.MemoryWriteProposalStatus.REJECTED.name().equals(proposal.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "MEMORY_PROPOSAL_REJECTED",
                    "Rejected memory proposal cannot be approved: " + proposal.id());
        }
        var item = repository.insertProjectMemoryItem(new ProjectMemoryItem(UUID.randomUUID(), proposal.workspaceId(),
                proposal.proposalType(), "workspace", proposal.title(), proposal.content(),
                "MEMORY_WRITE_PROPOSAL", proposal.id(), null, null, null,
                Domain.ProjectMemoryStatus.APPROVED.name(), 0.8, null, "agent", proposal.createdAt(),
                actor(request), Instant.now(), Map.of("proposalId", proposal.id().toString())));
        repository.resolveMemoryWriteProposal(proposal.id(), Domain.MemoryWriteProposalStatus.APPROVED.name(),
                item.id());
    }

    public void deny(ApprovalRequestRecord approval) {
        if (!Domain.ApprovalType.MEMORY_WRITE.name().equals(approval.approvalType())) {
            return;
        }
        var proposal = repository.findMemoryWriteProposalByApprovalRequestId(approval.id()).orElse(null);
        if (proposal == null || Domain.MemoryWriteProposalStatus.REJECTED.name().equals(proposal.status())) {
            return;
        }
        if (Domain.MemoryWriteProposalStatus.APPROVED.name().equals(proposal.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "MEMORY_PROPOSAL_APPROVED",
                    "Approved memory proposal cannot be rejected: " + proposal.id());
        }
        repository.resolveMemoryWriteProposal(proposal.id(), Domain.MemoryWriteProposalStatus.REJECTED.name(), null);
    }

    private String actor(ResolveApprovalRequest request) {
        return request == null || request.resolvedBy() == null || request.resolvedBy().isBlank()
                ? "local-user" : request.resolvedBy();
    }
}
