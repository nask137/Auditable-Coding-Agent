package com.nask.agent.conversation;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST API for workspace conversations.
 */
@RestController
@RequestMapping("/api/conversations")
public class ConversationController {
    private final ConversationService service;

    public ConversationController(ConversationService service) {
        this.service = service;
    }

    @GetMapping
    List<Conversation> list(@RequestParam UUID workspaceId) {
        return service.listByWorkspace(workspaceId);
    }

    @PostMapping
    Conversation create(@Valid @RequestBody CreateConversationRequest request) {
        return service.create(request);
    }

    @GetMapping("/{conversationId}")
    Conversation get(@PathVariable UUID conversationId) {
        return service.getRequired(conversationId);
    }
}
