package com.nask.agent.llm;

import com.nask.agent.common.Domain;

/**
 * Raised when a model call, structured parsing, or output validation fails.
 */
public class LlmGatewayException extends RuntimeException {
    private final Domain.RuntimeFailureType failureType;
    private final String decisionType;

    public LlmGatewayException(String message) {
        super(message);
        this.failureType = Domain.RuntimeFailureType.MODEL_CALL_FAILED;
        this.decisionType = null;
    }

    public LlmGatewayException(String message, Throwable cause) {
        super(message, cause);
        this.failureType = Domain.RuntimeFailureType.MODEL_CALL_FAILED;
        this.decisionType = null;
    }

    public LlmGatewayException(String message, Domain.RuntimeFailureType failureType, String decisionType) {
        super(message);
        this.failureType = failureType;
        this.decisionType = decisionType;
    }

    public LlmGatewayException(String message, Throwable cause, Domain.RuntimeFailureType failureType, String decisionType) {
        super(message, cause);
        this.failureType = failureType;
        this.decisionType = decisionType;
    }

    public Domain.RuntimeFailureType failureType() {
        return failureType;
    }

    public String decisionType() {
        return decisionType;
    }
}
