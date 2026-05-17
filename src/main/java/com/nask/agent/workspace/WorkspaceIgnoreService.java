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
        if (!repoRoot.startsWith(root)) {
            return new IgnoreView(List.of(), "git_root_outside_workspace", gitRoot.exitCode());
        }
        var ignored = execute(repoRoot, List.of("ls-files", "--others", "--ignored", "--exclude-standard", "-z"));
        if (ignored.exitCode() != 0) {
            return new IgnoreView(List.of(), "git_ls_files_failed", ignored.exitCode());
        }
        return new IgnoreView(collapseToPrefixes(parseNullSeparated(ignored.output())), "git_ls_files", 0);
    }

    private List<String> collapseToPrefixes(List<String> paths) {
        var prefixes = new LinkedHashSet<String>();
        paths.stream()
                .map(this::normalize)
                .filter(path -> !path.isBlank())
                .sorted()
                .forEach(path -> prefixes.add(path.endsWith("/") ? path : path + "/"));
        return prefixes.stream()
                .sorted(Comparator.comparingInt(String::length).thenComparing(String::compareTo))
                .toList();
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

    public record IgnoreView(List<String> ignoredPrefixes, String source, int exitCode) {
        public IgnoreView {
            ignoredPrefixes = ignoredPrefixes == null ? List.of() : List.copyOf(ignoredPrefixes);
        }
    }

    private record CachedIgnoreView(IgnoreView view, Instant expiresAt) {
    }

    private record ProcessResult(int exitCode, String output) {
    }
}
