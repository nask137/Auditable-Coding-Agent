package com.nask.agent.llm;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AgentDecision(UUID planItemId, List<Action> actions) {
    public record Action(String type, String reason, Map<String, Object> input) {
    }
}
