package com.nask.agent.action;

import com.nask.agent.common.Domain;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Creates and updates actions that explain what the agent is about to do.
 */
@Service
public class AgentActionService {
    private final AgentActionRepository repository;

    /**
     * Creates an action service.
     */
    public AgentActionService(AgentActionRepository repository) {
        this.repository = repository;
    }

    /**
     * Creates a new action with {@code CREATED} status.
     */
    public AgentAction create(UUID stepId, Domain.ActionType actionType, String reason, Domain.RiskLevel riskLevel) {
        return repository.insert(new AgentAction(UUID.randomUUID(), stepId, actionType.name(), reason,
                riskLevel.name(), Domain.ActionStatus.CREATED.name(), Instant.now()));
    }

    /**
     * Updates the persisted action status.
     */
    public void updateStatus(UUID actionId, Domain.ActionStatus status) {
        repository.updateStatus(actionId, status);
    }
}
