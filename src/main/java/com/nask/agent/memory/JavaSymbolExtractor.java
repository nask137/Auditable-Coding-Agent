package com.nask.agent.memory;

import com.nask.agent.common.Domain;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Best-effort Java outline extractor for classes, records, methods, and fields.
 */
@Component
public class JavaSymbolExtractor {
    private static final Pattern TYPE = Pattern.compile("\\b(class|interface|enum|record)\\s+([A-Za-z_$][\\w$]*)");
    private static final Pattern METHOD = Pattern.compile("""
            ^\\s*(public|protected|private)?\\s*(?:static\\s+)?(?:final\\s+)?(?:synchronized\\s+)?(?:<[^>]+>\\s+)?([\\w$<>\\[\\], ?]+)\\s+([A-Za-z_$][\\w$]*)\\s*\\([^;{}]*\\)\\s*(?:throws\\s+[^{;]+)?[;{]?\\s*$
            """.strip(), Pattern.COMMENTS);
    private static final Pattern CONSTRUCTOR = Pattern.compile("""
            ^\\s*(public|protected|private)?\\s*([A-Za-z_$][\\w$]*)\\s*\\([^;{}]*\\)\\s*(?:throws\\s+[^{;]+)?[;{]?\\s*$
            """.strip(), Pattern.COMMENTS);
    private static final Pattern FIELD = Pattern.compile("""
            ^\\s*(public|protected|private)?\\s*(?:static\\s+)?(?:final\\s+)?([\\w$<>\\[\\], ?]+)\\s+([A-Za-z_$][\\w$]*)\\s*(?:=.*)?;\\s*$
            """.strip(), Pattern.COMMENTS);

    /**
     * Extracts Java symbols from one scanner source observation.
     */
    public List<CodeSymbol> extract(UUID workspaceId, UUID scanRunId, ProjectScanObservation observation) {
        if (observation.contentSample() == null || observation.contentSample().isBlank()
                || !observation.path().endsWith(".java")) {
            return List.of();
        }
        var symbols = new ArrayList<CodeSymbol>();
        var lines = observation.contentSample().replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        String currentContainer = null;
        for (var i = 0; i < lines.length; i++) {
            var line = stripLineComment(lines[i]).strip();
            if (line.isBlank() || line.startsWith("package ") || line.startsWith("import ")
                    || (line.startsWith("@") && !TYPE.matcher(line).find())) {
                continue;
            }
            var typeMatcher = TYPE.matcher(line);
            if (typeMatcher.find()) {
                var type = typeMatcher.group(1);
                var name = typeMatcher.group(2);
                currentContainer = name;
                symbols.add(symbol(workspaceId, scanRunId, observation.path(), symbolType(type), name, null,
                        compactSignature(line), i + 1, visibility(line), Map.of("extractor", "regex-java")));
                continue;
            }
            var constructorMatcher = CONSTRUCTOR.matcher(line);
            if (constructorMatcher.matches() && currentContainer != null
                    && currentContainer.equals(constructorMatcher.group(2))) {
                symbols.add(symbol(workspaceId, scanRunId, observation.path(),
                        Domain.CodeSymbolType.CONSTRUCTOR.name(), currentContainer, currentContainer,
                        compactSignature(line), i + 1, visibility(line), Map.of("extractor", "regex-java")));
                continue;
            }
            var methodMatcher = METHOD.matcher(line);
            if (methodMatcher.matches()) {
                var name = methodMatcher.group(3);
                if (isControlKeyword(name)) {
                    continue;
                }
                var symbolType = currentContainer != null && currentContainer.equals(name)
                        ? Domain.CodeSymbolType.CONSTRUCTOR.name()
                        : Domain.CodeSymbolType.METHOD.name();
                symbols.add(symbol(workspaceId, scanRunId, observation.path(), symbolType, name, currentContainer,
                        compactSignature(line), i + 1, visibility(line), Map.of("extractor", "regex-java")));
                continue;
            }
            var fieldMatcher = FIELD.matcher(line);
            if (fieldMatcher.matches() && !line.contains("(")) {
                var name = fieldMatcher.group(3);
                var symbolType = line.contains(" static ") && line.contains(" final ")
                        ? Domain.CodeSymbolType.CONSTANT.name()
                        : Domain.CodeSymbolType.FIELD.name();
                symbols.add(symbol(workspaceId, scanRunId, observation.path(), symbolType, name, currentContainer,
                        compactSignature(line), i + 1, visibility(line), Map.of("extractor", "regex-java")));
            }
        }
        return symbols;
    }

    private CodeSymbol symbol(UUID workspaceId, UUID scanRunId, String path, String symbolType, String name,
                              String container, String signature, int line, String visibility,
                              Map<String, Object> metadata) {
        return new CodeSymbol(UUID.randomUUID(), workspaceId, scanRunId, path, "Java", symbolType, name, container,
                signature, line, line, visibility, metadata, Instant.now());
    }

    private String symbolType(String javaType) {
        return switch (javaType) {
            case "interface" -> Domain.CodeSymbolType.INTERFACE.name();
            case "enum" -> Domain.CodeSymbolType.ENUM.name();
            case "record" -> Domain.CodeSymbolType.RECORD.name();
            default -> Domain.CodeSymbolType.CLASS.name();
        };
    }

    private String visibility(String line) {
        if (line.startsWith("public ")) {
            return "public";
        }
        if (line.startsWith("protected ")) {
            return "protected";
        }
        if (line.startsWith("private ")) {
            return "private";
        }
        return "package-private";
    }

    private String compactSignature(String line) {
        return line.replaceAll("\\s+", " ").strip();
    }

    private String stripLineComment(String line) {
        var index = line.indexOf("//");
        return index < 0 ? line : line.substring(0, index);
    }

    private boolean isControlKeyword(String name) {
        return List.of("if", "for", "while", "switch", "catch", "return").contains(name);
    }
}
