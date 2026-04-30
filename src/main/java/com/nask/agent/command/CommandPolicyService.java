package com.nask.agent.command;

import com.nask.agent.common.Domain;
import com.nask.agent.workspace.WorkspaceService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Creates, lists, and classifies workspace command policies.
 */
@Service
public class CommandPolicyService {
    private final CommandPolicyRepository repository;
    private final WorkspaceService workspaceService;

    /**
     * Creates a command policy service.
     */
    public CommandPolicyService(CommandPolicyRepository repository, WorkspaceService workspaceService) {
        this.repository = repository;
        this.workspaceService = workspaceService;
    }

    /**
     * Creates a policy after verifying the workspace exists.
     */
    public CommandPolicy create(UUID workspaceId, CreateCommandPolicyRequest request) {
        workspaceService.getRequired(workspaceId);
        var now = Instant.now();
        var policy = new CommandPolicy(UUID.randomUUID(), workspaceId, request.policyType(), request.executable(),
                request.argsPattern(), request.cwdScope() == null ? "." : request.cwdScope(),
                Boolean.TRUE.equals(request.allowPipe()), Boolean.TRUE.equals(request.allowRedirect()),
                Boolean.TRUE.equals(request.allowBackground()), Map.of("inherit", true), true, now, now);
        return repository.insert(policy);
    }

    /**
     * Lists enabled policies for a workspace.
     */
    public List<CommandPolicy> list(UUID workspaceId) {
        return repository.findByWorkspace(workspaceId);
    }

    /**
     * Disables a policy.
     */
    public void delete(UUID policyId) {
        repository.delete(policyId);
    }

    /**
     * Classifies a command as allowlisted, approval-required, or blocked.
     */
    public Domain.CommandPolicyType classify(UUID workspaceId, String executable, List<String> arguments) {
        if (isDangerous(executable, arguments)) {
            return Domain.CommandPolicyType.BLOCKED;
        }
        for (var policy : repository.findByWorkspace(workspaceId)) {
            // Phase 1 matching is exact. More permissive matching should stay in
            // this service so CommandToolService only consumes a simple decision.
            if (!policy.executable().equals(executable)) {
                continue;
            }
            if (!policy.argsPattern().equals(arguments)) {
                continue;
            }
            return Domain.CommandPolicyType.valueOf(policy.policyType());
        }
        return Domain.CommandPolicyType.APPROVAL_REQUIRED;
    }

    /**
     * Blocks high-risk shell patterns before policy lookup.
     */
    private boolean isDangerous(String executable, List<String> arguments) {
        var command = (executable + " " + String.join(" ", arguments)).toLowerCase();
        return command.contains("rm -rf")
                || command.contains("del /s")
                || command.startsWith("format ")
                || command.contains("chmod -r")
                || command.contains("curl") && command.contains("|")
                || command.contains("git reset --hard")
                || command.contains("git clean -fd");
    }
}
