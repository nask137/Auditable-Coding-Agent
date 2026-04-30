package com.nask.agent.llm;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Model decision for how to execute the current plan item.
 */
public record AgentDecision(UUID planItemId, List<Action> actions) {
    /**
     * Tool action requested by the model.
     */
    public record Action(String type, String reason, Map<String, Object> input) {
    }
}
