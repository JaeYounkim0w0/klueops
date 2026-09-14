package io.strato.aiops.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisRegressionCorpusTest {

    @Test
    void corpusContainsDistinctOperationalScenariosAcrossCoreCategories() {
        AnalysisRegressionCorpus corpus = new AnalysisRegressionCorpus(new ObjectMapper());
        var fixtures = corpus.fixtures();

        assertThat(fixtures).hasSizeGreaterThanOrEqualTo(50);
        assertThat(fixtures).extracting(AnalysisRegressionCorpus.Fixture::id).doesNotHaveDuplicates();
        assertThat(fixtures).extracting(AnalysisRegressionCorpus.Fixture::signal).doesNotHaveDuplicates();
        assertThat(fixtures).extracting(AnalysisRegressionCorpus.Fixture::expectedCategory)
                .contains("STORAGE_CONFIG", "APPLICATION_STARTUP", "IMAGE", "TRAFFIC", "PROBE",
                        "CAPACITY", "ROLLOUT", "SCHEDULING", "RBAC", "QUOTA", "NODE", "NETWORK");
        assertThat(fixtures).allSatisfy(fixture -> {
            assertThat(fixture.evidence()).hasSizeGreaterThanOrEqualTo(2);
            assertThat(fixture.commands()).isNotEmpty();
        });
    }
}
