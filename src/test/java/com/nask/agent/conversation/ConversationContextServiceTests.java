package com.nask.agent.conversation;

import com.nask.agent.common.AgentSettings;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConversationContextServiceTests {
    private final ConversationService conversationService = mock(ConversationService.class);

    @Test
    void keepsFullContextWithinBudget() {
        var conversationId = UUID.randomUUID();
        var currentTaskId = UUID.randomUUID();
        var task = task("compile project", "short report", List.of("pom.xml"));
        when(conversationService.previousTaskContext(eq(conversationId), eq(currentTaskId), anyInt(), anyInt()))
                .thenReturn(List.of(task));
        var service = new ConversationContextService(conversationService, settings(512000));

        var window = service.window(conversationId, currentTaskId);

        assertThat(window.compressed()).isFalse();
        assertThat(window.tasks()).containsExactly(task);
        assertThat(window.usedBytes()).isLessThan(window.maxBytes());
    }

    @Test
    void compressesContextWhenBudgetIsExceeded() {
        var conversationId = UUID.randomUUID();
        var currentTaskId = UUID.randomUUID();
        var largeReport = "Compilation failed in src/main/java/cdu/wangnan/App.java because log is missing. "
                + "DETAIL ".repeat(500);
        when(conversationService.previousTaskContext(eq(conversationId), eq(currentTaskId), anyInt(), anyInt()))
                .thenReturn(List.of(task("编译一下", largeReport, List.of())));
        var service = new ConversationContextService(conversationService, settings(1200));

        var window = service.window(conversationId, currentTaskId);

        assertThat(window.compressed()).isTrue();
        assertThat(window.rawBytes()).isGreaterThan(window.maxBytes());
        assertThat(window.usedBytes()).isLessThanOrEqualTo(window.maxBytes());
        assertThat(window.tasks()).hasSize(1);
        assertThat(window.tasks().getFirst().finalReport())
                .contains("Compilation failed")
                .contains("[compressed]");
    }

    private ConversationTaskContext task(String prompt, String report, List<String> files) {
        return new ConversationTaskContext(UUID.randomUUID(), prompt, "FAILED", report, files, Instant.now());
    }

    private AgentSettings settings(int conversationContextMaxBytes) {
        return new AgentSettings(10, 20, 5, 300, 3, 2, 2, 3, 120, 200000,
                2000, 262144, 10485760, conversationContextMaxBytes);
    }
}
