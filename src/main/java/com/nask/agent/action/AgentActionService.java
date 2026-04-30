package com.nask.agent.action;

import com.nask.agent.common.Domain;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class AgentActionService {
    private final AgentActionRepository repository;

    public AgentActionService(AgentActionRepository repository) {
        this.repository = repository;
    }

    public AgentAction create(UUID stepId, Domain.ActionType actionType, String reason, Domain.RiskLevel riskLevel) {
        return repository.insert(new AgentAction(UUID.randomUUID(), stepId, actionType.name(), reason,
                riskLevel.name(), Domain.ActionStatus.CREATED.name(), Instant.now()));
    }

    public void updateStatus(UUID actionId, Domain.ActionStatus status) {
        repository.updateStatus(actionId, status);
    }
}
