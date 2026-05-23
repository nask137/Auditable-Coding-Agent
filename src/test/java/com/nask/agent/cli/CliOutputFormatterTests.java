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

    @Test
    void reportKeepsNarrativeAndOmitsRuntimeDetails() throws Exception {
        var report = mapper.readTree("""
                {
                  "contentMd": "# Result\\n\\nREADME.md is current.\\n\\n## Runtime Details\\n\\n- Changed files: README.md\\n\\n## Workflow\\n\\n- noisy\\n\\n## Audit Events\\n\\n- noisy"
                }
                """);

        var output = new CliOutputFormatter(mapper, false).report(report);

        assertThat(output).contains("README.md is current.");
        assertThat(output)
                .doesNotContain("Changed files")
                .doesNotContain("## Workflow")
                .doesNotContain("## Audit Events");
        assertThat(output).contains("Details:");
    }

    @Test
    void finalSummaryShowsConversationIdentityWhenPresent() throws Exception {
        var timeline = mapper.readTree("""
                {
                  "task": {
                    "id": "9840ba7f-e2e0-49f5-881b-b63a994459ca",
                    "status": "COMPLETED",
                    "conversationId": "11111111-2222-3333-4444-555555555555",
                    "promptIndex": 2
                  },
                  "run": {"id": "9840ba7f-e2e0-49f5-881b-b63a994459ca", "status": "COMPLETED"},
                  "report": {"contentMd": "# Result\\n\\nREADME.md is current.\\n\\n## Runtime Details\\n\\n- Conversation memory: previous prompt was `agent list`"},
                  "changes": [],
                  "failures": []
                }
                """);

        var output = new CliOutputFormatter(mapper, false).finalSummary(timeline);

        assertThat(output)
                .contains("Conversation 11111111; prompt #2")
                .contains("README.md is current.")
                .doesNotContain("previous prompt was `agent list`");
    }

    @Test
    void eventsSummarizesByType() throws Exception {
        var events = mapper.readTree("""
                [
                  {"eventType": "TaskCreated"},
                  {"eventType": "TaskCreated"},
                  {"eventType": "TaskExecutionStarted"}
                ]
                """);

        var output = new CliOutputFormatter(mapper, false).events(events);

        assertThat(output).contains("TaskCreated").contains("2").contains("Total events: 3");
    }
}
