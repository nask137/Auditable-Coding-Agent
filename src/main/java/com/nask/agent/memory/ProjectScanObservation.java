package com.nask.agent.memory;

import com.nask.agent.common.Domain;

/**
 * One file-level observation captured by the phase 4 scanner.
 */
public record ProjectScanObservation(
        String path,
        Domain.ProjectFileType fileType,
        long sizeBytes,
        boolean contentRead,
        String contentSample) {
}
