package com.nask.agent.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CliSessionStoreTests {
    Path tempDir;

    @BeforeEach
    void setup() throws IOException {
        tempDir = Path.of("target", "cli-session-test-" + UUID.randomUUID());
        Files.createDirectories(tempDir);
    }

    @AfterEach
    void cleanup() throws IOException {
        if (Files.isDirectory(tempDir)) {
            try (var stream = Files.list(tempDir)) {
                for (var path : stream.toList()) {
                    Files.deleteIfExists(path);
                }
            }
            Files.deleteIfExists(tempDir);
        }
    }

    @Test
    void appendsAndResumesLastSessionState() throws Exception {
        var store = new CliSessionStore(new ObjectMapper(), tempDir);
        var sessionId = store.sessionId();

        store.append("timeline", "workspace-1", "conversation-1", "task-1", "run-1", "RUNNING", "started");
        store.append("timeline", "workspace-1", "conversation-1", "task-1", "run-1", "COMPLETED", "done");

        assertThat(store.latestSessionId()).isEqualTo(sessionId);
        assertThat(store.lastState(sessionId))
                .containsEntry("workspaceId", "workspace-1")
                .containsEntry("conversationId", "conversation-1")
                .containsEntry("taskId", "task-1")
                .containsEntry("runId", "run-1")
                .containsEntry("status", "COMPLETED");
    }
}
