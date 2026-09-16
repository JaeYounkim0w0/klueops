package io.strato.aiops.application.service;

import io.strato.aiops.application.port.out.KubernetesNamespaceDiagnostics;
import org.springframework.stereotype.Component;

import java.util.List;

/** Selects bounded Kubernetes signals used by deterministic performance/scaling analysis. */
@Component
public class KubernetesPerformanceSignalPolicy {

    /** KubernetesPerformanceSignalPolicy의 select 처리에 필요한 업무 로직을 수행한다. */
    public DeterministicPerformanceScalingSectionBuilder.Input select(KubernetesNamespaceDiagnostics diagnostics) {
        List<KubernetesNamespaceDiagnostics.DiagnosticResource> problemResources = diagnostics.resources().stream()
                .filter(this::isProblemResource)
                .limit(10)
                .toList();
        List<KubernetesNamespaceDiagnostics.DiagnosticEvent> warningEvents = diagnostics.events().stream()
                .filter(this::isWarningEvent)
                .limit(10)
                .toList();
        long highSignalLogCount = diagnostics.podLogs().stream()
                .filter(this::isHighSignalLog)
                .count();

        List<DeterministicPerformanceScalingSectionBuilder.Signal> resourceBottlenecks = problemResources.stream()
                .filter(this::isPerformanceRelevantResource)
                .limit(6)
                .map(resource -> new DeterministicPerformanceScalingSectionBuilder.Signal(
                        resource.resourceType(), resource.resourceName(), valueOrBlank(resource.status()),
                        performanceRecommendation(resource), truncate(resource.summaryJson(), 300)))
                .toList();
        List<DeterministicPerformanceScalingSectionBuilder.Signal> eventBottlenecks = warningEvents.stream()
                .filter(this::isPerformanceRelevantEvent)
                .limit(4)
                .map(event -> new DeterministicPerformanceScalingSectionBuilder.Signal(
                        valueOrBlank(event.involvedKind()), valueOrBlank(event.involvedName()),
                        valueOrBlank(event.reason()) + " count=" + (event.count() == null ? 0 : event.count()),
                        performanceEventRecommendation(event), truncate(event.message(), 300)))
                .toList();
        List<DeterministicPerformanceScalingSectionBuilder.Signal> scaleCandidates = diagnostics.resources().stream()
                .filter(resource -> "HorizontalPodAutoscaler".equals(resource.resourceType()))
                .filter(this::isProblemResource)
                .limit(3)
                .map(resource -> new DeterministicPerformanceScalingSectionBuilder.Signal(
                        resource.resourceType(), resource.resourceName(), valueOrBlank(resource.status()),
                        "HPA condition과 metrics API 상태를 확인하고 target metric 수집이 가능한지 검증하세요.",
                        truncate(resource.summaryJson(), 260)))
                .toList();

        long pendingPods = diagnostics.resources().stream()
                .filter(resource -> "Pod".equals(resource.resourceType()))
                .filter(resource -> valueOrBlank(resource.status()).toLowerCase().contains("pending"))
                .count();
        long unavailableEndpoints = diagnostics.resources().stream()
                .filter(resource -> "Endpoint".equals(resource.resourceType()))
                .filter(resource -> valueOrBlank(resource.summaryJson()).contains("\"readyAddresses\":0"))
                .count();
        return new DeterministicPerformanceScalingSectionBuilder.Input(
                hasResourceKind(diagnostics, "Deployment"),
                hasResourceKind(diagnostics, "HorizontalPodAutoscaler"),
                hasResourceKind(diagnostics, "ResourceQuota"),
                hasResourceKind(diagnostics, "LimitRange"),
                pendingPods,
                unavailableEndpoints,
                problemResources.size(),
                warningEvents.size(),
                highSignalLogCount,
                resourceBottlenecks,
                eventBottlenecks,
                scaleCandidates);
    }

    /** KubernetesPerformanceSignalPolicy의 hasResourceKind 처리 조건의 충족 여부를 판단한다. */
    private boolean hasResourceKind(KubernetesNamespaceDiagnostics diagnostics, String kind) {
        return diagnostics.resources().stream().anyMatch(resource -> kind.equals(resource.resourceType()));
    }

    /** KubernetesPerformanceSignalPolicy의 isWarningEvent 처리 조건의 충족 여부를 판단한다. */
    private boolean isWarningEvent(KubernetesNamespaceDiagnostics.DiagnosticEvent event) {
        return "Warning".equalsIgnoreCase(event.type());
    }

    /** KubernetesPerformanceSignalPolicy의 isProblemResource 처리 조건의 충족 여부를 판단한다. */
    private boolean isProblemResource(KubernetesNamespaceDiagnostics.DiagnosticResource resource) {
        String status = valueOrBlank(resource.status()).toLowerCase();
        if (status.isBlank()) {
            return false;
        }
        return status.contains("pending") || status.contains("failed") || status.contains("error")
                || status.contains("crash") || status.contains("terminating") || status.contains("unknown")
                || status.matches("\\d+/\\d+") && !status.startsWith(status.substring(status.indexOf('/') + 1) + "/");
    }

