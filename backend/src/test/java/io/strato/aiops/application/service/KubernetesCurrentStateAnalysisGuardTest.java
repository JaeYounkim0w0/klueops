package io.strato.aiops.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.strato.aiops.application.port.out.KubernetesNamespaceDiagnostics;
import io.strato.aiops.domain.analysis.SupportedLocale;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KubernetesCurrentStateAnalysisGuardTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final KubernetesCurrentStateAnalysisGuard guard =
            new KubernetesCurrentStateAnalysisGuard(objectMapper);

    /** KubernetesCurrentStateAnalysisGuardTest의 resolvesTransientSchedulingAndStartupPermissionNoiseForStablePod 처리에 필요한 결과를 조합해 반환한다. */
    @Test
    void resolvesTransientSchedulingAndStartupPermissionNoiseForStablePod() {
        KubernetesNamespaceDiagnostics reconciled = guard.reconcile(diagnostics(
                "Running", 1, 1, 0, "Bound",
                "chmod: changing permissions of '/var/run/postgresql': Operation not permitted\n"
                        + "database system is ready to accept connections"));

        KubernetesNamespaceDiagnostics.DiagnosticEvent event = reconciled.events().get(0);
        assertThat(event.type()).isEqualTo("Normal");
        assertThat(event.reason()).isEqualTo("ResolvedTransient");
        assertThat(reconciled.podLogs().get(0).log())
                .doesNotContain("Operation not permitted")
                .contains("ready to accept connections");
        assertThat(reconciled.collectionStages().get(0).detail())
                .contains("resolvedTransientEvents=1")
                .contains("suppressedStartupLogLines=1");
    }

    /** KubernetesCurrentStateAnalysisGuardTest의 preservesSignalsWhilePodIsNotReady 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void preservesSignalsWhilePodIsNotReady() {
        KubernetesNamespaceDiagnostics reconciled = guard.reconcile(diagnostics(
                "Pending", 0, 1, 0, "Pending",
                "chmod: changing permissions of '/var/run/postgresql': Operation not permitted"));

        assertThat(reconciled.events().get(0).type()).isEqualTo("Warning");
        assertThat(reconciled.events().get(0).reason()).isEqualTo("FailedScheduling");
        assertThat(reconciled.podLogs().get(0).log()).contains("Operation not permitted");
    }

    /** KubernetesCurrentStateAnalysisGuardTest의 currentHealthyStateOverridesUnsupportedLlmFailureConclusion 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void currentHealthyStateOverridesUnsupportedLlmFailureConclusion() {
        KubernetesNamespaceDiagnostics reconciled = guard.reconcile(diagnostics(
                "Running", 1, 1, 0, "Bound",
                "database system is ready to accept connections"));
        ObjectNode result = objectMapper.createObjectNode();
        result.put("summary", "Pod is failing to initialize");
        result.put("severity", "HIGH");
        result.put("riskScore", 85);
        result.putArray("findings").add("permission failure");
        result.putArray("rootCauses").add("unbound PVC");
        result.putObject("logIntelligence").put("highSeveritySignals", 0);

        guard.enforce(result, reconciled, SupportedLocale.KOREAN, 35, "LOW");

        assertThat(result.path("severity").asText()).isEqualTo("LOW");
        assertThat(result.path("riskScore").asInt()).isEqualTo(35);
        assertThat(result.path("summary").asText()).contains("정상 실행 중");
        assertThat(result.path("findings")).isEmpty();
        assertThat(result.path("rootCauses")).isEmpty();
        assertThat(result.path("currentStateReconciliation").path("status").asText())
                .isEqualTo("CURRENTLY_HEALTHY");
    }

    /** KubernetesCurrentStateAnalysisGuardTest의 doesNotOverrideLlmConclusionWhileAControllerHasMissingReplicas 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void doesNotOverrideLlmConclusionWhileAControllerHasMissingReplicas() {
        KubernetesNamespaceDiagnostics reconciled = guard.reconcile(diagnostics(
                "Running", 1, 1, 0, "Bound",
                "database system is ready to accept connections", "0/1"));
        ObjectNode result = objectMapper.createObjectNode();
        result.put("summary", "StatefulSet has unavailable replicas");
        result.put("severity", "HIGH");
        result.put("riskScore", 85);
        result.putObject("logIntelligence").put("highSeveritySignals", 0);

        guard.enforce(result, reconciled, SupportedLocale.KOREAN, 35, "LOW");

        assertThat(result.path("severity").asText()).isEqualTo("HIGH");
        assertThat(result.has("currentStateReconciliation")).isFalse();
    }

    /** KubernetesCurrentStateAnalysisGuardTest의 diagnostics 처리에 필요한 업무 로직을 수행한다. */
    private KubernetesNamespaceDiagnostics diagnostics(String podStatus, int ready, int total, int restarts,
                                                        String pvcStatus, String log) {
        return diagnostics(podStatus, ready, total, restarts, pvcStatus, log, null);
    }

    /** KubernetesCurrentStateAnalysisGuardTest의 diagnostics 처리에 필요한 업무 로직을 수행한다. */
    private KubernetesNamespaceDiagnostics diagnostics(String podStatus, int ready, int total, int restarts,
                                                        String pvcStatus, String log, String statefulSetStatus) {
        String podSummary = "{\"phase\":\"" + podStatus + "\",\"readyContainers\":" + ready
                + ",\"totalContainers\":" + total + ",\"restartCount\":" + restarts + "}";
        List<KubernetesNamespaceDiagnostics.DiagnosticResource> resources = new java.util.ArrayList<>(List.of(
                new KubernetesNamespaceDiagnostics.DiagnosticResource(
                        "dde-postgres", "Pod", "postgres-0", podStatus, podSummary),
                new KubernetesNamespaceDiagnostics.DiagnosticResource(
                        "dde-postgres", "PersistentVolumeClaim", "data-postgres-0", pvcStatus,
                        "{\"phase\":\"" + pvcStatus + "\"}")
        ));
        if (statefulSetStatus != null) {
            resources.add(new KubernetesNamespaceDiagnostics.DiagnosticResource(
                    "dde-postgres", "StatefulSet", "postgres", statefulSetStatus, "{}"));
        }
        return new KubernetesNamespaceDiagnostics(
                resources,
                List.of(new KubernetesNamespaceDiagnostics.DiagnosticEvent(
                        "dde-postgres", "Pod", "postgres-0", "FailedScheduling", "Warning",
                        "pod has unbound immediate PersistentVolumeClaims", Instant.parse("2026-09-16T04:06:15Z"), 4)),
                List.of(new KubernetesNamespaceDiagnostics.DiagnosticPodLog(
                        "dde-postgres", "postgres-0", "postgres", log, false)),
                Instant.parse("2026-09-16T04:11:15Z")
        );
    }
}
