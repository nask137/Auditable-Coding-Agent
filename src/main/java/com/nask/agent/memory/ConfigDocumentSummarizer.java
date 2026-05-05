package com.nask.agent.memory;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Produces compact indexable summaries for configuration-like files.
 */
@Component
public class ConfigDocumentSummarizer {
    private static final int MAX_LINES = 80;

    /**
     * Keeps meaningful non-comment lines from config content for indexing.
     */
    public String summarize(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        var summarized = Arrays.stream(content.replace("\r\n", "\n").replace('\r', '\n').split("\n"))
                .map(String::strip)
                .filter(line -> !line.isBlank())
                .filter(line -> !line.startsWith("#"))
                .filter(line -> !line.startsWith("//"))
                .limit(MAX_LINES)
                .collect(Collectors.joining("\n"));
        return summarized.isBlank() ? content.strip() : summarized;
    }
}
