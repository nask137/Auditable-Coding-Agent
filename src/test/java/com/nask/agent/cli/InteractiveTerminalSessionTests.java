package com.nask.agent.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InteractiveTerminalSessionTests {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void findsRegisteredWorkspaceForCurrentRootPath() {
        var id = UUID.randomUUID().toString();
        var root = Path.of("target", "workspace-root").toAbsolutePath().normalize();
        var workspaces = mapper.createArrayNode()
                .add(mapper.createObjectNode()
                        .put("id", UUID.randomUUID().toString())
                        .put("rootPath", Path.of("target", "other-root").toAbsolutePath().normalize().toString()))
                .add(mapper.createObjectNode()
                        .put("id", id)
                        .put("rootPath", root.toString()));

        assertThat(InteractiveTerminalSession.findWorkspaceIdByRoot(workspaces, root)).isEqualTo(id);
    }

    @Test
    void returnsNullWhenCurrentRootPathIsNotRegistered() {
        var root = Path.of("target", "workspace-root").toAbsolutePath().normalize();
        var workspaces = mapper.createArrayNode()
                .add(mapper.createObjectNode()
                        .put("id", UUID.randomUUID().toString())
                        .put("rootPath", Path.of("target", "other-root").toAbsolutePath().normalize().toString()));

        assertThat(InteractiveTerminalSession.findWorkspaceIdByRoot(workspaces, root)).isNull();
    }

    @Test
    void routesReviewOnlyPromptsToReviewWorkflow() {
        assertThat(InteractiveTerminalSession.looksLikeReviewOnly("总结一下项目是干嘛的，有没有明显的bug"))
                .isTrue();
        assertThat(InteractiveTerminalSession.looksLikeReviewOnly("fix the bug in workspace registration"))
                .isFalse();
        assertThat(InteractiveTerminalSession.looksLikeReviewOnly("修复 workspace 注册的问题"))
                .isFalse();
    }
}
