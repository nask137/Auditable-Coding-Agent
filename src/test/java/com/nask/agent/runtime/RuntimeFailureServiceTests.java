package com.nask.agent.runtime;

import com.nask.agent.audit.AuditEventDraft;
import com.nask.agent.audit.AuditService;
import com.nask.agent.common.Domain;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RuntimeFailureServiceTests {
    private final RuntimeFailureRepository repository = mock(RuntimeFailureRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final RecoveryPolicy recoveryPolicy = mock(RecoveryPolicy.class);
    private final RuntimeFailureService service = new RuntimeFailureService(repository, auditService, recoveryPolicy);

    @Test
    void writesRetriedEventForRetrySameAction() {
        when(auditService.append(any())).thenReturn(UUID.randomUUID());
        when(repository.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(recoveryPolicy.decide(any(), any(), any(), any(), any()))
                .thenReturn(new RecoveryDecision(Domain.RecoveryStrategy.RETRY_SAME_ACTION, true, 1, 1, false));

        service.record(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
                Domain.RuntimeFailureType.MODEL_OUTPUT_PARSE_FAILED, "bad json", "create plan");

        var captor = org.mockito.ArgumentCaptor.forClass(AuditEventDraft.class);
        verify(auditService, org.mockito.Mockito.times(3)).append(captor.capture());
        assertThat(captor.getAllValues().stream().map(AuditEventDraft::eventType))
                .contains(Domain.AuditEventType.RecoveryRetried);
    }

    @Test
    void writesBudgetExhaustedEventWhenRetryBudgetFallsBackToUserInput() {
        when(auditService.append(any())).thenReturn(UUID.randomUUID());
        when(repository.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(recoveryPolicy.decide(any(), any(), any(), any(), any()))
                .thenReturn(new RecoveryDecision(Domain.RecoveryStrategy.ASK_USER, true, 1, 2, true));

        service.record(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
                Domain.RuntimeFailureType.MODEL_OUTPUT_PARSE_FAILED, "bad json", "create plan");

        var captor = org.mockito.ArgumentCaptor.forClass(AuditEventDraft.class);
        verify(auditService, org.mockito.Mockito.times(3)).append(captor.capture());
        assertThat(captor.getAllValues().stream().map(AuditEventDraft::eventType))
                .contains(Domain.AuditEventType.RecoveryBudgetExhausted)
                .doesNotContain(Domain.AuditEventType.RecoveryExhausted);
    }
}
