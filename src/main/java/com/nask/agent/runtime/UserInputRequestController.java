package com.nask.agent.runtime;

import com.nask.agent.task.TaskExecutionExecutor;
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
    private final TaskExecutionExecutor executionExecutor;

    public UserInputRequestController(UserInputRequestService service, TaskExecutionExecutor executionExecutor) {
        this.service = service;
        this.executionExecutor = executionExecutor;
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
        if ("ANSWERED".equals(answered.status())) {
            executionExecutor.execute(answered.taskId());
        }
        return service.getRequired(requestId);
    }

    @PostMapping("/{requestId}/cancel")
    UserInputRequestRecord cancel(@PathVariable UUID requestId) {
        return service.cancel(requestId);
    }
}
