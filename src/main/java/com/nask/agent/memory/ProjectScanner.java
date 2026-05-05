package com.nask.agent.memory;

import com.nask.agent.common.AgentSettings;
import com.nask.agent.workspace.Workspace;
import com.nask.agent.workspace.WorkspacePathException;
import com.nask.agent.workspace.WorkspacePathGuard;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Bounded scanner that observes workspace project shape without mutating files.
 */
@Component
public class ProjectScanner {
    private static final List<String> IGNORED_DIRECTORIES = List.of(
            ".git", "target", "node_modules", ".idea", ".vscode", "build", "out");

    private final WorkspacePathGuard pathGuard;
    private final AgentSettings settings;
    private final FileClassifier fileClassifier;

    public ProjectScanner(WorkspacePathGuard pathGuard, AgentSettings settings, FileClassifier fileClassifier) {
        this.pathGuard = pathGuard;
        this.settings = settings;
        this.fileClassifier = fileClassifier;
    }

    /**
     * Scans the workspace root using configured budgets and ignore rules.
     */
    public ProjectScanResult scan(Workspace workspace) {
        var rootCheck = pathGuard.check(workspace, ".", false);
        if (!rootCheck.allowed()) {
            throw new WorkspacePathException(rootCheck.reason());
        }
        var root = rootCheck.absolutePath();
        if (!Files.isDirectory(root)) {
            throw new WorkspacePathException("Workspace root is not a directory: " + rootCheck.relativePath());
        }
        var state = new ScanState(root);
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (!dir.equals(root) && shouldSkipDirectory(dir.getFileName().toString())) {
                        state.filesSkipped++;
                        state.skippedReasons.merge("ignored_directory:" + dir.getFileName(), 1, Integer::sum);
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return state.filesSeen >= settings.projectScanMaxFiles()
                            ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    state.observe(file, attrs);
                    return state.filesSeen >= settings.projectScanMaxFiles()
                            ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    state.filesSkipped++;
                    state.skippedReasons.merge("visit_failed", 1, Integer::sum);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            state.skippedReasons.merge("walk_failed", 1, Integer::sum);
        }
        var summary = "Scanned " + state.filesSeen + " files; indexed " + state.observations.size()
                + "; skipped " + state.filesSkipped;
        return new ProjectScanResult(List.copyOf(state.observations), state.filesSeen, state.observations.size(),
                state.filesSkipped, summary, Map.of(
                "ignoredDirectories", IGNORED_DIRECTORIES,
                "maxFiles", settings.projectScanMaxFiles(),
                "maxFileBytes", settings.projectScanMaxFileBytes(),
                "maxTotalBytes", settings.projectScanMaxTotalBytes(),
                "bytesRead", state.bytesRead,
                "skippedReasons", state.skippedReasons));
    }

    private boolean shouldSkipDirectory(String directoryName) {
        return IGNORED_DIRECTORIES.contains(directoryName);
    }

    private final class ScanState {
        private final Path root;
        private final List<ProjectScanObservation> observations = new ArrayList<>();
        private final Map<String, Integer> skippedReasons = new LinkedHashMap<>();
        private int filesSeen;
        private int filesSkipped;
        private long bytesRead;

        private ScanState(Path root) {
            this.root = root;
        }

        private void observe(Path file, BasicFileAttributes attrs) {
            filesSeen++;
            if (!attrs.isRegularFile()) {
                filesSkipped++;
                skippedReasons.merge("not_regular_file", 1, Integer::sum);
                return;
            }
            var relative = root.relativize(file).toString().replace('\\', '/');
            var type = fileClassifier.classify(relative);
            var size = attrs.size();
            var content = "";
            var read = false;
            if (shouldRead(type, relative, size)) {
                try {
                    content = Files.readString(file, StandardCharsets.UTF_8);
                    bytesRead += content.getBytes(StandardCharsets.UTF_8).length;
                    read = true;
                } catch (Exception e) {
                    filesSkipped++;
                    skippedReasons.merge("read_failed", 1, Integer::sum);
                }
            }
            observations.add(new ProjectScanObservation(relative, type, size, read, content));
        }

        private boolean shouldRead(com.nask.agent.common.Domain.ProjectFileType type, String relative, long size) {
            if (size > settings.projectScanMaxFileBytes()) {
                filesSkipped++;
                skippedReasons.merge("file_too_large", 1, Integer::sum);
                return false;
            }
            if (bytesRead + size > settings.projectScanMaxTotalBytes()) {
                filesSkipped++;
                skippedReasons.merge("total_bytes_exceeded", 1, Integer::sum);
                return false;
            }
            var lower = relative.toLowerCase(Locale.ROOT);
            return type == com.nask.agent.common.Domain.ProjectFileType.BUILD_FILE
                    || type == com.nask.agent.common.Domain.ProjectFileType.CONFIG
                    || type == com.nask.agent.common.Domain.ProjectFileType.DOCS
                    || type == com.nask.agent.common.Domain.ProjectFileType.MIGRATION
                    || type == com.nask.agent.common.Domain.ProjectFileType.SOURCE
                    || type == com.nask.agent.common.Domain.ProjectFileType.TEST
                    || lower.equals("readme.md");
        }
    }
}
