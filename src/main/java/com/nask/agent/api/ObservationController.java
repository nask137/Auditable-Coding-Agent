package com.nask.agent.api;

import com.nask.agent.audit.AuditEvent;
import com.nask.agent.audit.AuditService;
import com.nask.agent.file.FileChange;
import com.nask.agent.file.FileChangeRepository;
import com.nask.agent.report.ReportService;
import com.nask.agent.report.TaskReport;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tasks/{taskId}")
public class ObservationController {
    private final AuditService auditService;
    private final FileChangeRepository fileChangeRepository;
    private final ReportService reportService;

    public ObservationController(AuditService auditService, FileChangeRepository fileChangeRepository,
                                 ReportService reportService) {
        this.auditService = auditService;
        this.fileChangeRepository = fileChangeRepository;
        this.reportService = reportService;
    }

    @GetMapping("/events")
    List<AuditEvent> events(@PathVariable UUID taskId) {
        return auditService.eventsForTask(taskId);
    }

    @GetMapping("/changes")
    List<FileChange> changes(@PathVariable UUID taskId) {
        return fileChangeRepository.findByTask(taskId);
    }

    @GetMapping("/report")
    TaskReport report(@PathVariable UUID taskId) {
        return reportService.getLatestRequired(taskId);
    }
}
