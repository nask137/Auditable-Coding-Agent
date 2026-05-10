package com.nask.agent.cli;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlashCommandTests {
    @Test
    void parsesCommandNameAndArgument() {
        var command = SlashCommand.parse("/permissions read-only");

        assertThat(command.name()).isEqualTo("permissions");
        assertThat(command.argument()).isEqualTo("read-only");
    }

    @Test
    void ignoresNonSlashInput() {
        assertThat(SlashCommand.parse("implement this")).isNull();
    }
}
