package com.nask.agent.memory;

import com.nask.agent.common.AgentSettings;
import com.nask.agent.workspace.Workspace;
import com.nask.agent.workspace.WorkspaceIgnoreService;
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
    private final WorkspacePathGuard pathGuard;
    private final WorkspaceIgnoreService ignoreService;
    private final AgentSettings settings;
    private final FileClassifier fileClassifier;

    public ProjectScanner(WorkspacePathGuard pathGuard, WorkspaceIgnoreService ignoreService,
                          AgentSettings settings, FileClassifier fileClassifier) {
        this.pathGuard = pathGuard;
        this.ignoreService = ignoreService;
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
        var ignoreView = ignoreService.ignoreView(workspace);
        var state = new ScanState(workspace, root, ignoreView);
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (!dir.equals(root) && state.shouldSkipDirectory(dir)) {
                        state.filesSkipped++;
                        state.skippedReasons.merge("ignored_directory:" + root.relativize(dir)
                                .toString().replace('\\', '/'), 1, Integer::sum);
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
                "ignoreSource", ignoreView.source(),
                "ignoredPrefixes", ignoreView.ignoredPrefixes(),
                "maxFiles", settings.projectScanMaxFiles(),
                "maxFileBytes", settings.projectScanMaxFileBytes(),
                "maxTotalBytes", settings.projectScanMaxTotalBytes(),
                "bytesRead", state.bytesRead,
                "skippedReasons", state.skippedReasons));
    }

    private final class ScanState {
        private final Workspace workspace;
        private final Path root;
        private final WorkspaceIgnoreService.IgnoreView ignoreView;
        private final List<ProjectScanObservation> observations = new ArrayList<>();
        private final Map<String, Integer> skippedReasons = new LinkedHashMap<>();
        private int filesSeen;
        private int filesSkipped;
        private long bytesRead;

        private ScanState(Workspace workspace, Path root, WorkspaceIgnoreService.IgnoreView ignoreView) {
            this.workspace = workspace;
            this.root = root;
            this.ignoreView = ignoreView;
        }

        private boolean shouldSkipDirectory(Path dir) {
            var relative = root.relativize(dir.toAbsolutePath().normalize()).toString().replace('\\', '/');
            var check = pathGuard.check(workspace, relative, false);
            if (!check.allowed()) {
                return true;
            }
            var prefix = relative.endsWith("/") ? relative : relative + "/";
            for (var ignoredPrefix : ignoreView.ignoredPrefixes()) {
                if (ignoredPrefix.equals(prefix) || ignoredPrefix.startsWith(prefix)) {
                    return true;
                }
            }
            return false;
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
