package com.nask.agent.workflow;

import com.nask.agent.common.Domain;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;

/**
 * Validates the small phase 3 workflow DSL shape.
 */
@Component
public class WorkflowDefinitionValidator {
    public void validate(Map<String, Object> definition) {
        require(definition.containsKey("name"), "Workflow name is required");
        require(definition.containsKey("version"), "Workflow version is required");
        require(definition.containsKey("mode"), "Workflow mode is required");
        require(definition.containsKey("start"), "Workflow start node is required");
        Domain.WorkflowMode.valueOf(definition.get("mode").toString());
        var nodes = definition.get("nodes");
        require(nodes instanceof Collection<?> collection && !collection.isEmpty(),
                "Workflow nodes must be a non-empty collection");
        require(((Collection<?>) nodes).stream().anyMatch(node -> nodeId(node).equals(definition.get("start").toString())),
                "Workflow start node must exist in nodes");
    }

    private String nodeId(Object node) {
        if (node instanceof Map<?, ?> map && map.get("id") != null) {
            return map.get("id").toString();
        }
        return node.toString();
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
