package com.nask.agent.command;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST API for workspace command policies.
 */
@RestController
@RequestMapping("/api")
public class CommandPolicyController {
    private final CommandPolicyService service;

    /**
     * Creates a command policy controller.
     */
    public CommandPolicyController(CommandPolicyService service) {
        this.service = service;
    }

    /**
     * Lists enabled command policies for a workspace.
     */
    @GetMapping("/workspaces/{workspaceId}/command-policies")
    List<CommandPolicy> list(@PathVariable UUID workspaceId) {
        return service.list(workspaceId);
    }

    /**
     * Creates a command policy for a workspace.
     */
    @PostMapping("/workspaces/{workspaceId}/command-policies")
    CommandPolicy create(@PathVariable UUID workspaceId, @Valid @RequestBody CreateCommandPolicyRequest request) {
        return service.create(workspaceId, request);
    }

    /**
     * Soft-deletes a command policy.
     */
    @DeleteMapping("/command-policies/{policyId}")
    void delete(@PathVariable UUID policyId) {
        service.delete(policyId);
    }
}
