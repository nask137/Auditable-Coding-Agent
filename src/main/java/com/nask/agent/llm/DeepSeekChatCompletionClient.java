package com.nask.agent.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

/**
 * OpenAI-compatible DeepSeek chat-completions client using JSON output mode.
 */
@Component
@ConditionalOnProperty(name = "agent.llm.provider", havingValue = "http")
public class DeepSeekChatCompletionClient implements ChatCompletionClient {
    private final LlmSettings settings;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    /**
     * Creates the HTTP client.
     */
    public DeepSeekChatCompletionClient(LlmSettings settings, ObjectMapper objectMapper) {
        this.settings = settings;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(settings.timeout()).build();
    }

    @Override
    public ChatCompletionResult complete(LlmPrompt prompt) {
        if (settings.apiKey().isBlank()) {
            throw new LlmGatewayException("agent.llm.api-key must be configured when agent.llm.provider=http");
        }
        try {
            var body = objectMapper.writeValueAsString(new ChatRequest(
                    settings.model(),
                    List.of(new Message("system", prompt.system()), new Message("user", prompt.user())),
                    Map.of("type", "json_object"),
                    settings.maxTokens(),
                    settings.thinkingEnabled() ? null : settings.temperature(),
                    thinkingMode(),
                    settings.thinkingEnabled() ? settings.reasoningEffort() : null,
                    false));
            var request = HttpRequest.newBuilder(chatCompletionsUri())
                    .timeout(settings.timeout())
                    .header("Authorization", "Bearer " + settings.apiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new LlmGatewayException("Model API returned HTTP " + response.statusCode() + ": " + response.body());
            }
            var parsed = objectMapper.readValue(response.body(), ChatResponse.class);
            if (parsed.choices() == null || parsed.choices().isEmpty() || parsed.choices().getFirst().message() == null) {
                throw new LlmGatewayException("Model API response did not include a message choice");
            }
            var choice = parsed.choices().getFirst();
            var content = choice.message().content();
            if (content == null || content.isBlank()) {
                throw new LlmGatewayException("Model API returned empty JSON content");
            }
            var usage = parsed.usage();
            return new ChatCompletionResult(
                    parsed.model() == null ? settings.model() : parsed.model(),
                    content,
                    choice.finishReason(),
                    usage == null ? null : usage.promptTokens(),
                    usage == null ? null : usage.completionTokens(),
                    usage == null ? null : usage.totalTokens());
        } catch (IOException e) {
            throw new LlmGatewayException("Failed to call model API", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmGatewayException("Interrupted while calling model API", e);
        }
    }

    private URI chatCompletionsUri() {
        var base = settings.baseUrl().toString();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return URI.create(base + "/chat/completions");
    }

    private Map<String, String> thinkingMode() {
        return Map.of("type", settings.thinkingEnabled() ? "enabled" : "disabled");
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record ChatRequest(
            String model,
            List<Message> messages,
            @JsonProperty("response_format") Map<String, String> responseFormat,
            @JsonProperty("max_tokens") int maxTokens,
            Double temperature,
            Map<String, String> thinking,
            @JsonProperty("reasoning_effort") String reasoningEffort,
            boolean stream) {
    }

    private record Message(String role, String content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChatResponse(String model, List<Choice> choices, Usage usage) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Choice(
            ResponseMessage message,
            @JsonProperty("finish_reason") String finishReason) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ResponseMessage(String role, String content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Usage(
            @JsonProperty("prompt_tokens") Integer promptTokens,
            @JsonProperty("completion_tokens") Integer completionTokens,
            @JsonProperty("total_tokens") Integer totalTokens) {
    }
}
