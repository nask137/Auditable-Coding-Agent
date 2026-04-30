package com.nask.agent.workspace;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST API for workspace registration and lookup.
 */
@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {
    private final WorkspaceService service;

    /**
     * Creates a controller backed by the workspace service.
     */
    public WorkspaceController(WorkspaceService service) {
        this.service = service;
    }

    /**
     * Registers a workspace root for future tasks.
     */
    @PostMapping
    Workspace create(@Valid @RequestBody CreateWorkspaceRequest request) {
        return service.create(request);
    }

    /**
     * Lists known workspaces.
     */
    @GetMapping
    List<Workspace> list() {
        return service.list();
    }

    /**
     * Fetches a workspace by id.
     */
    @GetMapping("/{workspaceId}")
    Workspace get(@PathVariable UUID workspaceId) {
        return service.getRequired(workspaceId);
    }
}
