package com.nask.agent.run;

import com.nask.agent.audit.AuditEventDraft;
import com.nask.agent.audit.AuditService;
import com.nask.agent.common.Domain;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * Runs agent workflows on a bounded background executor for interactive clients.
 */
@Service
public class AgentRunAsyncExecutor {
    private final AgentLoopExecutor loopExecutor;
    private final AgentRunService runService;
    private final AuditService auditService;
    private final TaskExecutor agentTaskExecutor;

    public AgentRunAsyncExecutor(AgentLoopExecutor loopExecutor, AgentRunService runService,
                                 AuditService auditService, TaskExecutor agentTaskExecutor) {
        this.loopExecutor = loopExecutor;
        this.runService = runService;
        this.auditService = auditService;
        this.agentTaskExecutor = agentTaskExecutor;
    }

    /**
     * Schedules a run and guarantees unexpected worker errors become durable run failures.
     */
    public void submit(UUID taskId, UUID runId) {
        agentTaskExecutor.execute(() -> {
            try {
                loopExecutor.execute(runId);
            } catch (Exception e) {
                runService.fail(runId, taskId, "Async run failed: " + e.getMessage());
                auditService.append(new AuditEventDraft(taskId, runId, null, null,
                        Domain.AuditEventType.AgentRunFailed, Domain.AuditActor.RUNTIME, Domain.AuditLevel.ERROR,
                        "Async run failed", e.getMessage(), java.util.List.of(), null, null, null, null,
                        null, Domain.RiskLevel.MEDIUM, null, false, "ASYNC_RUN_FAILED", e.getMessage(), Map.of()));
            }
        });
    }
}
