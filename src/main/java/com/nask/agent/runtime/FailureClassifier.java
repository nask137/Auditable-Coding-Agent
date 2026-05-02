package com.nask.agent.runtime;

import com.nask.agent.common.Domain;
import com.nask.agent.llm.LlmGatewayException;
import com.nask.agent.tool.ToolExecutionResult;
import org.springframework.stereotype.Component;

/**
 * Maps low-level failures into stable phase 2 runtime failure types.
 */
@Component
public class FailureClassifier {
    public Domain.RuntimeFailureType fromModelException(LlmGatewayException exception) {
        return exception.failureType();
    }

    public Domain.RuntimeFailureType fromToolResult(ToolExecutionResult result) {
        var summary = result.summary() == null ? "" : result.summary().toLowerCase();
        if (summary.contains("unsupported action type")) {
            return Domain.RuntimeFailureType.UNSUPPORTED_TOOL_INTENT;
        }
        if (summary.contains("workspace") || summary.contains("path") || summary.contains(".git")) {
            return Domain.RuntimeFailureType.PATH_ACCESS_BLOCKED;
        }
        if (summary.contains("command") || summary.contains("policy")) {
            return Domain.RuntimeFailureType.COMMAND_POLICY_BLOCKED;
        }
        if (summary.contains("patch")) {
            return Domain.RuntimeFailureType.PATCH_CONFLICT;
        }
        return result.blocked()
                ? Domain.RuntimeFailureType.TOOL_PERMISSION_BLOCKED
                : Domain.RuntimeFailureType.TOOL_EXECUTION_FAILED;
    }
}
