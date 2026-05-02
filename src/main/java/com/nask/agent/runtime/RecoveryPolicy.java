package com.nask.agent.runtime;

import com.nask.agent.common.AgentSettings;
import com.nask.agent.common.Domain;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Chooses bounded recovery strategies for classified runtime failures.
 */
@Component
public class RecoveryPolicy {
    private final AgentSettings settings;
    private final RuntimeFailureRepository repository;

    public RecoveryPolicy(AgentSettings settings, RuntimeFailureRepository repository) {
        this.settings = settings;
        this.repository = repository;
    }

    public RecoveryDecision decide(UUID runId, Domain.RuntimeFailureType failureType) {
        return switch (failureType) {
            case MODEL_CALL_FAILED, MODEL_OUTPUT_PARSE_FAILED, MODEL_OUTPUT_VALIDATION_FAILED,
                    MODEL_DECISION_MISMATCH -> retryModel(runId, failureType);
            case UNSUPPORTED_TOOL_INTENT, PATCH_CONFLICT, PATH_ACCESS_BLOCKED -> replan(runId);
            case VALIDATION_FAILED, COMMAND_EXECUTION_FAILED -> replanRemaining(runId);
            case COMMAND_POLICY_BLOCKED, TOOL_PERMISSION_BLOCKED, USER_INPUT_REQUIRED -> askUser(runId);
            case RUNTIME_LIMIT_EXCEEDED, APPROVAL_DENIED, UNEXPECTED_RUNTIME_ERROR -> fail(failureType);
            default -> askUser(runId);
        };
    }

    private RecoveryDecision retryModel(UUID runId, Domain.RuntimeFailureType failureType) {
        var attempt = repository.countByRunAndType(runId, failureType.name()) + 1;
        var remaining = settings.maxModelRetries() - attempt;
        if (remaining >= 0) {
            return new RecoveryDecision(Domain.RecoveryStrategy.RETRY_SAME_ACTION, true, attempt, remaining);
        }
        return askUser(runId);
    }

    private RecoveryDecision replan(UUID runId) {
        var attempt = repository.countByRunAndStrategy(runId, Domain.RecoveryStrategy.REPLAN_CURRENT_ITEM.name()) + 1;
        var remaining = settings.maxReplanAttempts() - attempt;
        if (remaining >= 0) {
            return new RecoveryDecision(Domain.RecoveryStrategy.REPLAN_CURRENT_ITEM, true, attempt, remaining);
        }
        return askUser(runId);
    }

    private RecoveryDecision replanRemaining(UUID runId) {
        var attempt = repository.countByRunAndStrategy(runId, Domain.RecoveryStrategy.REPLAN_REMAINING_PLAN.name()) + 1;
        var remaining = settings.maxConsecutiveFailures() - attempt;
        if (remaining >= 0) {
            return new RecoveryDecision(Domain.RecoveryStrategy.REPLAN_REMAINING_PLAN, true, attempt, remaining);
        }
        return askUser(runId);
    }

    private RecoveryDecision askUser(UUID runId) {
        var attempt = repository.countByRunAndStrategy(runId, Domain.RecoveryStrategy.ASK_USER.name()) + 1;
        var remaining = settings.maxUserInputRequestsPerRun() - attempt;
        if (remaining < 0) {
            return fail(Domain.RuntimeFailureType.USER_INPUT_REQUIRED);
        }
        return new RecoveryDecision(Domain.RecoveryStrategy.ASK_USER, true, attempt, remaining);
    }

    private RecoveryDecision fail(Domain.RuntimeFailureType failureType) {
        return new RecoveryDecision(Domain.RecoveryStrategy.FAIL_TASK, false, 1, 0);
    }
}
