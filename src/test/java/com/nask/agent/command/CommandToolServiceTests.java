package com.nask.agent.command;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CommandToolServiceTests {
    Path tempDir;

    @BeforeEach
    void setup() throws Exception {
        tempDir = Path.of("target", "command-tool-test-" + UUID.randomUUID());
        Files.createDirectories(tempDir);
    }

    @AfterEach
    void cleanup() throws Exception {
        if (Files.isDirectory(tempDir)) {
            try (var stream = Files.walk(tempDir)) {
                var paths = stream.sorted(java.util.Comparator.reverseOrder()).toList();
                for (var path : paths) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    @Test
    void windowsExecutableResolutionUsesPathExtForExtensionlessCommands() throws Exception {
        var bin = tempDir.resolve("bin");
        Files.createDirectories(bin);
        var mvn = bin.resolve("mvn.cmd");
        Files.writeString(mvn, "@echo off");

        var resolved = CommandToolService.resolveExecutableForProcess("mvn",
                Map.of("Path", bin.toString(), "PATHEXT", ".COM;.EXE;.BAT;.CMD"), true);

        assertThat(Path.of(resolved)).isEqualTo(mvn);
    }

    @Test
    void nonWindowsExecutableResolutionLeavesCommandUnchanged() {
        assertThat(CommandToolService.resolveExecutableForProcess("mvn", Map.of(), false)).isEqualTo("mvn");
    }
}
