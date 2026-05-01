package com.nask.agent.llm;

/**
 * Raised when a model call, structured parsing, or output validation fails.
 */
public class LlmGatewayException extends RuntimeException {
    public LlmGatewayException(String message) {
        super(message);
    }

    public LlmGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
