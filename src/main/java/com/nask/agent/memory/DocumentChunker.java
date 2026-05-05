package com.nask.agent.memory;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits plain text into deterministic line-bounded chunks.
 */
@Component
public class DocumentChunker {
    private static final int MAX_CHUNK_CHARS = 4000;

    /**
     * Chunks text while preserving approximate source line ranges.
     */
    public List<DocumentChunk> chunk(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        var lines = content.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        var chunks = new ArrayList<DocumentChunk>();
        var buffer = new StringBuilder();
        var startLine = 1;
        for (var i = 0; i < lines.length; i++) {
            var line = lines[i];
            if (!buffer.isEmpty() && buffer.length() + line.length() + 1 > MAX_CHUNK_CHARS) {
                chunks.add(chunk(chunks.size(), buffer.toString().stripTrailing(), startLine, i));
                buffer.setLength(0);
                startLine = i + 1;
            }
            if (!buffer.isEmpty()) {
                buffer.append('\n');
            }
            buffer.append(line);
        }
        if (!buffer.isEmpty()) {
            chunks.add(chunk(chunks.size(), buffer.toString().stripTrailing(), startLine, lines.length));
        }
        return chunks;
    }

    private DocumentChunk chunk(int index, String content, int startLine, int endLine) {
        return new DocumentChunk(index, content, startLine, endLine, approximateTokens(content));
    }

    private int approximateTokens(String content) {
        return Math.max(1, (int) Math.ceil(content.length() / 4.0));
    }
}
