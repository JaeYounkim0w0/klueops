package io.strato.aiops.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.strato.aiops.application.port.out.KubernetesNamespaceDiagnostics;
import io.strato.aiops.domain.analysis.SupportedLocale;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
final class KubernetesCurrentStateAnalysisGuard {
    private static final Set<String> TRANSIENT_EVENT_REASONS = Set.of(
            "failedscheduling", "failedmount", "failedattachvolume", "failedbinding"
    );

    private final ObjectMapper objectMapper;

    /** KubernetesCurrentStateAnalysisGuard 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    KubernetesCurrentStateAnalysisGuard(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** KubernetesCurrentStateAnalysisGuard의 reconcile 처리에 필요한 업무 로직을 수행한다. */
    KubernetesNamespaceDiagnostics reconcile(KubernetesNamespaceDiagnostics diagnostics) {
        Set<String> healthyPods = diagnostics.resources().stream()
                .filter(resource -> "Pod".equals(resource.resourceType()))
                .filter(this::isStablePod)
                .map(KubernetesNamespaceDiagnostics.DiagnosticResource::resourceName)
                .collect(Collectors.toSet());
        Set<String> boundClaims = diagnostics.resources().stream()
                .filter(resource -> "PersistentVolumeClaim".equals(resource.resourceType()))
                .filter(resource -> "Bound".equalsIgnoreCase(resource.status()))
                .map(KubernetesNamespaceDiagnostics.DiagnosticResource::resourceName)
                .collect(Collectors.toSet());

        int[] resolvedEvents = {0};
        List<KubernetesNamespaceDiagnostics.DiagnosticEvent> events = diagnostics.events().stream()
                .map(event -> reconcileEvent(event, healthyPods, boundClaims, resolvedEvents))
                .toList();
        int[] suppressedLogLines = {0};
        List<KubernetesNamespaceDiagnostics.DiagnosticPodLog> logs = diagnostics.podLogs().stream()
                .map(log -> reconcileLog(log, healthyPods, suppressedLogLines))
                .toList();

        List<KubernetesNamespaceDiagnostics.CollectionStage> stages = new ArrayList<>(diagnostics.collectionStages());
        stages.add(new KubernetesNamespaceDiagnostics.CollectionStage(
                "current-state-reconciliation",
                "SUCCEEDED",
                resolvedEvents[0] + suppressedLogLines[0],
                0,
                "healthyPods=" + healthyPods.size()
                        + ", resolvedTransientEvents=" + resolvedEvents[0]
                        + ", suppressedStartupLogLines=" + suppressedLogLines[0]
        ));
        return new KubernetesNamespaceDiagnostics(
                diagnostics.resources(), events, logs, diagnostics.collectedAt(), stages);
    }

    /** KubernetesCurrentStateAnalysisGuard의 enforce 처리에 필요한 업무 로직을 수행한다. */
    void enforce(ObjectNode root, KubernetesNamespaceDiagnostics diagnostics, SupportedLocale locale,
                 int deterministicRiskScore, String deterministicSeverity) {
        boolean hasProblemResource = diagnostics.resources().stream().anyMatch(this::isProblemResource);
        boolean hasWarningEvent = diagnostics.events().stream()
                .anyMatch(event -> "Warning".equalsIgnoreCase(event.type()));
        boolean hasHighSeverityLog = root.path("logIntelligence").path("highSeveritySignals").asInt(0) > 0;
        boolean hasStablePod = diagnostics.resources().stream()
                .anyMatch(resource -> "Pod".equals(resource.resourceType()) && isStablePod(resource));
        if (!hasStablePod || hasProblemResource || hasWarningEvent || hasHighSeverityLog) {
            return;
        }

        // LLM 문장보다 현재 Ready 상태와 해소된 이벤트를 우선해 과거 시작 로그의 장애 오판을 차단한다.
        root.put("severity", deterministicSeverity);
        root.put("riskScore", deterministicRiskScore);
        root.put("summary", locale == SupportedLocale.KOREAN
                ? "현재 워크로드는 정상 실행 중이며 배포 초기의 일시적 스케줄링 신호는 해소되었습니다."
                : "The workload is currently healthy; transient scheduling signals from startup have been resolved.");
        root.putArray("findings");
        root.putArray("rootCauses");
        ObjectNode reconciliation = root.putObject("currentStateReconciliation");
        reconciliation.put("status", "CURRENTLY_HEALTHY");
        reconciliation.put("authoritativeSource", "KUBERNETES_CURRENT_STATE");
        reconciliation.put("activeProblemResources", 0);
        reconciliation.put("activeWarningEvents", 0);
        reconciliation.put("activeHighSeverityLogs", 0);
    }

