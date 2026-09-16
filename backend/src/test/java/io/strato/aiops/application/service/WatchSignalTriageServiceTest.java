package io.strato.aiops.application.service;

import io.strato.aiops.domain.operations.OperationsModels.WatchSignal;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WatchSignalTriageServiceTest {

    /** WatchSignalTriageServiceTest의 classifiesFailedMountAsHighStorageSignal 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void classifiesFailedMountAsHighStorageSignal() {
        WatchSignalTriageService.SignalClassification classification =
                WatchSignalTriageService.classify(signal("FailedMount", "Pending", "UPDATED"));

        assertThat(classification).isNotNull();
        assertThat(classification.category()).isEqualTo("STORAGE_CONFIG");
        assertThat(classification.severity()).isEqualTo("HIGH");
    }

    /** WatchSignalTriageServiceTest의 ignoresHealthyNormalSignalAndKeepsGenericWarning 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void ignoresHealthyNormalSignalAndKeepsGenericWarning() {
        assertThat(WatchSignalTriageService.classify(signal("Scheduled", "Running", "UPDATED"))).isNull();

        WatchSignalTriageService.SignalClassification warning =
                WatchSignalTriageService.classify(signal("CustomWarning", "Warning", "UPDATED"));
        assertThat(warning.severity()).isEqualTo("MEDIUM");
        assertThat(warning.category()).isEqualTo("KUBERNETES_EVENT");
    }

    /** WatchSignalTriageServiceTest의 signal 처리에 필요한 업무 로직을 수행한다. */
    private WatchSignal signal(String reason, String status, String action) {
        return new WatchSignal(UUID.randomUUID(), UUID.randomUUID(), "cluster", "default", "Pod", "api",
                action, reason, status, "summary", Instant.parse("2026-09-07T00:00:00Z"));
    }
}
