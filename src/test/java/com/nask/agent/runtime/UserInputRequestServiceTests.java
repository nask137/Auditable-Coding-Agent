package com.nask.agent.runtime;

import com.nask.agent.audit.AuditService;
import com.nask.agent.common.Domain;
import com.nask.agent.run.AgentRunService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserInputRequestServiceTests {
    private final UserInputRequestRepository repository = mock(UserInputRequestRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final AgentRunService runService = mock(AgentRunService.class);
    private final UserInputRequestService service = new UserInputRequestService(repository, auditService, runService);

    @Test
    void cancelTaskAnswerCancelsRequestAndFailsRun() {
        var request = request(Domain.UserInputStatus.PENDING.name(), null);
        var cancelled = request(Domain.UserInputStatus.CANCELLED.name(), null);
        when(repository.findById(request.id()))
                .thenReturn(Optional.of(request))
                .thenReturn(Optional.of(request))
                .thenReturn(Optional.of(cancelled));

        var result = service.answer(request.id(), new AnswerUserInputRequest("Cancel task"));

        assertThat(result.status()).isEqualTo(Domain.UserInputStatus.CANCELLED.name());
        verify(repository).cancel(request.id());
        verify(runService).fail(request.runId(), request.taskId(), "User input request cancelled");
        verify(runService, never()).markRunning(any(), any());
    }

    private UserInputRequestRecord request(String status, String answer) {
        return new UserInputRequestRecord(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), status, "Question", "Context",
                List.of("Retry with corrected model output", "Adjust task instructions", "Cancel task"),
                answer, Instant.now(), null);
    }
}
