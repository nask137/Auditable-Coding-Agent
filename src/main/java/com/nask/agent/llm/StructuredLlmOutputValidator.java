package com.nask.agent.llm;

import com.nask.agent.common.Domain;
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
    private static final Set<String> ACTION_TYPES = Set.of("LIST_FILES", "READ_FILE", "SEARCH_TEXT",
            "CREATE_DIRECTORY", "CREATE_FILE", "APPLY_PATCH", "GIT_STATUS", "GIT_DIFF");

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
            throw new LlmGatewayException("Model output failed bean validation: " + summarize(violations),
                    Domain.RuntimeFailureType.MODEL_OUTPUT_VALIDATION_FAILED, null);
        }
        switch (value) {
            case TaskUnderstanding understanding -> validateTaskUnderstanding(understanding);
            case PlanDraft plan -> validatePlanDraft(plan);
            case AgentDecision decision -> validateAgentDecision(decision);
            case ValidationDecision decision -> validateValidationDecision(decision);
            default -> {
            }
        }
        return value;
    }

    private void validateTaskUnderstanding(TaskUnderstanding understanding) {
        if (!Set.of("BUG_FIX", "TEST", "CODE_EDIT", "REVIEW", "OTHER").contains(understanding.taskType())) {
            throw new LlmGatewayException("Unsupported task type from model: " + understanding.taskType(),
                    Domain.RuntimeFailureType.MODEL_OUTPUT_VALIDATION_FAILED, null);
        }
    }

    private void validatePlanDraft(PlanDraft plan) {
        for (var item : plan.items()) {
            for (var path : item.relatedFiles()) {
                validateWorkspaceRelativePath(path, "relatedFiles");
            }
        }
    }

    private void validateAgentDecision(AgentDecision decision) {
        for (var action : decision.actions()) {
            if (!ACTION_TYPES.contains(action.type())) {
                throw new LlmGatewayException("Unsupported action type from model: " + action.type(),
                        Domain.RuntimeFailureType.UNSUPPORTED_TOOL_INTENT, null);
            }
            requireInputs(action.type(), action.input());
        }
    }

    private void requireInputs(String type, Map<String, Object> input) {
        switch (type) {
            case "LIST_FILES" -> {
                requireString(input, "path");
                requireNumber(input, "maxDepth");
                validateWorkspaceRelativePath(input.get("path").toString(), "path");
            }
            case "READ_FILE" -> {
                requireString(input, "path");
                validateWorkspaceRelativePath(input.get("path").toString(), "path");
            }
            case "SEARCH_TEXT" -> requireString(input, "query");
            case "CREATE_DIRECTORY" -> {
                requireString(input, "path");
                validateWorkspaceRelativePath(input.get("path").toString(), "path");
            }
            case "CREATE_FILE" -> {
                requireString(input, "path");
                requireString(input, "content");
                validateWorkspaceRelativePath(input.get("path").toString(), "path");
            }
            case "APPLY_PATCH" -> {
                requireString(input, "path");
                requireString(input, "oldText");
                requireStringValue(input, "newText");
                validateWorkspaceRelativePath(input.get("path").toString(), "path");
            }
            case "GIT_STATUS", "GIT_DIFF" -> {
                if (input.containsKey("workingDirectory")) {
                    requireString(input, "workingDirectory");
                    validateWorkspaceRelativePath(input.get("workingDirectory").toString(), "workingDirectory");
                }
            }
            default -> throw new LlmGatewayException("Unsupported action type from model: " + type,
                    Domain.RuntimeFailureType.UNSUPPORTED_TOOL_INTENT, null);
        }
    }

    private void validateValidationDecision(ValidationDecision decision) {
        if (decision.shouldValidate() && decision.executableAndArgs().isEmpty()) {
            throw new LlmGatewayException("Validation decision must include executableAndArgs when shouldValidate is true",
                    Domain.RuntimeFailureType.MODEL_OUTPUT_VALIDATION_FAILED, null);
        }
    }

    private void requireString(Map<String, Object> input, String key) {
        var value = input.get(key);
        if (!(value instanceof String string) || string.isBlank()) {
            throw new LlmGatewayException("Action input requires non-blank string field: " + key,
                    Domain.RuntimeFailureType.MODEL_OUTPUT_VALIDATION_FAILED, null);
        }
    }

    private void requireStringValue(Map<String, Object> input, String key) {
        if (!(input.get(key) instanceof String)) {
            throw new LlmGatewayException("Action input requires string field: " + key,
                    Domain.RuntimeFailureType.MODEL_OUTPUT_VALIDATION_FAILED, null);
        }
    }

    private void requireNumber(Map<String, Object> input, String key) {
        if (!(input.get(key) instanceof Number)) {
            throw new LlmGatewayException("Action input requires numeric field: " + key,
                    Domain.RuntimeFailureType.MODEL_OUTPUT_VALIDATION_FAILED, null);
        }
    }

    private void validateWorkspaceRelativePath(String path, String field) {
        var normalized = path == null ? "" : path.replace('\\', '/').strip();
        if (normalized.isBlank()) {
            throw new LlmGatewayException("Action input requires non-blank workspace-relative path field: " + field,
                    Domain.RuntimeFailureType.MODEL_OUTPUT_VALIDATION_FAILED, null);
        }
        if (normalized.startsWith("/") || normalized.matches("^[A-Za-z]:/.*")) {
            throw new LlmGatewayException("Model output path must be workspace-relative, not absolute: " + field,
                    Domain.RuntimeFailureType.MODEL_OUTPUT_VALIDATION_FAILED, null);
        }
        if (normalized.equals("task-reports") || normalized.startsWith("task-reports/")) {
            throw new LlmGatewayException("Model output used virtual task report path as workspace file: " + field,
                    Domain.RuntimeFailureType.MODEL_OUTPUT_VALIDATION_FAILED, null);
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
