package com.nask.agent.task;

import com.nask.agent.llm.LlmGateway;
import com.nask.agent.llm.TaskContext;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Selects the workflow that should run a task before the execution graph starts.
 */
@Component
public class TaskWorkflowSelector {
    private final LlmGateway llmGateway;

    public TaskWorkflowSelector(LlmGateway llmGateway) {
        this.llmGateway = llmGateway;
    }

    public String selectWorkflow(CodingTask task, String requestedWorkflow) {
        if (isExplicitWorkflow(requestedWorkflow)) {
            return requestedWorkflow;
        }
        var selection = llmGateway.selectAgentWorkflow(new TaskContext(task.id(), task.executionId(), null,
                task.workspaceId(), task.userRequest(), List.of()));
        return selection.workflow();
    }

    private boolean isExplicitWorkflow(String workflow) {
        return workflow != null
                && !workflow.isBlank()
                && !"auto".equalsIgnoreCase(workflow);
    }
}
