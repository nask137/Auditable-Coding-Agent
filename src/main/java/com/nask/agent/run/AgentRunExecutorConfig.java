package com.nask.agent.run;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Bounded worker pool for asynchronous agent runs.
 */
@Configuration
public class AgentRunExecutorConfig {
    @Bean
    TaskExecutor agentTaskExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("agent-run-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(16);
        executor.initialize();
        return executor;
    }
}
