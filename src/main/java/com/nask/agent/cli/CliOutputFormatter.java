package com.nask.agent.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Human-readable render helpers for terminal output.
 */
class CliOutputFormatter {
    private final ObjectMapper mapper;
    private final boolean rawJson;

    CliOutputFormatter(ObjectMapper mapper, boolean rawJson) {
        this.mapper = mapper;
        this.rawJson = rawJson;
    }

    String json(String body) throws Exception {
        if (rawJson) {
            return body;
        }
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mapper.readTree(body));
    }

    String status(JsonNode timeline, String baseUrl, String permissionPreset) {
        var task = timeline.path("task");
        return """
                Base URL: %s
                Permission: %s
                Workspace ID: %s
                Conversation: %s
                Task: %s %s
                """.formatted(baseUrl, permissionPreset, task.path("workspaceId").asText(""),
                task.path("conversationId").asText(""),
                task.path("id").asText(""), task.path("status").asText(""));
    }

    String plan(JsonNode timeline) {
        var plan = timeline.path("plan");
        if (plan.isMissingNode() || plan.isNull()) {
            return "No plan has been created yet.";
        }
        var rows = new ArrayList<String[]>();
        for (var item : plan.path("items")) {
            rows.add(new String[]{
                    item.path("orderIndex").asText(""),
                    item.path("status").asText(""),
                    oneLine(item.path("description").asText(""))
            });
        }
        return table(List.of("#", "Status", "Description"), rows);
    }

    String approvals(JsonNode approvals) {
        var rows = new ArrayList<String[]>();
        for (var item : approvals) {
            rows.add(new String[]{
                    shortId(item.path("id").asText("")),
                    item.path("status").asText(""),
                    item.path("approvalType").asText(""),
                    oneLine(item.path("reason").asText(""))
            });
        }
        return rows.isEmpty() ? "No approvals." : table(List.of("Id", "Status", "Type", "Reason"), rows);
    }

    String diff(JsonNode timeline) {
        return changes(timeline.path("changes"));
    }

    String changes(JsonNode changes) {
        if (!changes.isArray() || changes.isEmpty()) {
            return "No file changes recorded.";
        }
        var rows = new ArrayList<String[]>();
        for (var change : changes) {
            rows.add(new String[]{
                    change.path("changeType").asText(""),
                    change.path("lineAdded").asText("0"),
                    change.path("lineDeleted").asText("0"),
                    oneLine(change.path("path").asText(""))
            });
        }
        return table(List.of("Type", "+", "-", "Path"), rows)
                + "Use --json on wrapper commands or the web UI for full diffs.\n";
    }

    String report(JsonNode report) {
        if (report.isMissingNode() || report.isNull()) {
            return "No report has been generated yet.";
        }
        var content = conciseReportContent(report.path("contentMd").asText(""));
        if (content.isBlank()) {
            content = "Report generated.";
        }
        return content.strip() + "\n\nDetails: workflow, audit trail, recovery records, and raw validation output are available in the web UI or with --json.\n";
    }

    String finalSummary(JsonNode timeline) {
        var builder = new StringBuilder();
        var task = timeline.path("task");
        builder.append("Task ").append(shortId(task.path("id").asText("")))
                .append(" ").append(task.path("status").asText(""))
                .append("\n\n");
        if (!task.path("conversationId").asText("").isBlank()) {
            builder.append("Conversation ").append(shortId(task.path("conversationId").asText("")))
                    .append("; prompt #").append(task.path("promptIndex").asText("")).append("\n\n");
        }
        builder.append(report(timeline.path("report")));
        var changes = timeline.path("changes");
        if (changes.isArray() && !changes.isEmpty()) {
            builder.append("\n").append(changes(changes));
        }
        var failures = timeline.path("failures");
        if (failures.isArray() && !failures.isEmpty()) {
            builder.append("\nRecovery records: ").append(failures.size())
                    .append(" recorded. Use /status, /plan, or the web UI for details.\n");
        }
        return builder.toString();
    }

    String events(JsonNode events) {
        if (!events.isArray() || events.isEmpty()) {
            return "No audit events.";
        }
        var counts = new java.util.LinkedHashMap<String, Integer>();
        for (var event : events) {
            var type = event.path("eventType").asText("");
            counts.put(type, counts.getOrDefault(type, 0) + 1);
        }
        var rows = new ArrayList<String[]>();
        counts.forEach((type, count) -> rows.add(new String[]{type, count.toString()}));
        return table(List.of("Event", "Count"), rows)
                + "Total events: " + events.size() + ". Use --json or the web UI for the full audit trail.\n";
    }

    String failures(JsonNode failures) {
        if (!failures.isArray() || failures.isEmpty()) {
            return "No runtime failures.";
        }
        var rows = new ArrayList<String[]>();
        for (var failure : failures) {
            rows.add(new String[]{
                    failure.path("failureType").asText(""),
                    failure.path("strategy").asText(""),
                    oneLine(failure.path("summary").asText(""))
            });
        }
        return table(List.of("Type", "Strategy", "Summary"), rows);
    }

    String workflowPath(JsonNode nodes, JsonNode edges) {
        var rows = new ArrayList<String[]>();
        if (nodes.isArray()) {
            for (var node : nodes) {
                rows.add(new String[]{
                        node.path("nodeId").asText(""),
                        node.path("status").asText(""),
                        oneLine(node.path("outputSummary").asText(""))
                });
            }
        }
        var output = rows.isEmpty() ? "No workflow nodes.\n" : table(List.of("Node", "Status", "Summary"), rows);
        var edgeCount = edges.isArray() ? edges.size() : 0;
        return output + "Edges: " + edgeCount + ". Use --json or the web UI for transition reasons.\n";
    }

    String taskStatus(JsonNode task) {
        return """
                Task: %s
                Workspace ID: %s
                Status: %s
                Request: %s
                """.formatted(task.path("id").asText(""), task.path("workspaceId").asText(""),
                task.path("status").asText(""), oneLine(task.path("userRequest").asText("")));
    }

    String timelineUpdate(JsonNode timeline, int eventOffset) {
        var builder = new StringBuilder();
        var events = timeline.path("events");
        if (events.isArray() && eventOffset >= events.size()) {
            return "";
        }
        var task = timeline.path("task");
        builder.append("Task ").append(shortId(task.path("id").asText("")))
                .append(" status: ").append(task.path("status").asText("")).append("\n");
        var nodes = timeline.path("workflowNodes");
        if (nodes.isArray() && !nodes.isEmpty()) {
            var last = nodes.get(nodes.size() - 1);
            builder.append("Node: ").append(last.path("nodeId").asText(""))
                    .append(" ").append(last.path("status").asText(""))
                    .append(" - ").append(oneLine(last.path("outputSummary").asText(""))).append("\n");
        }
        return builder.toString();
    }

    private static String conciseReportContent(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        var result = content;
        for (var marker : List.of("\n## Project Context", "\n## File Changes", "\n## Failure and Recovery",
                "\n## Workflow", "\n## Audit Events")) {
            var index = result.indexOf(marker);
            if (index >= 0) {
                result = result.substring(0, index);
            }
        }
        return result;
    }

    private static String table(List<String> headers, List<String[]> rows) {
        var widths = new int[headers.size()];
        for (int i = 0; i < headers.size(); i++) {
            widths[i] = headers.get(i).length();
        }
        for (var row : rows) {
            for (int i = 0; i < widths.length; i++) {
                widths[i] = Math.max(widths[i], i < row.length ? row[i].length() : 0);
            }
        }
        var builder = new StringBuilder();
        appendRow(builder, headers.toArray(String[]::new), widths);
        appendRule(builder, widths);
        for (var row : rows) {
            appendRow(builder, row, widths);
        }
        return builder.toString();
    }

    private static void appendRow(StringBuilder builder, String[] row, int[] widths) {
        for (int i = 0; i < widths.length; i++) {
            if (i > 0) {
                builder.append("  ");
            }
            builder.append(String.format("%-" + widths[i] + "s", i < row.length ? row[i] : ""));
        }
        builder.append("\n");
    }

    private static void appendRule(StringBuilder builder, int[] widths) {
        for (int i = 0; i < widths.length; i++) {
            if (i > 0) {
                builder.append("  ");
            }
            builder.append("-".repeat(widths[i]));
        }
        builder.append("\n");
    }

    private static String oneLine(String value) {
        var line = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').strip();
        return line.length() > 100 ? line.substring(0, 100) + "..." : line;
    }

    private static String shortId(String value) {
        return value == null || value.length() <= 8 ? value : value.substring(0, 8);
    }
}
