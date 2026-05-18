package com.nask.agent.task;

import com.nask.agent.approval.ApprovalRequestRecord;
import com.nask.agent.audit.AuditEvent;
import com.nask.agent.conversation.ConversationContextWindow;
import com.nask.agent.file.FileChange;
import com.nask.agent.plan.PlanView;
import com.nask.agent.report.TaskReport;
import com.nask.agent.runtime.RuntimeFailure;
import com.nask.agent.runtime.UserInputRequestRecord;
import com.nask.agent.step.AgentStep;
import com.nask.agent.workflow.WorkflowEdgeDecision;
import com.nask.agent.workflow.WorkflowNodeExecution;

import java.util.List;

/**
 * Aggregated task execution state optimized for polling terminal and dashboard clients.
 */
public record TaskTimeline(
        CodingTask task,
        PlanView plan,
        List<AgentStep> steps,
        List<WorkflowNodeExecution> workflowNodes,
        List<WorkflowEdgeDecision> workflowEdges,
        List<AuditEvent> events,
        List<ApprovalRequestRecord> approvals,
        List<UserInputRequestRecord> userInputs,
        List<RuntimeFailure> failures,
        List<FileChange> changes,
        TaskReport report,
        ConversationContextWindow conversationContext) {
}
