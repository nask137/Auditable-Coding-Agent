package com.nask.agent.run;

import com.nask.agent.plan.PlanService;
import com.nask.agent.plan.PlanView;
import com.nask.agent.runtime.RuntimeFailure;
import com.nask.agent.runtime.RuntimeFailureService;
import com.nask.agent.step.AgentStep;
import com.nask.agent.step.AgentStepService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST API for observing an agent run and its derived timeline.
 */
@RestController
@RequestMapping("/api/runs")
public class RunController {
    private final AgentRunService runService;
    private final PlanService planService;
    private final AgentStepService stepService;
    private final RuntimeFailureService runtimeFailureService;
    private final RunTimelineService timelineService;

    /**
     * Creates a run observation controller.
     */
    public RunController(AgentRunService runService, PlanService planService, AgentStepService stepService,
                         RuntimeFailureService runtimeFailureService, RunTimelineService timelineService) {
        this.runService = runService;
        this.planService = planService;
        this.stepService = stepService;
        this.runtimeFailureService = runtimeFailureService;
        this.timelineService = timelineService;
    }

    /**
     * Lists runs for read-only dashboard selectors.
     */
    @GetMapping
    List<AgentRun> list() {
        return runService.list();
    }

    /**
     * Fetches run metadata and terminal status.
     */
    @GetMapping("/{runId}")
    AgentRun get(@PathVariable UUID runId) {
        return runService.getRequired(runId);
    }

    /**
     * Fetches the generated plan for a run.
     */
    @GetMapping("/{runId}/plan")
    PlanView plan(@PathVariable UUID runId) {
        return planService.getByRun(runId);
    }

    /**
     * Lists execution steps for a run in chronological order.
     */
    @GetMapping("/{runId}/steps")
    List<AgentStep> steps(@PathVariable UUID runId) {
        return stepService.findByRun(runId);
    }

    /**
     * Lists structured runtime failures for a run.
     */
    @GetMapping("/{runId}/failures")
    List<RuntimeFailure> failures(@PathVariable UUID runId) {
        return runtimeFailureService.findByRun(runId);
    }

    /**
     * Returns the full run timeline for polling TUI and dashboard clients.
     */
    @GetMapping("/{runId}/timeline")
    RunTimeline timeline(@PathVariable UUID runId) {
        return timelineService.get(runId);
    }
}
