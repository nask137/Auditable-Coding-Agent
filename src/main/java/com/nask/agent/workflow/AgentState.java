package com.nask.agent.workflow;

import com.nask.agent.command.CommandExecution;
import com.nask.agent.file.FileChange;
import com.nask.agent.plan.PlanItem;
import com.nask.agent.plan.PlanView;
import com.nask.agent.run.AgentRun;
import com.nask.agent.runtime.RuntimeFailure;
import com.nask.agent.runtime.UserInputRequestRecord;
import com.nask.agent.task.CodingTask;
import com.nask.agent.validation.ValidationResultRecord;
import com.nask.agent.workspace.Workspace;

import java.util.List;
import java.util.Map;

/**
 * Read-only aggregate state used by workflow decisions.
 */
public record AgentState(
        CodingTask task,
        AgentRun run,
        Workspace workspace,
        WorkflowDefinition workflow,
        PlanView plan,
        PlanItem currentPlanItem,
        List<FileChange> recentFileChanges,
        List<CommandExecution> recentCommandExecutions,
        List<ValidationResultRecord> recentValidationResults,
        UserInputRequestRecord pendingUserInput,
        List<RuntimeFailure> runtimeFailures,
        List<String> recoveryNotes,
        Map<String, Object> transientData) {
    public Object transientValue(String key) {
        return transientData.get(key);
    }
}
