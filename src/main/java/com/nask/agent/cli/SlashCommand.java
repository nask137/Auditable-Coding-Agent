package com.nask.agent.cli;

/**
 * Parsed interactive slash command.
 */
record SlashCommand(String name, String argument) {
    static SlashCommand parse(String line) {
        var value = line == null ? "" : line.trim();
        if (!value.startsWith("/")) {
            return null;
        }
        var body = value.substring(1).trim();
        if (body.isBlank()) {
            return new SlashCommand("", "");
        }
        var parts = body.split("\\s+", 2);
        return new SlashCommand(parts[0].toLowerCase(java.util.Locale.ROOT),
                parts.length > 1 ? parts[1].trim() : "");
    }
}
