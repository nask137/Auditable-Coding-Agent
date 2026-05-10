package com.nask.agent.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CliOutputFormatterTests {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void rendersPlanTableFromTimeline() throws Exception {
        var timeline = mapper.readTree("""
                {"plan":{"items":[{"orderIndex":1,"status":"PENDING","description":"Read README"}]}}
                """);

        var output = new CliOutputFormatter(mapper, false).plan(timeline);

        assertThat(output).contains("Status").contains("PENDING").contains("Read README");
    }

    @Test
    void skipsTimelineOutputWhenNoNewEventsArrived() throws Exception {
        var timeline = mapper.readTree("""
                {
                  "run": {"id": "9840ba7f-e2e0-49f5-881b-b63a994459ca", "status": "RUNNING"},
                  "workflowNodes": [
                    {"nodeId": "create_plan", "status": "SUCCESS", "outputSummary": "Created 4 plan items"}
                  ],
                  "events": [
                    {"eventType": "PlanCreated", "outputSummary": "Created 4 plan items"}
                  ]
                }
                """);

        var output = new CliOutputFormatter(mapper, false).timelineUpdate(timeline, 1);

        assertThat(output).isEmpty();
    }
}
