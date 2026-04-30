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

@RestController
@RequestMapping("/api")
public class CommandPolicyController {
    private final CommandPolicyService service;

    public CommandPolicyController(CommandPolicyService service) {
        this.service = service;
    }

    @GetMapping("/workspaces/{workspaceId}/command-policies")
    List<CommandPolicy> list(@PathVariable UUID workspaceId) {
        return service.list(workspaceId);
    }

    @PostMapping("/workspaces/{workspaceId}/command-policies")
    CommandPolicy create(@PathVariable UUID workspaceId, @Valid @RequestBody CreateCommandPolicyRequest request) {
        return service.create(workspaceId, request);
    }

    @DeleteMapping("/command-policies/{policyId}")
    void delete(@PathVariable UUID policyId) {
        service.delete(policyId);
    }
}
