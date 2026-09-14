package io.strato.aiops.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.List;

/** Builds the bounded Kubernetes-evidence performance and scaling result contract. */
@Component
public class DeterministicPerformanceScalingSectionBuilder {

    private static final int MAX_BOTTLENECKS = 6;
    private static final int MAX_EVENT_BOTTLENECKS = 4;
    private static final int MAX_SCALE_CANDIDATES = 8;

    private final ObjectMapper objectMapper;

    public DeterministicPerformanceScalingSectionBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ObjectNode build(Input input) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("_sectionName", "performance-scaling");
        result.put("_sectionSource", "DETERMINISTIC");

        ObjectNode performance = result.putObject("performance");
        performance.put("summary", "Prometheus 미연동 단계이므로 CPU/Memory/Latency 수치 대신 Kubernetes 상태, 이벤트, 로그 신호로 성능 병목을 판단했습니다. "
                + "problemResources=" + input.problemResourceCount()
                + ", warningEvents=" + input.warningEventCount()
                + ", highSignalLogs=" + input.highSignalLogCount() + ".");
        ArrayNode bottlenecks = performance.putArray("bottlenecks");
        safeList(input.resourceBottlenecks()).stream().limit(MAX_BOTTLENECKS).forEach(signal -> appendSignal(bottlenecks, signal));
        safeList(input.eventBottlenecks()).stream().limit(MAX_EVENT_BOTTLENECKS).forEach(signal -> appendSignal(bottlenecks, signal));
        if (bottlenecks.isEmpty()) {
            ObjectNode item = bottlenecks.addObject();
            item.put("resourceKind", "Namespace");
            item.put("resourceName", "-");
            item.put("signal", "No Kubernetes API performance bottleneck signal detected");
            item.put("recommendation", "Prometheus 또는 metrics-server 연동 후 CPU, memory, latency, saturation 지표로 추가 검증하세요.");
            item.putArray("evidence").add("No pending/problem resource, warning event, or high-signal log was selected as a performance bottleneck.");
        }
        ArrayNode improvements = performance.putArray("improvements");
        improvements.add("문제 Pod는 describe/events/log 순서로 확인하고, Pending/ContainerCreating이면 스케줄링/볼륨/이미지 상태를 먼저 검증하세요.");
        improvements.add("핵심 workload의 requests/limits, readiness/liveness/startup probe, endpoint readiness를 점검하세요.");
        improvements.add("정량 성능 판단은 Prometheus 연동 후 CPU/Memory/Latency/Saturation 기준으로 보정하세요.");

        ObjectNode scaling = result.putObject("scaling");
        scaling.put("summary", "Kubernetes API 기준으로 스케일링 준비도를 평가했습니다. "
                + (input.hasDeployment() ? "Deployment가 감지되었습니다. " : "Deployment가 감지되지 않았습니다. ")
                + (input.hasHpa() ? "HPA가 존재합니다." : "HPA가 없어 자동 확장 기준 검토가 필요합니다."));
        ArrayNode scaleUpCandidates = scaling.putArray("scaleUpCandidates");
        if (input.hasDeployment() && !input.hasHpa()) {
            appendScaleSignal(scaleUpCandidates, new Signal("HorizontalPodAutoscaler", "-",
                    "Deployment exists but HPA is not configured",
                    "핵심 Deployment부터 resource requests를 정의하고 HPA target을 적용하세요. 실제 target 값은 metrics-server/Prometheus 연동 후 보정해야 합니다.",
                    "resource inventory includes Deployment but no HorizontalPodAutoscaler"));
        }
        if (input.pendingPods() > 0) {
            appendScaleSignal(scaleUpCandidates, new Signal("NodeCapacity", "-",
                    "Pending pod count=" + input.pendingPods(),
                    "FailedScheduling 이벤트, node allocatable, taint/toleration, PVC binding을 확인한 뒤 node scale-out 또는 requests 조정을 검토하세요.",
                    "Pending Pod status detected in namespace diagnostics"));
        }
        safeList(input.scaleCandidates()).stream().limit(3).forEach(signal -> appendScaleSignal(scaleUpCandidates, signal));
        if (scaleUpCandidates.isEmpty()) {
            appendScaleSignal(scaleUpCandidates, new Signal("Namespace", "-",
                    "No immediate scale-up candidate from Kubernetes API signals",
                    "자동 확장 대상은 트래픽 패턴과 metrics 연동 후 workload별로 선정하세요.",
                    "No Pending pod, problematic HPA, or missing-HPA deployment signal selected as scale-up candidate."));
        }
        while (scaleUpCandidates.size() > MAX_SCALE_CANDIDATES) {
            scaleUpCandidates.remove(scaleUpCandidates.size() - 1);
        }

