package com.nask.agent.workflow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Parses workflow DSL JSON into a map representation stored as jsonb.
 */
@Component
public class WorkflowDefinitionParser {
    private final ObjectMapper objectMapper;
    private final TypeReference<Map<String, Object>> mapType = new TypeReference<>() {
    };

    public WorkflowDefinitionParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> parseJson(String json) {
        try {
            return objectMapper.readValue(json, mapType);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid workflow JSON", e);
        }
    }
}
