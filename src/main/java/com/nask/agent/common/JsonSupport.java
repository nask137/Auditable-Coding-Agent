package com.nask.agent.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Typed JSON serialization helpers for repository JSONB columns.
 */
@Component
public class JsonSupport {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    /**
     * Creates a support wrapper around the application ObjectMapper.
     */
    public JsonSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Serializes a value for JSONB storage. Null values are normalized to an
     * empty object to avoid writing SQL nulls where callers expect JSON.
     */
    public String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to serialize JSON value", e);
        }
    }

    /**
     * Parses a JSON array of strings, returning an empty list for blank values.
     */
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

    /**
     * Parses a JSON object into a generic map, returning an empty map for blank
     * values.
     */
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
