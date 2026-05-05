package com.nask.agent.memory;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigDocumentSummarizerTests {
    private final ConfigDocumentSummarizer summarizer = new ConfigDocumentSummarizer();

    @Test
    void keepsMeaningfulConfigLinesAndDropsComments() {
        var summary = summarizer.summarize("""
                # comment
                spring.application.name=agent

                // generated comment
                agent.project-scan.max-files=2000
                """);

        assertThat(summary).contains("spring.application.name=agent");
        assertThat(summary).contains("agent.project-scan.max-files=2000");
        assertThat(summary).doesNotContain("# comment");
        assertThat(summary).doesNotContain("generated comment");
    }
}
