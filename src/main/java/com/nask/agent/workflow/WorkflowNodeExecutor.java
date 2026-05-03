package com.nask.agent.workflow;

import java.util.Map;

/**
 * Extension point for workflow-native node executors.
 */
public interface WorkflowNodeExecutor {
    String nodeType();

    NodeExecutionResult execute(AgentState state, MapWorkflowNode node);
}
