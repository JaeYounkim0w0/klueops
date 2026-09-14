package io.strato.aiops.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.strato.aiops.application.port.out.KubernetesNamespaceDiagnostics;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KubernetesPortTopologyAnalyzerTest {

    private final KubernetesPortTopologyAnalyzer analyzer = new KubernetesPortTopologyAnalyzer(new ObjectMapper());

    @Test
    void detectsNumericAndNamedTargetPortMismatchesForSelectedWorkloads() {
        var diagnostics = diagnostics(
                pod("{\"labels\":{\"app\":\"api\"},\"containers\":[{\"name\":\"api\",\"ports\":[{\"name\":\"http\",\"containerPort\":8080,\"protocol\":\"TCP\"}]}]}"),
                service("[{\"port\":80,\"targetPort\":9090,\"protocol\":\"TCP\"},{\"port\":81,\"targetPort\":\"admin\",\"protocol\":\"TCP\"}]")
        );

        var signals = analyzer.analyze(diagnostics, false);

        assertThat(signals).hasSize(2);
        assertThat(signals).extracting(KubernetesPortTopologyAnalyzer.PortMismatchSignal::targetPort)
                .containsExactly("9090", "admin");
        assertThat(signals).allMatch(KubernetesPortTopologyAnalyzer.PortMismatchSignal::strongSignal);
    }

    @Test
    void acceptsMatchingNamedPortAndProtocol() {
        var diagnostics = diagnostics(
                pod("{\"labels\":{\"app\":\"api\"},\"containers\":[{\"name\":\"api\",\"ports\":[{\"name\":\"http\",\"containerPort\":8080,\"protocol\":\"TCP\"}]}]}"),
                service("[{\"port\":80,\"targetPort\":\"http\",\"protocol\":\"TCP\"}]")
        );

        assertThat(analyzer.analyze(diagnostics, false)).isEmpty();
    }

    @Test
    void abstainsWhenNumericTargetHasNoDeclaredPortOrCorroboratingStartupLog() {
        var diagnostics = diagnostics(
                pod("{\"labels\":{\"app\":\"api\"},\"containers\":[{\"name\":\"api\"}]}"),
                service("[{\"port\":80,\"targetPort\":9090,\"protocol\":\"TCP\"}]")
        );

        assertThat(analyzer.analyze(diagnostics, false)).isEmpty();
        assertThat(analyzer.analyze(diagnostics, true)).singleElement()
                .satisfies(signal -> assertThat(signal.strongSignal()).isFalse());
    }

    @Test
    void ignoresServicesWithoutSelectorsAndInvalidSummaries() {
        var diagnostics = diagnostics(
                pod("not-json"),
                new KubernetesNamespaceDiagnostics.DiagnosticResource("default", "Service", "external", "ClusterIP", "{\"ports\":[{\"port\":80}]}"),
                new KubernetesNamespaceDiagnostics.DiagnosticResource("default", "Service", "broken", "ClusterIP", "not-json")
        );

        assertThat(analyzer.analyze(diagnostics, true)).isEmpty();
    }

    private KubernetesNamespaceDiagnostics diagnostics(KubernetesNamespaceDiagnostics.DiagnosticResource... resources) {
        return new KubernetesNamespaceDiagnostics(List.of(resources), List.of(), List.of(), Instant.EPOCH);
    }

    private KubernetesNamespaceDiagnostics.DiagnosticResource pod(String summary) {
        return new KubernetesNamespaceDiagnostics.DiagnosticResource("default", "Pod", "api-123", "Running", summary);
    }

    private KubernetesNamespaceDiagnostics.DiagnosticResource service(String ports) {
        return new KubernetesNamespaceDiagnostics.DiagnosticResource("default", "Service", "api", "ClusterIP",
                "{\"selector\":{\"app\":\"api\"},\"ports\":" + ports + "}");
    }
}
