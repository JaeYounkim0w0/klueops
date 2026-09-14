package io.strato.aiops.application.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CapabilityMatrixSummaryTest {

    @Test
    void distinguishesDeniedUnknownAndPartialCapabilityCoverage() {
        var summary = CapabilityMatrixSummary.from(List.of("ALLOWED", "DENIED", "UNKNOWN"));

        assertThat(summary.status()).isEqualTo("PARTIAL");
        assertThat(summary.allowed()).isEqualTo(1);
        assertThat(summary.denied()).isEqualTo(1);
        assertThat(summary.unknown()).isEqualTo(1);
        assertThat(summary.score()).isEqualTo(33);
    }
}