        ArrayNode hpaRecommendations = scaling.putArray("hpaRecommendations");
        if (input.hasDeployment() && !input.hasHpa()) {
            hpaRecommendations.add("핵심 Deployment에 requests.cpu/memory를 먼저 정의한 뒤 HPA target을 설정하세요.");
        }
        if (input.hasHpa()) {
            hpaRecommendations.add("HPA condition, currentReplicas/desiredReplicas, metricTypes를 점검해 metrics 수집 상태를 확인하세요.");
        }
        if (hpaRecommendations.isEmpty()) {
            hpaRecommendations.add("현재 namespace에서는 HPA 적용 대상 workload를 먼저 식별하세요.");
        }
        ArrayNode capacityNotes = scaling.putArray("capacityNotes");
        capacityNotes.add("Pending pods=" + input.pendingPods() + ", endpointReadyIssues=" + input.unavailableEndpoints() + ".");
        capacityNotes.add(input.hasQuota() ? "ResourceQuota가 존재합니다. quota hard/used 비율을 확인하세요." : "ResourceQuota가 없어 namespace 용량 상한 정책이 없습니다.");
        capacityNotes.add(input.hasLimitRange() ? "LimitRange가 존재합니다. 기본 requests/limits 정책을 확인하세요." : "LimitRange가 없어 기본 requests/limits가 없는 Pod가 생성될 수 있습니다.");
        return result;
    }

    private void appendSignal(ArrayNode target, Signal signal) {
        ObjectNode item = target.addObject();
        item.put("resourceKind", valueOrBlank(signal.resourceKind()));
        item.put("resourceName", valueOrBlank(signal.resourceName()));
        item.put("signal", valueOrBlank(signal.signal()));
        item.put("recommendation", valueOrBlank(signal.recommendation()));
        item.putArray("evidence").add(valueOrBlank(signal.evidence()));
    }

    private void appendScaleSignal(ArrayNode target, Signal signal) {
        ObjectNode item = target.addObject();
        item.put("resourceKind", valueOrBlank(signal.resourceKind()));
        item.put("resourceName", valueOrBlank(signal.resourceName()));
        item.put("currentSignal", valueOrBlank(signal.signal()));
        item.put("recommendation", valueOrBlank(signal.recommendation()));
        item.putArray("evidence").add(valueOrBlank(signal.evidence()));
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values.stream().filter(java.util.Objects::nonNull).toList();
    }

    private static String valueOrBlank(String value) {
        return value == null ? "" : value;
    }

    public record Input(
            boolean hasDeployment,
            boolean hasHpa,
            boolean hasQuota,
            boolean hasLimitRange,
            long pendingPods,
            long unavailableEndpoints,
            int problemResourceCount,
            int warningEventCount,
            long highSignalLogCount,
            List<Signal> resourceBottlenecks,
            List<Signal> eventBottlenecks,
            List<Signal> scaleCandidates
    ) {
    }

    public record Signal(String resourceKind, String resourceName, String signal, String recommendation, String evidence) {
        public Signal(String resourceKind, String resourceName, String signal, String recommendation) {
            this(resourceKind, resourceName, signal, recommendation, "");
        }
    }
}
