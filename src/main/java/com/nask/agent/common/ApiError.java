package com.nask.agent.common;

import java.time.Instant;
import java.util.Map;

/**
 * Standard JSON error response returned by REST controllers.
 *
 * @param code stable machine-readable error code
 * @param message human-readable error message
 * @param details optional structured details for clients
 * @param timestamp time the response was created
 */
public record ApiError(String code, String message, Map<String, Object> details, Instant timestamp) {
    /**
     * Creates an error with no extra details and a fresh timestamp.
     */
    public static ApiError of(String code, String message) {
        return new ApiError(code, message, Map.of(), Instant.now());
    }
}
