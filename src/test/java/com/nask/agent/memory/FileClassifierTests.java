package com.nask.agent.memory;

import com.nask.agent.common.Domain;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FileClassifierTests {
    private final FileClassifier classifier = new FileClassifier();

    @Test
    void classifiesKnownProjectFileTypes() {
        assertThat(classifier.classify("pom.xml")).isEqualTo(Domain.ProjectFileType.BUILD_FILE);
        assertThat(classifier.classify("src/main/java/com/nask/agent/AgentApplication.java"))
                .isEqualTo(Domain.ProjectFileType.SOURCE);
        assertThat(classifier.classify("src/test/java/com/nask/agent/AgentApplicationTests.java"))
                .isEqualTo(Domain.ProjectFileType.TEST);
        assertThat(classifier.classify("docs/step4/phase4-work-plan.md")).isEqualTo(Domain.ProjectFileType.DOCS);
        assertThat(classifier.classify("src/main/resources/application.properties"))
                .isEqualTo(Domain.ProjectFileType.CONFIG);
        assertThat(classifier.classify("src/main/resources/db/migration/V4__phase4_project_memory.sql"))
                .isEqualTo(Domain.ProjectFileType.MIGRATION);
    }
}
