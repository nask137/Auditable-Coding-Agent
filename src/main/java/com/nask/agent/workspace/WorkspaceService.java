package com.nask.agent.workspace;

import com.nask.agent.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class WorkspaceService {
    private static final List<String> DEFAULT_ALLOWED_OPERATIONS = List.of("FILE_READ", "FILE_CREATE", "FILE_MODIFY");
    private static final List<String> DEFAULT_BLOCKED_PATHS = List.of(".git");
    private static final List<String> DEFAULT_SENSITIVE_PATTERNS = List.of(
            ".env", ".env.*", "*.pem", "*.key", "id_rsa", "id_ed25519", "*.p12", "*.jks", "credentials", "secrets");

    private final WorkspaceRepository repository;

    public WorkspaceService(WorkspaceRepository repository) {
        this.repository = repository;
    }

    public Workspace create(CreateWorkspaceRequest request) {
        var root = Path.of(request.rootPath()).toAbsolutePath().normalize();
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

    public Workspace getRequired(UUID id) {
        return repository.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "WORKSPACE_NOT_FOUND", "Workspace not found: " + id));
    }

    public List<Workspace> list() {
        return repository.findAll();
    }

    public void touch(UUID id) {
        repository.touch(id);
    }
}
