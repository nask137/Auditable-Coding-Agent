package com.nask.agent.workflow;

import org.springframework.stereotype.Component;

/**
 * Evaluates the whitelisted condition names supported by the first workflow DSL.
 */
@Component
public class ConditionEvaluator {
    public boolean evaluate(String condition, AgentState state, NodeExecutionResult lastResult) {
        return switch (condition) {
            case "plan.hasPendingItems" -> state.currentPlanItem() != null;
            case "plan.completed" -> state.plan() != null && state.currentPlanItem() == null;
            case "lastNode.status == SUCCESS" -> lastResult != null && "SUCCESS".equals(lastResult.status());
            case "lastNode.status == WAITING_APPROVAL" -> lastResult != null && "WAITING_APPROVAL".equals(lastResult.status());
            case "lastNode.status == WAITING_USER_INPUT" -> lastResult != null && "WAITING_USER_INPUT".equals(lastResult.status());
            case "lastValidation.passed" -> !state.recentValidationResults().isEmpty()
                    && state.recentValidationResults().getLast().success();
            case "lastRecovery.strategy == REPLAN_REMAINING_PLAN" -> !state.runtimeFailures().isEmpty()
                    && "REPLAN_REMAINING_PLAN".equals(state.runtimeFailures().getLast().strategy());
            default -> false;
        };
    }
}
