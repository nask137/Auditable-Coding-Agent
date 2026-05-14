package com.nask.agent.cli;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * JSONL transcript writer for CLI sessions.
 */
class CliSessionStore {
    private final ObjectMapper mapper;
    private final Path sessionsDir;
    private String sessionId;

    CliSessionStore(ObjectMapper mapper, Path sessionsDir) {
        this.mapper = mapper;
        this.sessionsDir = sessionsDir;
        this.sessionId = UUID.randomUUID().toString();
    }

    String sessionId() {
        return sessionId;
    }

    void use(String id) {
        this.sessionId = id;
    }

    String latestSessionId() throws IOException {
        if (!Files.isDirectory(sessionsDir)) {
            return null;
        }
        try (var stream = Files.list(sessionsDir)) {
            return stream.filter(path -> path.getFileName().toString().endsWith(".jsonl"))
                    .max(Comparator.comparing(this::modified))
                    .map(path -> path.getFileName().toString().replaceFirst("\\.jsonl$", ""))
                    .orElse(null);
        }
    }

    Map<String, String> lastState(String id) throws IOException {
        var path = sessionsDir.resolve(id + ".jsonl");
        if (!Files.exists(path)) {
            return Map.of();
        }
        String last = null;
        for (var line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            if (!line.isBlank()) {
                last = line;
            }
        }
        if (last == null) {
            return Map.of();
        }
        var node = mapper.readTree(last);
        var state = new LinkedHashMap<String, String>();
        for (var field : new String[]{"workspaceId", "conversationId", "taskId", "runId", "status"}) {
            var value = node.get(field);
            state.put(field, value == null || value.isNull() ? "" : value.asText());
        }
        return state;
    }

    void append(String type, String workspaceId, String conversationId, String taskId, String runId, String status,
                String text)
            throws IOException {
        Files.createDirectories(sessionsDir);
        var event = new LinkedHashMap<String, Object>();
        event.put("timestamp", Instant.now().toString());
        event.put("type", type);
        event.put("workspaceId", workspaceId == null ? "" : workspaceId);
        event.put("conversationId", conversationId == null ? "" : conversationId);
        event.put("taskId", taskId == null ? "" : taskId);
        event.put("runId", runId == null ? "" : runId);
        event.put("status", status == null ? "" : status);
        event.put("text", text == null ? "" : text);
        Files.writeString(sessionsDir.resolve(sessionId + ".jsonl"),
                mapper.writeValueAsString(event) + System.lineSeparator(), StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
    }

    private java.nio.file.attribute.FileTime modified(Path path) {
        try {
            return Files.getLastModifiedTime(path);
        } catch (IOException e) {
            return java.nio.file.attribute.FileTime.fromMillis(0);
        }
    }
}
