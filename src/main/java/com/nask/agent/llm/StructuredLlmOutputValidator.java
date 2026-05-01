package com.nask.agent.llm;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Applies bean validation and runtime-specific business rules to model JSON.
 */
@Component
public class StructuredLlmOutputValidator {
    private static final Set<String> ACTION_TYPES = Set.of("LIST_FILES", "READ_FILE", "SEARCH_TEXT", "CREATE_FILE");

    private final Validator validator;

    /**
     * Creates the validator.
     */
    public StructuredLlmOutputValidator(Validator validator) {
        this.validator = validator;
    }

    /**
     * Validates common bean constraints and type-specific business rules.
     */
    public <T> T validate(T value) {
        var violations = validator.validate(value);
        if (!violations.isEmpty()) {
            throw new LlmGatewayException("Model output failed bean validation: " + summarize(violations));
        }
        switch (value) {
            case TaskUnderstanding understanding -> validateTaskUnderstanding(understanding);
            case AgentDecision decision -> validateAgentDecision(decision);
            case ValidationDecision decision -> validateValidationDecision(decision);
            default -> {
            }
        }
        return value;
    }

    private void validateTaskUnderstanding(TaskUnderstanding understanding) {
        if (!Set.of("BUG_FIX", "TEST", "CODE_EDIT", "REVIEW", "OTHER").contains(understanding.taskType())) {
            throw new LlmGatewayException("Unsupported task type from model: " + understanding.taskType());
        }
    }

    private void validateAgentDecision(AgentDecision decision) {
        for (var action : decision.actions()) {
            if (!ACTION_TYPES.contains(action.type())) {
                throw new LlmGatewayException("Unsupported action type from model: " + action.type());
            }
            requireInputs(action.type(), action.input());
        }
    }

    private void requireInputs(String type, Map<String, Object> input) {
        switch (type) {
            case "LIST_FILES" -> {
                requireString(input, "path");
                requireNumber(input, "maxDepth");
            }
            case "READ_FILE" -> requireString(input, "path");
            case "SEARCH_TEXT" -> requireString(input, "query");
            case "CREATE_FILE" -> {
                requireString(input, "path");
                requireString(input, "content");
            }
            default -> throw new LlmGatewayException("Unsupported action type from model: " + type);
        }
    }

    private void validateValidationDecision(ValidationDecision decision) {
        if (decision.shouldValidate() && decision.executableAndArgs().isEmpty()) {
            throw new LlmGatewayException("Validation decision must include executableAndArgs when shouldValidate is true");
        }
    }

    private void requireString(Map<String, Object> input, String key) {
        var value = input.get(key);
        if (!(value instanceof String string) || string.isBlank()) {
            throw new LlmGatewayException("Action input requires non-blank string field: " + key);
        }
    }

    private void requireNumber(Map<String, Object> input, String key) {
        if (!(input.get(key) instanceof Number)) {
            throw new LlmGatewayException("Action input requires numeric field: " + key);
        }
    }

    private String summarize(Set<? extends ConstraintViolation<?>> violations) {
        return violations.stream()
                .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                .sorted()
                .toList()
                .toString();
    }
}
