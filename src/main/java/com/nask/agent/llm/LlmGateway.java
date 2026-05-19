package com.nask.agent.llm;

/**
 * Boundary between the deterministic agent runtime and model-driven decisions.
 */
public interface LlmGateway {
    /**
     * Produces a structured understanding of the user's task.
     */
    TaskUnderstanding understandTask(TaskContext context);

    /**
     * Creates an ordered plan for the run.
     */
    PlanDraft createPlan(PlanningContext context);

    /**
     * Chooses concrete tool actions for the current plan item.
     */
    AgentDecision decideNextAction(ExecutionContext context);

    /**
     * Replans the current item after a runtime rejection or failed validation.
     */
    PlanDraft replan(ExecutionContext context, String failureSummary);

    /**
     * Drafts the narrative part of the final report.
     */
    FinalReportDraft generateReport(ReportContext context);
}
