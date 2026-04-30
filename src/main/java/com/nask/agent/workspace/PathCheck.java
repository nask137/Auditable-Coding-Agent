package com.nask.agent.workspace;

import java.nio.file.Path;

public record PathCheck(
        boolean allowed,
        boolean sensitive,
        boolean blockedSensitive,
        String reason,
        Path absolutePath,
        String relativePath) {
    public static PathCheck allow(Path absolutePath, String relativePath, boolean sensitive, boolean blockedSensitive) {
        return new PathCheck(true, sensitive, blockedSensitive, null, absolutePath, relativePath);
    }

    public static PathCheck block(String reason, Path absolutePath, String relativePath) {
        return new PathCheck(false, false, false, reason, absolutePath, relativePath);
    }
}
