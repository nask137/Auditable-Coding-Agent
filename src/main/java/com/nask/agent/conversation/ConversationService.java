package com.nask.agent.conversation;

import com.nask.agent.common.ApiException;
import com.nask.agent.workspace.WorkspaceService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Application service for conversation lifecycle and task history context.
 */
@Service
public class ConversationService {

    private static final int DEFAULT_REPORT_EXCERPT_BYTES = 1200;
    private final ConversationRepository repository;
    private final WorkspaceService workspaceService;

    public ConversationService(ConversationRepository repository, WorkspaceService workspaceService) {
        this.repository = repository;
        this.workspaceService = workspaceService;
    }

    public Conversation create(CreateConversationRequest request) {
        workspaceService.getRequired(request.workspaceId());
        var now = Instant.now();
        var title = request.title() == null || request.title().isBlank() ? "Conversation" : request.title();
        return repository.insert(new Conversation(UUID.randomUUID(), request.workspaceId(), title, now, now));
    }

    public Conversation getRequired(UUID id) {
        return repository.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "CONVERSATION_NOT_FOUND", "Conversation not found: " + id));
    }

    public List<Conversation> listByWorkspace(UUID workspaceId) {
        workspaceService.getRequired(workspaceId);
        return repository.findByWorkspace(workspaceId);
    }

    public Conversation ensure(UUID workspaceId, UUID conversationId, String titleHint) {
        if (conversationId == null) {
            return create(new CreateConversationRequest(workspaceId, titleHint));
        }
        var conversation = getRequired(conversationId);
        if (!workspaceId.equals(conversation.workspaceId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CONVERSATION_WORKSPACE_MISMATCH",
                    "Conversation does not belong to workspace: " + conversationId);
        }
        return conversation;
    }

    public int nextTaskIndex(UUID conversationId) {
        return repository.nextTaskIndex(conversationId);
    }

    public void touch(UUID conversationId) {
        repository.touch(conversationId);
    }

    public Conversation rename(UUID conversationId, String title) {
        var conversation = getRequired(conversationId);
        var normalized = title == null ? "" : title.strip();
        if (normalized.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CONVERSATION_TITLE_REQUIRED",
                    "Conversation title is required");
        }
        return repository.updateTitle(conversation.id(), truncate(normalized, 120));
    }

    public List<ConversationTaskContext> previousTaskContext(UUID conversationId, UUID currentTaskId, int limit) {
        return previousTaskContext(conversationId, currentTaskId, limit,
                Math.max(1, limit) * DEFAULT_REPORT_EXCERPT_BYTES);
    }

    public List<ConversationTaskContext> previousTaskContext(UUID conversationId, UUID currentTaskId, int limit,
                                                             int contextFetchMaxBytes) {
        if (conversationId == null) {
            return List.of();
        }
        return repository.previousTaskContext(conversationId, currentTaskId, limit, contextFetchMaxBytes);
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
