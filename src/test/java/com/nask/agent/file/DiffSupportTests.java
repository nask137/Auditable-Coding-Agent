package com.nask.agent.file;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiffSupportTests {
    private final DiffSupport diffSupport = new DiffSupport();

    @Test
    void computesStableHash() {
        assertThat(diffSupport.sha256("abc")).isEqualTo(diffSupport.sha256("abc"));
        assertThat(diffSupport.sha256("abc")).isNotEqualTo(diffSupport.sha256("abcd"));
    }

    @Test
    void createsSimpleDiff() {
        var diff = diffSupport.simpleUnifiedDiff("a.txt", "before", "after");

        assertThat(diff).contains("--- a/a.txt");
        assertThat(diff).contains("+++ b/a.txt");
        assertThat(diff).contains("- before");
        assertThat(diff).contains("+ after");
    }
}
