package com.nask.agent.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class JsonSupport {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public JsonSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to serialize JSON value", e);
        }
    }

    public List<String> toStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to parse JSON string list", e);
        }
    }

    public Map<String, Object> toMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, OBJECT_MAP);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to parse JSON object", e);
        }
    }
}
