package com.nask.agent.workspace;

import org.springframework.stereotype.Component;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Locale;

@Component
public class WorkspacePathGuard {
    public PathCheck check(Workspace workspace, String requestedPath, boolean writeOperation) {
        var root = Path.of(workspace.rootPath()).toAbsolutePath().normalize();
        var requested = root.resolve(requestedPath == null || requestedPath.isBlank() ? "." : requestedPath)
                .toAbsolutePath()
                .normalize();

        if (!requested.startsWith(root)) {
            return PathCheck.block("Path is outside trusted workspace", requested, requested.toString());
        }
        var relativePath = root.relativize(requested);
        var relative = relativePath.toString().replace('\\', '/');
        if (containsGitSegment(relativePath) && writeOperation) {
            return PathCheck.block("Modifying .git directory is blocked", requested, relative);
        }
        if (isBlockedPath(workspace, relativePath)) {
            return PathCheck.block("Path matches blocked workspace rule", requested, relative);
        }
        var realPathCheck = checkRealPathBoundary(root, requested, relative, writeOperation);
        if (realPathCheck != null) {
            return realPathCheck;
        }

        var fileName = requested.getFileName() == null ? "" : requested.getFileName().toString();
        var sensitive = matchesAny(workspace.sensitivePatterns(), fileName);
        var lower = fileName.toLowerCase(Locale.ROOT);
        var blockedSensitive = lower.endsWith(".pem")
                || lower.endsWith(".key")
                || lower.equals("id_rsa")
                || lower.equals("id_ed25519")
                || lower.endsWith(".p12")
                || lower.endsWith(".jks")
                || lower.contains("credentials")
                || lower.contains("secrets");

        return PathCheck.allow(requested, relative, sensitive, blockedSensitive);
    }

    public Path ensureExistingReadable(Workspace workspace, String requestedPath) {
        var check = check(workspace, requestedPath, false);
        if (!check.allowed()) {
            throw new WorkspacePathException(check.reason());
        }
        if (!Files.exists(check.absolutePath())) {
            throw new WorkspacePathException("File does not exist: " + check.relativePath());
        }
        return check.absolutePath();
    }

    private PathCheck checkRealPathBoundary(Path root, Path requested, String relative, boolean writeOperation) {
        try {
            var relativePath = root.relativize(requested);
            var current = root;
            for (var part : relativePath) {
                current = current.resolve(part).normalize();
                if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                    break;
                }
                if (Files.isSymbolicLink(current)) {
                    var target = Files.readSymbolicLink(current);
                    var resolvedTarget = target.isAbsolute()
                            ? target.normalize()
                            : current.getParent().resolve(target).normalize();
                    if (!resolvedTarget.startsWith(root)) {
                        return PathCheck.block("Parent path resolves outside trusted workspace", requested, relative);
                    }
                }
            }
            return null;
        } catch (Exception e) {
            return PathCheck.block("Unable to verify symbolic link boundary: " + e.getMessage(), requested, relative);
        }
    }

    private boolean isBlockedPath(Workspace workspace, Path relative) {
        for (var part : relative) {
            var segment = part.toString();
            if (workspace.blockedPaths().contains(segment)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsGitSegment(Path relative) {
        for (var part : relative) {
            if (".git".equals(part.toString())) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesAny(Iterable<String> patterns, String fileName) {
        for (var pattern : patterns) {
            var matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
            if (matcher.matches(Path.of(fileName))) {
                return true;
            }
        }
        return false;
    }
}
