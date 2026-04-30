package com.nask.agent.workspace;

import com.nask.agent.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Application service for creating and retrieving workspace definitions.
 */
@Service
public class WorkspaceService {
    private static final List<String> DEFAULT_ALLOWED_OPERATIONS = List.of("FILE_READ", "FILE_CREATE", "FILE_MODIFY");
    private static final List<String> DEFAULT_BLOCKED_PATHS = List.of(".git");
    private static final List<String> DEFAULT_SENSITIVE_PATTERNS = List.of(
            ".env", ".env.*", "*.pem", "*.key", "id_rsa", "id_ed25519", "*.p12", "*.jks", "credentials", "secrets");

    private final WorkspaceRepository repository;

    /**
     * Creates a service backed by the workspace repository.
     */
    public WorkspaceService(WorkspaceRepository repository) {
        this.repository = repository;
    }

    /**
     * Registers a workspace and applies conservative default guard rules.
     */
    public Workspace create(CreateWorkspaceRequest request) {
        var root = Path.of(request.rootPath()).toAbsolutePath().normalize();
        // Defaults are deliberately local and explicit: the runtime can read and
        // edit normal workspace files, but repository internals and common secret
        // names are handled by the path/permission layers.
        var workspace = new Workspace(
                UUID.randomUUID(),
                request.name() == null || request.name().isBlank() ? root.getFileName().toString() : request.name(),
                root.toString(),
                request.trusted() == null || request.trusted(),
                DEFAULT_ALLOWED_OPERATIONS,
                DEFAULT_BLOCKED_PATHS,
                DEFAULT_SENSITIVE_PATTERNS,
                Instant.now(),
                null);
        return repository.insert(workspace);
    }

    /**
     * Loads a workspace or throws a REST-friendly 404 exception.
     */
    public Workspace getRequired(UUID id) {
        return repository.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "WORKSPACE_NOT_FOUND", "Workspace not found: " + id));
    }

    /**
     * Returns all registered workspaces ordered by repository policy.
     */
    public List<Workspace> list() {
        return repository.findAll();
    }

    /**
     * Marks a workspace as recently used by a run.
     */
    public void touch(UUID id) {
        repository.touch(id);
    }
}
