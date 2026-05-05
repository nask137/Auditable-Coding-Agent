package com.nask.agent.memory;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentChunkerTests {
    private final DocumentChunker chunker = new DocumentChunker();

    @Test
    void preservesLineRangesWhenChunking() {
        var content = "line1\nline2\nline3";

        var chunks = chunker.chunk(content);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst().lineStart()).isEqualTo(1);
        assertThat(chunks.getFirst().lineEnd()).isEqualTo(3);
        assertThat(chunks.getFirst().content()).isEqualTo(content);
        assertThat(chunks.getFirst().tokenCount()).isGreaterThan(0);
    }
}
