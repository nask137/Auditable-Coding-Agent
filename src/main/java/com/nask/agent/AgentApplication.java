package com.nask.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point for the Agent service.
 *
 * <p>The application exposes HTTP APIs, starts Flyway-managed persistence, and
 * wires the audited agent execution pipeline.</p>
 */
@SpringBootApplication
public class AgentApplication {

    /**
     * Boots the Spring application context.
     *
     * @param args command-line arguments passed to Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(AgentApplication.class, args);
    }

}
