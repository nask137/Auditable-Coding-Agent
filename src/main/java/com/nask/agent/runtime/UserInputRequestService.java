package com.nask.agent.runtime;

import com.nask.agent.audit.AuditEventDraft;
import com.nask.agent.audit.AuditService;
import com.nask.agent.common.ApiException;
import com.nask.agent.common.Domain;
import com.nask.agent.run.AgentRunService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Owns user-input request creation and resolution.
 */
@Service
public class UserInputRequestService {
    private final UserInputRequestRepository repository;
    private final AuditService auditService;
    private final AgentRunService runService;

    public UserInputRequestService(UserInputRequestRepository repository, AuditService auditService,
                                   AgentRunService runService) {
        this.repository = repository;
        this.auditService = auditService;
        this.runService = runService;
    }

    @Transactional
    public UserInputRequestRecord create(UUID taskId, UUID runId, UUID stepId, UUID planItemId,
                                         String question, String contextSummary, List<String> suggestedOptions) {
        var request = new UserInputRequestRecord(UUID.randomUUID(), taskId, runId, stepId, planItemId,
                Domain.UserInputStatus.PENDING.name(), question, contextSummary,
                suggestedOptions == null ? List.of() : suggestedOptions, null, Instant.now(), null);
        repository.insert(request);
        auditService.append(new AuditEventDraft(taskId, runId, stepId, null, Domain.AuditEventType.UserInputRequested,
                Domain.AuditActor.RUNTIME, Domain.AuditLevel.WARN, question, contextSummary,
                List.of(), null, null, null, null, null, Domain.RiskLevel.MEDIUM, null,
                true, null, null, Map.of("userInputRequestId", request.id().toString())));
        auditService.append(new AuditEventDraft(taskId, runId, stepId, null,
                Domain.AuditEventType.RecoveryUserInputRequested, Domain.AuditActor.RUNTIME, Domain.AuditLevel.WARN,
                "Ask user", question, List.of(), null, null, null, null, null, Domain.RiskLevel.MEDIUM,
                null, true, null, null, Map.of("userInputRequestId", request.id().toString())));
        runService.markWaitingUserInput(runId, taskId, "Waiting for user input: " + question);
        return request;
    }

    public List<UserInputRequestRecord> list(String status) {
        if (status == null || status.isBlank()) {
            return repository.findAll();
        }
        return repository.findByStatus(Domain.UserInputStatus.valueOf(status));
    }

    public UserInputRequestRecord getRequired(UUID id) {
        return repository.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "USER_INPUT_NOT_FOUND", "User input request not found: " + id));
    }

    @Transactional
    public UserInputRequestRecord answer(UUID id, AnswerUserInputRequest request) {
        var current = getRequired(id);
        requirePending(current);
        if (isCancelTask(request.answer())) {
            return cancel(id);
        }
        repository.answer(id, request.answer());
        auditService.append(new AuditEventDraft(current.taskId(), current.runId(), current.stepId(), null,
                Domain.AuditEventType.UserInputProvided, Domain.AuditActor.USER, Domain.AuditLevel.INFO,
                current.question(), request.answer(), List.of(), null, null, null, null, null,
                Domain.RiskLevel.LOW, null, true, null, null,
                Map.of("userInputRequestId", current.id().toString())));
        runService.markRunning(current.runId(), current.taskId());
        return getRequired(id);
    }

    @Transactional
    public UserInputRequestRecord cancel(UUID id) {
        var current = getRequired(id);
        requirePending(current);
        repository.cancel(id);
        auditService.append(new AuditEventDraft(current.taskId(), current.runId(), current.stepId(), null,
                Domain.AuditEventType.UserInputCancelled, Domain.AuditActor.USER, Domain.AuditLevel.WARN,
                current.question(), "User input request cancelled", List.of(), null, null, null, null,
                null, Domain.RiskLevel.MEDIUM, null, false, "USER_INPUT_CANCELLED",
                "User input request cancelled", Map.of("userInputRequestId", current.id().toString())));
        runService.fail(current.runId(), current.taskId(), "User input request cancelled");
        return getRequired(id);
    }

    public UserInputRequestRecord pendingByRun(UUID runId) {
        return repository.findPendingByRun(runId).orElse(null);
    }

    public List<String> answeredRecoveryNotes(UUID runId, int limit) {
        return repository.findAnsweredByRun(runId, limit).stream()
                .map(request -> "User answered recovery prompt `%s`: %s"
                        .formatted(request.question(), request.answer()))
                .toList();
    }

    private void requirePending(UserInputRequestRecord request) {
        if (!Domain.UserInputStatus.PENDING.name().equals(request.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "USER_INPUT_NOT_PENDING",
                    "User input request is not pending: " + request.id());
        }
    }

    private boolean isCancelTask(String answer) {
        return answer != null && "cancel task".equalsIgnoreCase(answer.strip());
    }
}
