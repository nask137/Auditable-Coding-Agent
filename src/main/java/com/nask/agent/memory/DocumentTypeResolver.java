package com.nask.agent.memory;

import com.nask.agent.common.Domain;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Resolves indexed document type from scanner file type and path.
 */
@Component
public class DocumentTypeResolver {
    /**
     * Returns the indexed document type name for a scanner observation.
     */
    public String resolve(ProjectScanObservation observation) {
        var path = observation.path().toLowerCase(Locale.ROOT);
        if (path.equals("readme.md") || path.endsWith("/readme.md")) {
            return Domain.IndexedDocumentType.README.name();
        }
        return switch (observation.fileType()) {
            case DOCS -> Domain.IndexedDocumentType.DOCS.name();
            case CONFIG -> Domain.IndexedDocumentType.CONFIG.name();
            case BUILD_FILE -> Domain.IndexedDocumentType.BUILD_FILE.name();
            case MIGRATION -> Domain.IndexedDocumentType.MIGRATION.name();
            case SOURCE -> Domain.IndexedDocumentType.SOURCE.name();
            case TEST -> Domain.IndexedDocumentType.TEST.name();
            default -> Domain.IndexedDocumentType.MEMORY.name();
        };
    }

    /**
     * Whether this observation should be indexed in Milestone 3.
     */
    public boolean indexable(ProjectScanObservation observation) {
        return observation.fileType() == Domain.ProjectFileType.DOCS
                || observation.fileType() == Domain.ProjectFileType.CONFIG
                || observation.fileType() == Domain.ProjectFileType.BUILD_FILE
                || observation.fileType() == Domain.ProjectFileType.MIGRATION;
    }
}
