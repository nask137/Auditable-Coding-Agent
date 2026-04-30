package com.nask.agent.llm;

public interface LlmGateway {
    TaskUnderstanding understandTask(TaskContext context);

    PlanDraft createPlan(PlanningContext context);

    AgentDecision decideNextAction(ExecutionContext context);

    ValidationDecision suggestValidation(ValidationContext context);

    FinalReportDraft generateReport(ReportContext context);
}
