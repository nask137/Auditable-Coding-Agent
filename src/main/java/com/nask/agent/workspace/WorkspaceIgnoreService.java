package com.nask.agent.workspace;

import com.nask.agent.common.AgentSettings;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Computes and caches the workspace file-view ignore set from Git metadata.
 */
@Service
public class WorkspaceIgnoreService {
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final AgentSettings settings;
    private final Map<UUID, CachedIgnoreView> cache = new ConcurrentHashMap<>();

    public WorkspaceIgnoreService(AgentSettings settings) {
        this.settings = settings;
    }

    /**
     * Returns cached Git ignored path prefixes for a workspace.
     */
    public IgnoreView ignoreView(Workspace workspace) {
        var cached = cache.get(workspace.id());
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return cached.view();
        }
        var computed = compute(workspace);
        cache.put(workspace.id(), new CachedIgnoreView(computed, Instant.now().plus(CACHE_TTL)));
        return computed;
    }

    private IgnoreView compute(Workspace workspace) {
        var root = Path.of(workspace.rootPath()).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            return new IgnoreView(List.of(), "workspace_root_missing", 0);
        }
        var gitRoot = execute(root, List.of("rev-parse", "--show-toplevel"));
        if (gitRoot.exitCode() != 0 || gitRoot.output().isBlank()) {
            return new IgnoreView(List.of(), "not_a_git_workspace", gitRoot.exitCode());
        }
        var repoRoot = Path.of(firstLine(gitRoot.output())).toAbsolutePath().normalize();
        if (!root.startsWith(repoRoot)) {
            return new IgnoreView(List.of(), "git_root_outside_workspace", gitRoot.exitCode());
        }
        var ignored = execute(repoRoot, List.of("ls-files", "--others", "--ignored", "--exclude-standard", "--directory", "-z"));
        if (ignored.exitCode() != 0) {
            return new IgnoreView(List.of(), List.of(), "git_ls_files_failed", ignored.exitCode());
        }
        return collapseIgnoredPaths(repoRoot, root, parseNullSeparated(ignored.output()));
    }

    private IgnoreView collapseIgnoredPaths(Path repoRoot, Path workspaceRoot, List<String> paths) {
        var files = new LinkedHashSet<String>();
        var directoryPrefixes = new LinkedHashSet<String>();
        var workspacePrefix = normalize(repoRoot.relativize(workspaceRoot).toString());
        paths.stream()
                .map(this::normalize)
                .filter(path -> !path.isBlank() && isInsideWorkspace(path, workspacePrefix))
                .sorted()
                .forEach(path -> {
                    var workspacePath = toWorkspaceRelative(path, workspacePrefix);
                    if (workspacePath.isBlank()) {
                        return;
                    }
                    if (path.endsWith("/") || Files.isDirectory(repoRoot.resolve(path), java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                        directoryPrefixes.add(workspacePath.endsWith("/") ? workspacePath : workspacePath + "/");
                    } else {
                        files.add(workspacePath);
                    }
                });
        return new IgnoreView(collapseFiles(files, directoryPrefixes), collapsePrefixes(directoryPrefixes), "git_ls_files", 0);
    }

    private boolean isInsideWorkspace(String repoRelativePath, String workspacePrefix) {
        return workspacePrefix.isBlank()
                || repoRelativePath.equals(workspacePrefix)
                || repoRelativePath.startsWith(workspacePrefix + "/");
    }

    private String toWorkspaceRelative(String repoRelativePath, String workspacePrefix) {
        if (workspacePrefix.isBlank()) {
            return repoRelativePath;
        }
        var workspacePath = repoRelativePath.substring(workspacePrefix.length());
        return workspacePath.replaceAll("^/+", "");
    }

    private List<String> collapseFiles(LinkedHashSet<String> files, LinkedHashSet<String> directoryPrefixes) {
        return files.stream()
                .filter(path -> directoryPrefixes.stream().noneMatch(path::startsWith))
                .sorted(Comparator.comparingInt(String::length).thenComparing(String::compareTo))
                .toList();
    }

    private List<String> collapsePrefixes(LinkedHashSet<String> prefixes) {
        var collapsed = new ArrayList<String>();
        prefixes.stream()
                .sorted(Comparator.comparingInt(String::length).thenComparing(String::compareTo))
                .forEach(prefix -> {
                    if (collapsed.stream().noneMatch(prefix::startsWith)) {
                        collapsed.add(prefix);
                    }
                });
        return List.copyOf(collapsed);
    }

    private String normalize(String path) {
        return path == null ? "" : path.replace('\\', '/').replaceAll("^/+", "");
    }

    private ProcessResult execute(Path cwd, List<String> gitArguments) {
        try {
            var command = new ArrayList<String>();
            command.add("git");
            command.add("--no-optional-locks");
            command.add("-c");
            command.add("core.fsmonitor=false");
            command.add("-c");
            command.add("core.untrackedCache=false");
            command.addAll(gitArguments);
            var process = new ProcessBuilder(command)
                    .directory(cwd.toFile())
                    .redirectErrorStream(true)
                    .start();
            var outputFuture = CompletableFuture.supplyAsync(() -> readOutput(process.getInputStream()));
            var finished = process.waitFor(settings.commandTimeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new ProcessResult(124, outputFuture.getNow(""));
            }
            return new ProcessResult(process.exitValue(), outputFuture.get(5, TimeUnit.SECONDS));
        } catch (Exception e) {
            return new ProcessResult(1, e.getMessage());
        }
    }

    private String readOutput(InputStream stream) {
        try {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    private List<String> parseNullSeparated(String output) {
        if (output == null || output.isEmpty()) {
            return List.of();
        }
        return java.util.Arrays.stream(output.split("\u0000"))
                .filter(value -> !value.isBlank())
                .toList();
    }

    private String firstLine(String output) {
        return output.lines().findFirst().orElse("").trim();
    }

    public record IgnoreView(List<String> ignoredFiles, List<String> ignoredPrefixes, String source, int exitCode) {
        public IgnoreView(List<String> ignoredPrefixes, String source, int exitCode) {
            this(List.of(), ignoredPrefixes, source, exitCode);
        }

        public IgnoreView {
            ignoredFiles = ignoredFiles == null ? List.of() : List.copyOf(ignoredFiles);
            ignoredPrefixes = ignoredPrefixes == null ? List.of() : List.copyOf(ignoredPrefixes);
        }
    }

    private record CachedIgnoreView(IgnoreView view, Instant expiresAt) {
    }

    private record ProcessResult(int exitCode, String output) {
    }
}
