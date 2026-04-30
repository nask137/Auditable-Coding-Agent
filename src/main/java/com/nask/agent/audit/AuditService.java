package com.nask.agent.audit;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Thin service facade for appending and reading audit events.
 */
@Service
public class AuditService {
    private final AuditRepository repository;

    /**
     * Creates an audit service.
     */
    public AuditService(AuditRepository repository) {
        this.repository = repository;
    }

    /**
     * Assigns id/timestamp to a draft and appends it.
     */
    public UUID append(AuditEventDraft draft) {
        return repository.insert(AuditRepository.fromDraft(UUID.randomUUID(), Instant.now(), draft));
    }

    /**
     * Returns all events attached to a task.
     */
    public List<AuditEvent> eventsForTask(UUID taskId) {
        return repository.findByTask(taskId);
    }

    /**
     * Returns all events attached to a run.
     */
    public List<AuditEvent> eventsForRun(UUID runId) {
        return repository.findByRun(runId);
    }
}
