package com.nask.agent.memory;

import com.nask.agent.approval.ApprovalService;
import com.nask.agent.approval.ResolveApprovalRequest;
import com.nask.agent.common.ApiException;
import com.nask.agent.common.Domain;
import com.nask.agent.workflow.AgentState;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Creates and resolves user-approved long-term memory write proposals.
 */
@Service
public class MemoryWriteProposalService {
    private final ProjectMemoryRepository repository;
    private final ApprovalService approvalService;

    public MemoryWriteProposalService(ProjectMemoryRepository repository, ApprovalService approvalService) {
        this.repository = repository;
        this.approvalService = approvalService;
    }

    /**
     * Creates a deterministic task lesson proposal for a completed run.
     */
    @Transactional
    public List<MemoryWriteProposal> proposeForTaskSummary(AgentState state) {
        var changedFiles = state.recentFileChanges().stream().map(change -> change.path()).distinct().toList();
        var validation = state.recentValidationResults().stream()
                .filter(result -> result.runId().equals(state.run().id()))
                .reduce((first, second) -> second);
        var title = "Task lesson: " + truncate(state.task().title(), 80);
        var content = """
                Request: %s
                Changed files: %s
                Validation: %s
                """.formatted(state.task().userRequest(), changedFiles,
                validation.map(result -> result.success() ? "passed - " + result.summary()
                        : "failed - " + result.summary()).orElse("not recorded")).strip();
        var type = Domain.ProjectMemoryType.TASK_LESSON.name();
        if (repository.existsMemoryWriteProposalForRun(state.run().id(), type, title, content)) {
            return List.of();
        }
        var proposal = new MemoryWriteProposal(UUID.randomUUID(), state.workspace().id(), state.task().id(),
                state.run().id(), type, title, content, sourceRefs(state), 
                Domain.MemoryWriteProposalStatus.WAITING_APPROVAL.name(), null, null, Instant.now(), null,
                Map.of("changedFiles", changedFiles, "validationRecorded", validation.isPresent()));
        repository.insertMemoryWriteProposal(proposal);
        var approval = approvalService.create(state.task().id(), state.run().id(), null, null,
                Domain.ApprovalType.MEMORY_WRITE, Domain.RiskLevel.LOW,
                "Approve long-term project memory write: " + title,
                List.of("memory-proposal:" + proposal.id()), null, null, content, false);
        repository.attachApprovalToMemoryWriteProposal(proposal.id(), approval.id());
        return List.of(repository.findMemoryWriteProposalById(proposal.id()).orElse(proposal));
    }

    /**
     * Lists proposals for a workspace.
     */
    public List<MemoryWriteProposal> list(UUID workspaceId) {
        return repository.findMemoryWriteProposalsByWorkspace(workspaceId);
    }

    /**
     * Approves a proposal, resolves its approval request, and writes an
     * approved project memory item.
     */
    @Transactional
    public MemoryWriteProposal approve(UUID proposalId, ResolveApprovalRequest request) {
        var proposal = getRequired(proposalId);
        if (Domain.MemoryWriteProposalStatus.APPROVED.name().equals(proposal.status())) {
            return proposal;
        }
        if (Domain.MemoryWriteProposalStatus.REJECTED.name().equals(proposal.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "MEMORY_PROPOSAL_REJECTED",
                    "Rejected memory proposal cannot be approved: " + proposalId);
        }
        if (proposal.approvalRequestId() != null) {
            approvalService.approve(proposal.approvalRequestId(), request);
            return getRequired(proposal.id());
        }
        var item = repository.insertProjectMemoryItem(new ProjectMemoryItem(UUID.randomUUID(), proposal.workspaceId(),
                proposal.proposalType(), "workspace", proposal.title(), proposal.content(),
                "MEMORY_WRITE_PROPOSAL", proposal.id(), null, null, null,
                Domain.ProjectMemoryStatus.APPROVED.name(), 0.8, null, "agent", proposal.createdAt(),
                actor(request), Instant.now(), Map.of("proposalId", proposal.id().toString())));
        repository.resolveMemoryWriteProposal(proposal.id(), Domain.MemoryWriteProposalStatus.APPROVED.name(),
                item.id());
        return getRequired(proposal.id());
    }

    /**
     * Rejects a proposal and resolves the linked approval without failing the run.
     */
    @Transactional
    public MemoryWriteProposal reject(UUID proposalId, ResolveApprovalRequest request) {
        var proposal = getRequired(proposalId);
        if (Domain.MemoryWriteProposalStatus.APPROVED.name().equals(proposal.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "MEMORY_PROPOSAL_APPROVED",
                    "Approved memory proposal cannot be rejected: " + proposalId);
        }
        if (Domain.MemoryWriteProposalStatus.REJECTED.name().equals(proposal.status())) {
            return proposal;
        }
        if (proposal.approvalRequestId() != null) {
            approvalService.deny(proposal.approvalRequestId(), request);
            return getRequired(proposal.id());
        }
        repository.resolveMemoryWriteProposal(proposal.id(), Domain.MemoryWriteProposalStatus.REJECTED.name(), null);
        return getRequired(proposal.id());
    }

    private MemoryWriteProposal getRequired(UUID proposalId) {
        return repository.findMemoryWriteProposalById(proposalId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "MEMORY_PROPOSAL_NOT_FOUND",
                        "Memory write proposal not found: " + proposalId));
    }

    private List<SourceReference> sourceRefs(AgentState state) {
        if (state.memoryContext() == null) {
            return List.of();
        }
        return state.memoryContext().sourceReferences().stream().limit(10).toList();
    }

    private String actor(ResolveApprovalRequest request) {
        return request == null || request.resolvedBy() == null || request.resolvedBy().isBlank()
                ? "local-user" : request.resolvedBy();
    }

    private String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value == null ? "untitled" : value;
        }
        return value.substring(0, max);
    }
}
