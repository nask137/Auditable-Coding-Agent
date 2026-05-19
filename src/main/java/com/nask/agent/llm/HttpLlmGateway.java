package com.nask.agent.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nask.agent.audit.AuditEventDraft;
import com.nask.agent.audit.AuditService;
import com.nask.agent.common.Domain;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Real model gateway backed by an OpenAI-compatible HTTP chat-completions API.
 */
@Component
@ConditionalOnProperty(name = "agent.llm.provider", havingValue = "http")
public class HttpLlmGateway implements LlmGateway {
    private final LlmPromptFactory promptFactory;
    private final ChatCompletionClient client;
    private final ObjectMapper objectMapper;
    private final StructuredLlmOutputValidator outputValidator;
    private final AuditService auditService;

    /**
     * Creates the HTTP-backed gateway.
     */
    public HttpLlmGateway(LlmPromptFactory promptFactory, ChatCompletionClient client, ObjectMapper objectMapper,
                          StructuredLlmOutputValidator outputValidator, AuditService auditService) {
        this.promptFactory = promptFactory;
        this.client = client;
        this.objectMapper = objectMapper;
        this.outputValidator = outputValidator;
        this.auditService = auditService;
    }

    @Override
    public TaskUnderstanding understandTask(TaskContext context) {
        return invoke(context.taskId(), context.runId(), context.stepId(), "understand task",
                promptFactory.taskUnderstanding(context), TaskUnderstanding.class);
    }

    @Override
    public PlanDraft createPlan(PlanningContext context) {
        return invoke(context.taskId(), context.runId(), null, "create plan",
                promptFactory.planDraft(context), PlanDraft.class);
    }

    @Override
    public AgentDecision decideNextAction(ExecutionContext context) {
        var decision = invoke(context.taskId(), context.runId(), context.stepId(), "decide next action",
                promptFactory.agentDecision(context), AgentDecision.class);
        if (!context.currentItem().id().equals(decision.planItemId())) {
            throw new LlmGatewayException("Model decision planItemId does not match current plan item",
                    Domain.RuntimeFailureType.MODEL_DECISION_MISMATCH, "decide next action");
        }
        return decision;
    }

    @Override
    public PlanDraft replan(ExecutionContext context, String failureSummary) {
        return invoke(context.taskId(), context.runId(), context.stepId(), "replan after failure",
                promptFactory.replan(context, failureSummary), PlanDraft.class);
    }

    @Override
    public FinalReportDraft generateReport(ReportContext context) {
        return invoke(context.taskId(), context.runId(), null, "generate report",
                promptFactory.finalReport(context), FinalReportDraft.class);
    }

    private <T> T invoke(UUID taskId, UUID runId, UUID stepId, String decisionType, LlmPrompt prompt, Class<T> type) {
        auditStarted(taskId, runId, stepId, decisionType, prompt);
        ChatCompletionResult result = null;
        try {
            result = client.complete(prompt);
            var parsed = objectMapper.readValue(result.content(), type);
            var validated = outputValidator.validate(parsed);
            auditCompleted(taskId, runId, stepId, decisionType, prompt, result, validated);
            return validated;
        } catch (JsonProcessingException e) {
            auditFailed(taskId, runId, stepId, decisionType, prompt, result, "PARSE_FAILED", e.getOriginalMessage());
            throw new LlmGatewayException("Model output was not valid " + type.getSimpleName() + " JSON", e,
                    Domain.RuntimeFailureType.MODEL_OUTPUT_PARSE_FAILED, decisionType);
        } catch (LlmGatewayException e) {
            var failureType = e.failureType() == null
                    ? Domain.RuntimeFailureType.MODEL_OUTPUT_VALIDATION_FAILED : e.failureType();
            auditFailed(taskId, runId, stepId, decisionType, prompt, result, failureType.name(), e.getMessage());
            throw new LlmGatewayException(e.getMessage(), e, failureType, decisionType);
        } catch (RuntimeException e) {
            auditFailed(taskId, runId, stepId, decisionType, prompt, result, "MODEL_CALL_FAILED", e.getMessage());
            throw new LlmGatewayException("Model call failed: " + e.getMessage(), e,
                    Domain.RuntimeFailureType.MODEL_CALL_FAILED, decisionType);
        }
    }

    private void auditStarted(UUID taskId, UUID runId, UUID stepId, String decisionType, LlmPrompt prompt) {
        auditService.append(new AuditEventDraft(taskId, runId, stepId, null, Domain.AuditEventType.ModelCallStarted,
                Domain.AuditActor.AGENT, Domain.AuditLevel.INFO, decisionType, "Prompt sent to model",
                List.of(), null, null, null, null, null, Domain.RiskLevel.LOW, null,
                true, null, null, Map.of("promptVersion", prompt.version())));
    }

    private void auditCompleted(UUID taskId, UUID runId, UUID stepId, String decisionType, LlmPrompt prompt,
                                ChatCompletionResult result, Object parsed) {
        auditService.append(new AuditEventDraft(taskId, runId, stepId, null, Domain.AuditEventType.ModelCallCompleted,
                Domain.AuditActor.AGENT, Domain.AuditLevel.INFO, decisionType, outputSummary(parsed),
                List.of(), null, null, null, null, null, Domain.RiskLevel.LOW, null,
                true, null, null, metadata(prompt, result, true)));
    }

    private void auditFailed(UUID taskId, UUID runId, UUID stepId, String decisionType, LlmPrompt prompt,
                             ChatCompletionResult result, String errorCode, String message) {
        auditService.append(new AuditEventDraft(taskId, runId, stepId, null, Domain.AuditEventType.ModelCallFailed,
                Domain.AuditActor.AGENT, Domain.AuditLevel.ERROR, decisionType,
                message == null ? "Model call failed" : message,
                List.of(), null, null, null, null, null, Domain.RiskLevel.LOW, null,
                false, errorCode, message, metadata(prompt, result, false)));
    }

    private Map<String, Object> metadata(LlmPrompt prompt, ChatCompletionResult result, boolean parseSuccess) {
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("promptVersion", prompt.version());
        metadata.put("parseSuccess", parseSuccess);
        if (result != null) {
            metadata.put("model", result.model());
            metadata.put("finishReason", result.finishReason());
            metadata.put("rawOutputSha256", sha256(result.content()));
            putIfPresent(metadata, "promptTokens", result.promptTokens());
            putIfPresent(metadata, "completionTokens", result.completionTokens());
            putIfPresent(metadata, "totalTokens", result.totalTokens());
        }
        return metadata;
    }

    private void putIfPresent(Map<String, Object> metadata, String key, Object value) {
        if (value != null) {
            metadata.put(key, value);
        }
    }

    private String outputSummary(Object parsed) {
        return switch (parsed) {
            case TaskUnderstanding understanding -> understanding.summary();
            case PlanDraft plan -> "Created " + plan.items().size() + " plan items";
            case AgentDecision decision -> "Proposed " + decision.actions().size() + " tool actions";
            case FinalReportDraft report -> report.markdown().lines().findFirst().orElse("Report drafted");
            default -> parsed.getClass().getSimpleName();
        };
    }

    private String sha256(String content) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest is unavailable", e);
        }
    }
}
