package com.nask.agent.memory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Stable SHA-256 content hashes for document de-duplication.
 */
final class ContentHash {
    private ContentHash() {
    }

    static String sha256(String content) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            var hex = new StringBuilder();
            for (var b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash content", e);
        }
    }
}
