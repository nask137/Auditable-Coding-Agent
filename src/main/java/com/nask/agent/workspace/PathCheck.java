package com.nask.agent.workspace;

import java.nio.file.Path;

/**
 * Result of resolving a user-supplied path against a workspace boundary.
 *
 * @param allowed whether the path may be used for the requested operation
 * @param sensitive whether the file name matched a sensitive pattern
 * @param blockedSensitive whether the path is a credential/private-key style file
 * @param reason reason for a block, or null when allowed
 * @param absolutePath normalized absolute filesystem path
 * @param relativePath slash-normalized path relative to the workspace root
 */
public record PathCheck(
        boolean allowed,
        boolean sensitive,
        boolean blockedSensitive,
        String reason,
        Path absolutePath,
        String relativePath) {
    /**
     * Creates an allowed path check.
     */
    public static PathCheck allow(Path absolutePath, String relativePath, boolean sensitive, boolean blockedSensitive) {
        return new PathCheck(true, sensitive, blockedSensitive, null, absolutePath, relativePath);
    }

    /**
     * Creates a blocked path check with a reason safe for API/audit output.
     */
    public static PathCheck block(String reason, Path absolutePath, String relativePath) {
        return new PathCheck(false, false, false, reason, absolutePath, relativePath);
    }
}
