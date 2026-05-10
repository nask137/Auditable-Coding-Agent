package com.nask.agent.run;

import com.nask.agent.approval.ApprovalService;
import com.nask.agent.audit.AuditService;
import com.nask.agent.file.FileChangeRepository;
import com.nask.agent.plan.PlanService;
import com.nask.agent.report.TaskReportRepository;
import com.nask.agent.runtime.RuntimeFailureService;
import com.nask.agent.runtime.UserInputRequestService;
import com.nask.agent.step.AgentStepService;
import com.nask.agent.task.TaskService;
import com.nask.agent.workflow.WorkflowService;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Builds a single polling payload for one run.
 */
@Service
public class RunTimelineService {
    private final AgentRunService runService;
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

    public RunTimelineService(AgentRunService runService, TaskService taskService, PlanService planService,
                              AgentStepService stepService, WorkflowService workflowService, AuditService auditService,
                              ApprovalService approvalService, UserInputRequestService userInputRequestService,
                              RuntimeFailureService runtimeFailureService, FileChangeRepository fileChangeRepository,
                              TaskReportRepository reportRepository) {
        this.runService = runService;
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
    }

    public RunTimeline get(UUID runId) {
        var run = runService.getRequired(runId);
        var task = taskService.getRequired(run.taskId());
        return new RunTimeline(
                run,
                task,
                planService.findByRun(runId),
                stepService.findByRun(runId),
                workflowService.nodes(runId),
                workflowService.edges(runId),
                auditService.eventsForRun(runId),
                approvalService.list(null).stream().filter(approval -> runId.equals(approval.runId())).toList(),
                userInputRequestService.list(null).stream().filter(request -> runId.equals(request.runId())).toList(),
                runtimeFailureService.findByRun(runId),
                fileChangeRepository.findByTask(task.id()).stream().filter(change -> runId.equals(change.runId())).toList(),
                reportRepository.findLatestByTask(task.id()).filter(report -> runId.equals(report.runId())).orElse(null));
    }
}
