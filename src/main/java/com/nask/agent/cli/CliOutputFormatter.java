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

    String status(JsonNode timeline, String sessionId, String baseUrl, String permissionPreset) {
        var run = timeline.path("run");
        var task = timeline.path("task");
        return """
                Session: %s
                Base URL: %s
                Permission: %s
                Workspace: %s
                Task: %s %s
                Run: %s %s
                """.formatted(sessionId, baseUrl, permissionPreset, task.path("workspaceId").asText(""),
                task.path("id").asText(""), task.path("status").asText(""),
                run.path("id").asText(""), run.path("status").asText(""));
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
        var changes = timeline.path("changes");
        if (!changes.isArray() || changes.isEmpty()) {
            return "No file changes recorded.";
        }
        var builder = new StringBuilder();
        for (var change : changes) {
            builder.append("### ").append(change.path("path").asText(""))
                    .append(" ").append(change.path("changeType").asText(""))
                    .append(" +").append(change.path("lineAdded").asInt(0))
                    .append(" -").append(change.path("lineDeleted").asInt(0)).append("\n");
            var diff = change.path("diff").asText("");
            if (!diff.isBlank()) {
                builder.append(diff.length() > 2000 ? diff.substring(0, 2000) + "\n...diff truncated..." : diff)
                        .append("\n");
            }
        }
        return builder.toString();
    }

    String timelineUpdate(JsonNode timeline, int eventOffset) {
        var builder = new StringBuilder();
        var events = timeline.path("events");
        if (events.isArray() && eventOffset >= events.size()) {
            return "";
        }
        var run = timeline.path("run");
        builder.append("Run ").append(shortId(run.path("id").asText("")))
                .append(" status: ").append(run.path("status").asText("")).append("\n");
        var nodes = timeline.path("workflowNodes");
        if (nodes.isArray() && !nodes.isEmpty()) {
            var last = nodes.get(nodes.size() - 1);
            builder.append("Node: ").append(last.path("nodeId").asText(""))
                    .append(" ").append(last.path("status").asText(""))
                    .append(" - ").append(oneLine(last.path("outputSummary").asText(""))).append("\n");
        }
        if (events.isArray()) {
            for (int i = eventOffset; i < events.size(); i++) {
                var event = events.get(i);
                builder.append("- ").append(event.path("eventType").asText(""))
                        .append(": ").append(oneLine(event.path("outputSummary").asText(""))).append("\n");
            }
        }
        return builder.toString();
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