    /** KubernetesPerformanceSignalPolicy의 isHighSignalLog 처리 조건의 충족 여부를 판단한다. */
    private boolean isHighSignalLog(KubernetesNamespaceDiagnostics.DiagnosticPodLog log) {
        String text = valueOrBlank(log.log()).toLowerCase();
        return text.contains("error") || text.contains("exception") || text.contains("failed")
                || text.contains("panic") || text.contains("oom") || text.contains("crashloop");
    }

    /** KubernetesPerformanceSignalPolicy의 isPerformanceRelevantResource 처리 조건의 충족 여부를 판단한다. */
    private boolean isPerformanceRelevantResource(KubernetesNamespaceDiagnostics.DiagnosticResource resource) {
        String type = valueOrBlank(resource.resourceType());
        String status = valueOrBlank(resource.status()).toLowerCase();
        return switch (type) {
            case "Pod", "Deployment", "ReplicaSet", "StatefulSet", "DaemonSet", "Endpoint", "PersistentVolumeClaim",
                    "HorizontalPodAutoscaler" -> true;
            default -> status.contains("pending") || status.contains("failed") || status.contains("error") || status.contains("unknown");
        };
    }

    /** KubernetesPerformanceSignalPolicy의 isPerformanceRelevantEvent 처리 조건의 충족 여부를 판단한다. */
    private boolean isPerformanceRelevantEvent(KubernetesNamespaceDiagnostics.DiagnosticEvent event) {
        String reason = valueOrBlank(event.reason()).toLowerCase();
        return reason.contains("failedscheduling") || reason.contains("failedmount") || reason.contains("unhealthy")
                || reason.contains("backoff") || reason.contains("failed") || reason.contains("notready") || reason.contains("oom");
    }

    /** KubernetesPerformanceSignalPolicy의 performanceRecommendation 처리에 필요한 업무 로직을 수행한다. */
    private String performanceRecommendation(KubernetesNamespaceDiagnostics.DiagnosticResource resource) {
        String type = valueOrBlank(resource.resourceType());
        String status = valueOrBlank(resource.status()).toLowerCase();
        if ("Pod".equals(type) && status.contains("pending")) {
            return "Pod 이벤트에서 FailedScheduling/FailedMount 여부를 확인하고 node capacity, PVC, ConfigMap/Secret, image pull 상태를 순서대로 검증하세요.";
        }
        if ("Endpoint".equals(type)) return "Service selector와 Pod readiness를 확인해 트래픽이 실제 ready endpoint로 전달되는지 검증하세요.";
        if ("PersistentVolumeClaim".equals(type)) return "PVC phase, StorageClass, PV binding 상태를 확인하세요.";
        if ("HorizontalPodAutoscaler".equals(type)) return "HPA condition과 metrics API 수집 상태를 확인하세요.";
        if (type.contains("Deployment") || type.contains("StatefulSet") || type.contains("ReplicaSet") || type.contains("DaemonSet")) {
            return "desired/ready replica 차이, rollout status, Pod 이벤트를 확인하세요.";
        }
        return "관련 리소스 describe 결과와 이벤트를 확인해 성능 저하로 이어질 상태 신호인지 검증하세요.";
    }

    /** KubernetesPerformanceSignalPolicy의 performanceEventRecommendation 처리에 필요한 업무 로직을 수행한다. */
    private String performanceEventRecommendation(KubernetesNamespaceDiagnostics.DiagnosticEvent event) {
        String reason = valueOrBlank(event.reason()).toLowerCase();
        if (reason.contains("failedscheduling")) return "node allocatable, taint/toleration, affinity, requests/limits, quota 상태를 확인하세요.";
        if (reason.contains("failedmount")) return "ConfigMap/Secret/PVC 이름과 volumeMount 설정, StorageClass/PV binding 상태를 확인하세요.";
        if (reason.contains("unhealthy")) return "readiness/liveness probe path, timeout, initialDelay, 애플리케이션 응답 시간을 확인하세요.";
        if (reason.contains("backoff")) return "container 종료 코드와 이전 로그를 확인하고 반복 재시작 원인을 분류하세요.";
        return "이벤트 발생 시각과 대상 리소스 상태를 함께 확인하세요.";
    }

    /** KubernetesPerformanceSignalPolicy의 truncate 처리에 필요한 업무 로직을 수행한다. */
    private static String truncate(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    /** KubernetesPerformanceSignalPolicy의 valueOrBlank 처리에 필요한 업무 로직을 수행한다. */
    private static String valueOrBlank(String value) {
        return value == null ? "" : value;
    }
}
