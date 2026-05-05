package com.nask.agent.memory;

import com.nask.agent.common.Domain;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MemorySearchServiceTests {
    private final UUID workspaceId = UUID.randomUUID();
    private final ProjectMemoryRepository repository = mock(ProjectMemoryRepository.class);
    private final MemorySearchService service = new MemorySearchService(repository);

    @Test
    void ranksExactSymbolAndApprovedMemoryAheadOfWeakDocumentHits() {
        when(repository.findIndexedDocumentsByWorkspace(workspaceId)).thenReturn(List.of(
                document("docs/testing.md", "DOCS", "Testing", "Run mvn test before submitting."),
                document("docs/notes.md", "DOCS", "Notes", "General implementation notes.")));
        when(repository.searchCodeSymbols(workspaceId, null, null)).thenReturn(List.of(
                symbol("src/main/java/App.java", "CLASS", "App"),
                symbol("src/main/java/Other.java", "METHOD", "helper")));
        when(repository.findProjectMemoryItemsByWorkspace(workspaceId)).thenReturn(List.of(
                memory("COMMON_COMMAND", "APPROVED", "Maven test", "Use mvn test for validation."),
                memory("TASK_LESSON", "PROPOSED", "Old lesson", "Prefer small patches.")));

        var results = service.search(new MemoryQuery(workspaceId, "App mvn test", null, null, null,
                List.of(), List.of(), List.of(), 10));

        assertThat(results).extracting(MemorySearchResult::resultType)
                .contains("SYMBOL", "MEMORY", "DOCUMENT");
        assertThat(results.getFirst().title()).contains("App");
        assertThat(results).extracting(result -> result.source().sourceType())
                .contains("CODE_SYMBOL", "PROJECT_MEMORY_ITEM", "INDEXED_DOCUMENT");
    }

    @Test
    void appliesDocumentSymbolAndMemoryTypeFilters() {
        when(repository.findIndexedDocumentsByWorkspace(workspaceId)).thenReturn(List.of(
                document("README.md", "README", "README", "mvn test"),
                document("task-reports/task/report.md", "TASK_REPORT", "Task report", "mvn test")));
        when(repository.searchCodeSymbols(workspaceId, null, null)).thenReturn(List.of(
                symbol("src/main/java/App.java", "CLASS", "App"),
                symbol("src/main/java/App.java", "METHOD", "testCommand")));
        when(repository.findProjectMemoryItemsByWorkspace(workspaceId)).thenReturn(List.of(
                memory("COMMON_COMMAND", "APPROVED", "Maven test", "mvn test"),
                memory("PROJECT_RULE", "APPROVED", "Rule", "keep changes small")));

        var results = service.search(new MemoryQuery(workspaceId, "test", null, null, null,
                List.of("COMMON_COMMAND"), List.of("TASK_REPORT"), List.of("METHOD"), 10));

        assertThat(results).allSatisfy(result -> {
            if ("DOCUMENT".equals(result.resultType())) {
                assertThat(result.metadata().get("documentType")).isEqualTo("TASK_REPORT");
            }
            if ("SYMBOL".equals(result.resultType())) {
                assertThat(result.metadata().get("symbolType")).isEqualTo("METHOD");
            }
            if ("MEMORY".equals(result.resultType())) {
                assertThat(result.metadata().get("memoryType")).isEqualTo("COMMON_COMMAND");
            }
        });
    }

    private IndexedDocument document(String path, String type, String title, String content) {
        return new IndexedDocument(UUID.randomUUID(), workspaceId, UUID.randomUUID(), path, type, title,
                0, content, "hash-" + path, 1, 3, 12, Map.of(), Instant.now());
    }

    private CodeSymbol symbol(String path, String type, String name) {
        return new CodeSymbol(UUID.randomUUID(), workspaceId, UUID.randomUUID(), path, "Java", type, name,
                null, type.toLowerCase() + " " + name, 1, 1, "public", Map.of(), Instant.now());
    }

    private ProjectMemoryItem memory(String type, String status, String title, String content) {
        return new ProjectMemoryItem(UUID.randomUUID(), workspaceId, type, "workspace", title, content,
                "USER", null, null, null, null, status, 0.9, null, "test", Instant.now(),
                "test", Instant.now(), Map.of("source", Domain.ProjectMemoryType.valueOf(type).name()));
    }
}
