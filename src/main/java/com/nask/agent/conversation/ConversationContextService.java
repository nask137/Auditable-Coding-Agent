package com.nask.agent.conversation;

import com.nask.agent.common.AgentSettings;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Builds bounded conversation context for model prompts.
 */
@Service
public class ConversationContextService {
    private static final int MAX_CONTEXT_TASKS = 1000;
    private static final int COMPRESSED_REPORT_BYTES = 2048;
    private static final int COMPRESSED_PROMPT_BYTES = 512;

    private final ConversationService conversationService;
    private final AgentSettings settings;

    public ConversationContextService(ConversationService conversationService, AgentSettings settings) {
        this.conversationService = conversationService;
        this.settings = settings;
    }

    public ConversationContextWindow window(UUID conversationId, UUID currentTaskId) {
        var maxBytes = settings.conversationContextMaxBytes();
        if (conversationId == null) {
            return new ConversationContextWindow(List.of(), 0, 0, maxBytes,
                    false, 0, 0);
        }
        var fetchMaxBytes = (int) Math.min(Integer.MAX_VALUE, Math.max(1L, (long) maxBytes * 2L));
        var tasks = conversationService.previousTaskContext(conversationId, currentTaskId, MAX_CONTEXT_TASKS,
                fetchMaxBytes);
        var rawBytes = bytes(tasks);
        if (rawBytes <= maxBytes) {
            return new ConversationContextWindow(tasks, rawBytes, rawBytes, maxBytes, false,
                    tasks.size(), tasks.size());
        }
        var compressed = new ArrayList<ConversationTaskContext>();
        var used = 0;
        for (var task : tasks) {
            var candidate = compress(task);
            var candidateBytes = bytes(candidate);
            if (!compressed.isEmpty() && used + candidateBytes > maxBytes) {
                break;
            }
            if (candidateBytes > maxBytes) {
                candidate = fitSingleTask(candidate, maxBytes);
                candidateBytes = bytes(candidate);
            }
            compressed.add(candidate);
            used += candidateBytes;
            if (used >= maxBytes) {
                break;
            }
        }
        return new ConversationContextWindow(List.copyOf(compressed), used, rawBytes, maxBytes, true,
                compressed.size(), tasks.size());
    }

    private ConversationTaskContext compress(ConversationTaskContext task) {
        return new ConversationTaskContext(task.taskId(),
                truncateBytes(task.prompt(), COMPRESSED_PROMPT_BYTES),
                task.status(),
                summarizeReport(task.finalReport(), COMPRESSED_REPORT_BYTES),
                task.affectedFiles(),
                task.createdAt() == null ? Instant.EPOCH : task.createdAt());
    }

    private ConversationTaskContext fitSingleTask(ConversationTaskContext task, int maxBytes) {
        var budget = Math.max(256, maxBytes / 2);
        return new ConversationTaskContext(task.taskId(),
                truncateBytes(task.prompt(), Math.min(COMPRESSED_PROMPT_BYTES, budget / 4)),
                task.status(),
                summarizeReport(task.finalReport(), budget),
                task.affectedFiles(),
                task.createdAt());
    }

    private String summarizeReport(String value, int maxBytes) {
        if (value == null || value.isBlank()) {
            return "";
        }
        var normalized = value.replaceAll("\\s+", " ").trim();
        if (utf8Bytes(normalized) <= maxBytes) {
            return normalized;
        }
        var headBudget = Math.max(64, maxBytes * 2 / 3);
        var tailBudget = Math.max(64, maxBytes - headBudget - 64);
        return truncateBytes(normalized, headBudget) + " ... [compressed] ... "
                + tailBytes(normalized, tailBudget);
    }

    private int bytes(List<ConversationTaskContext> tasks) {
        return tasks.stream().mapToInt(this::bytes).sum();
    }

    private int bytes(ConversationTaskContext task) {
        return utf8Bytes(nullToBlank(task.prompt()))
                + utf8Bytes(nullToBlank(task.status()))
                + utf8Bytes(nullToBlank(task.finalReport()))
                + utf8Bytes(String.join(",", task.affectedFiles() == null ? List.of() : task.affectedFiles()))
                + 64;
    }

    private int utf8Bytes(String value) {
        return nullToBlank(value).getBytes(StandardCharsets.UTF_8).length;
    }

    private String truncateBytes(String value, int maxBytes) {
        var normalized = nullToBlank(value);
        if (utf8Bytes(normalized) <= maxBytes) {
            return normalized;
        }
        var builder = new StringBuilder();
        var used = 0;
        for (var i = 0; i < normalized.length(); i++) {
            var next = normalized.substring(i, i + 1);
            var size = utf8Bytes(next);
            if (used + size > Math.max(0, maxBytes - 3)) {
                break;
            }
            builder.append(next);
            used += size;
        }
        return builder.append("...").toString();
    }

    private String tailBytes(String value, int maxBytes) {
        var normalized = nullToBlank(value);
        if (utf8Bytes(normalized) <= maxBytes) {
            return normalized;
        }
        var builder = new StringBuilder();
        var used = 0;
        for (var i = normalized.length(); i > 0; i--) {
            var next = normalized.substring(i - 1, i);
            var size = utf8Bytes(next);
            if (used + size > maxBytes) {
                break;
            }
            builder.insert(0, next);
            used += size;
        }
        return builder.toString();
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }
}
