package com.nask.agent.workflow;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Registry for workflow-native node executors.
 */
@Component
public class WorkflowNodeRegistry {
    private final Map<String, WorkflowNodeExecutor> executors;

    public WorkflowNodeRegistry(List<WorkflowNodeExecutor> executors) {
        this.executors = executors.stream()
                .collect(Collectors.toUnmodifiableMap(WorkflowNodeExecutor::nodeType, Function.identity()));
    }

    public WorkflowNodeExecutor get(String nodeType) {
        var executor = executors.get(nodeType);
        if (executor == null) {
            throw new IllegalArgumentException("Unsupported workflow node type: " + nodeType);
        }
        return executor;
    }
}
