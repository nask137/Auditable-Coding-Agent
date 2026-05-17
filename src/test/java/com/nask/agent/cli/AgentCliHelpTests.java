package com.nask.agent.cli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

class AgentCliHelpTests {
    @Test
    void subcommandsAcceptHelpOption() {
        assertHelp("tui", "--help");
        assertHelp("workspace", "--help");
        assertHelp("command", "--help");
        assertHelp("run", "--help");
    }

    private static void assertHelp(String... args) {
        var out = new StringWriter();
        var err = new StringWriter();
        var command = new CommandLine(new AgentCli());
        command.setOut(new PrintWriter(out));
        command.setErr(new PrintWriter(err));

        var exitCode = command.execute(args);

        assertThat(exitCode).isZero();
        assertThat(err.toString()).doesNotContain("Unknown option");
        assertThat(out.toString()).contains("--help");
    }
}
