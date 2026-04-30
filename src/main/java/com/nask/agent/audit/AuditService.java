package com.nask.agent.audit;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AuditService {
    private final AuditRepository repository;

    public AuditService(AuditRepository repository) {
        this.repository = repository;
    }

    public UUID append(AuditEventDraft draft) {
        return repository.insert(AuditRepository.fromDraft(UUID.randomUUID(), Instant.now(), draft));
    }

    public List<AuditEvent> eventsForTask(UUID taskId) {
        return repository.findByTask(taskId);
    }

    public List<AuditEvent> eventsForRun(UUID runId) {
        return repository.findByRun(runId);
    }
}
