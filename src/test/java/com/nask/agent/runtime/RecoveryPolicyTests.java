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
    private final AgentSettings settings = new AgentSettings(20, 50, 5, 300, 3, 2, 2, 3, 120, 200000);
    private final RecoveryPolicy policy = new RecoveryPolicy(settings, repository);
    private final UUID runId = UUID.randomUUID();

    @Test
    void retriesModelOutputFailuresWithinBudget() {
        when(repository.countByRunAndType(runId, Domain.RuntimeFailureType.MODEL_OUTPUT_PARSE_FAILED.name()))
                .thenReturn(0);

        var decision = policy.decide(runId, Domain.RuntimeFailureType.MODEL_OUTPUT_PARSE_FAILED);

        assertThat(decision.strategy()).isEqualTo(Domain.RecoveryStrategy.RETRY_SAME_ACTION);
        assertThat(decision.recoverable()).isTrue();
        assertThat(decision.budgetRemaining()).isEqualTo(1);
    }

    @Test
    void asksUserWhenModelRetryBudgetIsExhausted() {
        when(repository.countByRunAndType(runId, Domain.RuntimeFailureType.MODEL_OUTPUT_PARSE_FAILED.name()))
                .thenReturn(2);
        when(repository.countByRunAndStrategy(runId, Domain.RecoveryStrategy.ASK_USER.name()))
                .thenReturn(0);

        var decision = policy.decide(runId, Domain.RuntimeFailureType.MODEL_OUTPUT_PARSE_FAILED);

        assertThat(decision.strategy()).isEqualTo(Domain.RecoveryStrategy.ASK_USER);
        assertThat(decision.recoverable()).isTrue();
    }

    @Test
    void failsWhenUserInputRequestBudgetIsExhausted() {
        when(repository.countByRunAndStrategy(runId, Domain.RecoveryStrategy.ASK_USER.name()))
                .thenReturn(3);

        var decision = policy.decide(runId, Domain.RuntimeFailureType.COMMAND_POLICY_BLOCKED);

        assertThat(decision.strategy()).isEqualTo(Domain.RecoveryStrategy.FAIL_TASK);
        assertThat(decision.recoverable()).isFalse();
        assertThat(decision.budgetRemaining()).isZero();
    }

    @Test
    void replansCurrentItemForRuntimeRejectedToolIntent() {
        when(repository.countByRunAndStrategy(runId, Domain.RecoveryStrategy.REPLAN_CURRENT_ITEM.name()))
                .thenReturn(0);

        var decision = policy.decide(runId, Domain.RuntimeFailureType.UNSUPPORTED_TOOL_INTENT);

        assertThat(decision.strategy()).isEqualTo(Domain.RecoveryStrategy.REPLAN_CURRENT_ITEM);
        assertThat(decision.budgetRemaining()).isEqualTo(1);
    }
}
