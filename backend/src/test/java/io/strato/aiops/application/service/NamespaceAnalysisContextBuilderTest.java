package io.strato.aiops.application.service;

import io.strato.aiops.application.port.out.KubernetesNamespaceDiagnostics;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NamespaceAnalysisContextBuilderTest {

    private final NamespaceAnalysisContextBuilder builder = new NamespaceAnalysisContextBuilder();

    /** NamespaceAnalysisContextBuilderTest의 writesStableScopeHeaderAndDiagnosticCountsBeforeSectionSignals 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void writesStableScopeHeaderAndDiagnosticCountsBeforeSectionSignals() {
        KubernetesNamespaceDiagnostics diagnostics = new KubernetesNamespaceDiagnostics(
                List.of(new KubernetesNamespaceDiagnostics.DiagnosticResource("payments", "Pod", "api", "Pending", "{}")),
                List.of(), List.of(), Instant.parse("2026-09-03T00:00:00Z"));

        String context = builder.build(UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "payments", "checkout", diagnostics, "root-cause", 14_000,
                target -> target.append("problemResourceSignals:\n- Pod/api Pending\n"));

        assertThat(context).startsWith("analysisMode=namespace-sectioned\nsection=root-cause\n")
                .contains("namespace=payments\napplicationName=checkout\n")
                .contains("Current Ready/resource status is authoritative")
                .contains("ResolvedTransient are historical context")
                .contains("diagnosticCounts resources=1 events=0 podLogs=0 collectedAt=2026-09-03T00:00:00Z")
                .endsWith("problemResourceSignals:\n- Pod/api Pending\n");
    }

    /** NamespaceAnalysisContextBuilderTest의 exposesPartialCollectionWithoutClaimingCompleteCoverage 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void exposesPartialCollectionWithoutClaimingCompleteCoverage() {
        KubernetesNamespaceDiagnostics diagnostics = new KubernetesNamespaceDiagnostics(
                List.of(), List.of(), List.of(), Instant.EPOCH,
                List.of(
                        new KubernetesNamespaceDiagnostics.CollectionStage("pods", "SUCCEEDED", 2, 12, null),
                        new KubernetesNamespaceDiagnostics.CollectionStage("events", "FAILED", 0, 50, "forbidden")
                ));

        String context = new NamespaceAnalysisContextBuilder().build(UUID.randomUUID(), "payments", null,
                diagnostics, "root-cause", 4000, value -> { });

        assertThat(context).contains("collectionQuality=PARTIAL")
                .contains("source=events status=FAILED")
                .contains("Do not claim complete namespace coverage");
    }

    /** NamespaceAnalysisContextBuilderTest의 truncatesOversizedContextWithAnExplicitOriginalSizeMarker 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void truncatesOversizedContextWithAnExplicitOriginalSizeMarker() {
        KubernetesNamespaceDiagnostics diagnostics = new KubernetesNamespaceDiagnostics(
                List.of(), List.of(), List.of(), Instant.parse("2026-09-03T00:00:00Z"));

        String context = builder.build(UUID.randomUUID(), "default", null, diagnostics,
                "log-analysis", 220, target -> target.append("x".repeat(500)));

        assertThat(context).hasSizeGreaterThan(220)
                .startsWith("analysisMode=namespace-sectioned")
                .contains("[context truncated: originalChars=");
    }
}
