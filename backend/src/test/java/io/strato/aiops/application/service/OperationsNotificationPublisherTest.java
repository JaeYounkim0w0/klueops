package io.strato.aiops.application.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class OperationsNotificationPublisherTest {

    @Test
    void keepsDedupKeyStableInsideSuppressionWindow() {
        String first = OperationsNotificationPublisher.dedupKey(
                "INCIDENT_CREATED", "incident:1", 10, Instant.parse("2026-09-07T00:01:00Z"));
        String second = OperationsNotificationPublisher.dedupKey(
                "INCIDENT_CREATED", "incident:1", 10, Instant.parse("2026-09-07T00:09:59Z"));
        String nextWindow = OperationsNotificationPublisher.dedupKey(
                "INCIDENT_CREATED", "incident:1", 10, Instant.parse("2026-09-07T00:10:00Z"));

        assertThat(first).isEqualTo(second).isNotEqualTo(nextWindow);
    }

    @Test
    void masksSecretsAndBoundsNotificationText() {
        String sanitized = OperationsNotificationPublisher.cleanText("password=strato123 token=abcdef", 22);

        assertThat(sanitized).doesNotContain("strato123", "abcdef");
        assertThat(sanitized.length()).isLessThanOrEqualTo(22);
    }
}
