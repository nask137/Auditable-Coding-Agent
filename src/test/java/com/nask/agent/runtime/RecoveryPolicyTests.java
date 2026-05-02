package com.nask.agent.runtime;

import com.nask.agent.common.AgentSettings;
import com.nask.agent.common.Domain;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecoveryPolicyTests {
    private final RuntimeFailureRepository repository = mock(RuntimeFailureRepository.class);
    private final UserInputRequestRepository userInputRequestRepository = mock(UserInputRequestRepository.class);
    private final AgentSettings settings = new AgentSettings(20, 50, 5, 300, 3, 2, 2, 3, 120, 200000);
    private final RecoveryPolicy policy = new RecoveryPolicy(settings, repository, userInputRequestRepository);
    private final UUID runId = UUID.randomUUID();

    @Test
    void retriesModelOutputFailuresWithinActiveDecisionBudget() {
        var stepId = UUID.randomUUID();
        var priorStepId = UUID.randomUUID();
        when(repository.countByDecisionScope(runId, stepId, null,
                Domain.RuntimeFailureType.MODEL_OUTPUT_PARSE_FAILED.name(), "create plan"))
                .thenReturn(0);
        when(repository.countByDecisionScope(runId, priorStepId, null,
                Domain.RuntimeFailureType.MODEL_OUTPUT_PARSE_FAILED.name(), "create plan"))
                .thenReturn(2);

        var decision = policy.decide(runId, stepId, null,
                Domain.RuntimeFailureType.MODEL_OUTPUT_PARSE_FAILED, "create plan");

        assertThat(decision.strategy()).isEqualTo(Domain.RecoveryStrategy.RETRY_SAME_ACTION);
        assertThat(decision.recoverable()).isTrue();
        assertThat(decision.budgetRemaining()).isEqualTo(1);
    }

    @Test
    void asksUserWhenModelRetryBudgetIsExhausted() {
        var stepId = UUID.randomUUID();
        when(repository.countByDecisionScope(runId, stepId, null,
                Domain.RuntimeFailureType.MODEL_OUTPUT_PARSE_FAILED.name(), "create plan"))
                .thenReturn(2);
        when(userInputRequestRepository.countPendingByRun(runId))
                .thenReturn(0);

        var decision = policy.decide(runId, stepId, null,
                Domain.RuntimeFailureType.MODEL_OUTPUT_PARSE_FAILED, "create plan");

        assertThat(decision.strategy()).isEqualTo(Domain.RecoveryStrategy.ASK_USER);
        assertThat(decision.recoverable()).isTrue();
    }

    @Test
    void failsWhenPendingUserInputRequestBudgetIsExhausted() {
        when(userInputRequestRepository.countPendingByRun(runId))
                .thenReturn(3);

        var decision = policy.decide(runId, UUID.randomUUID(), null,
                Domain.RuntimeFailureType.COMMAND_POLICY_BLOCKED, "blocked command");

        assertThat(decision.strategy()).isEqualTo(Domain.RecoveryStrategy.FAIL_TASK);
        assertThat(decision.recoverable()).isFalse();
        assertThat(decision.budgetRemaining()).isZero();
    }

    @Test
    void allowsUserInputWhenHistoricalPromptsAreResolved() {
        when(userInputRequestRepository.countPendingByRun(runId))
                .thenReturn(0);

        var decision = policy.decide(runId, UUID.randomUUID(), null,
                Domain.RuntimeFailureType.COMMAND_POLICY_BLOCKED, "blocked command");

        assertThat(decision.strategy()).isEqualTo(Domain.RecoveryStrategy.ASK_USER);
        assertThat(decision.recoverable()).isTrue();
        assertThat(decision.budgetRemaining()).isEqualTo(2);
    }

    @Test
    void replansCurrentItemForRuntimeRejectedToolIntent() {
        when(repository.countByRunAndStrategy(runId, Domain.RecoveryStrategy.REPLAN_CURRENT_ITEM.name()))
                .thenReturn(0);

        var decision = policy.decide(runId, UUID.randomUUID(), UUID.randomUUID(),
                Domain.RuntimeFailureType.UNSUPPORTED_TOOL_INTENT, "decide next action");

        assertThat(decision.strategy()).isEqualTo(Domain.RecoveryStrategy.REPLAN_CURRENT_ITEM);
        assertThat(decision.budgetRemaining()).isEqualTo(1);
    }
}
