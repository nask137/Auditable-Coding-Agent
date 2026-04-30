package com.nask.agent;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;

public final class TestFiles {
    private TestFiles() {
    }

    public static Path createTempDirectory(String prefix) throws IOException {
        return Files.createTempDirectory(prefix);
    }

    public static void deleteRecursivelyQuietly(Path root) {
        if (root == null || !Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            var paths = walk.sorted(Comparator.reverseOrder()).toList();
            for (var path : paths) {
                deleteWithRetry(path);
            }
        } catch (Exception ignored) {
            // Cleanup must not hide the assertion result on Windows/JDK symlink edge cases.
        }
    }

    private static void deleteWithRetry(Path path) throws IOException, InterruptedException {
        IOException last = null;
        for (var attempt = 0; attempt < 3; attempt++) {
            try {
                Files.deleteIfExists(path);
                return;
            } catch (AccessDeniedException e) {
                last = e;
                System.gc();
                Thread.sleep(50L);
            }
        }
        if (last != null) {
            throw last;
        }
    }
}
