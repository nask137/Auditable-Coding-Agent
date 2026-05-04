package com.nask.agent.workflow;

import com.nask.agent.common.Domain;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Small parsed view of the built-in workflow map.
 */
record WorkflowGraph(String start, Map<String, MapWorkflowNode> nodes, List<WorkflowEdge> edges, int maxNodes) {
    @SuppressWarnings("unchecked")
    static WorkflowGraph from(WorkflowDefinition definition) {
        var source = definition.definition();
        var nodes = ((Collection<?>) source.get("nodes")).stream()
                .map(WorkflowGraph::node)
                .collect(java.util.stream.Collectors.toMap(MapWorkflowNode::id, java.util.function.Function.identity()));
        var edges = ((Collection<?>) source.getOrDefault("edges", List.of())).stream()
                .map(WorkflowGraph::edge)
                .toList();
        var limits = source.get("limits") instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.<String, Object>of();
        return new WorkflowGraph(source.get("start").toString(), nodes, edges, integer(limits.get("maxNodes"), 30));
    }

    private static MapWorkflowNode node(Object value) {
        if (value instanceof String id) {
            return new MapWorkflowNode(id, legacyType(id), Map.of());
        }
        if (value instanceof Map<?, ?> map) {
            var id = map.get("id").toString();
            var type = map.get("type") == null ? legacyType(id) : map.get("type").toString();
            @SuppressWarnings("unchecked")
            var input = map.get("input") instanceof Map<?, ?> inputMap
                    ? (Map<String, Object>) inputMap
                    : Map.<String, Object>of();
            return new MapWorkflowNode(id, type, input);
        }
        throw new IllegalArgumentException("Invalid workflow node: " + value);
    }

    private static WorkflowEdge edge(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Invalid workflow edge: " + value);
        }
        var type = map.get("type") == null ? Domain.WorkflowEdgeType.ON_SUCCESS.name() : map.get("type").toString();
        var condition = map.get("condition") == null ? "" : map.get("condition").toString();
        return new WorkflowEdge(map.get("from").toString(), map.get("to").toString(), type, condition);
    }

    private static String legacyType(String id) {
        return switch (id) {
            case "understand_task" -> Domain.WorkflowNodeType.TASK_UNDERSTANDING.name();
            case "inspect_workspace" -> Domain.WorkflowNodeType.WORKSPACE_INSPECTION.name();
            case "project_memory" -> Domain.WorkflowNodeType.PROJECT_MEMORY.name();
            case "code_understanding" -> Domain.WorkflowNodeType.CODE_UNDERSTANDING.name();
            case "create_plan" -> Domain.WorkflowNodeType.PLAN_CREATION.name();
            case "execute_plan_item" -> Domain.WorkflowNodeType.PLAN_ITEM_EXECUTION.name();
            case "validate" -> Domain.WorkflowNodeType.VALIDATION.name();
            case "report" -> Domain.WorkflowNodeType.REPORT.name();
            case "finish" -> Domain.WorkflowNodeType.FINISH.name();
            case "fail" -> Domain.WorkflowNodeType.FAIL.name();
            default -> Domain.WorkflowNodeType.CONDITION.name();
        };
    }

    private static int integer(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return value == null ? defaultValue : Integer.parseInt(value.toString());
    }
}

record WorkflowEdge(String from, String to, String type, String condition) {
}
