package com.nask.agent.workflow;

import java.util.Map;

/**
 * Minimal map-backed workflow node definition.
 */
public record MapWorkflowNode(String id, String type, Map<String, Object> input) {
}
