package com.nask.agent.task;

import com.nask.agent.approval.ApprovalService;
import com.nask.agent.audit.AuditService;
import com.nask.agent.conversation.ConversationContextService;
import com.nask.agent.file.FileChangeRepository;
import com.nask.agent.plan.PlanService;
import com.nask.agent.report.TaskReportRepository;
import com.nask.agent.runtime.RuntimeFailureService;
import com.nask.agent.runtime.UserInputRequestService;
import com.nask.agent.step.AgentStepService;
import com.nask.agent.workflow.WorkflowService;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Builds a single polling payload for one task execution.
 */
@Service
public class TaskTimelineService {
    private final TaskService taskService;
    private final PlanService planService;
    private final AgentStepService stepService;
    private final WorkflowService workflowService;
    private final AuditService auditService;
    private final ApprovalService approvalService;
    private final UserInputRequestService userInputRequestService;
    private final RuntimeFailureService runtimeFailureService;
    private final FileChangeRepository fileChangeRepository;
    private final TaskReportRepository reportRepository;
    private final ConversationContextService conversationContextService;

    public TaskTimelineService(TaskService taskService, PlanService planService,
                               AgentStepService stepService, WorkflowService workflowService, AuditService auditService,
                               ApprovalService approvalService, UserInputRequestService userInputRequestService,
                               RuntimeFailureService runtimeFailureService, FileChangeRepository fileChangeRepository,
                               TaskReportRepository reportRepository,
                               ConversationContextService conversationContextService) {
        this.taskService = taskService;
        this.planService = planService;
        this.stepService = stepService;
        this.workflowService = workflowService;
        this.auditService = auditService;
        this.approvalService = approvalService;
        this.userInputRequestService = userInputRequestService;
        this.runtimeFailureService = runtimeFailureService;
        this.fileChangeRepository = fileChangeRepository;
        this.reportRepository = reportRepository;
        this.conversationContextService = conversationContextService;
    }

    public TaskTimeline get(UUID taskId) {
        var task = taskService.getRequired(taskId);
        return new TaskTimeline(
                task,
                planService.findByRun(taskId),
                stepService.findByRun(taskId),
                workflowService.nodes(taskId),
                workflowService.edges(taskId),
                auditService.eventsForRun(taskId),
                approvalService.list(null).stream().filter(approval -> taskId.equals(approval.runId())).toList(),
                userInputRequestService.list(null).stream().filter(request -> taskId.equals(request.runId())).toList(),
                runtimeFailureService.findByRun(taskId),
                fileChangeRepository.findByTask(task.id()).stream().filter(change -> taskId.equals(change.runId())).toList(),
                reportRepository.findLatestByTask(task.id()).filter(report -> taskId.equals(report.runId())).orElse(null),
                conversationContextService.window(task.conversationId(), task.id()));
    }
}
