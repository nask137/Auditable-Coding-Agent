package com.nask.agent.approval;

import com.nask.agent.audit.AuditService;
import com.nask.agent.common.Domain;
import com.nask.agent.run.AgentRunService;
import com.nask.agent.step.AgentStepService;
import com.nask.agent.workflow.WorkflowService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApprovalServiceTests {
    private final ApprovalRepository repository = mock(ApprovalRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final AgentRunService runService = mock(AgentRunService.class);
    private final AgentStepService stepService = mock(AgentStepService.class);
    private final WorkflowService workflowService = mock(WorkflowService.class);
    private final ApprovalService service = new ApprovalService(repository, auditService, runService, stepService,
            workflowService);

    @Test
    void consumesMatchingApprovedRequest() {
        var runId = UUID.randomUUID();
        var approval = approval(runId, List.of("pom.xml"), null, null);
        when(repository.findApprovedCandidates(runId, Domain.ApprovalType.SENSITIVE_FILE_MODIFY))
                .thenReturn(List.of(approval));
        when(repository.findById(approval.id())).thenReturn(java.util.Optional.of(approval));

        var consumed = service.consumeApproved(runId, Domain.ApprovalType.SENSITIVE_FILE_MODIFY,
                List.of("pom.xml"), null, null);

        assertThat(consumed).isNotNull();
        verify(repository).consume(approval.id());
        verify(auditService).append(any());
    }

    @Test
    void ignoresApprovedRequestWhenCommandDiffers() {
        var runId = UUID.randomUUID();
        var approval = approval(runId, List.of(), "mvn test", ".");
        when(repository.findApprovedCandidates(runId, Domain.ApprovalType.COMMAND_EXECUTION))
                .thenReturn(List.of(approval));

        var consumed = service.consumeApproved(runId, Domain.ApprovalType.COMMAND_EXECUTION,
                List.of(), "mvn package", ".");

        assertThat(consumed).isNull();
    }

    private ApprovalRequestRecord approval(UUID runId, List<String> files, String command, String cwd) {
        var id = UUID.randomUUID();
        return new ApprovalRequestRecord(id, UUID.randomUUID(), runId, UUID.randomUUID(), UUID.randomUUID(),
                Domain.ApprovalType.COMMAND_EXECUTION.name(), "reason", Domain.RiskLevel.HIGH.name(),
                files, command, cwd, null, Domain.ApprovalStatus.APPROVED.name(), Instant.now(), Instant.now(),
                "tester", null);
    }
}
