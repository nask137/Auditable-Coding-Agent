package com.nask.agent.file;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Utility methods for hashing file contents and producing compact diffs.
 */
@Component
public class DiffSupport {
    /**
     * Computes a SHA-256 hash for persisted before/after content identity.
     */
    public String sha256(String content) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to compute SHA-256", e);
        }
    }

    /**
     * Produces a small unified-diff-like preview for audit and reports.
     *
     * <p>This is intentionally simple for Phase 1 and does not try to be a full
     * Myers diff. It is enough to show before/after content around small changes.</p>
     */
    public String simpleUnifiedDiff(String path, String before, String after) {
        if (before.equals(after)) {
            return "";
        }
        return "--- a/" + path + "\n"
                + "+++ b/" + path + "\n"
                + "@@\n"
                + "- " + before.replace("\n", "\n- ") + "\n"
                + "+ " + after.replace("\n", "\n+ ") + "\n";
    }

    /**
     * Estimates added lines by comparing the common prefix.
     */
    public int addedLines(String before, String after) {
        return Math.max(0, lineCount(after) - commonPrefixLineCount(before, after));
    }

    /**
     * Estimates deleted lines by comparing the common prefix.
     */
    public int deletedLines(String before, String after) {
        return Math.max(0, lineCount(before) - commonPrefixLineCount(before, after));
    }

    private int lineCount(String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        return value.split("\\R", -1).length;
    }

    private int commonPrefixLineCount(String before, String after) {
        var left = before.split("\\R", -1);
        var right = after.split("\\R", -1);
        var count = 0;
        while (count < left.length && count < right.length && left[count].equals(right[count])) {
            count++;
        }
        return count;
    }
}
