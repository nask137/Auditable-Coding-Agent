package com.nask.agent.runtime;

import com.nask.agent.run.AgentLoopExecutor;
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
 * REST API for user-intervention requests.
 */
@RestController
@RequestMapping("/api/user-input-requests")
public class UserInputRequestController {
    private final UserInputRequestService service;
    private final AgentLoopExecutor loopExecutor;

    public UserInputRequestController(UserInputRequestService service, AgentLoopExecutor loopExecutor) {
        this.service = service;
        this.loopExecutor = loopExecutor;
    }

    @GetMapping
    List<UserInputRequestRecord> list(@RequestParam(required = false) String status) {
        return service.list(status);
    }

    @GetMapping("/{requestId}")
    UserInputRequestRecord get(@PathVariable UUID requestId) {
        return service.getRequired(requestId);
    }

    @PostMapping("/{requestId}/answer")
    UserInputRequestRecord answer(@PathVariable UUID requestId, @Valid @RequestBody AnswerUserInputRequest request) {
        var answered = service.answer(requestId, request);
        loopExecutor.execute(answered.runId());
        return service.getRequired(requestId);
    }

    @PostMapping("/{requestId}/cancel")
    UserInputRequestRecord cancel(@PathVariable UUID requestId) {
        return service.cancel(requestId);
    }
}
