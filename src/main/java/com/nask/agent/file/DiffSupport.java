package com.nask.agent.file;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
public class DiffSupport {
    public String sha256(String content) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to compute SHA-256", e);
        }
    }

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

    public int addedLines(String before, String after) {
        return Math.max(0, lineCount(after) - commonPrefixLineCount(before, after));
    }

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
