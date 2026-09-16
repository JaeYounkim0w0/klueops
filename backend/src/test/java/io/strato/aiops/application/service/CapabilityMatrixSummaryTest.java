package io.strato.aiops.application.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CapabilityMatrixSummaryTest {

    /** CapabilityMatrixSummaryTest의 distinguishesDeniedUnknownAndPartialCapabilityCoverage 처리에 필요한 업무 로직을 수행한다. */
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
