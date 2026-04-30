package com.nask.agent.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the shared Jackson mapper used by repositories and HTTP clients.
 */
@Configuration
public class JacksonConfig {
    /**
     * Registers Java time and other discovered modules when no mapper is
     * already supplied by Spring Boot.
     */
    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}
