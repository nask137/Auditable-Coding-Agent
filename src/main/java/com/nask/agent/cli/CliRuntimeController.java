package com.nask.agent.cli;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

/**
 * Local CLI runtime settings and transcript summaries for the dashboard.
 */
@RestController
@RequestMapping("/api/cli")
public class CliRuntimeController {
    private final CliRuntimeStateService service;

    public CliRuntimeController(CliRuntimeStateService service) {
        this.service = service;
    }

    @GetMapping("/settings")
    CliRuntimeSettings settings() {
        return service.readSettings();
    }

    @PostMapping("/settings")
    CliRuntimeSettings saveSettings(@RequestBody CliRuntimeSettings settings) throws IOException {
        return service.writeSettings(settings);
    }

    @GetMapping("/sessions")
    List<CliSessionSummary> sessions() throws IOException {
        return service.sessions();
    }
}
