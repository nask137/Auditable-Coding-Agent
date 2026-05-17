package com.nask.agent.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;
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
    void initializesWorkspaceFromRegisteredCurrentDirectory() throws Exception {
        var id = UUID.randomUUID().toString();
        var cli = new FakeAgentCli(mapper.createArrayNode()
                .add(mapper.createObjectNode()
                        .put("id", id)
                        .put("rootPath", Path.of("").toAbsolutePath().normalize().toString()))
                .toString());
        var session = new InteractiveTerminalSession(cli, false);

        assertThat(session.initializeWorkspace()).isEqualTo(id);
        assertThat(cli.postCalls).isZero();
    }

    @Test
    void initializesWorkspaceByRegisteringCurrentDirectoryWhenMissing() throws Exception {
        var id = UUID.randomUUID().toString();
        var cli = new FakeAgentCli(mapper.createArrayNode().toString(), id);
        var session = new InteractiveTerminalSession(cli, false);

        assertThat(session.initializeWorkspace()).isEqualTo(id);
        assertThat(cli.postCalls).isEqualTo(1);
        assertThat(cli.postBody).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        var request = (Map<String, Object>) cli.postBody;
        assertThat(request)
                .containsEntry("rootPath", Path.of("").toAbsolutePath().normalize().toString())
                .containsEntry("trusted", true);
    }

    @Test
    void routesReviewOnlyPromptsToReviewWorkflow() {
        assertThat(InteractiveTerminalSession.looksLikeReviewOnly("总结一下项目是干嘛的，有没有明显的bug"))
                .isTrue();
        assertThat(InteractiveTerminalSession.looksLikeReviewOnly("说一下项目的特点")).isTrue();
        assertThat(InteractiveTerminalSession.looksLikeReviewOnly("查看一下项目的readme文件呢")).isTrue();
        assertThat(InteractiveTerminalSession.looksLikeReviewOnly("为什么之前说这是Flutter项目")).isTrue();
        assertThat(InteractiveTerminalSession.looksLikeReviewOnly("fix the bug in workspace registration"))
                .isFalse();
        assertThat(InteractiveTerminalSession.looksLikeReviewOnly("修复 workspace 注册的问题"))
                .isFalse();
    }

    private static class FakeAgentCli extends AgentCli {
        private final String workspaces;
        private final String createdId;
        private Object postBody;
        private int postCalls;

        FakeAgentCli(String workspaces) {
            this(workspaces, UUID.randomUUID().toString());
        }

        FakeAgentCli(String workspaces, String createdId) {
            this.workspaces = workspaces;
            this.createdId = createdId;
        }

        @Override
        String get(String path) {
            assertThat(path).isEqualTo("/api/workspaces");
            return workspaces;
        }

        @Override
        String post(String path, Object body) {
            assertThat(path).isEqualTo("/api/workspaces");
            postCalls++;
            postBody = body;
            return "{\"id\":\"" + createdId + "\"}";
        }
    }
}