    /** KubernetesCurrentStateAnalysisGuard의 reconcileEvent 처리에 필요한 업무 로직을 수행한다. */
    private KubernetesNamespaceDiagnostics.DiagnosticEvent reconcileEvent(
            KubernetesNamespaceDiagnostics.DiagnosticEvent event,
            Set<String> healthyPods,
            Set<String> boundClaims,
            int[] resolvedEvents
    ) {
        if (!"Warning".equalsIgnoreCase(event.type()) || !isTransientReason(event.reason())) {
            return event;
        }
        boolean resolved = "Pod".equalsIgnoreCase(event.involvedKind()) && healthyPods.contains(event.involvedName())
                || "PersistentVolumeClaim".equalsIgnoreCase(event.involvedKind()) && boundClaims.contains(event.involvedName());
        if (!resolved) {
            return event;
        }
        resolvedEvents[0]++;
        return new KubernetesNamespaceDiagnostics.DiagnosticEvent(
                event.namespace(), event.involvedKind(), event.involvedName(), "ResolvedTransient", "Normal",
                "Resolved transient startup event (" + value(event.reason()) + "): " + value(event.message()),
                event.eventTime(), event.count());
    }

    /** KubernetesCurrentStateAnalysisGuard의 reconcileLog 처리에 필요한 업무 로직을 수행한다. */
    private KubernetesNamespaceDiagnostics.DiagnosticPodLog reconcileLog(
            KubernetesNamespaceDiagnostics.DiagnosticPodLog log,
            Set<String> healthyPods,
            int[] suppressedLogLines
    ) {
        if (!healthyPods.contains(log.podName())) {
            return log;
        }
        String sanitized = value(log.log()).lines()
                .filter(line -> {
                    if (!isRecoveredStartupPermissionNoise(line)) {
                        return true;
                    }
                    suppressedLogLines[0]++;
                    return false;
                })
                .collect(Collectors.joining("\n"));
        return new KubernetesNamespaceDiagnostics.DiagnosticPodLog(
                log.namespace(), log.podName(), log.containerName(), sanitized, log.truncated());
    }

    /** KubernetesCurrentStateAnalysisGuard의 isStablePod 처리 조건의 충족 여부를 판단한다. */
    private boolean isStablePod(KubernetesNamespaceDiagnostics.DiagnosticResource resource) {
        if (!"Running".equalsIgnoreCase(resource.status())) {
            return false;
        }
        try {
            JsonNode summary = objectMapper.readTree(value(resource.summaryJson()));
            int ready = summary.path("readyContainers").asInt(0);
            int total = summary.path("totalContainers").asInt(0);
            int restarts = summary.path("restartCount").asInt(0);
            return total > 0 && ready == total && restarts == 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    /** KubernetesCurrentStateAnalysisGuard의 isProblemResource 처리 조건의 충족 여부를 판단한다. */
    private boolean isProblemResource(KubernetesNamespaceDiagnostics.DiagnosticResource resource) {
        String status = value(resource.status()).toLowerCase(Locale.ROOT);
        return status.contains("pending") || status.contains("failed") || status.contains("error")
                || status.contains("crash") || status.contains("terminating") || status.contains("unknown")
                || isReplicaMismatch(status);
    }

    /** KubernetesCurrentStateAnalysisGuard의 isReplicaMismatch 처리 조건의 충족 여부를 판단한다. */
    private boolean isReplicaMismatch(String status) {
        if (!status.matches("\\d+/\\d+")) {
            return false;
        }
        String[] replicas = status.split("/", 2);
        return !replicas[0].equals(replicas[1]);
    }

    /** KubernetesCurrentStateAnalysisGuard의 isTransientReason 처리 조건의 충족 여부를 판단한다. */
    private boolean isTransientReason(String reason) {
        return TRANSIENT_EVENT_REASONS.contains(value(reason).toLowerCase(Locale.ROOT));
    }

    /** KubernetesCurrentStateAnalysisGuard의 isRecoveredStartupPermissionNoise 처리 조건의 충족 여부를 판단한다. */
    private boolean isRecoveredStartupPermissionNoise(String line) {
        String lower = value(line).toLowerCase(Locale.ROOT);
        return lower.contains("chmod: changing permissions") && lower.contains("operation not permitted");
    }

    /** KubernetesCurrentStateAnalysisGuard의 value 처리에 필요한 업무 로직을 수행한다. */
    private String value(String value) {
        return value == null ? "" : value;
    }
}
