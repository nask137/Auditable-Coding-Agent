package com.nask.agent.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nask.agent.audit.AuditService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class LlmGatewayConfigurationTests {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(LlmPromptFactory.class)
            .withBean(ChatCompletionClient.class,
                    () -> prompt -> new ChatCompletionResult("test-model", "{}", "test-id", null, null, null))
            .withBean(ObjectMapper.class)
            .withBean(StructuredLlmOutputValidator.class, () -> mock(StructuredLlmOutputValidator.class))
            .withBean(AuditService.class, () -> mock(AuditService.class))
            .withBean(HttpLlmGateway.class);

    @Test
    void defaultConfigurationCreatesHttpGateway() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(LlmGateway.class);
            assertThat(context).hasSingleBean(HttpLlmGateway.class);
        });
    }
}
