package com.nask.agent.run;

import com.nask.agent.plan.PlanService;
import com.nask.agent.plan.PlanView;
import com.nask.agent.step.AgentStep;
import com.nask.agent.step.AgentStepService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/runs")
public class RunController {
    private final AgentRunService runService;
    private final PlanService planService;
    private final AgentStepService stepService;

    public RunController(AgentRunService runService, PlanService planService, AgentStepService stepService) {
        this.runService = runService;
        this.planService = planService;
        this.stepService = stepService;
    }

    @GetMapping("/{runId}")
    AgentRun get(@PathVariable UUID runId) {
        return runService.getRequired(runId);
    }

    @GetMapping("/{runId}/plan")
    PlanView plan(@PathVariable UUID runId) {
        return planService.getByRun(runId);
    }

    @GetMapping("/{runId}/steps")
    List<AgentStep> steps(@PathVariable UUID runId) {
        return stepService.findByRun(runId);
    }
}
