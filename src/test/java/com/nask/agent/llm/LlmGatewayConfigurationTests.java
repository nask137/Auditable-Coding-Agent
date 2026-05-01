package com.nask.agent.llm;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class LlmGatewayConfigurationTests {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(StubLlmGateway.class);

    @Test
    void defaultProviderCreatesStubGateway() {
        contextRunner
                .withPropertyValues("agent.llm.provider=stub")
                .run(context -> assertThat(context).hasSingleBean(LlmGateway.class));
    }
}
