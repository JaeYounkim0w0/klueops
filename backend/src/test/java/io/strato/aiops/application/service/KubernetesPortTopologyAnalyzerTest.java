package io.strato.aiops.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.strato.aiops.application.port.out.KubernetesNamespaceDiagnostics;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KubernetesPortTopologyAnalyzerTest {

    private final KubernetesPortTopologyAnalyzer analyzer = new KubernetesPortTopologyAnalyzer(new ObjectMapper());

    /** KubernetesPortTopologyAnalyzerTest의 detectsNumericAndNamedTargetPortMismatchesForSelectedWorkloads 처리에 필요한 업무 로직을 수행한다. */
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

    /** KubernetesPortTopologyAnalyzerTest의 acceptsMatchingNamedPortAndProtocol 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void acceptsMatchingNamedPortAndProtocol() {
        var diagnostics = diagnostics(
                pod("{\"labels\":{\"app\":\"api\"},\"containers\":[{\"name\":\"api\",\"ports\":[{\"name\":\"http\",\"containerPort\":8080,\"protocol\":\"TCP\"}]}]}"),
                service("[{\"port\":80,\"targetPort\":\"http\",\"protocol\":\"TCP\"}]")
        );

        assertThat(analyzer.analyze(diagnostics, false)).isEmpty();
    }

    /** KubernetesPortTopologyAnalyzerTest의 abstainsWhenNumericTargetHasNoDeclaredPortOrCorroboratingStartupLog 처리에 필요한 업무 로직을 수행한다. */
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

    /** KubernetesPortTopologyAnalyzerTest의 ignoresServicesWithoutSelectorsAndInvalidSummaries 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void ignoresServicesWithoutSelectorsAndInvalidSummaries() {
        var diagnostics = diagnostics(
                pod("not-json"),
                new KubernetesNamespaceDiagnostics.DiagnosticResource("default", "Service", "external", "ClusterIP", "{\"ports\":[{\"port\":80}]}"),
                new KubernetesNamespaceDiagnostics.DiagnosticResource("default", "Service", "broken", "ClusterIP", "not-json")
        );

        assertThat(analyzer.analyze(diagnostics, true)).isEmpty();
    }

    /** KubernetesPortTopologyAnalyzerTest의 diagnostics 처리에 필요한 업무 로직을 수행한다. */
    private KubernetesNamespaceDiagnostics diagnostics(KubernetesNamespaceDiagnostics.DiagnosticResource... resources) {
        return new KubernetesNamespaceDiagnostics(List.of(resources), List.of(), List.of(), Instant.EPOCH);
    }

    /** KubernetesPortTopologyAnalyzerTest의 pod 처리에 필요한 업무 로직을 수행한다. */
    private KubernetesNamespaceDiagnostics.DiagnosticResource pod(String summary) {
        return new KubernetesNamespaceDiagnostics.DiagnosticResource("default", "Pod", "api-123", "Running", summary);
    }

    /** KubernetesPortTopologyAnalyzerTest의 service 처리에 필요한 업무 로직을 수행한다. */
    private KubernetesNamespaceDiagnostics.DiagnosticResource service(String ports) {
        return new KubernetesNamespaceDiagnostics.DiagnosticResource("default", "Service", "api", "ClusterIP",
                "{\"selector\":{\"app\":\"api\"},\"ports\":" + ports + "}");
    }
}
