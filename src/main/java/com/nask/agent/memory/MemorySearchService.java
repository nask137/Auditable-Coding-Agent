package com.nask.agent.memory;

import com.nask.agent.common.Domain;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Deterministic keyword search across project memory, indexed documents, and symbols.
 */
@Service
public class MemorySearchService {
    private static final Map<String, Integer> DOCUMENT_PRIORITIES = Map.of(
            Domain.IndexedDocumentType.README.name(), 18,
            Domain.IndexedDocumentType.DOCS.name(), 16,
            Domain.IndexedDocumentType.TASK_REPORT.name(), 14,
            Domain.IndexedDocumentType.BUILD_FILE.name(), 12,
            Domain.IndexedDocumentType.CONFIG.name(), 10,
            Domain.IndexedDocumentType.MIGRATION.name(), 8);

    private final ProjectMemoryRepository repository;

    public MemorySearchService(ProjectMemoryRepository repository) {
        this.repository = repository;
    }

    /**
     * Returns ranked and deduplicated context hits for a query.
     */
    public List<MemorySearchResult> search(MemoryQuery query) {
        var tokens = tokens(query.queryText());
        var results = new ArrayList<MemorySearchResult>();
        addDocuments(query, tokens, results);
        addSymbols(query, tokens, results);
        addMemoryItems(query, tokens, results);
        return results.stream()
                .filter(result -> result.score() > 0 || tokens.isEmpty())
                .sorted(Comparator.comparingDouble(MemorySearchResult::score).reversed()
                        .thenComparing(MemorySearchResult::title))
                .collect(java.util.stream.Collectors.toMap(this::dedupeKey, result -> result,
                        (left, right) -> left.score() >= right.score() ? left : right, LinkedHashMap::new))
                .values()
                .stream()
                .limit(query.limit())
                .toList();
    }

    private void addDocuments(MemoryQuery query, List<String> tokens, List<MemorySearchResult> results) {
        for (var document : repository.findIndexedDocumentsByWorkspace(query.workspaceId())) {
            if (!matchesFilter(query.documentTypes(), document.documentType())) {
                continue;
            }
            var score = DOCUMENT_PRIORITIES.getOrDefault(document.documentType(), 5)
                    + scoreText(tokens, document.title(), 6)
                    + scoreText(tokens, document.path(), 5)
                    + scoreText(tokens, document.content(), 3)
                    + recencyBoost(document.createdAt());
            results.add(new MemorySearchResult("DOCUMENT", score, document.title(),
                    snippet(document.content(), tokens), new SourceReference("INDEXED_DOCUMENT", document.id(),
                    document.path(), document.lineStart(), document.lineEnd(), null, document.scanRunId(),
                    null, null), Map.of("documentType", document.documentType(), "chunkIndex",
                    document.chunkIndex(), "tokenCount", document.tokenCount())));
        }
    }

    private void addSymbols(MemoryQuery query, List<String> tokens, List<MemorySearchResult> results) {
        for (var symbol : repository.searchCodeSymbols(query.workspaceId(), null, null)) {
            if (!matchesFilter(query.symbolTypes(), symbol.symbolType())) {
                continue;
            }
            var score = 10 + scoreSymbol(tokens, symbol) + recencyBoost(symbol.createdAt());
            results.add(new MemorySearchResult("SYMBOL", score,
                    symbol.symbolType() + " " + symbol.symbolName(), symbol.signature(),
                    new SourceReference("CODE_SYMBOL", symbol.id(), symbol.path(), symbol.lineStart(),
                            symbol.lineEnd(), symbol.symbolName(), symbol.scanRunId(), null, null),
                    Map.of("symbolType", symbol.symbolType(), "containerName",
                            symbol.containerName() == null ? "" : symbol.containerName(),
                            "language", symbol.language())));
        }
    }

    private void addMemoryItems(MemoryQuery query, List<String> tokens, List<MemorySearchResult> results) {
        for (var item : repository.findProjectMemoryItemsByWorkspace(query.workspaceId())) {
            if (!matchesFilter(query.memoryTypes(), item.memoryType())) {
                continue;
            }
            var score = ("APPROVED".equals(item.status()) ? 30 : 10)
                    + item.confidence() * 10
                    + scoreText(tokens, item.title(), 8)
                    + scoreText(tokens, item.content(), 4)
                    + scoreText(tokens, item.scope(), 3)
                    + recencyBoost(item.createdAt());
            results.add(new MemorySearchResult("MEMORY", score, item.title(), snippet(item.content(), tokens),
                    new SourceReference("PROJECT_MEMORY_ITEM", item.id(), item.sourcePath(),
                            item.sourceLineStart(), item.sourceLineEnd(), null, null, null, null),
                    Map.of("memoryType", item.memoryType(), "status", item.status(),
                            "confidence", item.confidence())));
        }
    }

    private List<String> tokens(String queryText) {
        if (queryText == null || queryText.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(queryText.toLowerCase(Locale.ROOT).split("[^a-z0-9_./-]+"))
                .filter(token -> token.length() > 1)
                .distinct()
                .toList();
    }

    private double scoreSymbol(List<String> tokens, CodeSymbol symbol) {
        var score = scoreText(tokens, symbol.path(), 4) + scoreText(tokens, symbol.signature(), 2);
        for (var token : tokens) {
            var name = symbol.symbolName().toLowerCase(Locale.ROOT);
            if (name.equals(token)) {
                score += 45;
            } else if (name.contains(token)) {
                score += 25;
            }
            if (symbol.containerName() != null && symbol.containerName().toLowerCase(Locale.ROOT).contains(token)) {
                score += 12;
            }
        }
        return score;
    }

    private double scoreText(List<String> tokens, String text, int weight) {
        if (tokens.isEmpty() || text == null || text.isBlank()) {
            return tokens.isEmpty() ? 1 : 0;
        }
        var normalized = text.toLowerCase(Locale.ROOT);
        var score = 0;
        for (var token : tokens) {
            var index = normalized.indexOf(token);
            while (index >= 0) {
                score += weight;
                index = normalized.indexOf(token, index + token.length());
            }
        }
        return score;
    }

    private double recencyBoost(Instant createdAt) {
        if (createdAt == null) {
            return 0;
        }
        var ageDays = Math.max(0, ChronoUnit.DAYS.between(createdAt, Instant.now()));
        return Math.max(0, 5 - Math.min(5, ageDays));
    }

    private String snippet(String content, List<String> tokens) {
        if (content == null || content.isBlank()) {
            return "";
        }
        var compact = content.replaceAll("\\s+", " ").strip();
        var lower = compact.toLowerCase(Locale.ROOT);
        var firstHit = tokens.stream()
                .mapToInt(lower::indexOf)
                .filter(index -> index >= 0)
                .min()
                .orElse(0);
        var start = Math.max(0, firstHit - 80);
        var end = Math.min(compact.length(), start + 320);
        return compact.substring(start, end);
    }

    private boolean matchesFilter(List<String> filters, String value) {
        return filters.isEmpty() || filters.stream().anyMatch(filter -> filter.equalsIgnoreCase(value));
    }

    private String dedupeKey(MemorySearchResult result) {
        var source = result.source();
        return source.sourceType() + ":" + source.sourceId() + ":" + source.path()
                + ":" + source.lineStart() + ":" + source.lineEnd();
    }
}
