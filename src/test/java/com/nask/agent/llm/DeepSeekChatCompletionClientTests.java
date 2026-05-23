package com.nask.agent.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class DeepSeekChatCompletionClientTests {
    private HttpServer server;
    private String capturedBody;
    private String capturedAuthorization;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsJsonModeChatCompletionRequestAndParsesUsage() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat/completions", exchange -> {
            capturedAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
            capturedBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            var response = """
                    {
                      "model": "deepseek-v4-pro",
                      "choices": [
                        {
                          "finish_reason": "stop",
                          "message": {
                            "role": "assistant",
                            "reasoning_content": "I should return structured JSON only.",
                            "content": "{\\"summary\\":\\"ok\\",\\"taskType\\":\\"CODE_EDIT\\",\\"constraints\\":[],\\"initialSearchHints\\":[]}"
                          }
                        }
                      ],
                      "usage": {
                        "prompt_tokens": 11,
                        "completion_tokens": 7,
                        "total_tokens": 18
                      }
                    }
                    """;
            exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
            exchange.getResponseBody().write(response.getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });
        server.start();
        var settings = new LlmSettings("http://localhost:" + server.getAddress().getPort(),
                "test-key", "deepseek-v4-pro", 10, 1024, 0.1, true, "high");
        var client = new DeepSeekChatCompletionClient(settings, new ObjectMapper());

        var result = client.complete(new LlmPrompt("task-understanding-v1", "json system", "json user"));

        assertThat(capturedAuthorization).isEqualTo("Bearer test-key");
        assertThat(capturedBody).contains("\"model\":\"deepseek-v4-pro\"");
        assertThat(capturedBody).contains("\"response_format\":{\"type\":\"json_object\"}");
        assertThat(capturedBody).contains("\"thinking\":{\"type\":\"enabled\"}");
        assertThat(capturedBody).contains("\"reasoning_effort\":\"high\"");
        assertThat(capturedBody).doesNotContain("\"temperature\"");
        assertThat(result.model()).isEqualTo("deepseek-v4-pro");
        assertThat(result.finishReason()).isEqualTo("stop");
        assertThat(result.totalTokens()).isEqualTo(18);
        assertThat(result.content()).contains("\"taskType\":\"CODE_EDIT\"");
    }

    @Test
    void sendsDisabledThinkingModeWithTemperature() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat/completions", exchange -> {
            capturedBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            var response = """
                    {
                      "model": "deepseek-v4-pro",
                      "choices": [
                        {
                          "finish_reason": "stop",
                          "message": {
                            "role": "assistant",
                            "reasoning_content": "Thinking field should not break response parsing.",
                            "content": "{\\"summary\\":\\"ok\\",\\"taskType\\":\\"CODE_EDIT\\",\\"constraints\\":[],\\"initialSearchHints\\":[]}"
                          }
                        }
                      ]
                    }
                    """;
            exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
            exchange.getResponseBody().write(response.getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });
        server.start();
        var settings = new LlmSettings("http://localhost:" + server.getAddress().getPort(),
                "test-key", "deepseek-v4-pro", 10, 1024, 0.1, false, "high");
        var client = new DeepSeekChatCompletionClient(settings, new ObjectMapper());

        client.complete(new LlmPrompt("task-understanding-v1", "json system", "json user"));

        assertThat(capturedBody).contains("\"thinking\":{\"type\":\"disabled\"}");
        assertThat(capturedBody).doesNotContain("\"reasoning_effort\"");
        assertThat(capturedBody).contains("\"temperature\":0.1");
    }
}
