package com.nask.agent.llm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;

/**
 * Configuration for the model API used behind {@link LlmGateway}.
 */
@Component
public class LlmSettings {
    private final URI baseUrl;
    private final String apiKey;
    private final String model;
    private final Duration timeout;
    private final int maxTokens;
    private final double temperature;
    private final boolean thinkingEnabled;
    private final String reasoningEffort;

    /**
     * Creates immutable model settings from application properties.
     */
    public LlmSettings(
            @Value("${agent.llm.base-url:https://api.deepseek.com}") String baseUrl,
            @Value("${agent.llm.api-key:}") String apiKey,
            @Value("${agent.llm.model:deepseek-v4-pro}") String model,
            @Value("${agent.llm.timeout-seconds:60}") int timeoutSeconds,
            @Value("${agent.llm.max-tokens:4096}") int maxTokens,
            @Value("${agent.llm.temperature:0.1}") double temperature,
            @Value("${agent.llm.thinking.enabled:true}") boolean thinkingEnabled,
            @Value("${agent.llm.reasoning-effort:high}") String reasoningEffort) {
        this.baseUrl = URI.create(baseUrl);
        this.apiKey = apiKey;
        this.model = model;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.maxTokens = maxTokens;
        this.temperature = temperature;
        this.thinkingEnabled = thinkingEnabled;
        this.reasoningEffort = reasoningEffort;
    }

    public URI baseUrl() {
        return baseUrl;
    }

    public String apiKey() {
        return apiKey;
    }

    public String model() {
        return model;
    }

    public Duration timeout() {
        return timeout;
    }

    public int maxTokens() {
        return maxTokens;
    }

    public double temperature() {
        return temperature;
    }

    public boolean thinkingEnabled() {
        return thinkingEnabled;
    }

    public String reasoningEffort() {
        return reasoningEffort;
    }
}
