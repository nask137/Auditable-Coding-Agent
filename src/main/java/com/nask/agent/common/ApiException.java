package com.nask.agent.common;

import org.springframework.http.HttpStatus;

/**
 * Runtime exception that carries a stable HTTP status and API error code.
 */
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    /**
     * Creates an exception that can be converted directly into an API error.
     *
     * @param status HTTP status returned by the global exception handler
     * @param code stable machine-readable error code
     * @param message human-readable failure message
     */
    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    /**
     * HTTP status associated with this failure.
     */
    public HttpStatus status() {
        return status;
    }

    /**
     * Stable machine-readable error code.
     */
    public String code() {
        return code;
    }
}
